# AGENTS.md

Este es un monorepo sobre un proyecto para la gestión de Flashcards, tarjetas que permiten el estudio por medio de la
repetición espaciada usando como base el algoritmo SM-2.

# Estructura del proyecto

Monorepo. Dos módulos independientes, sin sistema compartido de build. Cada uno tiene su propia toolchain.

| Módulo | Ruta     | Responsabilidades                                                                                 |
|--------|----------|---------------------------------------------------------------------------------------------------|
| API    | `api/`   | Backend del sistema dedicado al procesamiento de las peticiones y gestión de base de datos.       |
| Front  | `front/` | Frontend del sistema, su única responsabilidad es permitir a los usuarios gestionar sus tarjetas. |

# Fuentes de verdad

La documentación es la fuente de verdad a partir de la cual se implementa el código y toda funcionalidad del sistema

# Comandos

## API module (run from `api/`)

```
*completar
```

## Frontend module (run from `front/`)

```
*completar
```

## Personalizados para el agente IA

```
*completar
```

## Developer Commands

```powershell
*completar
```

# Database

- PostgreSQL via Docker Compose: `docker-compose up -d`
- Port: 5433 (mapped from 5432)
- Credentials: `postgres:password`, DB: `flashcards_db`
- Migrations: Flyway (check `src/main/resources/db/migration`)
- V1-V8: shared across all profiles (`db/migration/`)
    - V6: seed de datos de prueba (1 usuario `test_user` con 7 decks, ~83 cards, 5 sesiones, ~72 reviews)
    - V8: tabla `user_api_keys` para API keys cifradas de proveedores IA
- V5 uses Flyway placeholders (`${ADMIN_USERNAME}`, `${ADMIN_EMAIL}`, etc.) resolved from each profile's YAML
    - See `application-dev.template.yaml` for the required dev configuration (copy to `application-dev.yaml`)

# Deployment Profiles

| Profile         | File                    | Purpose                               |
|-----------------|-------------------------|---------------------------------------|
| `dev` (default) | `application-dev.yaml`  | Local development with Docker Compose |
| `prod`          | `application-prod.yaml` | Full production                       |

See [`.env.example`](.env.example) for the full list with descriptions and defaults.
Required for `prod`: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `ADMIN_USERNAME`,
`ADMIN_EMAIL`, `ADMIN_PSW_HASH`, `ADMIN_ZONE`, `ALLOWED_ORIGINS`, `MAILEROO_API_KEY`, `MAILEROO_WEBHOOK_SECRET`,
`APP_URL`

# API Endpoints

## Security matrix

| Security level     | Matcher                                                                                                                      | Endpoints                                   |
|--------------------|------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `permitAll`        | `/auth/**`, `/unsubscribe/**`, `/webhook/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`, `/actuator/health/**` | Auth, Unsubscribe, Webhook, Swagger, Health |
| `hasRole('ADMIN')` | `/actuator/**` (non-health), `/admin/**`                                                                                     | Admin                                       |
| `authenticated`    | All other requests                                                                                                           | Users, Decks, Cards, Reviews, Sessions      |

> Refresh token is set as `httpOnly` cookie with `SameSite=None; Secure` (all profiles) — never in response body (
`@JsonIgnore` on `AuthResponse.refreshToken`).

## Auth Controller (`/auth`)

