package com.cards.api.infraestructure.mother;

import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Object Mother para crear instancias de Deck consistentes en tests.
 */
public final class DeckMother {

    private DeckMother() {
    }

    /**
     * El "base" que asegura que el Deck tenga lo mínimo para no fallar en DB.
     */
    public static Deck createWithUser(User user, String name) {
        Deck deck = Deck.builder()
                .name(name)
                .user(user)
                .build();
        deck.setCards(new HashSet<>());
        return deck;
    }

    /**
     * Crea un deck con el flag de pendientes activado.
     */
    public static Deck createPending(User user, String name) {
        Deck deck = createWithUser(user, name);
        deck.setHasPendingCards(true);
        return deck;
    }

    /**
     * Crea un deck con una fecha de creación específica para tests de paginación.
     */
    public static Deck createWithDate(User user, String name, Instant createdAt) {
        Deck deck = createWithUser(user, name);
        deck.setCreatedAt(createdAt);
        return deck;
    }

    /**
     * Crea un deck con una colección de cartas predefinida.
     */
    public static Deck createWithCards(User user, String name, Set<Card> cards) {
        Deck deck = createWithUser(user, name);
        if (cards != null) {
            deck.setCards(cards);
            // Sincronizar el lado inverso si es necesario
            cards.forEach(card -> card.setDeck(deck));
        }
        return deck;
    }

    /**
     * Generador de nombres únicos para evitar colisiones en UK (user_id, name).
     */
    public static String uniqueDeckName(String prefix) {
        return prefix + " " + System.nanoTime();
    }
}