package com.game.loot.pojo.request;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.PlayedCard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveRequest {
    String roomCode;
    String roomId;
    String playerId;
    PlayedCard card;
    Card selectedCard;
}
