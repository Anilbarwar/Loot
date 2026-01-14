package com.game.loot.store;

import com.game.loot.pojo.Card;
import com.game.loot.pojo.Color;
import com.game.loot.pojo.Type;
import com.opencsv.CSVReader;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Loads card definitions from Cards.csv and can generate a fresh in-memory deck per room.
 */
@Component
@Log4j2
public class CardCatalog {

    private volatile List<CardDef> defs = null;

    public void ensureLoaded() {
        if (defs != null) return;
        synchronized (this) {
            if (defs != null) return;
            defs = Collections.unmodifiableList(loadDefsFromCsv());
        }
    }

    public List<Card> newDeck() {
        ensureLoaded();
        List<Card> deck = new ArrayList<>();
        for (CardDef def : defs) {
            for (int i = 0; i < def.count; i++) {
                deck.add(new Card(UUID.randomUUID().toString(), def.color, def.type, def.value));
            }
        }
        return deck;
    }

    private List<CardDef> loadDefsFromCsv() {
        List<CardDef> loaded = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ClassPathResource("Cards.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String[] line;
            reader.readNext(); // header
            while ((line = reader.readNext()) != null) {
                Color color = Color.valueOf(line[0]);
                Type type = Type.valueOf(line[1]);
                int value = Integer.parseInt(line[2]);
                int count = Integer.parseInt(line[3]);
                loaded.add(new CardDef(color, type, value, count));
            }
        } catch (Exception e) {
            log.error("Failed to load Cards.csv", e);
        }
        return loaded;
    }

    private record CardDef(Color color, Type type, int value, int count) {}
}

