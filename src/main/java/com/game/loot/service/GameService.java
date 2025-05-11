package com.game.loot.service;

import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.RoomState;
import com.game.loot.pojo.request.MoveRequest;

import java.util.List;
import java.util.Map;

public interface GameService {
    RoomState dealCards(List<Player> players, String roomCode);

    void populateAllCards();

    RoomState currentState(List<Player> players, String roomCode);

    boolean checkTurn(MoveRequest playCard);

    Boolean canMove(MoveRequest moveRequest);

    void moveCard(MoveRequest moveRequest);

    List<Object> getWinningStacks(Room room, String playerId);

    void discardStack(List<Object> stackId, String id, String playerId);

    int getCard(String roomCode);

    void updatePassAndGame(String roomCode);

    void resetPassCount(String id);
}
