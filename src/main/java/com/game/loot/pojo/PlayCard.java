package com.game.loot.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayCard {
    private String roomCode;
    private String playerId;
    private String cardId;
    private String targetPlayerId;
    private String targetCardId;
}
