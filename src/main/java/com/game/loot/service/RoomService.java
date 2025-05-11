package com.game.loot.service;

import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;

import java.util.List;
import java.util.Map;

public interface RoomService {
    String generateCode();

    Room createRoom(String roomCode);

    Room getRoom(String roomCode);

    List<Player> insertPlayerDetail(String playerName, Room room);

    List<Player> getPlayerByRoom(String roomCode);

    String initializeTurn(List<Player> players, String roomCode);

    List<Player> getAllPlayers(Room room);

    boolean checkWin(String roomCode);

    String checkGameOver(String roomCode);

    String updateWinner(Room room);

    Map<String, Integer> getPlayerValueMap(String roomCode);
}
