# Flashcards API

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)

# Features

- **Deck & Card management** — CRUD operations with cursor-based pagination
- **SM-2 Spaced Repetition** — Review cards with the proven SM-2 algorithm
- **Study Sessions** — Track study sessions grouped by time
- **JWT Authentication** — Access + refresh token pair with configurable expiration
- **Rate Limiting** — Bucket4j-based rate limiting on auth endpoints
- **Email Notifications** — Maileroo REST API review reminders with Thymeleaf templates
- **Timezone Detection** — Automatic timezone detection per user
- **Metrics & Monitoring** — Micrometer + Prometheus via Spring Boot Actuator
- **Optimistic Locking** — JPA version-based concurrency control for cards and sessions
- **Comprehensive Testing** — 43 test classes using TestContainers, MockMvcTester, and AssertJ
