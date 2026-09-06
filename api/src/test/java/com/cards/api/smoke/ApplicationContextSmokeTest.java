package com.cards.api.smoke;

import com.cards.api.config.*;
import com.cards.api.controller.admin.AdminController;
import com.cards.api.controller.auth.AuthController;
import com.cards.api.controller.flashcard.*;
import com.cards.api.controller.user.UserController;
import com.cards.api.exception.GlobalExceptionHandler;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.interceptor.TimeZoneInterceptor;
import com.cards.api.listener.UserLoginEventListener;
import com.cards.api.listener.UserSettingsEventListener;
import com.cards.api.mapper.*;
import com.cards.api.repo.*;
import com.cards.api.security.JwtAuthenticationFilter;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.*;
import com.cards.api.service.notification.EmailService;
import com.cards.api.service.notification.NotificationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that verifies the full Spring application context loads successfully
 * and all critical beans are properly wired.
 *
 * <p>This is the first test to run in CI: if it fails, nothing else matters.
 * It catches wiring issues, missing beans, and misconfiguration before any
 * functional test executes.</p>
 *
 * <p>Uses {@code @SpringBootTest} with Testcontainers for a real PostgreSQL instance.
 * Mail and scheduling are configured with dummy values to prevent startup failures.</p>
 */
@SpringBootTest(properties = {
    "application.security.jwt.secret-key=smokeTestSecretKeyThatIsLongEnoughForHS256Algorithm!!",
    "application.security.jwt.expiration=900000",
    "application.security.jwt.refresh-token.expiration=604800000",
    "application.security.verification-token-expiration=86400000",
    "application.security.reset-token-expiration=900000",
    "application.notifications.send-at-hour=9",
    "application.notifications.threshold-hours=20",
    "application.notifications.cron=0 0 0 * * *",
    "application.notifications.app-url=http://localhost:5173",
    "application.notifications.from-address=notificaciones@flashcards.app",
    "application.notifications.from-name=Flashcards App",
    "application.notifications.unsubscribe-token-expiration=2592000000",
    "application.notifications.api-url=http://localhost:8080",
    "application.security.secure-cookie=false",
    "application.security.same-site=Strict",
    "maileroo.api-key=smoke-test-api-key",
    "maileroo.webhook-secret=smoke-test-webhook-secret",
    "rate-limiter.auth.login.capacity=10",
    "rate-limiter.auth.login.refill-tokens=5",
    "rate-limiter.auth.login.refill-period=10s",
    "rate-limiter.auth.signup.capacity=10",
    "rate-limiter.auth.signup.refill-tokens=5",
    "rate-limiter.auth.signup.refill-period=10s",
    "rate-limiter.auth.confirm.capacity=100",
    "rate-limiter.auth.confirm.refill-tokens=10",
    "rate-limiter.auth.confirm.refill-period=10s",
    "rate-limiter.auth.refresh-token.capacity=10",
    "rate-limiter.auth.refresh-token.refill-tokens=2",
    "rate-limiter.auth.refresh-token.refill-period=10s",
    "rate-limiter.auth.logout.capacity=20",
    "rate-limiter.auth.logout.refill-tokens=5",
    "rate-limiter.auth.logout.refill-period=10s"
})
@Import({TestcontainersConfig.class})
@DisplayName("Smoke: Application Context")
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    // ================================================================== //
    //  Context loads
    // ================================================================== //

    @Nested
    @DisplayName("Context startup")
    class ContextStartup {

        @Test
        @DisplayName("Application context should load without errors")
        void contextLoads() {
            // If we reach this line, the context loaded successfully.
            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("FlashcardsApplication should be the main bean")
        void mainApplicationBeanExists() {
            assertThat(context.containsBean("apiApplication")).isTrue();
        }
    }

    // ================================================================== //
    //  Controllers
    // ================================================================== //

    @Nested
    @DisplayName("Controller beans")
    class ControllerBeans {

        @Test
        @DisplayName("All 7 controllers should be loaded")
        void allControllersAreLoaded() {
            assertThat(context.getBean(AuthController.class)).isNotNull();
            assertThat(context.getBean(UserController.class)).isNotNull();
            assertThat(context.getBean(DeckController.class)).isNotNull();
            assertThat(context.getBean(CardController.class)).isNotNull();
            assertThat(context.getBean(ReviewController.class)).isNotNull();
            assertThat(context.getBean(SessionController.class)).isNotNull();
            assertThat(context.getBean(AdminController.class)).isNotNull();
        }
    }

    // ================================================================== //
    //  Services
    // ================================================================== //

    @Nested
    @DisplayName("Service beans")
    class ServiceBeans {

        @Test
        @DisplayName("All business services should be loaded")
        void allBusinessServicesAreLoaded() {
            assertThat(context.getBean(AuthService.class)).isNotNull();
            assertThat(context.getBean(UserService.class)).isNotNull();
            assertThat(context.getBean(DeckService.class)).isNotNull();
            assertThat(context.getBean(CardService.class)).isNotNull();
            assertThat(context.getBean(ReviewService.class)).isNotNull();
            assertThat(context.getBean(SessionService.class)).isNotNull();
            assertThat(context.getBean(AdminService.class)).isNotNull();
            assertThat(context.getBean(JwtService.class)).isNotNull();
            assertThat(context.getBean(CustomUserDetailService.class)).isNotNull();
        }

        @Test
        @DisplayName("Notification services should be loaded")
        void notificationServicesAreLoaded() {
            assertThat(context.getBean(EmailService.class)).isNotNull();
            assertThat(context.getBean(NotificationScheduler.class)).isNotNull();
        }
    }

    // ================================================================== //
    //  Repositories
    // ================================================================== //

    @Nested
    @DisplayName("Repository beans")
    class RepositoryBeans {

        @Test
        @DisplayName("All 5 repositories should be loaded")
        void allRepositoriesAreLoaded() {
            assertThat(context.getBean(UserRepository.class)).isNotNull();
            assertThat(context.getBean(DeckRepository.class)).isNotNull();
            assertThat(context.getBean(CardRepository.class)).isNotNull();
            assertThat(context.getBean(CardReviewLogRepository.class)).isNotNull();
            assertThat(context.getBean(StudySessionRepository.class)).isNotNull();
        }
    }

    // ================================================================== //
    //  Mappers
    // ================================================================== //

    @Nested
    @DisplayName("Mapper beans")
    class MapperBeans {

        @Test
        @DisplayName("All 4 mappers should be loaded")
        void allMappersAreLoaded() {
            assertThat(context.getBean(UserMapper.class)).isNotNull();
            assertThat(context.getBean(DeckMapper.class)).isNotNull();
            assertThat(context.getBean(CardMapper.class)).isNotNull();
            assertThat(context.getBean(SessionMapper.class)).isNotNull();
        }
    }

    // ================================================================== //
    //  Security
    // ================================================================== //

    @Nested
    @DisplayName("Security beans")
    class SecurityBeans {

        @Test
        @DisplayName("AuthenticationManager should be available")
        void authenticationManagerIsAvailable() {
            assertThat(context.getBean(AuthenticationManager.class)).isNotNull();
        }

        @Test
        @DisplayName("PasswordEncoder should be available")
        void passwordEncoderIsAvailable() {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            assertThat(encoder).isNotNull();

            // Verify it's actually a BCrypt encoder as configured
            String encoded = encoder.encode("test-password");
            assertThat(encoder.matches("test-password", encoded)).isTrue();
        }

        @Test
        @DisplayName("SecurityFilterChain should be configured")
        void securityFilterChainIsConfigured() {
            assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
        }

        @Test
        @DisplayName("JwtAuthenticationFilter should be loaded")
        void jwtAuthenticationFilterIsLoaded() {
            assertThat(context.getBean(JwtAuthenticationFilter.class)).isNotNull();
        }

        @Test
        @DisplayName("SecurityConfig should be loaded")
        void securityConfigIsLoaded() {
            assertThat(context.getBean(SecurityConfig.class)).isNotNull();
        }
    }

    // ================================================================== //
    //  Infrastructure / Config
    // ================================================================== //

    @Nested
    @DisplayName("Infrastructure beans")
    class InfrastructureBeans {

        @Test
        @DisplayName("All configuration classes should be loaded")
        void allConfigsAreLoaded() {
            assertThat(context.getBean(ApplicationConfig.class)).isNotNull();
            assertThat(context.getBean(WebConfig.class)).isNotNull();
            assertThat(context.getBean(AuditConfig.class)).isNotNull();
            assertThat(context.getBean(AsyncConfig.class)).isNotNull();
            assertThat(context.getBean(SchedulingConfig.class)).isNotNull();
        }

        @Test
        @DisplayName("MailerooClient should be configured (dummy for tests)")
        void mailerooClientIsConfigured() {
            assertThat(context.getBean(com.maileroo.MailerooClient.class)).isNotNull();
        }

        @Test
        @DisplayName("TaskScheduler should be available")
        void taskSchedulerIsAvailable() {
            assertThat(context.getBean(TaskScheduler.class)).isNotNull();
        }

        @Test
        @DisplayName("Async executors should be available")
        void asyncExecutorsAreAvailable() {
            assertThat(context.getBean("updateLastLoginExecutor", Executor.class)).isNotNull();
            assertThat(context.getBean("mailExecutor", Executor.class)).isNotNull();
            assertThat(context.getBean("systemEventsExecutor", Executor.class)).isNotNull();
            assertThat(context.getBean("defaultAsyncExecutor", Executor.class)).isNotNull();
        }

        @Test
        @DisplayName("GlobalExceptionHandler should be loaded")
        void globalExceptionHandlerIsLoaded() {
            assertThat(context.getBean(GlobalExceptionHandler.class)).isNotNull();
        }

        @Test
        @DisplayName("Event listeners should be loaded")
        void eventListenersAreLoaded() {
            assertThat(context.getBean(UserLoginEventListener.class)).isNotNull();
            assertThat(context.getBean(UserSettingsEventListener.class)).isNotNull();
        }

        @Test
        @DisplayName("Interceptors should be loaded")
        void interceptorsAreLoaded() {
            assertThat(context.getBean(TimeZoneInterceptor.class)).isNotNull();
        }
    }

}
