package com.cards.api.service.ai;

/**
 * Modelo de datos intermedio entre los clientes de IA y el servicio de tarjetas.
 * <p>
 * Representa una tarjeta de estudio generada por IA en su forma más simple:
 * una pregunta ({@code front}) y una respuesta ({@code back}). Carece de lógica
 * de negocio, validaciones JSR-380 y anotaciones JPA, manteniendo a los clientes
 * {@link AiClient} desacoplados de las capas de persistencia y presentación.
 * </p>
 *
 * <p><b>Flujo t&iacute;pico:</b></p>
 * <pre>{@code
 * List<Flashcard> cards = aiClient.generateCards(...);
 * deckService.createDeckWithCards(deckName, cards, user);
 * }</pre>
 *
 * @param front Cara frontal de la tarjeta (pregunta / término)
 * @param back  Cara trasera de la tarjeta (respuesta / definición)
 */
public record Flashcard(String front, String back) {
}