| Method | Path                    | Request                 | Response                       | Notes                                                                                                                                                 |
|--------|-------------------------|-------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST` | `/auth/signup`          | `RegisterRequest`       | `SignupResponse` (202)         | `@Valid`: username 4-50 chars, password 8-20 with complexity regex, valid IANA zone. Sends verification email — no account created until confirmation |
| `GET`  | `/auth/confirm`         | Query param `token`     | HTML page (200)                | Shows "Confirm Email" button (Thymeleaf)                                                                                                              |
| `POST` | `/auth/confirm`         | Form param `token`      | HTML page (200)                | Validates JWT, creates user, shows result page (Thymeleaf)                                                                                            |
| `POST` | `/auth/login`           | `LoginRequest`          | `AuthResponse` (200)           | Authenticate via username/password                                                                                                                    |
| `POST` | `/auth/refresh-token`   | Cookie `refresh_token`  | `AuthResponse` (200)           | Exchange refresh cookie for new token pair                                                                                                            |
| `POST` | `/auth/logout`          | —                       | `Void` (204)                   | Clears refresh token cookie                                                                                                                           |
| `POST` | `/auth/forgot-password` | `ForgotPasswordRequest` | `ForgotPasswordResponse` (202) | Sends password reset email if account exists. Always returns 202 to avoid user enumeration                                                            |
| `POST` | `/auth/reset-password`  | `ResetPasswordRequest`  | `ResetPasswordResponse` (200)  | Validates JWT and updates password. Token invalidated after use                                                                                       |

## User Controller (`/users/me`) — authenticated

| Method   | Path        | Request            | Response       | Notes                                |
|----------|-------------|--------------------|----------------|--------------------------------------|
| `GET`    | `/users/me` | —                  | `UserResponse` | Current user profile                 |
| `PATCH`  | `/users/me` | `PatchUserRequest` | `UserResponse` | Partial update (all fields optional) |
| `DELETE` | `/users/me` | —                  | `Void` (204)   | Permanently deletes own account      |

## User API Key Controller (`/users/me/api-keys`) — authenticated

| Method   | Path                         | Request               | Response               | Notes                                  |
|----------|------------------------------|-----------------------|------------------------|----------------------------------------|
| `GET`    | `/users/me/api-keys`         | —                     | `List<ApiKeyResponse>` | Key values never exposed               |
| `POST`   | `/users/me/api-keys`         | `CreateApiKeyRequest` | `ApiKeyResponse` (201) | Encrypted, `409` if duplicate provider |
| `DELETE` | `/users/me/api-keys/{keyId}` | —                     | `Void` (204)           | Own key only                           |

## Deck Controller (`/decks`) — authenticated

| Method   | Path              | Request                         | Response                     | Notes                                     |
|----------|-------------------|---------------------------------|------------------------------|-------------------------------------------|
| `POST`   | `/decks`          | `CreateDeckRequest`             | `DeckResponse` (201)         | Name max 100 chars                        |
| `POST`   | `/decks/ai`       | `MultipartFile + @RequestParam` | `AiGenerationResponse` (201) | Generate deck + cards from .txt/.pdf file |
| `POST`   | `/decks/ai/topic` | `@RequestBody AiTopicRequest`   | `AiGenerationResponse` (201) | Generate deck + cards from topic prompt   |
| `PATCH`  | `/decks/{deckId}` | `PatchDeckRequest`              | `DeckResponse`               | Update deck name                          |
| `DELETE` | `/decks/{deckId}` | —                               | `Void` (204)                 | Deletes deck + all cards                  |
| `GET`    | `/decks`          | `CursorPaginationRequest`       | `Window<DeckResponse>`       | Paginated list of user's decks            |
| `GET`    | `/decks/due`      | `CursorPaginationRequest`       | `Window<DeckResponse>`       | Decks with cards due for review           |

## Card Controller (`/cards`) — authenticated

| Method   | Path                           | Request                         | Response                     | Notes                                     |
|----------|--------------------------------|---------------------------------|------------------------------|-------------------------------------------|
| `POST`   | `/cards/deck/{deckId}`         | `CreateCardRequest`             | `CardResponse` (201)         | front max 255 chars, back max 5000        |
| `POST`   | `/cards/ai/deck/{deckId}`      | `MultipartFile + @RequestParam` | `AiGenerationResponse` (201) | Generate cards in existing deck from file |
| `PATCH`  | `/cards/{cardId}`              | `PatchCardRequest`              | `CardResponse`               | Update front/back                         |
| `DELETE` | `/cards/{cardId}`              | —                               | `Void` (204)                 | Permanently deletes card                  |
| `GET`    | `/cards/deck/{deckId}/pending` | `CursorPaginationRequest`       | `Window<CardResponse>`       | Cards due for review in a deck            |
| `GET`    | `/cards/deck/{deckId}`         | `CursorPaginationRequest`       | `Window<CardResponse>`       | All cards in a deck                       |
| `GET`    | `/cards/{cardId}`              | —                               | `CardResponse`               | Single card by ID                         |

## Review Controller (`/reviews`) — authenticated

| Method | Path                     | Request         | Response       | Notes                                                        |
|--------|--------------------------|-----------------|----------------|--------------------------------------------------------------|
| `POST` | `/reviews/card/{cardId}` | `ReviewRequest` | `CardResponse` | Quality 0-5 (0=blackout, 5=perfect) — SM-2 spaced repetition |

## Session Controller (`/sessions`) — authenticated

| Method | Path              | Request                   | Response                  | Notes                                                                 |
|--------|-------------------|---------------------------|---------------------------|-----------------------------------------------------------------------|
| `GET`  | `/sessions`       | `CursorPaginationRequest` | `Window<SessionResponse>` | Paginated study session history                                       |
| `GET`  | `/sessions/stats` | —                         | `UserStatsResponse`       | Aggregated stats (total reviews, accuracy rate, quality distribution) |

## Admin Controller (`/admin`) — `ROLE_ADMIN`

| Method   | Path                                  | Request                   | Response               | Notes                                   |
|----------|---------------------------------------|---------------------------|------------------------|-----------------------------------------|
| `GET`    | `/admin/users`                        | `CursorPaginationRequest` | `Window<UserResponse>` | All registered users                    |
| `GET`    | `/admin/users/{userId}/decks`         | `CursorPaginationRequest` | `Window<DeckResponse>` | Decks of a specific user                |
| `GET`    | `/admin/decks/{deckId}/cards`         | `CursorPaginationRequest` | `Window<CardResponse>` | Cards of a specific deck                |
| `GET`    | `/admin/cards/{cardId}`               | —                         | `CardResponse`         | Single card by ID                       |
| `DELETE` | `/admin/users/{userId}`               | —                         | `Void` (204)           | Permanently deletes any user            |
| `DELETE` | `/admin/decks/{deckId}`               | —                         | `Void` (204)           | Permanently deletes any deck            |
| `DELETE` | `/admin/cards/{cardId}`               | —                         | `Void` (204)           | Permanently deletes any card            |
| `POST`   | `/admin/users/notifications/{userId}` | —                         | `Void` (204)           | Triggers review reminder email manually |

## Unsubscribe Controller — `permitAll`

| Method | Path                         | Request           | Response        | Notes                                                     |
|--------|------------------------------|-------------------|-----------------|-----------------------------------------------------------|
| `GET`  | `/unsubscribe?token={token}` | Query param token | HTML page (200) | Unsubscribes user from review reminder emails (Thymeleaf) |

## Webhook Controller — `permitAll`, HMAC-verified

| Method | Path                | Request          | Response     | Notes                                                                                               |
|--------|---------------------|------------------|--------------|-----------------------------------------------------------------------------------------------------|
| `POST` | `/webhook/maileroo` | Raw JSON payload | `Void` (200) | Processes email delivery events; signature verified via `x-maileroo-signature` header (HMAC-SHA256) |

## Cursor Pagination (Keyset)

All list endpoints use keyset pagination via `CursorPaginationRequest` as query parameters:

| Parameter     | Type                 | Default               | Description                                 |
|---------------|----------------------|-----------------------|---------------------------------------------|
| `lastId`      | `Long`               | —                     | ID of last item from previous page (cursor) |
| `cursorValue` | `Instant` (ISO 8601) | —                     | Sort-field value of last item               |
| `pageSize`    | `Integer`            | `15` (max `100`)      | Items per page                              |
| `direction`   | `Sort.Direction`     | (depends on endpoint) | `ASC` or `DESC`                             |

Factory methods define default sort per endpoint:

- `forDecks()` — `createdAt` ASC
- `forCards()` — `nextReviewDate` ASC
- `forSessions()` — `startTime` DESC
- `forUsers()` — `createdAt` ASC

Secondary sort is always `id ASC` as tiebreaker.

# SM-2 Spaced Repetition Algorithm

The review system implements the SM-2 algorithm (Piotr Wozniak, 1987) with timezone-aware date normalization:

- **Quality:** 0 (blackout) to 5 (perfect). Quality `< 3` = fail, `>= 3` = pass.
- **Fail path:** `intervalDays = 1`, `repetitionCount = 0` (full reset). EF still updated.
- **Success path:** `n=0 → 1 day`, `n=1 → 6 days`, `n>=2 → round(interval × EF)`. Increments `repetitionCount`.
- **EF formula:** `EF' = EF + (0.1 - (5 - q) × (0.08 + (5 - q) × 0.02))`, clamped to minimum `1.3`.
- **Order:** Interval uses the **pre-update** EF (per SM-2 spec); EF is updated after interval calculation.
- **Next review:** `reviewMoment + intervalDays`, normalized to user's `startOfDay` hour in their IANA timezone.
- **Snapshot:** `CardReviewLog` stores quality, EF, interval, repetitionCount, and nextReviewDate **after** the review.
- **Orchestrator:** `ReviewService.review()` loads card with authorization, validates `nextReviewDate`, applies SM-2,
  persists `CardReviewLog`, updates `StudySession` metrics. Concurrent reviews produce HTTP 409 Conflict via `@Version`
  optimistic locking.

