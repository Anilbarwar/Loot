package com.game.loot.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayedStack {
    private String stackId;
    private String ownerId;
    private String cardId;
    private List<PlayedCard> cards;
}
