package com.cards.api.service.notification;

import com.maileroo.EmailAddress;
import com.maileroo.MailerooClient;
import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.service.JwtService;
import com.cards.api.service.UserService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String SUBJECT = "Time to review your Flashcards!";

    private final MailerooClient mailerooClient;
    private final UserService userService;
    private final JwtService jwtService;
    private final RetryTemplate mailRetryTemplate;
    private final SpringTemplateEngine templateEngine;
    private final MeterRegistry meterRegistry;
    private final Timer emailDeliveryTimer;
    private final Counter failedEmailsCounter;
    private final ApplicationProperties properties;

    public EmailService(MailerooClient mailerooClient, UserService userService,
                        JwtService jwtService, RetryTemplate mailRetryTemplate,
                        SpringTemplateEngine templateEngine,
                        MeterRegistry meterRegistry, Timer emailDeliveryTimer,
                        Counter failedEmailsCounter,
                        ApplicationProperties properties) {
        this.mailerooClient = mailerooClient;
        this.userService = userService;
        this.jwtService = jwtService;
        this.mailRetryTemplate = mailRetryTemplate;
        this.templateEngine = templateEngine;
        this.meterRegistry = meterRegistry;
        this.emailDeliveryTimer = emailDeliveryTimer;
        this.failedEmailsCounter = failedEmailsCounter;
        this.properties = properties;
    }

    /**
     * Executes the core email-sending logic with retry support.
     * <p>
     * This is the shared implementation used by both the async batch flow
     * ({@link #sendReviewReminder}) and the synchronous admin-triggered flow
     * ({@link #sendReviewReminderSync}).
     */
    private void doSendReviewReminder(String toEmail, String username, Long userId, Instant now) throws Exception {
        mailRetryTemplate.execute(context -> {
            var notifications = properties.getNotifications();
            String unsubscribeToken = jwtService.generateUnsubscribeToken(userId);
            String unsubscribeUrl = notifications.getApiUrl() + "/unsubscribe?token=" + unsubscribeToken;

            Context ctx = new Context();
            ctx.setVariable("username", username);
            ctx.setVariable("appUrl", notifications.getAppUrl());
            ctx.setVariable("unsubscribeUrl", unsubscribeUrl);

            String htmlContent = templateEngine.process("email/review-reminder", ctx);

            Map<String, Object> email = new HashMap<>();
            email.put("from", new EmailAddress(notifications.getFromAddress(), notifications.getFromName()));
            email.put("to", new EmailAddress(toEmail, username));
            email.put("subject", SUBJECT);
            email.put("html", htmlContent);

            mailerooClient.sendBasicEmail(email);

            // Updated inside the RetryTemplate so that if the DB fails,
            // the backoff retry (3 attempts, 2s → 4s → 8s) resolves it.
            // If it were outside and failed, the user would receive a duplicate email
            // on the next cycle because lastNotificationSent was not updated.
            userService.updateUserNotificationTimestamp(userId, now);
            return null;
        });
    }

    /**
     * Sends an email verification link with retry and metrics.
     * <p>
     * Synchronous — intended for the signup flow where the caller must know
     * whether the email was sent. Uses {@link #mailRetryTemplate} (3 attempts
     * with exponential backoff) and tracks delivery time / failures via
     * Micrometer.
     *
     * @param toEmail         recipient email address
     * @param username        recipient display name
     * @param verificationUrl the full confirmation URL (includes JWT token)
     * @throws RuntimeException if the email could not be sent after all retries
     */
    public void sendVerificationEmail(String toEmail, String username, String verificationUrl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            var notifications = properties.getNotifications();
            mailRetryTemplate.execute(context -> {
                Context ctx = new Context();
                ctx.setVariable("username", username);
                ctx.setVariable("appUrl", notifications.getAppUrl());
                ctx.setVariable("verificationUrl", verificationUrl);

                String htmlContent = templateEngine.process("email/email-verification", ctx);

                Map<String, Object> email = new HashMap<>();
                email.put("from", new EmailAddress(notifications.getFromAddress(), notifications.getFromName()));
                email.put("to", new EmailAddress(toEmail, username));
                email.put("subject", "Confirm your email address");
                email.put("html", htmlContent);

                mailerooClient.sendBasicEmail(email);
                return null;
            });
        } catch (Exception ex) {
            failedEmailsCounter.increment();
            log.error("Failed to send verification email to {} ({}) after retries: {}",
                username, toEmail, ex.getMessage());
            throw new RuntimeException("Failed to send verification email", ex);
        } finally {
            sample.stop(emailDeliveryTimer);
        }
    }

    /**
     * Sends a review reminder email asynchronously.
     * <p>
     * Intended for batch processing by {@code NotificationScheduler}.
     * The method runs on the {@code mailExecutor} thread pool. Exceptions are
     * caught, logged, and returned as a failed {@link CompletableFuture} —
     * they do not propagate to the caller.
     *
     * @param toEmail  recipient email address
     * @param username recipient display name
     * @param userId   recipient database ID (for timestamp update)
     * @param now      timestamp used for both the email context and
     *                 {@code lastNotificationSent}
     * @return a future that completes normally on success or exceptionally on
     *         failure after all retries are exhausted
     */
    @Async("mailExecutor")
    public CompletableFuture<Void> sendReviewReminder(String toEmail, String username, Long userId, Instant now) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            doSendReviewReminder(toEmail, username, userId, now);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            failedEmailsCounter.increment();
            log.warn("Failed to send reminder email to user {} ({}) after retries: {}",
                userId, toEmail, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        } finally {
            sample.stop(emailDeliveryTimer);
        }
    }

    /**
     * Sends a password reset email with retry and metrics.
     * <p>
     * Synchronous — intended for the forgot-password flow. Uses
     * {@link #mailRetryTemplate} and tracks delivery time / failures
     * via Micrometer.
     *
     * @param toEmail  recipient email address
     * @param username recipient display name
     * @param resetUrl the full password reset URL (includes JWT token)
     * @throws RuntimeException if the email could not be sent after all retries
     */
    public void sendPasswordResetEmail(String toEmail, String username, String resetUrl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            mailRetryTemplate.execute(context -> {
                Context ctx = new Context();
                ctx.setVariable("username", username);
                ctx.setVariable("appUrl", properties.getNotifications().getAppUrl());
                ctx.setVariable("resetUrl", resetUrl);

                String htmlContent = templateEngine.process("email/password-reset", ctx);

                Map<String, Object> email = new HashMap<>();
                email.put("from", new EmailAddress(properties.getNotifications().getFromAddress(), properties.getNotifications().getFromName()));
                email.put("to", new EmailAddress(toEmail, username));
                email.put("subject", "Reset your password");
                email.put("html", htmlContent);

                mailerooClient.sendBasicEmail(email);
                return null;
            });
        } catch (Exception ex) {
            failedEmailsCounter.increment();
            log.error("Failed to send password reset email to {} ({}) after retries: {}",
                username, toEmail, ex.getMessage());
            throw new RuntimeException("Failed to send password reset email", ex);
        } finally {
            sample.stop(emailDeliveryTimer);
        }
    }

    /**
     * Sends a review reminder email synchronously.
     * <p>
     * Intended for admin-triggered manual notifications where the caller
     * expects to know the outcome immediately. Exceptions propagate to the
     * caller after logging and metric recording.
     *
     * @param toEmail  recipient email address
     * @param username recipient display name
     * @param userId   recipient database ID (for timestamp update)
     * @param now      timestamp used for both the email context and
     *                 {@code lastNotificationSent}
     * @throws RuntimeException if the email could not be sent after all retries
     */
    public void sendReviewReminderSync(String toEmail, String username, Long userId, Instant now) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            doSendReviewReminder(toEmail, username, userId, now);
        } catch (Exception ex) {
            failedEmailsCounter.increment();
            log.warn("Failed to send reminder email to user {} ({}) after retries: {}",
                userId, toEmail, ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            sample.stop(emailDeliveryTimer);
        }
    }
}
