package com.game.loot.repository;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.request.MoveRequest;

import java.util.List;
import java.util.Map;

public interface GameRepositoryService {
    void insertCardsToDB(List<Object[]> cards);

    List<Card> getAllCards(List<Player> current);

    void updateChosenCards(List<Object[]> params);

    List<Card> getRemainingCards(String id);

    List<PlayedCard> getPlayedCards(List<Player> players);

    Map<String, List<Card>> getPlayerCards(List<Player> players);

    List<PlayedCard> getCurrentPlayedStack(String roomId, List<Player> players, PlayedCard target);

    void moveToGameArea(MoveRequest moveRequest);

    Integer getMerchantValue(Room room, String stackId);

    void updateDiscardPile(List<Object> winningStacks, String roomId);

    void updatePoints(int totalPoints, String roomId, String playerId);

    List<PlayedCard> getPlayedCardByStack(MoveRequest moveRequest);

    void updateStackWinner(String playerId, String stackId);

    String checkWinningPlayer(Room room, String playerId);

    Map<String, Integer> getPlayerScores(List<Player> players);
}