# Observability

- **Metrics:** Micrometer vía Spring Boot Actuator
    - Contadores, timers y gauges expuestos en `/actuator/metrics` y `/actuator/prometheus`
    - Métricas registradas: `decks.created.total`, `email.delivery.time`, `users.active.count`,
      `scheduled.tasks.failed.total`
- **Health checks:** `/actuator/health` (DB incluida)

# Notification System

- **Scheduler:** Cron `NOTIFY_CRON: 0 0 * * * *` (every hour), timezone-aware — sends at `NOTIFY_MIN_HOUR` (default 9
  AM) in each user's IANA zone
- **Threshold:** `NOTIFY_THRESHOLD_HOURS` (default 20h) minimum gap between consecutive notifications to the same user
- **Query:** JPQL `EXISTS` subquery finds users with decks containing cards due (`nextReviewDate <= now`)
- **Pending flags:** After processing, `DeckService.updatePendingFlagsForUsers()` updates `hasPendingCards` in
  `REQUIRES_NEW` transaction
- **Email client:** `MailerooClient` (maileroo-java-sdk 1.0.0) with 30s timeout, API key from `MAILEROO_API_KEY` env var
- **Retry:** `RetryTemplate` — 3 attempts, exponential backoff 2s→4s→8s (max 10s), only on `IOException`
- **Async path:** `@Async("mailExecutor")` — pool 1/2 core/max, queue 10
- **Sync path:** `sendReviewReminderSync()` — blocking, used by admin trigger (
  `POST /admin/users/notifications/{userId}`), re-throws as `RuntimeException`
