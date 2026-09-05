package com.cards.api.controller.verification;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import com.cards.api.service.VerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@WebMvcTest(EmailVerificationController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("EmailVerificationController")
class EmailVerificationControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private VerificationService verificationService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;

    // ======================== GET /auth/confirm ========================

    @Nested
    @DisplayName("GET /auth/confirm")
    class ShowConfirmPage {

        @Test
        @DisplayName("should return 200 and validate token structure when token is valid")
        void shouldRenderConfirmPage() {
            assertThat(mvc.get().uri("/auth/confirm")
                .param("token", "valid-token"))
                .hasStatusOk();

            verify(verificationService).validateTokenStructure("valid-token");
        }

        @Test
        @DisplayName("should return 200 with error view when token is invalid")
        void shouldRenderErrorForInvalidToken() {
            doThrow(new RuntimeException("Invalid token"))
                .when(verificationService).validateTokenStructure("bad-token");

            assertThat(mvc.get().uri("/auth/confirm")
                .param("token", "bad-token"))
                .hasStatusOk();
        }
    }

    // ======================== POST /auth/confirm ========================

    @Nested
    @DisplayName("POST /auth/confirm")
    class ConfirmEmail {

        @Test
        @DisplayName("should return 200 and confirm email when token is valid")
        void shouldConfirmSuccessfully() {
            assertThat(mvc.post().uri("/auth/confirm")
                .param("token", "valid-token"))
                .hasStatusOk();

            verify(verificationService).confirmEmail("valid-token");
        }

        @Test
        @DisplayName("should return 200 with error view when confirmation fails")
        void shouldRenderErrorWhenConfirmationFails() {
            doThrow(new RuntimeException("Verification failed"))
                .when(verificationService).confirmEmail("bad-token");

            assertThat(mvc.post().uri("/auth/confirm")
                .param("token", "bad-token"))
                .hasStatusOk();
        }
    }
}
