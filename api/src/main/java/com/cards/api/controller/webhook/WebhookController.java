package com.cards.api.controller.webhook;

import com.cards.api.config.properties.MailerooProperties;
import com.cards.api.service.webhook.MailerooWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhook/maileroo")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final MailerooWebhookService webhookService;
    private final String sharedSecret;

    public WebhookController(MailerooWebhookService webhookService,
                             MailerooProperties mailerooProperties) {
        this.webhookService = webhookService;
        this.sharedSecret = mailerooProperties.getWebhookSecret();
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader("x-maileroo-signature") String signature) {

        if (!isValidSignature(payload, signature)) {
            log.warn("Invalid Maileroo webhook signature");
            return ResponseEntity.status(401).build();
        }

        webhookService.processEvent(payload);
        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
            return MessageDigest.isEqual(expected.getBytes(), signature.getBytes());
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }
}
