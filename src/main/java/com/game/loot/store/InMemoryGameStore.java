package com.game.loot.store;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.Type;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGameStore {

    private final ConcurrentHashMap<String, RoomData> roomsByCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoomData> roomsById = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CardCatalog catalog;

    public InMemoryGameStore(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public RoomData createRoom(String roomCode) {
        RoomData data = new RoomData(UUID.randomUUID().toString(), roomCode);
        RoomData existing = roomsByCode.putIfAbsent(roomCode, data);
        RoomData room = existing != null ? existing : data;
        roomsById.putIfAbsent(room.id, room);
        return room;
    }

    public RoomData getRoom(String roomCode) {
        RoomData data = roomsByCode.get(roomCode);
        if (data == null) throw new IllegalArgumentException("Room not found: " + roomCode);
        return data;
    }

    public RoomData getRoomById(String roomId) {
        RoomData data = roomsById.get(roomId);
        if (data == null) throw new IllegalArgumentException("Room not found: " + roomId);
        return data;
    }

    public void ensureDeck(RoomData room) {
        synchronized (room.lock) {
            if (!room.deck.isEmpty()) return;
            room.deck.addAll(catalog.newDeck());
            Collections.shuffle(room.deck, random);
        }
    }

    public static final class RoomData {
        public final String id;
        public final String code;
        public final Object lock = new Object();

        public String game = "NOT_STARTED";
        public int pass = 0;
        public String currentTurn = null;

        public final List<Player> players = new ArrayList<>();
        public final Map<String, List<Card>> hands = new HashMap<>();
        public final List<StackData> stacks = new ArrayList<>();
        public final Map<String, Integer> points = new HashMap<>();
        public final List<Card> discard = new ArrayList<>();
        public final List<Card> deck = new ArrayList<>();

        public RoomData(String id, String code) {
            this.id = id;
            this.code = code;
        }

        public Room toRoomPojo() {
            Room r = new Room();
            r.setId(id);
            r.setCode(code);
            r.setGame(game);
            r.setPass(pass);
            return r;
        }
    }

    public static final class StackData {
        public final String id;
        public final String ownerId; // merchant owner
        public String winPlayerId;   // current winner, can be null on tie
        public final List<PlayedCard> cards = new ArrayList<>();

        public StackData(String id, String ownerId) {
            this.id = id;
            this.ownerId = ownerId;
            this.winPlayerId = ownerId; // matches old DB behavior (initial winner = merchant owner)
        }

        public int merchantValue() {
            for (PlayedCard c : cards) {
                if (c.getType() == Type.MERCHANT) return c.getValue() == null ? 0 : c.getValue();
            }
            return 0;
        }

        public void discardInto(List<Card> discardPile, Map<String, Integer> points, String roomIdIgnored, String playerId) {
            // discard pile is tracked by card objects elsewhere; here we only update points.
            int mv = merchantValue();
            points.put(playerId, points.getOrDefault(playerId, 0) + mv);
        }

        public Date createdAtOrNow() {
            if (!cards.isEmpty() && cards.get(0).getCreatedAt() != null) return cards.get(0).getCreatedAt();
            return new Date();
        }
    }
}

