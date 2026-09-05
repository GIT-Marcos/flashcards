package com.cards.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.exception.domain.AiGenerationException;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterProvider implements AiClient {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";

    private final ObjectMapper objectMapper;

    public OpenRouterProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENROUTER;
    }

    @Override
    public List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey) {
        return generateFlashcards(systemPrompt, userText, apiKey, null);
    }

    public List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userText)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }

        var response = buildClient(apiKey).post()
            .uri("/chat/completions")
            .body(body)
            .retrieve()
            .body(Map.class);

        return parseResponse(response);
    }

    private RestClient buildClient(String apiKey) {
        return RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("HTTP-Referer", "https://github.com/GIT-Marcos/flashcards")
            .defaultHeader("X-Title", "Flashcards")
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<Flashcard> parseResponse(Map<?, ?> response) {
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty())
            throw new AiGenerationException("No choices in OpenRouter response");

        var message = (Map<String, Object>) choices.get(0).get("message");
        var content = (String) message.get("content");
        if (content == null || content.isBlank())
            throw new AiGenerationException("Empty content in OpenRouter response");

        return AiClient.parseFlashcards(objectMapper, content, "OpenRouter");
    }
}
