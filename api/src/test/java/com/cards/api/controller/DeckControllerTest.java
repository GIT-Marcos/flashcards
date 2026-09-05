package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.flashcard.DeckController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.PatchDeckRequest;
import com.cards.api.dto.response.AiGenerationResponse;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.DeckService;
import com.cards.api.service.JwtService;
import com.cards.api.service.ai.AiCardGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(DeckController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("DeckController")
class DeckControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DeckService deckService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AiCardGeneratorService aiCardGeneratorService;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private DeckResponse createDeckResponse() {
        return new DeckResponse(DECK_ID, "Spanish", false, now(), now());
    }

    private Window<DeckResponse> createDeckWindow() {
        return Window.from(
            List.of(createDeckResponse()),
            i -> ScrollPosition.keyset(),
            true
        );
    }

    // ======================== CREATE ========================

    @Nested
    @DisplayName("POST /decks")
    class Create {

        @Test
        @DisplayName("should return 201 with deck response")
        void shouldCreateDeck() {
            when(deckService.create(anyLong(), any(CreateDeckRequest.class)))
                .thenReturn(createDeckResponse());

            String body = """
                {
                    "name": "Spanish"
                }
                """;

            var result = assertThat(mvc.post().uri("/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatus(HttpStatus.CREATED);
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == DECK_ID);
            result.bodyJson().extractingPath("$.name").asString().isEqualTo("Spanish");
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "name": "Spanish"
                }
                """;

            assertThat(mvc.post().uri("/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() {
            String body = """
                {
                    "name": ""
                }
                """;

            assertThat(mvc.post().uri("/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should sanitize name with leading/trailing spaces")
        void shouldSanitizeName() {
            when(deckService.create(anyLong(), any(CreateDeckRequest.class)))
                .thenReturn(createDeckResponse());

            String body = """
                {
                    "name": "  Spanish  "
                }
                """;

            assertThat(mvc.post().uri("/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.CREATED);

            verify(deckService).create(eq(USER_ID), argThat(req ->
                "Spanish".equals(req.name())
            ));
        }
    }

    // ======================== AI TOPIC ========================

    @Nested
    @DisplayName("POST /decks/ai/topic")
    class GenerateDeckFromTopic {

        @Test
        @DisplayName("should return 201 with generated deck and cards")
        void shouldCreateDeckFromTopic() {
            var deckResponse = createDeckResponse();
            var cardResponse = new CardResponse(100L, "Front", "Back", null);
            var aiResponse = new AiGenerationResponse(deckResponse, List.of(cardResponse), 1, 0);

            when(aiCardGeneratorService.generateDeckFromTopic(anyLong(), anyString(), any(), anyString(), any()))
                .thenReturn(aiResponse);

            String body = """
                {
                    "prompt": "Spanish vocabulary for beginners",
                    "provider": "OPENAI",
                    "deckName": "Spanish Basics"
                }
                """;

            assertThat(mvc.post().uri("/decks/ai/topic")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .convertTo(AiGenerationResponse.class)
                .satisfies(response -> {
                    assertThat(response.totalGenerated()).isEqualTo(1);
                    assertThat(response.deck().id()).isEqualTo(DECK_ID);
                });
        }

        @Test
        @DisplayName("should return 400 when prompt is blank")
        void shouldReturn400WhenPromptIsBlank() {
            String body = """
                {
                    "prompt": "",
                    "provider": "OPENAI",
                    "deckName": "Spanish Basics"
                }
                """;

            assertThat(mvc.post().uri("/decks/ai/topic")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when deckName is blank")
        void shouldReturn400WhenDeckNameIsBlank() {
            String body = """
                {
                    "prompt": "Spanish vocabulary",
                    "provider": "OPENAI",
                    "deckName": ""
                }
                """;

            assertThat(mvc.post().uri("/decks/ai/topic")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "prompt": "Spanish vocabulary",
                    "provider": "OPENAI",
                    "deckName": "Spanish Basics"
                }
                """;

            assertThat(mvc.post().uri("/decks/ai/topic")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("PATCH /decks/{deckId}")
    class Patch {

        @Test
        @DisplayName("should return 200 with updated deck")
        void shouldPatchDeck() {
            when(deckService.patch(anyLong(), anyLong(), any(PatchDeckRequest.class)))
                .thenReturn(createDeckResponse());

            String body = """
                {
                    "name": "French"
                }
                """;

            var result = assertThat(mvc.patch().uri("/decks/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.name").asString().isEqualTo("Spanish");
        }

        @Test
        @DisplayName("should return 404 when deck not found")
        void shouldReturn404() {
            when(deckService.patch(anyLong(), anyLong(), any(PatchDeckRequest.class)))
                .thenThrow(new ResourceNotFoundException("Deck not found"));

            String body = """
                {
                    "name": "French"
                }
                """;

            assertThat(mvc.patch().uri("/decks/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "name": "French"
                }
                """;

            assertThat(mvc.patch().uri("/decks/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("should return 200 when body is empty JSON (no changes)")
        void shouldPatchWithoutChanges() {
            when(deckService.patch(anyLong(), anyLong(), any(PatchDeckRequest.class)))
                .thenReturn(createDeckResponse());

            assertThat(mvc.patch().uri("/decks/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(securityUser())))
                .hasStatusOk();
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("DELETE /decks/{deckId}")
    class Delete {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteDeck() {
            assertThat(mvc.delete().uri("/decks/{deckId}", DECK_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when deck not found")
        void shouldReturn404() {
            doThrow(new ResourceNotFoundException("Deck not found"))
                .when(deckService).delete(anyLong(), anyLong());

            assertThat(mvc.delete().uri("/decks/{deckId}", DECK_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.delete().uri("/decks/{deckId}", DECK_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("GET /decks")
    class GetDecks {

        @Test
        @DisplayName("should return 200 with paginated decks")
        void shouldReturnPaginatedDecks() {
            when(deckService.getDecks(anyLong(), any())).thenReturn(createDeckWindow());

            var result = assertThat(mvc.get().uri("/decks")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray();
            result.bodyJson().extractingPath("$.content[0].id").asNumber().matches(n -> n.longValue() == DECK_ID);
        }

    }

    @Nested
    @DisplayName("GET /decks/due")
    class GetDueDecks {

        @Test
        @DisplayName("should return 200 with due decks")
        void shouldReturnDueDecks() {
            when(deckService.getDueDecks(anyLong(), any())).thenReturn(createDeckWindow());

            var result = assertThat(mvc.get().uri("/decks/due")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray();
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/decks/due"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
