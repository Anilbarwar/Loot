package com.game.loot.service.impl;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Color;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.RoomState;
import com.game.loot.pojo.Type;
import com.game.loot.pojo.request.MoveRequest;
import com.game.loot.repository.GameRepositoryService;
import com.game.loot.repository.RoomRepositoryService;
import com.game.loot.service.GameService;
import com.opencsv.CSVReader;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    GameRepositoryService gameRepositoryService;

    @Autowired
    RoomRepositoryService roomRepositoryService;

    @Override
    public RoomState dealCards(List<Player> players, String roomCode) {
        List<Card> deckCards = gameRepositoryService.getAllCards(players);

        List<Object[]> distributeParam = new ArrayList<>();
        RoomState roomState = new RoomState();
        roomState.setRoomCode(roomCode);
        roomState.setPlayers(players);
        roomState.setHands(getPlayerCards(players, deckCards, distributeParam));
        roomState.setPlayedCards(new ArrayList<>());

        gameRepositoryService.updateChosenCards(distributeParam);

        return roomState;

    }

    private static Map<String, List<Card>> getPlayerCards(List<Player> players, List<Card> deckCards,
                                                          List<Object[]> distributeParam) {
        SecureRandom random = new SecureRandom();
        Map<String, List<Card>> playerCards = new HashMap<>();
        for (Player player: players) {
            List<Card> cards = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int position = random.nextInt(deckCards.size());
                cards.add(deckCards.get(position));
                distributeParam.add(new Object[]{
                        UUID.fromString(player.getId()),
                        UUID.fromString(deckCards.get(position).getId()),
                });
                deckCards.remove(position);
            }
            playerCards.put(player.getId(), cards);
        }
        return playerCards;
    }

    @Override
    public void populateAllCards() {
        List<Object[]> cards = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new ClassPathResource("./Cards.csv")
                .getInputStream(), StandardCharsets.UTF_8))) {
            String[] line;
            reader.readNext();
            while ((line = reader.readNext()) != null) {
                for (int i = 0;i < Integer.parseInt(line[3]);i++) {
                    cards.add(new Object[]{
                            line[0], line[1], line[2]
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse csv");
        }

        gameRepositoryService.insertCardsToDB(cards);
    }

    @Override
    public RoomState currentState(List<Player> players, String roomCode) {
        Room room = roomRepositoryService.getRoomByCode(roomCode);
        RoomState roomState = new RoomState();
        roomState.setRoomCode(roomCode);
        roomState.setPlayers(players);
        roomState.setHands(gameRepositoryService.getPlayerCards(players));
        roomState.setPlayedCards(gameRepositoryService.getCurrentPlayedStack(room.getId(), players, null));

        return roomState;
    }

    @Override
    public boolean checkTurn(MoveRequest moveRequest) {
        String currentPlayer = roomRepositoryService.getCurrentPlayer(moveRequest.getRoomCode());
        return currentPlayer.equalsIgnoreCase(moveRequest.getPlayerId());
    }

    @Override
    public Boolean canMove(MoveRequest moveRequest) {
        PlayedCard target = moveRequest.getCard();
        Card selected = moveRequest.getSelectedCard();
        if (target == null) {
            return selected.getType().equals(Type.MERCHANT);
        }

        List<PlayedCard> playedStack = gameRepositoryService.getCurrentPlayedStack(moveRequest.getRoomId(),
                new ArrayList<>(), target);

        Color myColor = null;
        Type myType = null;
        List<Color> othersColors = new ArrayList<>();
        for (PlayedCard card: playedStack) {
            if (card.getType().equals(Type.MERCHANT)) {
                target = card;
                continue;
            }
            if (card.getPlayerId().equalsIgnoreCase(moveRequest.getPlayerId())) {
                if (card.getType().equals(Type.PIRATE)) {
                    myColor = card.getColor();
                }
                if (card.getType().equals(Type.CAPTAIN) || card.getType().equals(Type.ADMIRAL)) {
                    myType = card.getType();
                }
            } else {
                othersColors.add(card.getColor());
            }
        }

        if (myType != null) {
            return false;
        }

        if (selected.getType().equals(Type.PIRATE)) {
            return !othersColors.contains(selected.getColor())
                    && (myColor == null || selected.getColor().equals(myColor));
        } else if (selected.getType().equals(Type.CAPTAIN)) {
            return selected.getColor().equals(myColor);
        } else if (selected.getType().equals(Type.ADMIRAL)) {
            return target.getPlayerId().equals(moveRequest.getPlayerId());
        } else {
            return false;
        }
    }

    @Override
    public void moveCard(MoveRequest moveRequest) {
        gameRepositoryService.moveToGameArea(moveRequest);

        Player currentPlayer = new Player();
        currentPlayer.setId(moveRequest.getPlayerId());

        int cardCount = gameRepositoryService.getPlayerCards(Collections.singletonList(currentPlayer))
                .get(moveRequest.getPlayerId()).size();

        if (cardCount == 0) {
            int remainCard = gameRepositoryService.getRemainingCards(moveRequest.getRoomId()).size();
            if (remainCard == 0) {
                Room room = new Room();
                room.setId(moveRequest.getRoomId());
                room.setPass(1);
                room.setGame("FINISHED");
                roomRepositoryService.updateRoom(room);
            }
        }

        boolean winner = false, noWinner = false;

        if (moveRequest.getCard() != null) {
            if (moveRequest.getSelectedCard().getType().equals(Type.ADMIRAL)
                    || moveRequest.getSelectedCard().getType().equals(Type.CAPTAIN)) {
                winner = true;
            } else {
                List<PlayedCard> playedCards = gameRepositoryService.getPlayedCardByStack(moveRequest);

                Map<String, Integer> playerValueMap = new HashMap<>();
                for (PlayedCard card : playedCards) {
                    if (!card.getType().equals(Type.MERCHANT)) {
                        int value = playerValueMap.getOrDefault(card.getPlayerId(), 0);
                        playerValueMap.put(card.getPlayerId(), value + card.getValue());
                    }
                }

                int maxValue = -1, count = 0, playerValue = 0;
                for (Map.Entry<String, Integer> entry: playerValueMap.entrySet()) {
                    if (entry.getKey().equals(moveRequest.getPlayerId())) {
                        playerValue += entry.getValue();
                    }

                    if (maxValue < entry.getValue()) {
                        maxValue = entry.getValue();;
                        count = 1;
                    } else if (maxValue == entry.getValue()) {
                        count++;
                    }
                }

                if (playerValue == maxValue && count < 2) {
                    winner = true;
                }
                if (count >= 2) {
                    noWinner = true;
                }
            }
        }

        if (noWinner) {
            gameRepositoryService.updateStackWinner(null, moveRequest.getCard().getStackId());
        }

        if (winner) {
            gameRepositoryService.updateStackWinner(moveRequest.getPlayerId(), moveRequest.getCard().getStackId());
        }

    }

    @Override
    public List<Object> getWinningStacks(Room room, String playerId) {
        List<Object> data = new ArrayList<>();
        String stackId = gameRepositoryService.checkWinningPlayer(room, playerId);
        if (stackId == null) {
            return new ArrayList<>();
        }

        Integer winValue = gameRepositoryService.getMerchantValue(room, stackId);
        data.add(stackId);
        data.add(winValue);
        return data;
    }

    @Override
    public void discardStack(List<Object> stackData, String roomId, String playerId) {
        gameRepositoryService.updateDiscardPile(stackData, roomId);
        gameRepositoryService.updatePoints((Integer) stackData.get(1), roomId, playerId);
    }

    @Override
    public int getCard(String roomCode) {
        Room room = roomRepositoryService.getRoomByCode(roomCode);
        List<Card> cards = gameRepositoryService.getRemainingCards(room.getId());

        if (cards.isEmpty()) {
            return 0;
        }

        String playerId = roomRepositoryService.getCurrentPlayer(roomCode);

        SecureRandom random = new SecureRandom();
        int position = random.nextInt(cards.size());
        List<Object[]> params = Collections.singletonList(new Object[]{
                UUID.fromString(playerId), UUID.fromString(cards.get(position).getId())
        });
        gameRepositoryService.updateChosenCards(params);
        return cards.size();
    }

    @Override
    public void updatePassAndGame(String roomCode) {
        Room room = roomRepositoryService.getRoomByCode(roomCode);
        room.setPass(room.getPass() + 1);

        List<Player> players = roomRepositoryService.getAllPlayers(room.getId());

        if (room.getPass() >= players.size()) {
            room.setGame("FINISHED");
        }

        roomRepositoryService.updateRoom(room);
    }

    @Override
    public void resetPassCount(String id) {
        Room room = new Room();
        room.setId(id);
        room.setGame("STARTED");
        room.setPass(0);
        roomRepositoryService.updateRoom(room);
    }
}
