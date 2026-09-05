package com.cards.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.exception.domain.AiGenerationException;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GoogleAiProvider implements AiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String MODEL = "gemini-2.0-flash";

    private final ObjectMapper objectMapper;

    public GoogleAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GOOGLE;
    }

    @Override
    public List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey) {
        var body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "contents", List.of(Map.of("parts", List.of(Map.of("text", userText)))),
            "generationConfig", Map.of("responseMimeType", "application/json")
        );

        var response = buildClient(apiKey).post()
            .uri("/models/" + MODEL + ":generateContent")
            .body(body)
            .retrieve()
            .body(Map.class);

        return parseResponse(response);
    }

    private RestClient buildClient(String apiKey) {
        return RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("x-goog-api-key", apiKey)
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<Flashcard> parseResponse(Map<?, ?> response) {
        var candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new AiGenerationException("No candidates in Gemini response");

        var content = (Map<String, Object>) candidates.get(0).get("content");
        var parts = (List<Map<String, Object>>) content.get("parts");
        var text = (String) parts.get(0).get("text");
        if (text == null || text.isBlank())
            throw new AiGenerationException("Empty text in Gemini response");

        return AiClient.parseFlashcards(objectMapper, text, "Gemini");
    }
}
