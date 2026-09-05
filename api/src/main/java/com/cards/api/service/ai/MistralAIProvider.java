package com.cards.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.exception.domain.AiGenerationException;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class MistralAIProvider implements AiClient {

    private static final String BASE_URL = "https://api.mistral.ai/v1";
    private static final String MODEL = "mistral-small-latest";

    private final ObjectMapper objectMapper;

    public MistralAIProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.MISTRAL;
    }

    @Override
    public List<Flashcard> generateFlashcards(String systemPrompt, String userText, String apiKey) {
        var body = Map.of(
            "model", MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userText)
            ),
            "response_format", Map.of("type", "json_object")
        );

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
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<Flashcard> parseResponse(Map<?, ?> response) {
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty())
            throw new AiGenerationException("No choices in Mistral response");

        var message = (Map<String, Object>) choices.get(0).get("message");
        var content = (String) message.get("content");
        if (content == null || content.isBlank())
            throw new AiGenerationException("Empty content in Mistral response");

        return AiClient.parseFlashcards(objectMapper, content, "Mistral");
    }
}
