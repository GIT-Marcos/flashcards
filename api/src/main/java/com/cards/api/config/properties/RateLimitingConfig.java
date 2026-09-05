package com.cards.api.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token-bucket rate limiting configuration for authentication endpoints.
 * <p>
 * Prefix: {@code rate-limiter.*}. Defines per-endpoint capacity, refill rate, and refill period
 * used by {@link com.cards.api.security.RateLimitingFilter}.
 */
@Validated
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimitingConfig {

    private Auth auth;

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * Per-endpoint rate limit settings for each auth operation.
     */
    @Validated
    public static class Auth {

        private EndpointConfig signup;
        private EndpointConfig confirm;
        private EndpointConfig login;
        private EndpointConfig refreshToken;
        private EndpointConfig logout;
        private EndpointConfig forgotPassword;
        private EndpointConfig resetPassword;

        public EndpointConfig getSignup() {
            return signup;
        }

        public void setSignup(EndpointConfig signup) {
            this.signup = signup;
        }

        public EndpointConfig getConfirm() {
            return confirm;
        }

        public void setConfirm(EndpointConfig confirm) {
            this.confirm = confirm;
        }

        public EndpointConfig getLogin() {
            return login;
        }

        public void setLogin(EndpointConfig login) {
            this.login = login;
        }

        public EndpointConfig getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(EndpointConfig refreshToken) {
            this.refreshToken = refreshToken;
        }

        public EndpointConfig getLogout() {
            return logout;
        }

        public void setLogout(EndpointConfig logout) {
            this.logout = logout;
        }

        public EndpointConfig getForgotPassword() {
            return forgotPassword;
        }

        public void setForgotPassword(EndpointConfig forgotPassword) {
            this.forgotPassword = forgotPassword;
        }

        public EndpointConfig getResetPassword() {
            return resetPassword;
        }

        public void setResetPassword(EndpointConfig resetPassword) {
            this.resetPassword = resetPassword;
        }

        public EndpointConfig getForPath(String path) {
            if (path == null) {
                return login;
            }
            if (path.endsWith("/signup")) {
                return signup;
            }
            if (path.endsWith("/confirm")) {
                return confirm;
            }
            if (path.endsWith("/login")) {
                return login;
            }
            if (path.endsWith("/refresh-token")) {
                return refreshToken;
            }
            if (path.endsWith("/logout")) {
                return logout;
            }
            if (path.endsWith("/forgot-password")) {
                return forgotPassword;
            }
            if (path.endsWith("/reset-password")) {
                return resetPassword;
            }
            return login;
        }
    }

    /**
     * Capacity, refill tokens, and refill period for a single rate-limited endpoint.
     */
    @Validated
    public static class EndpointConfig {

        @Min(1)
        private long capacity;
        @Min(1)
        private long refillTokens;
        @NotNull
        private Duration refillPeriod;

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }
    }
}
