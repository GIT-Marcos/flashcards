package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.flashcard.CardController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.PatchCardRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CardService;
import com.cards.api.service.CustomUserDetailService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(CardController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("CardController")
class CardControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CardService cardService;
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
    private static final Long CARD_ID = 100L;

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private CardResponse createCardResponse() {
        return new CardResponse(CARD_ID, "hola", "hello", now());
    }

    private Window<CardResponse> createCardWindow() {
        return Window.from(
            List.of(createCardResponse()),
            i -> ScrollPosition.keyset(),
            true
        );
    }

    // ======================== CREATE ========================

    @Nested
    @DisplayName("POST /cards/deck/{deckId}")
    class Create {

        @Test
        @DisplayName("should return 201 with card response")
        void shouldCreateCard() {
            when(cardService.create(anyLong(), anyLong(), any(CreateCardRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "front": "hola",
                    "back": "hello"
                }
                """;

            var result = assertThat(mvc.post().uri("/cards/deck/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatus(HttpStatus.CREATED);
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("hello");
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "front": "hola",
                    "back": "hello"
                }
                """;

            assertThat(mvc.post().uri("/cards/deck/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("should return 400 when front is blank")
        void shouldReturn400WhenFrontBlank() {
            String body = """
                {
                    "front": "",
                    "back": "hello"
                }
                """;

            assertThat(mvc.post().uri("/cards/deck/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when back is blank")
        void shouldReturn400WhenBackBlank() {
            String body = """
                {
                    "front": "hola",
                    "back": ""
                }
                """;

            assertThat(mvc.post().uri("/cards/deck/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 404 when deck not found")
        void shouldReturn404WhenDeckNotFound() {
            when(cardService.create(anyLong(), anyLong(), any(CreateCardRequest.class)))
                .thenThrow(new ResourceNotFoundException("That deck does not exist."));

            String body = """
                {
                    "front": "hola",
                    "back": "hello"
                }
                """;

            assertThat(mvc.post().uri("/cards/deck/{deckId}", DECK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("PATCH /cards/{cardId}")
    class Patch {

        @Test
        @DisplayName("should return 200 with updated card")
        void shouldPatchCard() {
            when(cardService.patch(anyLong(), anyLong(), any(PatchCardRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "front": "adios",
                    "back": "bye"
                }
                """;

            var result = assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404() {
            when(cardService.patch(anyLong(), anyLong(), any(PatchCardRequest.class)))
                .thenThrow(new ResourceNotFoundException("Card not found"));

            String body = """
                {
                    "front": "new",
                    "back": "nuevo"
                }
                """;

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
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
                    "front": "adios"
                }
                """;

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("should return 200 when body is empty JSON (no changes)")
        void shouldPatchWithoutChanges() {
            when(cardService.patch(anyLong(), anyLong(), any(PatchCardRequest.class)))
                .thenReturn(createCardResponse());

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(securityUser())))
                .hasStatusOk();
        }

        @Test
        @DisplayName("should return 200 when updating only front")
        void shouldPatchFrontOnly() {
            when(cardService.patch(anyLong(), anyLong(), any(PatchCardRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "front": "adios"
                }
                """;

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatusOk();
        }

        @Test
        @DisplayName("should return 200 when updating only back")
        void shouldPatchBackOnly() {
            when(cardService.patch(anyLong(), anyLong(), any(PatchCardRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "back": "bye"
                }
                """;

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatusOk();
        }

        @Test
        @DisplayName("should return 400 when front and back are empty strings")
        void shouldReturn400WhenFrontAndBackBlank() {
            String body = """
                {
                    "front": "",
                    "back": ""
                }
                """;

            assertThat(mvc.patch().uri("/cards/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("DELETE /cards/{cardId}")
    class Delete {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteCard() {
            assertThat(mvc.delete().uri("/cards/{cardId}", CARD_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404() {
            doThrow(new ResourceNotFoundException("Card not found"))
                .when(cardService).delete(anyLong(), anyLong());

            assertThat(mvc.delete().uri("/cards/{cardId}", CARD_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.delete().uri("/cards/{cardId}", CARD_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("GET /cards/{cardId}")
    class GetById {

        @Test
        @DisplayName("should return 200 with card response")
        void shouldReturnCard() {
            when(cardService.getById(anyLong(), anyLong())).thenReturn(createCardResponse());

            var result = assertThat(mvc.get().uri("/cards/{cardId}", CARD_ID)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("hello");
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404() {
            when(cardService.getById(anyLong(), anyLong()))
                .thenThrow(new ResourceNotFoundException("Card not found"));

            assertThat(mvc.get().uri("/cards/{cardId}", CARD_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/cards/{cardId}", CARD_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("GET /cards/deck/{deckId}")
    class GetByDeck {

        @Test
        @DisplayName("should return 200 with paginated cards")
        void shouldReturnPaginatedCards() {
            when(cardService.getByDeck(anyLong(), anyLong(), any())).thenReturn(createCardWindow());

            var result = assertThat(mvc.get().uri("/cards/deck/{deckId}", DECK_ID)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray();
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/cards/deck/{deckId}", DECK_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("GET /cards/deck/{deckId}/pending")
    class GetPendingByDeck {

        @Test
        @DisplayName("should return 200 with pending cards")
        void shouldReturnPendingCards() {
            when(cardService.getPendingByDeck(anyLong(), anyLong(), any())).thenReturn(createCardWindow());

            var result = assertThat(mvc.get().uri("/cards/deck/{deckId}/pending", DECK_ID)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray();
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/cards/deck/{deckId}/pending", DECK_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
