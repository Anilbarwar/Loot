package com.game.loot.repository.impl;

import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.repository.RoomRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RoomRepositoryServiceImpl implements RoomRepositoryService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public void insertRoomData(String roomCode) {
        String query = "INSERT INTO ROOMS (code, game, pass, created_at) VALUES (?, 'NOT_STARTED', 0, ?)";
        jdbcTemplate.update(query, roomCode, LocalDateTime.now());
    }

    @Override
    public Room getRoomByCode(String roomCode) {
        String query = "SELECT * FROM ROOMS WHERE code = ?";

        Room room = new Room();
        jdbcTemplate.query(query, new Object[]{roomCode}, rs -> {
            room.setId(rs.getString("id"));
            room.setGame(rs.getString("game"));
            room.setPass(rs.getInt("pass"));
            room.setCode(rs.getString("code"));
        });

        return room;
    }

    @Override
    public List<Player> insertPlayerDetails(String playerName, Room room) {
        String query = "INSERT INTO players (name, room_id, joined_at, index) VALUES (?,?,?,?)";

        Integer turn = getCurrentCount(room);
        List<Player> players = getAllPlayers(room.getId());

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, playerName);
            ps.setObject(2, UUID.fromString(room.getId()));
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(4, turn+1);
            return ps;
        }, keyHolder);

        Player player = new Player();
        player.setId(keyHolder.getKeys().get("id").toString());
        player.setName(playerName);
        player.setIndex(turn+1);

        players.add(player);

        return players;
    }

    public Integer getCurrentCount(Room room) {
        String query = "SELECT max(index) as turn FROM players WHERE room_id = ?";
        AtomicReference<Integer> turn = new AtomicReference<>(0);

        jdbcTemplate.query(query, new Object[]{UUID.fromString(room.getId())}, rs -> {
            turn.set(rs.getInt("turn"));
        });

        return turn.get();
    }

    @Override
    public List<Player> getAllPlayers(String id) {
        String getAllPlayer = "SELECT id, name, index FROM players WHERE room_id = ?";

        List<Player> players = new ArrayList<>();
        jdbcTemplate.query(getAllPlayer, new Object[] {UUID.fromString(id)}, rs -> {
            Player player = new Player();
            player.setId(rs.getString("id"));
            player.setName(rs.getString("name"));
            player.setIndex(rs.getInt("index"));

            players.add(player);
        });
        return players;
    }

    @Override
    public void updateRoom(Room room) {
        String updateRoom = "UPDATE rooms SET pass = ?, game = ? WHERE id = ?";

        jdbcTemplate.update(updateRoom, room.getPass(), room.getGame(), UUID.fromString(room.getId()));
    }

    @Override
    public String checkGameOver(String roomCode) {
        String query = "SELECT game FROM rooms WHERE code = ?";

        AtomicReference<String> status = new AtomicReference<>("STARTED");
        jdbcTemplate.query(query, new Object[]{roomCode}, rs -> {
            status.set(rs.getString("game"));
        });

        return status.get();
    }

    @Override
    public int checkPassCount(String roomId) {
        String query = "SELECT pass FROM rooms WHERE id = ?";

        AtomicInteger pass = new AtomicInteger();
        jdbcTemplate.query(query, new Object[]{UUID.fromString(roomId)}, rs -> {
            pass.addAndGet(rs.getInt("pass"));
        });

        return pass.get();

    }

    @Override
    public Map<String, Integer> getPlayerScoreMap(Room room) {
        String query = "SELECT * FROM players p JOIN player_points pp ON pp.player_id = p.id WHERE pp.room_id = ?";

        Map<String, Integer> score = new HashMap<>();
        jdbcTemplate.query(query, new Object[]{UUID.fromString(room.getId())}, rs -> {
            score.put(rs.getString("name"),
                        rs.getInt("total_points"));
        });
        return score;
    }

    @Override
    public List<Player> getPlayerByRoom(String roomCode) {
        String query = "SELECT p.id as player_id, p.name as player_name, index from ROOMS r join PLAYERS p "
                + "ON r.id = p.room_id where r.code = ? order by p.joined_at";
        List<Player> players = new ArrayList<>();
        jdbcTemplate.query(query, new Object[] {roomCode}, rs -> {
            Player player = new Player();
            player.setId(rs.getString("player_id"));
            player.setName(rs.getString("player_name"));
            player.setIndex(rs.getInt("index"));

            players.add(player);
        });
        return players;
    }

    @Override
    public void setPlayerTurn(Player player, String roomCode) {
        String query = "UPDATE rooms SET current_turn = ? WHERE code = ?";
        jdbcTemplate.update(query, UUID.fromString(player.getId()), roomCode);
    }

    @Override
    public String getCurrentPlayer(String roomCode) {
        String query = "SELECT current_turn FROM rooms where code = ?";
        AtomicReference<String> player = new AtomicReference<>();
        jdbcTemplate.query(query, new Object[]{roomCode}, rs -> {
            player.set(rs.getString("current_turn"));
        });
        return player.get();
    }
}
