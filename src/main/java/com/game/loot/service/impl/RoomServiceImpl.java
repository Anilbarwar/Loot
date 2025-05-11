package com.game.loot.service.impl;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.Type;
import com.game.loot.repository.GameRepositoryService;
import com.game.loot.repository.RoomRepositoryService;
import com.game.loot.service.GameService;
import com.game.loot.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Service
public class RoomServiceImpl implements RoomService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    @Autowired
    RoomRepositoryService roomRepositoryService;

    @Autowired
    GameService gameService;

    @Autowired
    GameRepositoryService gameRepositoryService;

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
        Room room = new Room();
        roomRepositoryService.insertRoomData(roomCode);
        room.setCode(roomCode);
        return room;
    }

    @Override
    public Room getRoom(String roomCode) {
        return roomRepositoryService.getRoomByCode(roomCode);
    }

    @Override
    public List<Player> insertPlayerDetail(String playerName, Room room) {
        return roomRepositoryService.insertPlayerDetails(playerName, room);
    }

    @Override
    public List<Player> getPlayerByRoom(String roomCode) {
        return roomRepositoryService.getPlayerByRoom(roomCode);
    }

    @Override
    public String initializeTurn(List<Player> players, String roomCode) {
        String playerId = roomRepositoryService.getCurrentPlayer(roomCode);

        int index = 0;
        if (playerId != null) {
            for (int i = 0;i < players.size();i++) {
                if (players.get(i).getId().equalsIgnoreCase(playerId)) {
                    index = (i+1) % players.size();
                    break;
                }
            }
        }

        roomRepositoryService.setPlayerTurn(players.get(index), roomCode);
        return players.get(index).getId();
    }

    @Override
    public List<Player> getAllPlayers(Room room) {
        return roomRepositoryService.getAllPlayers(room.getId());
    }

    @Override
    public boolean checkWin(String roomCode) {
        Room room = roomRepositoryService.getRoomByCode(roomCode);
        String playerId = roomRepositoryService.getCurrentPlayer(room.getCode());
        List<Object> stackData = gameService.getWinningStacks(room, playerId);

        if (!stackData.isEmpty()) {
            gameService.discardStack(stackData, room.getId(), playerId);
            return true;
        }
        return false;
    }

    @Override
    public String checkGameOver(String roomCode) {
        return roomRepositoryService.checkGameOver(roomCode);

    }

    @Override
    public String updateWinner(Room room) {
        List<Player> players = roomRepositoryService.getAllPlayers(room.getId());
        Map<String, List<Card>> plyerCardMap = gameRepositoryService.getPlayerCards(players);
        Map<String, Integer> playerScoreMap = gameRepositoryService.getPlayerScores(players);

        for (String player: plyerCardMap.keySet()) {
            if (!playerScoreMap.containsKey(player)) {
                playerScoreMap.put(player, 0);
            }
        }

        for (Map.Entry<String, List<Card>> entry: plyerCardMap.entrySet()) {
            String playerId = entry.getKey();
            for (Card card: entry.getValue()) {
                if  (card.getType().equals(Type.MERCHANT)) {
                    playerScoreMap.put(playerId, playerScoreMap.getOrDefault(playerId, 0) - card.getValue());
                }
            }
        }

        StringBuilder winner = new StringBuilder();
        int maximum = -1000;
        for (Player player: players) {
            if (playerScoreMap.get(player.getId()) > maximum) {
                winner = new StringBuilder(player.getName());
                maximum = playerScoreMap.get(player.getId());
            } else if (playerScoreMap.get(player.getId()) == maximum) {
                winner.append(", ").append(player.getName());
            }
        }

        return winner.toString();
    }

    @Override
    public Map<String, Integer> getPlayerValueMap(String roomCode) {
        Room room = roomRepositoryService.getRoomByCode(roomCode);
        return roomRepositoryService.getPlayerScoreMap(room);
    }

}