- **Template:** Thymeleaf `templates/email/review-reminder.html` with `username` and `appUrl` variables
- **DB update inside retry:** `UserService.updateUserNotificationTimestamp(userId, now)` runs in `REQUIRES_NEW` inside
  the retry callback, so transient DB failures also get retried
- **Webhook rollback:** On `failed` event via `POST /webhook/maileroo`, `lastNotificationSent` is reset to `null` so the
  scheduler re-queues the user
- **Metrics tracked:**
    - `flashcards.email.delivery.time` — Timer, percentile histogram, SLA targets 500ms/1s/2s/5s
    - `flashcards.email.failed.total` — Counter, incremented after all retries exhausted

# Application Events

The project uses Spring's `ApplicationEventPublisher` with two async events:

| Event                     | Trigger                                               | Listener                                           | Executor                           | Phase                                       |
|---------------------------|-------------------------------------------------------|----------------------------------------------------|------------------------------------|---------------------------------------------|
| `UserLoginEvent`          | `AuthService.login()`                                 | `UserLoginEventListener.handleLastLoginUpdate()`   | `updateLastLoginExecutor` (1:1:10) | `@TransactionalEventListener(AFTER_COMMIT)` |
| `UserTimeZoneUpdateEvent` | `TimeZoneInterceptor` when `Time-Zone` header differs | `UserSettingsEventListener.handleTimeZoneUpdate()` | `systemEventsExecutor` (1:2:20)    | `@EventListener` + `@Transactional`         |

