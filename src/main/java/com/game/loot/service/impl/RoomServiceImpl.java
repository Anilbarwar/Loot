package com.game.loot.service.impl;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.Type;
import com.game.loot.service.GameService;
import com.game.loot.service.RoomService;
import com.game.loot.store.InMemoryGameStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomServiceImpl implements RoomService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    @Autowired
    InMemoryGameStore store;

    @Autowired
    GameService gameService;

    @Override
    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    @Override
    public Room createRoom(String roomCode) {
        return store.createRoom(roomCode).toRoomPojo();
    }

    @Override
    public Room getRoom(String roomCode) {
        return store.getRoom(roomCode).toRoomPojo();
    }

    @Override
    public List<Player> insertPlayerDetail(String playerName, Room room) {
        InMemoryGameStore.RoomData data = store.getRoom(room.getCode());
        synchronized (data.lock) {
            Player p = new Player();
            p.setId(UUID.randomUUID().toString());
            p.setName(playerName);
            p.setIndex(data.players.size() + 1);
            data.players.add(p);
            data.hands.putIfAbsent(p.getId(), new ArrayList<>());
            data.points.putIfAbsent(p.getId(), 0);
            return new ArrayList<>(data.players);
        }
    }

    @Override
    public List<Player> getPlayerByRoom(String roomCode) {
        InMemoryGameStore.RoomData data = store.getRoom(roomCode);
        synchronized (data.lock) {
            return new ArrayList<>(data.players);
        }
    }

    @Override
    public String initializeTurn(List<Player> players, String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            if (room.players.isEmpty()) return null;

            String current = room.currentTurn;
            int index = 0;
            if (current != null) {
                for (int i = 0; i < room.players.size(); i++) {
                    if (room.players.get(i).getId().equalsIgnoreCase(current)) {
                        index = (i + 1) % room.players.size();
                        break;
                    }
                }
            }

            room.currentTurn = room.players.get(index).getId();
            return room.currentTurn;
        }
    }

    @Override
    public List<Player> getAllPlayers(Room room) {
        InMemoryGameStore.RoomData data = store.getRoomById(room.getId());
        synchronized (data.lock) {
            return new ArrayList<>(data.players);
        }
    }

    @Override
    public boolean checkWin(String roomCode) {
        InMemoryGameStore.RoomData data = store.getRoom(roomCode);
        synchronized (data.lock) {
            if (data.currentTurn == null) return false;
            Room room = data.toRoomPojo();
            String playerId = data.currentTurn;
            List<Object> stackData = gameService.getWinningStacks(room, playerId);
            if (!stackData.isEmpty()) {
                gameService.discardStack(stackData, room.getId(), playerId);
                return true;
            }
            return false;
        }
    }

    @Override
    public String checkGameOver(String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            return room.game;
        }

    }

    @Override
    public String updateWinner(Room room) {
        InMemoryGameStore.RoomData data = store.getRoomById(room.getId());
        synchronized (data.lock) {
            Map<String, Integer> scoreByPlayer = new HashMap<>();
            for (Player p : data.players) {
                scoreByPlayer.put(p.getId(), data.points.getOrDefault(p.getId(), 0));
            }

            // Subtract merchant value remaining in hand (same rule as old DB logic).
            for (Map.Entry<String, List<Card>> e : data.hands.entrySet()) {
                for (Card c : e.getValue()) {
                    if (c.getType() == Type.MERCHANT) {
                        scoreByPlayer.put(e.getKey(), scoreByPlayer.getOrDefault(e.getKey(), 0) - (c.getValue() == null ? 0 : c.getValue()));
                    }
                }
            }

            StringBuilder winner = new StringBuilder();
            int maximum = Integer.MIN_VALUE;
            for (Player p : data.players) {
                int s = scoreByPlayer.getOrDefault(p.getId(), 0);
                if (s > maximum) {
                    winner = new StringBuilder(p.getName());
                    maximum = s;
                } else if (s == maximum) {
                    winner.append(", ").append(p.getName());
                }
            }
            return winner.toString();
        }
    }

    @Override
    public Map<String, Integer> getPlayerValueMap(String roomCode) {
        InMemoryGameStore.RoomData room = store.getRoom(roomCode);
        synchronized (room.lock) {
            Map<String, Integer> out = new HashMap<>();
            for (Player p : room.players) {
                out.put(p.getName(), room.points.getOrDefault(p.getId(), 0));
            }
            return out;
        }
    }

}
