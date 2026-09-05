package com.cards.api.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.exception.domain.AiGenerationException;
import com.cards.api.util.AiProvider;

import java.util.List;
import java.util.Map;

public interface AiClient {

    AiProvider provider();

    List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey);

    static List<Flashcard> parseFlashcards(ObjectMapper mapper, String json, String providerName) {
        try {
            var flashcards = mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            return flashcards.stream()
                .map(m -> new Flashcard(m.get("front"), m.get("back")))
                .toList();
        } catch (Exception e) {
            throw new AiGenerationException("Failed to parse " + providerName + " response: " + e.getMessage());
        }
    }
}
