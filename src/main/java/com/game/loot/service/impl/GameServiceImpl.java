package com.game.loot.service.impl;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Color;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.RoomState;
import com.game.loot.pojo.Type;
import com.game.loot.pojo.request.MoveRequest;
import com.game.loot.service.GameService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.game.loot.store.CardCatalog;
import com.game.loot.store.InMemoryGameStore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Log4j2
public class GameServiceImpl implements GameService {

    @Autowired
    InMemoryGameStore store;

    @Autowired
    CardCatalog catalog;

    @Override
    public RoomState dealCards(List<Player> players, String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            catalog.ensureLoaded();

            // Reset per-room game state for a fresh start.
            room.game = "STARTED";
            room.pass = 0;
            room.points.clear();
            room.stacks.clear();
            room.discard.clear();
            room.hands.clear();
            room.deck.clear();

            room.deck.addAll(catalog.newDeck());
            Collections.shuffle(room.deck, new SecureRandom());

            for (Player p : room.players) {
                room.points.putIfAbsent(p.getId(), 0);
                room.hands.put(p.getId(), new ArrayList<>());
                for (int i = 0; i < 6 && !room.deck.isEmpty(); i++) {
                    room.hands.get(p.getId()).add(room.deck.remove(room.deck.size() - 1));
                }
            }

            RoomState state = new RoomState();
            state.setRoomCode(roomCode);
            state.setPlayers(new ArrayList<>(room.players));
            state.setHands(copyHands(room.hands));
            state.setPlayedCards(new ArrayList<>());
            state.setGame(room.game);
            return state;
        }
    }

    private static Map<String, List<Card>> copyHands(Map<String, List<Card>> hands) {
        Map<String, List<Card>> copied = new HashMap<>();
        for (Map.Entry<String, List<Card>> e : hands.entrySet()) {
            copied.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copied;
    }

    @Override
    public void populateAllCards() {
        // In-memory mode: warm the in-memory catalog (idempotent).
        catalog.ensureLoaded();
    }

    @Override
    public RoomState currentState(List<Player> players, String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            RoomState state = new RoomState();
            state.setRoomCode(roomCode);
            state.setPlayers(new ArrayList<>(room.players));
            state.setHands(copyHands(room.hands));
            state.setPlayedCards(flattenPlayed(room));
            state.setGame(room.game);
            state.setCurrentTurn(room.currentTurn);
            return state;
        }
    }

    @Override
    public boolean checkTurn(MoveRequest moveRequest) {
        InMemoryGameStore.RoomData room = store.getRoom(moveRequest.getRoomCode());
        String currentPlayer = room.currentTurn;
        return currentPlayer != null && currentPlayer.equalsIgnoreCase(moveRequest.getPlayerId());
    }

    @Override
    public Boolean canMove(MoveRequest moveRequest) {
        InMemoryGameStore.RoomData room = store.getRoom(moveRequest.getRoomCode());
        PlayedCard target = moveRequest.getCard();
        Card selected = moveRequest.getSelectedCard();

        if (selected == null) return false;
        if (target == null) return selected.getType() == Type.MERCHANT;

        synchronized (room.lock) {
            InMemoryGameStore.StackData stack = findStack(room, target.getStackId());
            if (stack == null) return false;

            Color myColor = null;
            Type myType = null;
            List<Color> othersColors = new ArrayList<>();

            for (PlayedCard c : stack.cards) {
                if (c.getType() == Type.MERCHANT) continue;
                if (c.getPlayerId().equalsIgnoreCase(moveRequest.getPlayerId())) {
                    if (c.getType() == Type.PIRATE) myColor = c.getColor();
                    if (c.getType() == Type.CAPTAIN || c.getType() == Type.ADMIRAL) myType = c.getType();
                } else {
                    othersColors.add(c.getColor());
                }
            }

            if (myType != null) return false;

            if (selected.getType() == Type.PIRATE) {
                return !othersColors.contains(selected.getColor())
                        && (myColor == null || selected.getColor().equals(myColor));
            } else if (selected.getType() == Type.CAPTAIN) {
                return selected.getColor().equals(myColor);
            } else if (selected.getType() == Type.ADMIRAL) {
                return stack.ownerId.equalsIgnoreCase(moveRequest.getPlayerId());
            } else {
                return false;
            }
        }
    }

    @Override
    public void moveCard(MoveRequest moveRequest) {
        InMemoryGameStore.RoomData room = store.getRoom(moveRequest.getRoomCode());
        synchronized (room.lock) {
            List<Card> hand = room.hands.getOrDefault(moveRequest.getPlayerId(), new ArrayList<>());
            Card selected = removeFromHand(hand, moveRequest.getSelectedCard() == null ? null : moveRequest.getSelectedCard().getId());
            room.hands.put(moveRequest.getPlayerId(), hand);
            if (selected == null) return;

            InMemoryGameStore.StackData stack;
            boolean isNewStack = (moveRequest.getCard() == null);
            if (isNewStack) {
                stack = new InMemoryGameStore.StackData(UUID.randomUUID().toString(), moveRequest.getPlayerId());
                room.stacks.add(stack);
            } else {
                stack = findStack(room, moveRequest.getCard().getStackId());
                if (stack == null) return;
            }

            PlayedCard played = new PlayedCard();
            played.setId(UUID.randomUUID().toString());
            played.setStackId(stack.id);
            played.setCardId(selected.getId());
            played.setPlayerId(moveRequest.getPlayerId());
            played.setType(selected.getType());
            played.setColor(selected.getColor());
            played.setValue(selected.getValue());
            played.setCreatedAt(new java.util.Date());
            played.setIndex(playerIndex(room, moveRequest.getPlayerId()));
            stack.cards.add(played);

            // Finish if player is out of cards and deck is empty (matches old behavior).
            if (hand.isEmpty() && room.deck.isEmpty()) {
                room.pass = 1;
                room.game = "FINISHED";
            }

            if (isNewStack) return;

            boolean winner = false, noWinner = false;
            if (selected.getType() == Type.ADMIRAL || selected.getType() == Type.CAPTAIN) {
                winner = true;
            } else {
                Map<String, Integer> valueByPlayer = new HashMap<>();
                for (PlayedCard c : stack.cards) {
                    if (c.getType() != Type.MERCHANT) {
                        valueByPlayer.put(
                                c.getPlayerId(),
                                valueByPlayer.getOrDefault(c.getPlayerId(), 0) + (c.getValue() == null ? 0 : c.getValue())
                        );
                    }
                }

                int maxValue = -1, count = 0;
                int myValue = valueByPlayer.getOrDefault(moveRequest.getPlayerId(), 0);
                for (int v : valueByPlayer.values()) {
                    if (v > maxValue) {
                        maxValue = v;
                        count = 1;
                    } else if (v == maxValue) {
                        count++;
                    }
                }

                if (myValue == maxValue && count < 2) winner = true;
                if (count >= 2) noWinner = true;
            }

            if (noWinner) stack.winPlayerId = null;
            if (winner) stack.winPlayerId = moveRequest.getPlayerId();
        }
    }

    @Override
    public List<Object> getWinningStacks(Room room, String playerId) {
        InMemoryGameStore.RoomData data = store.getRoom(room.getCode());
        synchronized (data.lock) {
            for (InMemoryGameStore.StackData s : data.stacks) {
                if (s.winPlayerId != null && s.winPlayerId.equalsIgnoreCase(playerId)) {
                    List<Object> out = new ArrayList<>();
                    out.add(s.id);
                    out.add(s.merchantValue());
                    return out;
                }
            }
            return new ArrayList<>();
        }
    }

    @Override
    public void discardStack(List<Object> stackData, String roomId, String playerId) {
        InMemoryGameStore.RoomData room = store.getRoomById(roomId);
        synchronized (room.lock) {
            String stackId = stackData.get(0).toString();
            InMemoryGameStore.StackData stack = findStack(room, stackId);
            if (stack == null) return;

            for (PlayedCard pc : stack.cards) {
                room.discard.add(new Card(pc.getCardId(), pc.getColor(), pc.getType(), pc.getValue()));
            }
            room.points.put(playerId, room.points.getOrDefault(playerId, 0) + (Integer) stackData.get(1));
            room.stacks.remove(stack);
        }
    }

    @Override
    public int getCard(String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            store.ensureDeck(room);
            int remaining = room.deck.size();
            if (remaining == 0) return 0;

            String playerId = room.currentTurn;
            if (playerId == null) return 0;

            int position = new SecureRandom().nextInt(room.deck.size());
            Card drawn = room.deck.remove(position);
            room.hands.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(drawn);

            // Match old semantics: return remaining BEFORE the draw.
            return remaining;
        }
    }

    @Override
    public void updatePassAndGame(String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            room.pass = room.pass + 1;
            if (room.pass >= room.players.size()) {
                room.game = "FINISHED";
            }
        }
    }

    @Override
    public void resetPassCount(String id) {
        InMemoryGameStore.RoomData room = store.getRoomById(id);
        synchronized (room.lock) {
            room.game = "STARTED";
            room.pass = 0;
        }
    }

    private static Card removeFromHand(List<Card> hand, String cardId) {
        if (hand == null || cardId == null) return null;
        for (int i = 0; i < hand.size(); i++) {
            if (cardId.equalsIgnoreCase(hand.get(i).getId())) return hand.remove(i);
        }
        return null;
    }

    private static InMemoryGameStore.StackData findStack(InMemoryGameStore.RoomData room, String stackId) {
        if (stackId == null) return null;
        for (InMemoryGameStore.StackData s : room.stacks) {
            if (s.id.equalsIgnoreCase(stackId)) return s;
        }
        return null;
    }

    private static List<PlayedCard> flattenPlayed(InMemoryGameStore.RoomData room) {
        List<PlayedCard> out = new ArrayList<>();
        for (InMemoryGameStore.StackData s : room.stacks) {
            out.addAll(s.cards);
        }
        return out;
    }

    private static int playerIndex(InMemoryGameStore.RoomData room, String playerId) {
        for (Player p : room.players) {
            if (p.getId().equalsIgnoreCase(playerId)) {
                return p.getIndex() == null ? 0 : p.getIndex();
            }
        }
        return 0;
    }
}
