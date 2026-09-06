package com.cards.api.unit.ai;

import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.service.CardService;
import com.cards.api.service.DeckService;
import com.cards.api.service.UserApiKeyService;
import com.cards.api.service.ai.*;
import com.cards.api.util.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiCardGeneratorService")
class AiCardGeneratorServiceTest {

    @Mock
    private FileParserService fileParser;

    @Mock
    private UserApiKeyService apiKeyService;

    @Mock
    private AiClientFactory clientFactory;

    @Mock
    private CardService cardService;

    @Mock
    private DeckService deckService;

    @Mock
    private AiClient aiClient;

    private AiCardGeneratorService service;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final AiProvider PROVIDER = AiProvider.OPENAI;
    private static final String API_KEY = "sk-test-key";
    private static final String FILENAME = "test.txt";
    private static final byte[] FILE_CONTENT = "some content".getBytes();
    private static final String PARSED_TEXT = "parsed content";
    private static final String DECK_NAME = "AI Deck";
    private static final String MODEL = "gpt-4o-mini";
    private static final Instant NOW = Instant.now();

    private DeckResponse deckResponse;
    private CardResponse cardResponse;

    @BeforeEach
    void setUp() {
        service = new AiCardGeneratorService(fileParser, apiKeyService, clientFactory, cardService, deckService);

        deckResponse = new DeckResponse(DECK_ID, DECK_NAME, false, NOW, null);
        cardResponse = new CardResponse(100L, "Front", "Back", null);
    }

    @Nested
    @DisplayName("generateCardsInDeck")
    class GenerateCardsInDeck {

