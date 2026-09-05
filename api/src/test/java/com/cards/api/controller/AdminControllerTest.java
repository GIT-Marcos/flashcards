package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.admin.AdminController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.AdminService;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Set;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("AdminController")
class AdminControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final Long CARD_ID = 100L;

    private static SecurityUser adminUser() {
        return new SecurityUser(1L, "admin", "admin@test.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UserResponse createUserResponse(Long id, String username) {
        return new UserResponse(id, username, username + "@email.com", "America/Buenos_Aires",
            now(), now(), now(), 30, 6, true, Set.of("ROLE_USER"));
    }

    private DeckResponse createDeckResponse(Long id, String name) {
        return new DeckResponse(id, name, false, now(), now());
    }

    private CardResponse createCardResponse(Long id, String front) {
        return new CardResponse(id, front, "back", now());
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("GET /admin/users")
    class GetAllUsers {

        @Test
        @DisplayName("should return 200 with window of users")
        void shouldReturnAllUsers() {
            Window<UserResponse> window = Window.from(
                List.of(
                    createUserResponse(1L, "alice"),
                    createUserResponse(2L, "bob")
                ),
                i -> ScrollPosition.keyset(),
                false
            );
            when(adminService.getAllUsers(any())).thenReturn(window);

            var result = assertThat(mvc.get().uri("/admin/users")
                .with(user(adminUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray().hasSize(2);
            result.bodyJson().extractingPath("$.content[0].username").asString().isEqualTo("alice");
            result.bodyJson().extractingPath("$.content[1].username").asString().isEqualTo("bob");
        }

        @Test
        @DisplayName("should return 200 with empty content when no users")
        void shouldReturnEmptyList() {
            Window<UserResponse> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );
            when(adminService.getAllUsers(any())).thenReturn(empty);

            var result = assertThat(mvc.get().uri("/admin/users")
                .with(user(adminUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray().isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /admin/users/{userId}/decks")
    class GetUserDecks {

        @Test
        @DisplayName("should return 200 with user's decks")
        void shouldReturnUserDecks() {
            Window<DeckResponse> window = Window.from(
                List.of(
                    createDeckResponse(DECK_ID, "Spanish"),
                    createDeckResponse(DECK_ID + 1, "French")
                ),
                i -> ScrollPosition.keyset(),
                false
            );
            when(adminService.getUserDecks(anyLong(), any())).thenReturn(window);

            var result = assertThat(mvc.get().uri("/admin/users/{userId}/decks", USER_ID)
                .with(user(adminUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray().hasSize(2);
            result.bodyJson().extractingPath("$.content[0].name").asString().isEqualTo("Spanish");
        }

        @Test
        @DisplayName("should return 404 when user does not exist")
        void shouldReturn404WhenUserNotFound() {
            when(adminService.getUserDecks(anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

            assertThat(mvc.get().uri("/admin/users/{userId}/decks", 999L)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /admin/decks/{deckId}/cards")
    class GetDeckCards {

        @Test
        @DisplayName("should return 200 with deck's cards")
        void shouldReturnDeckCards() {
            Window<CardResponse> window = Window.from(
                List.of(
                    createCardResponse(CARD_ID, "hola"),
                    createCardResponse(CARD_ID + 1, "adios")
                ),
                i -> ScrollPosition.keyset(),
                false
            );
            when(adminService.getDeckCards(anyLong(), any())).thenReturn(window);

            var result = assertThat(mvc.get().uri("/admin/decks/{deckId}/cards", DECK_ID)
                .with(user(adminUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray().hasSize(2);
            result.bodyJson().extractingPath("$.content[0].front").asString().isEqualTo("hola");
        }

        @Test
        @DisplayName("should return 404 when deck does not exist")
        void shouldReturn404WhenDeckNotFound() {
            when(adminService.getDeckCards(anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("Deck not found"));

            assertThat(mvc.get().uri("/admin/decks/{deckId}/cards", 999L)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /admin/cards/{cardId}")
    class GetCard {

        @Test
        @DisplayName("should return 200 with card data")
        void shouldReturnCard() {
            when(adminService.getCard(anyLong())).thenReturn(createCardResponse(CARD_ID, "hola"));

            var result = assertThat(mvc.get().uri("/admin/cards/{cardId}", CARD_ID)
                .with(user(adminUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("back");
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404() {
            when(adminService.getCard(anyLong()))
                .thenThrow(new ResourceNotFoundException("The card with the id '999' does not exist"));

            var result = assertThat(mvc.get().uri("/admin/cards/999")
                .with(user(adminUser())));
            result.hasStatus(HttpStatus.NOT_FOUND);
            result.bodyJson().extractingPath("$.detail").isNotEmpty();
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("DELETE /admin/users/{userId}")
    class DeleteUser {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteUser() {
            assertThat(mvc.delete().uri("/admin/users/{userId}", USER_ID)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("The user with the id '999' does not exist"))
                .when(adminService).deleteUser(anyLong());

            assertThat(mvc.delete().uri("/admin/users/999")
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("DELETE /admin/decks/{deckId}")
    class DeleteDeck {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteDeck() {
            assertThat(mvc.delete().uri("/admin/decks/{deckId}", DECK_ID)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when deck not found")
        void shouldReturn404() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("The deck with the id '999' does not exist"))
                .when(adminService).deleteDeck(anyLong());

            assertThat(mvc.delete().uri("/admin/decks/999")
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("DELETE /admin/cards/{cardId}")
    class DeleteCard {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteCard() {
            assertThat(mvc.delete().uri("/admin/cards/{cardId}", CARD_ID)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("The card with the id '999' does not exist"))
                .when(adminService).deleteCard(anyLong());

            assertThat(mvc.delete().uri("/admin/cards/999")
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /admin/users/notifications/{userId}")
    class SendNotification {

        @Test
        @DisplayName("should return 204 when notification sent")
        void shouldReturn204() {
            assertThat(mvc.post().uri("/admin/users/notifications/{userId}", USER_ID)
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("User not found"))
                .when(adminService).sendNotification(anyLong());

            assertThat(mvc.post().uri("/admin/users/notifications/999")
                .with(user(adminUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
