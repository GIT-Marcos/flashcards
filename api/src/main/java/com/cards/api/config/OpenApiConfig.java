package com.cards.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI flashcardsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Flashcards API")
                .description("Spaced-repetition flashcard study application API. "
                    + "All endpoints except /auth/** require a JWT Bearer token "
                    + "obtained via POST /auth/login or POST /auth/signup.")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact()
                    .name("https://marcos-portfolio-jade.vercel.app/")
                    .email("mpardo@issd.edu.ar")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                    .name(SECURITY_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Provide the JWT access token. "
                        + "Prefix with 'Bearer '. "
                        + "Example: 'Bearer eyJhbGciOiJIUzI1NiIs...'")));
    }
}
