package com.game.loot.controller;

import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.RoomState;
import com.game.loot.service.GameService;
import com.game.loot.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/room")
public class RoomController {

    @Autowired
    RoomService roomService;

    @Autowired
    GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/getRoom")
    public ResponseEntity<Room> createRoom() {
        String roomCode = roomService.generateCode();
        Room room = roomService.createRoom(roomCode);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/joinRoom/{roomCode}/{playerName}")
    public ResponseEntity<List<Player>> joinRoom(@PathVariable String roomCode,
                                                 @PathVariable String playerName) {
        Room room = roomService.getRoom(roomCode);
        if ("NOT_STARTED".equals(room.getGame())) {
            List<Player> updatedPlayers = roomService.insertPlayerDetail(playerName, room);
            messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/players", updatedPlayers);
            return ResponseEntity.ok(updatedPlayers);
        } else {
            return ResponseEntity.badRequest().body(new ArrayList<>());
        }
    }

    @GetMapping("/players/{roomCode}")
    public ResponseEntity<List<Player>> getAllPlayers(@PathVariable String roomCode) {
        Room room = roomService.getRoom(roomCode);
        return ResponseEntity.ok(roomService.getAllPlayers(room));
    }

    @PostMapping("/start/{roomCode}")
    public ResponseEntity<RoomState> startGame(@PathVariable String roomCode) {
        List<Player> players = roomService.getPlayerByRoom(roomCode);
        String currentTurn = roomService.initializeTurn(players, roomCode);
        RoomState state = gameService.dealCards(players, roomCode);
        state.setCurrentTurn(currentTurn);

        messagingTemplate.convertAndSend("/topic/start/" + roomCode, state);

        return ResponseEntity.ok(state);
    }

    @GetMapping("/state/{roomCode}")
    public ResponseEntity<RoomState> getCurrentRoomState(@PathVariable String roomCode) {
        List<Player> players = roomService.getPlayerByRoom(roomCode);
        String currentTurn = roomService.initializeTurn(players, roomCode);

        boolean win = roomService.checkWin(roomCode);

        RoomState state = gameService.currentState(players, roomCode);
        state.setGame(roomService.checkGameOver(roomCode));
        state.setCurrentTurn(currentTurn);
        if (win) {
            state.setPlayerValueMap(roomService.getPlayerValueMap(roomCode));
        }
        messagingTemplate.convertAndSend("/topic/state/" + roomCode, state);
        return ResponseEntity.ok(state);
    }

    @GetMapping("/checkWin/{roomCode}")
    public void checkWin(@PathVariable String roomCode) {
        boolean action = roomService.checkWin(roomCode);
        if (action) {
            messagingTemplate.convertAndSend("/topic/win/" + roomCode, true);
        }
    }

    @GetMapping("/gameOver/{roomCode}")
    public ResponseEntity<String> gameOver(@PathVariable String roomCode) {
        Room room = roomService.getRoom(roomCode);
        return ResponseEntity.ok(roomService.updateWinner(room));
    }

}