        @Test
        @DisplayName("should parse file, call AI, create cards, return response")
        void shouldGenerateCardsInExistingDeck() {
            var flashcards = List.of(
                new Flashcard("Q1", "A1"),
                new Flashcard("Q2", "A2")
            );

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY)))
                .thenReturn(flashcards);
            when(cardService.create(DECK_ID, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q1", "A1")))
                .thenReturn(cardResponse);
            when(cardService.create(DECK_ID, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q2", "A2")))
                .thenReturn(cardResponse);
            when(deckService.getDeckById(DECK_ID, USER_ID)).thenReturn(deckResponse);

            var result = service.generateCardsInDeck(DECK_ID, USER_ID, FILENAME, FILE_CONTENT, PROVIDER, null);

            assertThat(result.totalGenerated()).isEqualTo(2);
            assertThat(result.totalSkipped()).isZero();
            assertThat(result.deck().id()).isEqualTo(DECK_ID);
            assertThat(result.cards()).hasSize(2);
        }

        @Test
        @DisplayName("should filter out null or blank front/back silently")
        void shouldFilterInvalidCards() {
            var flashcards = List.of(
                new Flashcard("Valid", "Answer"),
                new Flashcard(null, "No front"),
                new Flashcard("No back", null),
                new Flashcard("  ", "Blank front"),
                new Flashcard("Blank back", "  ")
            );

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY)))
                .thenReturn(flashcards);
            when(cardService.create(eq(DECK_ID), eq(USER_ID), any()))
                .thenReturn(cardResponse);
            when(deckService.getDeckById(DECK_ID, USER_ID)).thenReturn(deckResponse);

            var result = service.generateCardsInDeck(DECK_ID, USER_ID, FILENAME, FILE_CONTENT, PROVIDER, null);

            assertThat(result.totalGenerated()).isEqualTo(1);
            assertThat(result.totalSkipped()).isZero();
        }

        @Test
        @DisplayName("should truncate front to 255 and back to 5000 chars")
        void shouldTruncateLongFields() {
            var longFront = "a".repeat(300);
            var longBack = "b".repeat(6000);
            var flashcards = List.of(new Flashcard(longFront, longBack));

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY)))
                .thenReturn(flashcards);

            var captor = ArgumentCaptor.forClass(com.cards.api.dto.request.CreateCardRequest.class);
            when(cardService.create(eq(DECK_ID), eq(USER_ID), captor.capture()))
                .thenReturn(cardResponse);
            when(deckService.getDeckById(DECK_ID, USER_ID)).thenReturn(deckResponse);

            service.generateCardsInDeck(DECK_ID, USER_ID, FILENAME, FILE_CONTENT, PROVIDER, null);

            var request = captor.getValue();
            assertThat(request.front()).hasSize(255);
            assertThat(request.back()).hasSize(5000);
        }

        @Test
        @DisplayName("should count duplicates as skipped")
        void shouldSkipOnCardCreationException() {
            var flashcards = List.of(
                new Flashcard("Q1", "A1"),
                new Flashcard("Q2", "A2")
            );

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY)))
                .thenReturn(flashcards);
            when(cardService.create(eq(DECK_ID), eq(USER_ID), any()))
                .thenThrow(new RuntimeException("Duplicate"))
                .thenReturn(cardResponse);
            when(deckService.getDeckById(DECK_ID, USER_ID)).thenReturn(deckResponse);

            var result = service.generateCardsInDeck(DECK_ID, USER_ID, FILENAME, FILE_CONTENT, PROVIDER, null);

            assertThat(result.totalGenerated()).isEqualTo(1);
            assertThat(result.totalSkipped()).isEqualTo(1);
        }

        @Test
        @DisplayName("should use OpenRouter provider-specific overload")
        void shouldUseOpenRouterOverload() {
            var routerProvider = AiProvider.OPENROUTER;
            var flashcards = List.of(new Flashcard("Q", "A"));

            var openRouterMock = mock(com.cards.api.service.ai.OpenRouterProvider.class);
            when(openRouterMock.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY), any()))
                .thenReturn(flashcards);

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, routerProvider)).thenReturn(API_KEY);
            when(clientFactory.getClient(routerProvider)).thenReturn(openRouterMock);
            when(cardService.create(eq(DECK_ID), eq(USER_ID), any()))
                .thenReturn(cardResponse);
            when(deckService.getDeckById(DECK_ID, USER_ID)).thenReturn(deckResponse);

            var result = service.generateCardsInDeck(DECK_ID, USER_ID, FILENAME, FILE_CONTENT, routerProvider, MODEL);

            assertThat(result.totalGenerated()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("generateDeckWithCards")
    class GenerateDeckWithCards {

        @Test
        @DisplayName("should create deck and cards from AI")
        void shouldGenerateDeckAndCards() {
            var flashcards = List.of(
                new Flashcard("Q1", "A1"),
                new Flashcard("Q2", "A2")
            );
            var newDeckResponse = new DeckResponse(99L, DECK_NAME, false, NOW, null);

            when(fileParser.parse(FILENAME, FILE_CONTENT)).thenReturn(PARSED_TEXT);
            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(PARSED_TEXT), eq(API_KEY)))
                .thenReturn(flashcards);
            when(deckService.create(USER_ID, new com.cards.api.dto.request.CreateDeckRequest(DECK_NAME)))
                .thenReturn(newDeckResponse);
            when(cardService.create(99L, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q1", "A1")))
                .thenReturn(cardResponse);
            when(cardService.create(99L, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q2", "A2")))
                .thenReturn(cardResponse);

            var result = service.generateDeckWithCards(USER_ID, FILENAME, FILE_CONTENT, PROVIDER, DECK_NAME, null);

            assertThat(result.totalGenerated()).isEqualTo(2);
            assertThat(result.deck().id()).isEqualTo(99L);
            assertThat(result.deck().name()).isEqualTo(DECK_NAME);
        }
    }

    @Nested
    @DisplayName("generateDeckFromTopic")
    class GenerateDeckFromTopic {

        private static final String TOPIC = "Spanish vocabulary for beginners";

        @Test
        @DisplayName("should create deck and cards from topic prompt")
        void shouldGenerateDeckAndCardsFromTopic() {
            var flashcards = List.of(
                new Flashcard("Q1", "A1"),
                new Flashcard("Q2", "A2")
            );
            var newDeckResponse = new DeckResponse(99L, DECK_NAME, false, NOW, null);

            when(apiKeyService.getDecryptedKey(USER_ID, PROVIDER)).thenReturn(API_KEY);
            when(clientFactory.getClient(PROVIDER)).thenReturn(aiClient);
            when(aiClient.generateFlashcards(anyString(), eq(TOPIC), eq(API_KEY)))
                .thenReturn(flashcards);
            when(deckService.create(USER_ID, new com.cards.api.dto.request.CreateDeckRequest(DECK_NAME)))
                .thenReturn(newDeckResponse);
            when(cardService.create(99L, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q1", "A1")))
                .thenReturn(cardResponse);
            when(cardService.create(99L, USER_ID, new com.cards.api.dto.request.CreateCardRequest("Q2", "A2")))
                .thenReturn(cardResponse);

            var result = service.generateDeckFromTopic(USER_ID, TOPIC, PROVIDER, DECK_NAME, null);

            assertThat(result.totalGenerated()).isEqualTo(2);
            assertThat(result.deck().id()).isEqualTo(99L);
            assertThat(result.deck().name()).isEqualTo(DECK_NAME);

            verify(aiClient).generateFlashcards(
                argThat(s -> s.contains("Given the following topic")),
                eq(TOPIC), eq(API_KEY));
        }

        @Test
        @DisplayName("should use OpenRouter provider-specific overload")
        void shouldUseOpenRouterOverload() {
            var routerProvider = AiProvider.OPENROUTER;
            var flashcards = List.of(new Flashcard("Q", "A"));
            var newDeckResponse = new DeckResponse(99L, DECK_NAME, false, NOW, null);

            var openRouterMock = mock(OpenRouterProvider.class);
            when(openRouterMock.generateFlashcards(anyString(), eq(TOPIC), eq(API_KEY), eq(MODEL)))
                .thenReturn(flashcards);

            when(apiKeyService.getDecryptedKey(USER_ID, routerProvider)).thenReturn(API_KEY);
            when(clientFactory.getClient(routerProvider)).thenReturn(openRouterMock);
            when(deckService.create(USER_ID, new com.cards.api.dto.request.CreateDeckRequest(DECK_NAME)))
                .thenReturn(newDeckResponse);
            when(cardService.create(eq(99L), eq(USER_ID), any()))
                .thenReturn(cardResponse);

            var result = service.generateDeckFromTopic(USER_ID, TOPIC, routerProvider, DECK_NAME, MODEL);

            assertThat(result.totalGenerated()).isEqualTo(1);
        }
    }
}
