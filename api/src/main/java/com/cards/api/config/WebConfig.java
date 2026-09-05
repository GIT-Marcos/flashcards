package com.cards.api.config;

import com.cards.api.config.properties.CorsProperties;
import com.cards.api.interceptor.TimeZoneInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TimeZoneInterceptor timeZoneInterceptor;
    private final CorsProperties corsProperties;

    public WebConfig(TimeZoneInterceptor timeZoneInterceptor, CorsProperties corsProperties) {
        this.timeZoneInterceptor = timeZoneInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(timeZoneInterceptor).addPathPatterns(
            "/auth/refresh-token",
            "/reviews/**"
        );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Time-Zone", "X-Requested-With")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
