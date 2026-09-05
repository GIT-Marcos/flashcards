package com.cards.api.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the flashcards application.
 * <p>
 * Groups security, JWT, and notification settings under the {@code application.*} prefix.
 */
@Validated
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {

    private final Security security = new Security();
    private final Notifications notifications = new Notifications();

    public Security getSecurity() {
        return security;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    /**
     * Security-related settings including JWT token configuration.
     */
    @Validated
    public static class Security {

        private final Jwt jwt = new Jwt();
        private boolean secureCookie = true;
        private String sameSite = "Strict";
        @Min(1)
        private long verificationTokenExpiration = 86400000;
        @Min(1)
        private long resetTokenExpiration = 900000;

        public Jwt getJwt() {
            return jwt;
        }

        public boolean isSecureCookie() {
            return secureCookie;
        }

        public void setSecureCookie(boolean secureCookie) {
            this.secureCookie = secureCookie;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public long getVerificationTokenExpiration() {
            return verificationTokenExpiration;
        }

        public void setVerificationTokenExpiration(long verificationTokenExpiration) {
            this.verificationTokenExpiration = verificationTokenExpiration;
        }

        public long getResetTokenExpiration() {
            return resetTokenExpiration;
        }

        public void setResetTokenExpiration(long resetTokenExpiration) {
            this.resetTokenExpiration = resetTokenExpiration;
        }
    }

    /**
     * JWT token configuration.
     */
    @Validated
    public static class Jwt {

        @NotBlank
        private String secretKey;
        @Min(1)
        private long expiration = 900000;
        private final RefreshToken refreshToken = new RefreshToken();

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public long getExpiration() {
            return expiration;
        }

        public void setExpiration(long expiration) {
            this.expiration = expiration;
        }

        public RefreshToken getRefreshToken() {
            return refreshToken;
        }
    }

    /**
     * Refresh token expiration settings.
     */
    @Validated
    public static class RefreshToken {

        @Min(1)
        private long expiration = 604800000;

        public long getExpiration() {
            return expiration;
        }

        public void setExpiration(long expiration) {
            this.expiration = expiration;
        }
    }

    /**
     * Email notification settings for review reminders.
     */
    @Validated
    public static class Notifications {

        @Min(0)
        private int sendAtHour = 9;
        @Min(1)
        private long thresholdHours = 20;
        private String appUrl = "http://localhost:5173";
        private String apiUrl = "http://localhost:8080";
        @NotBlank
        private String fromAddress;
        private String fromName = "Flashcards App";
        @Min(1)
        private long unsubscribeTokenExpiration = 2592000000L;
        private String cron = "0 0 * * * *";

        public int getSendAtHour() {
            return sendAtHour;
        }

        public void setSendAtHour(int sendAtHour) {
            this.sendAtHour = sendAtHour;
        }

        public long getThresholdHours() {
            return thresholdHours;
        }

        public void setThresholdHours(long thresholdHours) {
            this.thresholdHours = thresholdHours;
        }

        public String getAppUrl() {
            return appUrl;
        }

        public void setAppUrl(String appUrl) {
            this.appUrl = appUrl;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public long getUnsubscribeTokenExpiration() {
            return unsubscribeTokenExpiration;
        }

        public void setUnsubscribeTokenExpiration(long unsubscribeTokenExpiration) {
            this.unsubscribeTokenExpiration = unsubscribeTokenExpiration;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
