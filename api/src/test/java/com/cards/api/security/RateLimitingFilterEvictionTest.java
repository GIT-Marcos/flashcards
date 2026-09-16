package com.cards.api.security;

import com.cards.api.config.properties.RateLimitingConfig;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingFilter")
class RateLimitingFilterEvictionTest {

    private static final int MAXIMUM_SIZE = 3;
    private static final int DISTINCT_IPS = 5;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitingConfig config = new RateLimitingConfig();

        RateLimitingConfig.Auth authConfig = new RateLimitingConfig.Auth();
        RateLimitingConfig.EndpointConfig loginConfig = new RateLimitingConfig.EndpointConfig();
        loginConfig.setCapacity(5);
        loginConfig.setRefillTokens(1);
        loginConfig.setRefillPeriod(Duration.ofSeconds(10));
        authConfig.setLogin(loginConfig);
        config.setAuth(authConfig);

        RateLimitingConfig.CacheConfig cacheConfig = new RateLimitingConfig.CacheConfig();
        cacheConfig.setMaximumSize(MAXIMUM_SIZE);
        cacheConfig.setExpireAfterAccess(Duration.ofHours(1));
        config.setCache(cacheConfig);

        filter = new RateLimitingFilter(config);
    }

    @Test
    @DisplayName("should evict buckets when maximum cache size is reached")
    void shouldEvictBucketsWhenMaximumSizeReached() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 1; i <= DISTINCT_IPS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setServletPath("/auth/login");
            request.setRemoteAddr("192.168.1." + i);
            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(DISTINCT_IPS)).doFilter(any(), any());

        Cache<String, Bucket> cache = filter.bucketCache();
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(MAXIMUM_SIZE);

        long presentKeys = 0;
        for (int i = 1; i <= DISTINCT_IPS; i++) {
            if (cache.getIfPresent("192.168.1." + i + ":/auth/login") != null) {
                presentKeys++;
            }
        }
        assertThat(presentKeys).isLessThanOrEqualTo(MAXIMUM_SIZE);
    }
}
