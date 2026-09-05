package com.cards.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.exception.domain.AiGenerationException;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class AnthropicProvider implements AiClient {

    private static final String BASE_URL = "https://api.anthropic.com/v1";
    private static final String MODEL = "claude-sonnet-4-0";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;

    public AnthropicProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey) {
        var body = Map.of(
            "model", MODEL,
            "max_tokens", 4096,
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userText)
            )
        );

        var response = buildClient(apiKey).post()
            .uri("/messages")
            .body(body)
            .retrieve()
            .body(Map.class);

        return parseResponse(response);
    }

    private RestClient buildClient(String apiKey) {
        return RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<Flashcard> parseResponse(Map<?, ?> response) {
        var content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty())
            throw new AiGenerationException("No content in Anthropic response");

        var text = (String) content.get(0).get("text");
        if (text == null || text.isBlank())
            throw new AiGenerationException("Empty text in Anthropic response");

        return AiClient.parseFlashcards(objectMapper, text, "Anthropic");
    }
}
