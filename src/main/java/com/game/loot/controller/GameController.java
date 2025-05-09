package com.game.loot.controller;

import com.game.loot.pojo.Room;
import com.game.loot.pojo.request.MoveRequest;
import com.game.loot.service.GameService;
import com.game.loot.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game")
public class GameController {

    @Autowired
    GameService gameService;

    @Autowired
    RoomService roomService;

    @Autowired
    private SimpMessagingTemplate template;

    @PostMapping("/populate")
    public ResponseEntity<String> populateCards() {
        gameService.populateAllCards();
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/moveCard")
    public ResponseEntity<Boolean> moveCard(@RequestBody MoveRequest moveRequest) {
        Room room = roomService.getRoom(moveRequest.getRoomCode());
        moveRequest.setRoomId(room.getId());
        boolean isMyTurn = gameService.checkTurn(moveRequest);

        if (!isMyTurn) {
            return ResponseEntity.ok(false);
        }

        boolean canMove = gameService.canMove(moveRequest);

        if (canMove) {
            gameService.resetPassCount(room.getId());
            gameService.moveCard(moveRequest);
        } else {
            return ResponseEntity.ok(false);
        }

        return ResponseEntity.ok(true);
    }

    @GetMapping("/drawCard/{roomCode}")
    public ResponseEntity<Integer> getCard(@PathVariable String roomCode, @RequestParam String playerId) {
        MoveRequest request = new MoveRequest();
        request.setRoomCode(roomCode);
        request.setPlayerId(playerId);
        boolean isMyTurn = gameService.checkTurn(request);

        if (!isMyTurn) {
            return ResponseEntity.ok(-1);
        }

        int remainingCards = gameService.getCard(roomCode);
        if (remainingCards == 0) {
            gameService.updatePassAndGame(roomCode);
        }
        return ResponseEntity.ok(remainingCards);
    }
}
