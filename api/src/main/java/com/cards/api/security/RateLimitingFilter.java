package com.cards.api.security;

import com.cards.api.config.properties.RateLimitingConfig;
import com.cards.api.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingConfig rateLimitingConfig;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitingConfig rateLimitingConfig) {
        this.rateLimitingConfig = rateLimitingConfig;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String path = request.getServletPath();
        String bucketKey = clientIp + ":" + path;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(path));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            int retryAfter = (int) rateLimitingConfig.getAuth().getForPath(path).getRefillPeriod().getSeconds();
            throw new TooManyRequestsException(
                "Too many requests. Please try again in " + retryAfter + " seconds.",
                retryAfter
            );
        }
    }

    private Bucket createBucket(String path) {
        RateLimitingConfig.EndpointConfig config = rateLimitingConfig.getAuth().getForPath(path);

        Bandwidth limit = Bandwidth.builder()
            .capacity(config.getCapacity())
            .refillGreedy(config.getRefillTokens(), config.getRefillPeriod())
            .build();

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
