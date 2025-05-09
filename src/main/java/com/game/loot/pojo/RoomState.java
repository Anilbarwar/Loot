package com.game.loot.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomState {
    private String roomCode;
    private String currentTurn;
    private String game;
    private List<Player> players;
    private Map<String, List<Card>> hands;
    private List<PlayedCard> playedCards;
    private Map<String, Integer> playerValueMap;
}
