package com.cards.api.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class MailerooWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MailerooWebhookService.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MailerooWebhookService(UserRepository userRepository, ObjectMapper objectMapper, Clock clock) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Procesa eventos del webhook de Maileroo.
     * <p>
     * La actualización de timestamps sigue un patrón de actualización optimista:
     * <p>
     * EmailService.sendReviewReminder() asume éxito al enviar el email y actualiza
     * lastNotificationSent inmediatamente (optimistic write). Si el email rebota,
     * el webhook "failed" funciona como rollback, reseteando lastNotificationSent
     * a null para que el usuario sea reintentado en el próximo ciclo.
     * <p>
     * El webhook "delivered" es mayormente no-op: confirma que la entrega ocurrió,
     * pero el optimistic write ya registró el timestamp. Su guard (lastNotificationSent
     * == null) cubre el escenario donde el optimistic write no se ejecutó
     * (por ej., si en el futuro se separa el envío de la actualización).
     */
    @Transactional
    public void processEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.get("event_type").asText();
            String toEmail = root.path("event_data").path("to").asText();

            if (toEmail.isEmpty()) {
                log.warn("Maileroo webhook missing recipient email");
                return;
            }

            switch (eventType) {
                case "delivered" -> handleDelivered(toEmail);
                case "failed" -> handleFailed(toEmail);
                default -> log.debug("Ignoring Maileroo event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Failed to process Maileroo webhook payload", e);
        }
    }

    /**
     * Confirmación de entrega. Es no-op si el optimistic write ya registró el
     * timestamp (caso normal). Solo actualiza si lastNotificationSent es null,
     * cubriendo el caso en que el optimistic write no se ejecutó.
     */
    private void handleDelivered(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(
            user -> {
                if (user.getLastNotificationSent() == null) {
                    user.setLastNotificationSent(Instant.now(clock));
                    userRepository.save(user);
                    log.info("Updated notification timestamp for {} after delivery", email);
                }
            },
            () -> log.warn("No user found for delivered email: {}", email)
        );
    }

    /**
     * Rollback del optimistic write. Si el email rebotó, resetea
     * lastNotificationSent a null para que el scheduler reintente al usuario
     * en el próximo ciclo.
     */
    private void handleFailed(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(
            user -> {
                if (user.getLastNotificationSent() != null) {
                    user.setLastNotificationSent(null);
                    userRepository.save(user);
                    log.info("Reset notification timestamp for {} after delivery failure", email);
                }
            },
            () -> log.warn("No user found for failed email: {}", email)
        );
    }
}
