package com.cards.api.service.ai;

import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.response.AiGenerationResponse;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.service.CardService;
import com.cards.api.service.DeckService;
import com.cards.api.service.UserApiKeyService;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiCardGeneratorService {

    private static final String SYSTEM_PROMPT = """
        You are a flashcard generator. Extract key concepts from the provided text and create
        question-answer pairs. Return a JSON array of objects with "front" (question, max 255
        characters) and "back" (answer, max 5000 characters). Each front must be distinct.
        Focus on important concepts, definitions, and relationships.
        Create between 3 and 50 cards.
        Return ONLY valid JSON, no markdown, no explanation.
        """.stripIndent();

    private static final String SYSTEM_PROMPT_TOPIC = """
        You are a flashcard generator. Given the following topic, create
        question-answer pairs to help study it. Return a JSON array of objects
        with "front" (question, max 255 characters) and "back" (answer, max 5000
        characters). Each front must be distinct. Focus on important concepts,
        definitions, and relationships. Create between 3 and 50 cards.
        Return ONLY valid JSON, no markdown, no explanation.
        """.stripIndent();

    private final FileParserService fileParser;
    private final UserApiKeyService apiKeyService;
    private final AiClientFactory clientFactory;
    private final CardService cardService;
    private final DeckService deckService;

    public AiCardGeneratorService(FileParserService fileParser, UserApiKeyService apiKeyService,
                                  AiClientFactory clientFactory, CardService cardService,
                                  DeckService deckService) {
        this.fileParser = fileParser;
        this.apiKeyService = apiKeyService;
        this.clientFactory = clientFactory;
        this.cardService = cardService;
        this.deckService = deckService;
    }

    @Transactional
    public AiGenerationResponse generateCardsInDeck(Long deckId, Long userId, String filename,
                                                    byte[] fileContent, AiProvider provider, String model) {
        var text = fileParser.parse(filename, fileContent);
        var apiKey = apiKeyService.getDecryptedKey(userId, provider);
        var client = clientFactory.getClient(provider);

        var flashcards = callAi(client, apiKey, text, model, provider, SYSTEM_PROMPT);
        var result = createCards(flashcards, deckId, userId);
        var deck = deckService.getDeckById(deckId, userId);

        return new AiGenerationResponse(deck, result.cards, result.cards.size(), result.skipped);
    }

    @Transactional
    public AiGenerationResponse generateDeckWithCards(Long userId, String filename, byte[] fileContent,
                                                      AiProvider provider, String deckName, String model) {
        var text = fileParser.parse(filename, fileContent);
        var apiKey = apiKeyService.getDecryptedKey(userId, provider);
        var client = clientFactory.getClient(provider);

        var flashcards = callAi(client, apiKey, text, model, provider, SYSTEM_PROMPT);
        var deck = deckService.create(userId, new CreateDeckRequest(deckName));
        var result = createCards(flashcards, deck.id(), userId);

        return new AiGenerationResponse(deck, result.cards, result.cards.size(), result.skipped);
    }

    @Transactional
    public AiGenerationResponse generateDeckFromTopic(Long userId, String prompt,
                                                      AiProvider provider, String deckName, String model) {
        var apiKey = apiKeyService.getDecryptedKey(userId, provider);
        var client = clientFactory.getClient(provider);

        var flashcards = callAi(client, apiKey, prompt, model, provider, SYSTEM_PROMPT_TOPIC);
        var deck = deckService.create(userId, new CreateDeckRequest(deckName));
        var result = createCards(flashcards, deck.id(), userId);

        return new AiGenerationResponse(deck, result.cards, result.cards.size(), result.skipped);
    }

    private CardCreationResult createCards(List<Flashcard> flashcards, Long deckId, Long userId) {
        var createdCards = new ArrayList<CardResponse>();
        var skipped = 0;

        for (var fc : flashcards) {
            if (fc.front() == null || fc.back() == null) continue;
            var front = fc.front().strip();
            var back = fc.back().strip();
            if (front.isBlank() || back.isBlank()) continue;
            if (front.length() > 255) front = front.substring(0, 255);
            if (back.length() > 5000) back = back.substring(0, 5000);

            try {
                var card = cardService.create(deckId, userId, new CreateCardRequest(front, back));
                createdCards.add(card);
            } catch (Exception e) {
                skipped++;
            }
        }

        return new CardCreationResult(createdCards, skipped);
    }

    private record CardCreationResult(List<CardResponse> cards, int skipped) {
    }

    private List<Flashcard> callAi(AiClient client, String apiKey, String text, String model,
                                   AiProvider provider, String systemPrompt) {
        if (provider == AiProvider.OPENROUTER) {
            var openRouter = (OpenRouterProvider) client;
            return openRouter.generateFlashcards(systemPrompt, text, apiKey, model);
        }
        return client.generateFlashcards(systemPrompt, text, apiKey);
    }
}
