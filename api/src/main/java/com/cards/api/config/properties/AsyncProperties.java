package com.cards.api.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Thread pool configuration for asynchronous executors.
 * <p>
 * Prefix: {@code app.async.*}. Each nested {@link Pool} defines core/max sizes and queue capacity.
 */
@Validated
@ConfigurationProperties(prefix = "app.async")
public class AsyncProperties {

    private Pool updateLastLogin = new Pool(1, 1, 10);
    private Pool mail = new Pool(1, 2, 10);
    private Pool systemEvents = new Pool(1, 2, 20);
    private Pool defaultPool = new Pool(1, 2, 20);

    public Pool getUpdateLastLogin() {
        return updateLastLogin;
    }

    public void setUpdateLastLogin(Pool updateLastLogin) {
        this.updateLastLogin = updateLastLogin;
    }

    public Pool getMail() {
        return mail;
    }

    public void setMail(Pool mail) {
        this.mail = mail;
    }

    public Pool getSystemEvents() {
        return systemEvents;
    }

    public void setSystemEvents(Pool systemEvents) {
        this.systemEvents = systemEvents;
    }

    public Pool getDefaultPool() {
        return defaultPool;
    }

    public void setDefaultPool(Pool defaultPool) {
        this.defaultPool = defaultPool;
    }

    /**
     * Configuration for a single thread pool (core, max, queue capacity).
     */
    @Validated
    public static class Pool {

        @Min(1)
        private int corePoolSize;
        @Min(1)
        private int maxPoolSize;
        @Min(0)
        private int queueCapacity;

        public Pool() {
        }

        public Pool(int corePoolSize, int maxPoolSize, int queueCapacity) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
