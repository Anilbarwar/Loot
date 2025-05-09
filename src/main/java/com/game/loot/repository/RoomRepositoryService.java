package com.game.loot.repository;

import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;

import java.util.List;
import java.util.Map;

public interface RoomRepositoryService {
    void insertRoomData(String roomCode);

    Room getRoomByCode(String roomCode);

    List<Player> insertPlayerDetails(String playerName, Room room);

    List<Player> getPlayerByRoom(String roomCode);

    void setPlayerTurn(Player player, String roomCode);

    String getCurrentPlayer(String roomCode);

    List<Player> getAllPlayers(String roomId);

    void updateRoom(Room room);

    String checkGameOver(String roomCode);

    int checkPassCount(String roomId);

    Map<String, Integer> getPlayerScoreMap(Room room);
}
