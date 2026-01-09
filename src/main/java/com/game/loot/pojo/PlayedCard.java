package com.game.loot.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayedCard {
    private Type type;
    private Color color;
    private Integer value;
    private String id;
    private String stackId;
    private String cardId;
    private String playerId;
    private Date createdAt;
    private Integer index;
}
