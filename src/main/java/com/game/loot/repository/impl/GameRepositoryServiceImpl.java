package com.game.loot.repository.impl;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Color;
import com.game.loot.pojo.PlayedCard;
import com.game.loot.pojo.Player;
import com.game.loot.pojo.Room;
import com.game.loot.pojo.Type;
import com.game.loot.pojo.request.MoveRequest;
import com.game.loot.repository.GameRepositoryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GameRepositoryServiceImpl implements GameRepositoryService {

    @Autowired
    JdbcTemplate jdbcTemplate;


    @Override
    public void insertCardsToDB(List<Object[]> cards) {
        String query = "INSERT INTO CARDS (color, type, value) VALUES (?,?,?)";
        jdbcTemplate.batchUpdate(query, cards);
    }

    @Override
    public List<Card> getAllCards(List<Player> players) {
        String query = "SELECT * FROM CARDS";
        List<Card> cards = new ArrayList<>();
        jdbcTemplate.query(query, rs -> {
            Card card = new Card();
            card.setId(rs.getString("id"));
            card.setColor(Color.valueOf(rs.getString("color")));
            card.setValue(rs.getInt("value"));
            card.setType(Type.valueOf(rs.getString("type")));

            cards.add(card);
        });

        return cards;
    }

    @Override
    public void updateChosenCards(List<Object[]> params) {
        String query = "INSERT INTO HANDS (player_id, card_id) VALUES (?,?)";
        jdbcTemplate.batchUpdate(query, params);
    }

    @Override
    public List<Card> getRemainingCards(String roomId) {
        String query = "SELECT * FROM cards WHERE id NOT IN ("
                + "SELECT card_id FROM discard_cards WHERE room_id = ? "
                + "UNION "
                + "SELECT pc.card_id FROM played_cards pc JOIN players p ON pc.player_id = p.id WHERE p.room_id = ? "
                + "UNION "
                + "SELECT h.card_id FROM hands h JOIN players p ON h.player_id = p.id WHERE p.room_id = ?)";

        List<Card> cards = new ArrayList<>();
        jdbcTemplate.query(query, new Object[] {
                UUID.fromString(roomId), UUID.fromString(roomId), UUID.fromString(roomId)
        }, rs -> {
            Card card = new Card();
            card.setType(Type.valueOf(rs.getString("type")));
            card.setId(rs.getObject("id").toString());
            card.setColor(Color.valueOf(rs.getString("color")));
            card.setValue(rs.getInt("value"));

            cards.add(card);
        });

        return cards;
    }

    @Override
    public List<PlayedCard> getPlayedCards(List<Player> players) {
        StringBuilder query = new StringBuilder("SELECT * FROM cards WHERE id NOT IN (SELECT card_id FROM hands "
                + "WHERE player_id IN (");
        query.append(StringUtils.repeat("?,", players.size()));
        query.deleteCharAt(query.length()-1).append("))");

        List<UUID> playerParam = new ArrayList<>();
        Map<String, Integer> playerIndex = new HashMap<>();
        for (Player player: players) {
            playerParam.add(UUID.fromString(player.getId()));
            playerIndex.put(player.getId(), player.getIndex());
        }

        List<PlayedCard> playedCards = new ArrayList<>();
        jdbcTemplate.query(query.toString(), playerParam.toArray(), rs -> {
            PlayedCard card = new PlayedCard();
            card.setCardId(rs.getString("id"));
            card.setValue(rs.getInt("value"));
            card.setColor(Color.valueOf(rs.getString("color")));
            card.setType(Type.valueOf(rs.getString("type")));

            String playerId = rs.getString("hands.player_id");
            card.setPlayerId(playerId);
            card.setIndex(playerIndex.get(playerId));
            playedCards.add(card);
        });

        return playedCards;
    }

    @Override
    public Map<String, List<Card>> getPlayerCards(List<Player> players) {
        StringBuilder query = new StringBuilder("SELECT * FROM players p JOIN hands h ON p.id = h.player_id "
                + "JOIN cards c ON h.card_id = c.id WHERE p.id = ?");

        Map<String, List<Card>> playerCards = new HashMap<>();
        for (Player player: players) {
            List<Card> cards = new ArrayList<>();
            jdbcTemplate.query(query.toString(), new Object[]{UUID.fromString(player.getId())}, rs -> {
                Card card = new Card();
                card.setValue(rs.getInt("value"));
                card.setId(rs.getString("card_id"));
                card.setType(Type.valueOf(rs.getString("type")));
                card.setColor(Color.valueOf(rs.getString("color")));

                cards.add(card);
            });

            playerCards.put(player.getId(), cards);
        }

        return playerCards;
    }

    @Override
    public List<PlayedCard> getCurrentPlayedStack(String roomId, List<Player> players, PlayedCard target) {
        List<PlayedCard> playedCards = new ArrayList<>();
        String query;
        if (target == null) {
            query = "SELECT * FROM played_cards WHERE stack_id IN (SELECT id FROM played_stacks WHERE room_id = ?)";

            Map<String, Integer> playerIndex = new HashMap<>();
            for (Player player : players) {
                playerIndex.put(player.getId(), player.getIndex());
            }
            jdbcTemplate.query(query, new Object[]{UUID.fromString(roomId)}, rs -> {
                PlayedCard playedCard = new PlayedCard();
                String playerId = rs.getString("player_id");
                playedCard.setPlayerId(playerId);
                playedCard.setCardId(rs.getString("card_id"));
                playedCard.setStackId(rs.getString("stack_id"));
                playedCard.setId(rs.getString("id"));
                playedCard.setCreatedAt(rs.getDate("created_at"));
                playedCard.setIndex(playerIndex.getOrDefault(playerId, 0));

                playedCards.add(playedCard);
            });

        } else {
            query = "SELECT * FROM played_cards where stack_id = ?";

            jdbcTemplate.query(query, new Object[]{UUID.fromString(target.getStackId())}, rs -> {
                PlayedCard playedCard = new PlayedCard();
                String playerId = rs.getString("player_id");
                playedCard.setPlayerId(playerId);
                playedCard.setCardId(rs.getString("card_id"));
                playedCard.setStackId(rs.getString("stack_id"));
                playedCard.setId(rs.getString("id"));
                playedCard.setCreatedAt(rs.getDate("created_at"));

                playedCards.add(playedCard);
            });
        }
        if (!playedCards.isEmpty()) {
            getPlayedCardsDetails(playedCards);
        }

        return playedCards;
    }

    @Override
    public void moveToGameArea(MoveRequest moveRequest) {
        String stackId;
        if (moveRequest.getCard() == null) {
            String query = "INSERT INTO played_stacks (room_id, created_at, win) VALUES (?, current_timestamp, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                ps.setObject(1, UUID.fromString(moveRequest.getRoomId()));
                ps.setObject(2, UUID.fromString(moveRequest.getPlayerId()));
                return ps;
            }, keyHolder);
            stackId = keyHolder.getKeys().get("id").toString();
        } else {
            stackId = moveRequest.getCard().getStackId();
        }


        String insertPlayed = "INSERT INTO played_cards (stack_id, card_id, player_id) VALUES (?, ?, ?)";
        jdbcTemplate.update(insertPlayed, UUID.fromString(stackId),
                UUID.fromString(moveRequest.getSelectedCard().getId()), UUID.fromString(moveRequest.getPlayerId()));

        String removeHand = "DELETE FROM hands WHERE player_id = ? and card_id = ?";
        jdbcTemplate.update(removeHand, UUID.fromString(moveRequest.getPlayerId()),
                UUID.fromString(moveRequest.getSelectedCard().getId()));
    }

    @Override
    public Integer getMerchantValue(Room room, String stackId) {
        String query = "SELECT * FROM cards WHERE id IN (SELECT card_id FROM played_cards WHERE stack_id = ?)";

        AtomicReference<Integer> value = new AtomicReference<>(0);
        jdbcTemplate.query(query, new Object[]{UUID.fromString(stackId)}, rs -> {
            if (Type.MERCHANT.toString().equals(rs.getString("type"))) {
                value.set(rs.getInt("value"));
            }
        });

        return value.get();
    }

    @Override
    public void updateDiscardPile(List<Object> stackData, String roomId) {
        String discardCards = "INSERT INTO discard_cards (room_id, card_id) "
                + "SELECT ?::uuid as room_id, card_id FROM played_cards WHERE stack_id = ?";

        List<Object> params = new ArrayList<>();
        params.add(UUID.fromString(roomId));
        params.add(UUID.fromString(stackData.get(0).toString()));

        jdbcTemplate.update(discardCards, params.toArray());

        String query = "DELETE FROM played_cards WHERE stack_id = ?";
        jdbcTemplate.update(query, UUID.fromString(stackData.get(0).toString()));

        String deleteStack = "DELETE FROM played_stacks WHERE id = ?";
        jdbcTemplate.update(deleteStack, UUID.fromString(stackData.get(0).toString()));
    }

    @Override
    public void updatePoints(int totalPoints, String roomId, String playerId) {
        String query = "INSERT INTO player_points (room_id, player_id, total_points) VALUES (?, ?, ?) "
                + "ON CONFLICT (room_id, player_id) "
                + "DO UPDATE SET total_points = player_points.total_points + EXCLUDED.total_points;";
        jdbcTemplate.update(query, UUID.fromString(roomId), UUID.fromString(playerId), totalPoints);
    }

    @Override
    public List<PlayedCard> getPlayedCardByStack(MoveRequest moveRequest) {
        String query = "SELECT * FROM played_cards WHERE stack_id = ?";

        List<PlayedCard> playedCards = new ArrayList<>();
        jdbcTemplate.query(query, new Object[]{UUID.fromString(moveRequest.getCard().getStackId())}, rs -> {
            PlayedCard playedCard = new PlayedCard();
            playedCard.setCardId(rs.getString("card_id"));
            playedCard.setStackId(rs.getString("stack_id"));
            playedCard.setPlayerId(rs.getString("player_id"));
            playedCard.setCreatedAt(rs.getDate("created_at"));

            playedCards.add(playedCard);
        });

        if (!playedCards.isEmpty()) {
            getPlayedCardsDetails(playedCards);
        }

        return playedCards;
    }

    @Override
    public void updateStackWinner(String playerId, String stackId) {
        String updateWinner = "UPDATE played_stacks SET win = ? WHERE id = ?";
        UUID param;
        if (playerId != null) {
            param = UUID.fromString(playerId);
        } else {
            param = null;
        }
        jdbcTemplate.update(updateWinner, param, UUID.fromString(stackId));
    }

    @Override
    public String checkWinningPlayer(Room room, String playerId) {
        String query = "SELECT * FROM played_stacks WHERE room_id = ? AND win = ?";
        AtomicReference<String> stackId = new AtomicReference<>();
        jdbcTemplate.query(query, new Object[] {UUID.fromString(room.getId()), UUID.fromString(playerId)}, rs -> {
            stackId.set(rs.getString("id"));
        });

        return stackId.get();
    }

    @Override
    public Map<String, Integer> getPlayerScores(List<Player> players) {
        StringBuilder query = new StringBuilder("SELECT * FROM player_points WHERE player_id IN (");
        query.append(StringUtils.repeat("?,", players.size()));
        query.deleteCharAt(query.length()-1).append(")");

        List<Object> playerParam = new ArrayList<>();
        for (Player player: players) {
            playerParam.add(UUID.fromString(player.getId()));
        }

        Map<String, Integer> playerValueMap = new HashMap<>();
        jdbcTemplate.query(query.toString(), playerParam.toArray(), rs -> {
            String playerId = rs.getString("player_id");
            int value = playerValueMap.getOrDefault(playerId, 0) + rs.getInt("total_points");
            playerValueMap.put(playerId, value);
        });

        return playerValueMap;
    }

    private void getPlayedCardsDetails(List<PlayedCard> playedCards) {
        StringBuilder query = new StringBuilder("SELECT * FROM cards WHERE id in (");
        query.append(StringUtils.repeat("?,", playedCards.size()));
        query.deleteCharAt(query.length()-1).append(")");

        List<UUID> cardIds = new ArrayList<>();
        for (PlayedCard playedCard: playedCards) {
            cardIds.add(UUID.fromString(playedCard.getCardId()));
        }

        jdbcTemplate.query(query.toString(), cardIds.toArray(), rs -> {
            String id = rs.getString("id");
            for (PlayedCard playedCard : playedCards) {
                if (playedCard.getCardId().equalsIgnoreCase(id)) {
                    playedCard.setType(Type.valueOf(rs.getString("type")));
                    playedCard.setColor(Color.valueOf(rs.getString("color")));
                    playedCard.setValue(rs.getInt("value"));
                    break;
                }
            }
        });
    }
}