- **UserLoginEvent:** Published after successful login. Updates `lastLogin` via JPQL `UPDATE` in `REQUIRES_NEW`
  transaction (runs only if login tx commits).
- **UserTimeZoneUpdateEvent:** Published by `TimeZoneInterceptor` on `/auth/login`, `/auth/refresh-token`,
  `/reviews/**`. Validates `Time-Zone` header (IANA), skips if unchanged. Listener validates zone, finds user, and
  `save()` only if zone actually differs.
- **Executor pools:** 4 named executors in `AsyncConfig`, all using `LoggingCallerRunsPolicy` (logs pool saturation then
  runs in caller thread).

# Key Rotation Procedure

## Purpose

Rotate the `API_KEY_ENCRYPTION_SECRET` master key (AES-256-GCM) if compromised.

## How it works

- `KeyReEncryptionRunner` (`CommandLineRunner`, `@Order(1)`) runs at startup only when `API_KEY_ENCRYPTION_SECRET_V2` is
  set
  and differs from the current key.
- Reads all `UserApiKey` entities → JPA decrypts with V1 (via `AesEncryptionConverter`).
- Re-encrypts each with V2 (via `EncryptionUtil`) → persists with native SQL (bypasses the JPA converter).
- Verifies all migrated keys are readable with V2 → aborts startup if verification fails.
- Uses SQL `updated_at = NOW()` (auditing bypassed intentionally — native update).

## Procedure

1. **Generate new key:**
   ```powershell
   openssl rand -base64 32
   ```

2. **In Render → Environment, add:**
   ```
   API_KEY_ENCRYPTION_SECRET_V2 = <new_base64_key>
   ```

3. **Deploy the app** (code includes `KeyReEncryptionRunner` and `EncryptionUtil`).

4. **On startup**, the runner automatically:
    - Detects V2 present and different from V1
    - Re-encrypts all API keys, logs progress per key
    - Verifies every migrated key by decrypting with V2
    - Logs: `"Re-encrypted N API keys with V2"` and `"All N migrated keys verified successfully with V2"`

5. **Verify:**
   ```text
   GET /actuator/health → UP
   ```

6. **In Render → Environment:**
    - Update `API_KEY_ENCRYPTION_SECRET` to the same value as V2
    - Remove `API_KEY_ENCRYPTION_SECRET_V2`
    - Deploy or restart

7. **(Optional) Clean up:** Remove `KeyReEncryptionRunner` and `EncryptionUtil` from the codebase.
   Both are harmless without the V2 env var (logs `"V2 not set — skipping"` and exits).

# Common Issues

- Java 21 required (check `java.version` in pom.xml)
- Tests require Docker running for TestContainers
- Email service needs Maileroo API key (`MAILEROO_API_KEY`) in .env — see `.env.example`
- Metrics require `management.endpoints.web.exposure.include=metrics,prometheus` (ya configurado en todos los perfiles)
- Swagger UI only available in dev profile (disabled in prod for security)
- Rate limits apply to auth endpoints (login, register, refresh-token, logout) - configurable in `application.yaml`
  under `rate-limiter`
- Concurrent review on the same card produces HTTP 409 Conflict (optimistic locking via `@Version`). The client should
  retry the request.
- Filter execution order in the security chain: `RateLimitingFilter` → `JwtAuthenticationFilter` → `AuthorizationFilter`
  (both are registered with `addFilterBefore(UsernamePasswordAuthenticationFilter.class)`; the last registered filter
  executes first).
