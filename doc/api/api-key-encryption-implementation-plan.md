# API Key Encryption — Implementation Plan

**Status:** Approved
**Date:** 2026-06-19

## Overview

Implement AES-256-GCM encryption at the application level for user API keys (OpenAI,
Anthropic, Google, Mistral, OpenRouter) using a JPA `AttributeConverter`. The master
encryption key lives exclusively in an environment variable — never in the database,
code, or image.

---

## Decisions

| Aspect | Decision |
|--------|----------|
| **Provider type** | `AiProvider` enum — `OPENAI`, `ANTHROPIC`, `GOOGLE`, `MISTRAL`, `OPENROUTER` |
| **Admin endpoints** | Yes — user (`/users/me/api-keys`) + admin (`/admin/users/{userId}/api-keys`) |
| **Key alias (UI)** | Last 4 characters only (e.g., `"...aB3x"`) |
| **Tests** | Not included in this iteration |

---

## Implementation Order

### Phase 0 — Configuration and Properties

**1. `.env.example`** — add `API_KEY_ENCRYPTION_SECRET`

```properties
# Master key for AES-256-GCM encryption of user API keys
# Generate with: openssl rand -base64 32
API_KEY_ENCRYPTION_SECRET=
```

**2. `src/main/resources/application.yaml`** — add config section:

```yaml
api-key:
  encryption:
    secret: ${API_KEY_ENCRYPTION_SECRET}
```

**3. `src/main/resources/application-dev.yaml`** — add with fallback (AI not used in dev):

```yaml
api-key:
  encryption:
    secret: ${API_KEY_ENCRYPTION_SECRET:}
```

**4. `docker-compose.yml`** — add `API_KEY_ENCRYPTION_SECRET` environment variable
   to the app service.

---

### Phase 1 — Provider Enum

**5. New: `src/main/java/com/usuario/flashcards/util/AiProvider.java`**

```java
package com.usuario.flashcards.util;

public enum AiProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    MISTRAL,
    OPENROUTER
}
```

---

### Phase 2 — Flyway Migration

**6. New: `src/main/resources/db/migration/V8__user_api_keys.sql`**

```sql
CREATE TABLE user_api_keys (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider       VARCHAR(20) NOT NULL,
    key_alias      VARCHAR(20) NOT NULL,
    encrypted_key  TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_api_keys_user_id ON user_api_keys(user_id);
```

---

### Phase 3 — Encryption Config + Converter

**7. New: `src/main/java/com/usuario/flashcards/config/properties/EncryptionProperties.java`**

```java
@ConfigurationProperties(prefix = "api-key.encryption")
@Validated
public class EncryptionProperties {

    @NotBlank(message = "API key encryption secret must be configured")
    private String secret;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
```

Register via `@ConfigurationPropertiesScan` (already active in project).

**8. New: `src/main/java/com/usuario/flashcards/config/encryption/AesEncryptionConverter.java`**

```java
@Component
@Converter
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public AesEncryptionConverter(EncryptionProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getSecret());
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(IV_LENGTH + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encrypt API key", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt API key", e);
        }
    }
}
```

- `autoApply = false` (default) — only applied via explicit `@Convert`
- Format: `base64(12-byte-IV + ciphertext)` — IV prepended for stateless decryption
- Never log plaintext or ciphertext
- Throws `IllegalArgumentException` on failure (never silently return garbage)

---

### Phase 4 — Entity

**9. New: `src/main/java/com/usuario/flashcards/entity/UserApiKey.java`**

```java
@Entity
@Table(name = "user_api_keys",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
public class UserApiKey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "key_alias", nullable = false, length = 20)
    private String keyAlias;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "encrypted_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedKey;

    // -- builder, getters, setters (manual, no Lombok) --
    // Pattern: private constructor, static Builder, Objects.requireNonNull in build()
}
```

Conventions:
- `extends BaseEntity` → `createdAt`/`updatedAt` via Spring Data Auditing
- `@ManyToOne(fetch = LAZY)` → `User` (consistent with other entities)
- No `@Version` (append-mostly, like `CardReviewLog`)
- Manual Builder pattern (existing project convention)

---

### Phase 5 — Repository

**10. New: `src/main/java/com/usuario/flashcards/repo/UserApiKeyRepository.java`**

```java
public interface UserApiKeyRepository
        extends JpaRepository<UserApiKey, Long>, JpaSpecificationExecutor<UserApiKey> {

    Optional<UserApiKey> findByIdAndUserId(Long id, Long userId);

    List<UserApiKey> findByUserId(Long userId);

    Optional<UserApiKey> findByUserIdAndProvider(Long userId, AiProvider provider);

    boolean existsByUserIdAndProvider(Long userId, AiProvider provider);
}
```

Extends `JpaSpecificationExecutor<T>` (consistent with all other repositories).

---

### Phase 6 — DTOs

**11. New: `src/main/java/com/usuario/flashcards/dto/request/CreateApiKeyRequest.java`**

```java
public record CreateApiKeyRequest(
    @NotNull AiProvider provider,
    @NotBlank String apiKey
) {
    public CreateApiKeyRequest {
        apiKey = apiKey.trim();
    }
}
```

**12. New: `src/main/java/com/usuario/flashcards/dto/response/ApiKeyResponse.java`**

```java
public record ApiKeyResponse(
    Long id,
    AiProvider provider,
    String keyAlias,
    Instant createdAt
) {}
```

- `keyAlias` = last 4 characters (e.g., `"...aB3x"`)
- Full key is **never** exposed in any response

---

### Phase 7 — Mapper

**13. New: `src/main/java/com/usuario/flashcards/mapper/UserApiKeyMapper.java`**

```java
@Component
public class UserApiKeyMapper {

    public UserApiKey toEntity(CreateApiKeyRequest request, User user) {
        return UserApiKey.builder()
            .user(user)
            .provider(request.provider())
            .keyAlias(buildKeyAlias(request.apiKey()))
            .encryptedKey(request.apiKey())   // ← plaintext, converter encrypts on persist
            .build();
    }

    public ApiKeyResponse toResponse(UserApiKey entity) {
        return new ApiKeyResponse(
            entity.getId(),
            entity.getProvider(),
            entity.getKeyAlias(),
            entity.getCreatedAt()
        );
    }

    private static String buildKeyAlias(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) return apiKey;
        return "..." + apiKey.substring(apiKey.length() - 4);
    }
}
```

- `toEntity` receives plaintext key; the JPA converter handles encryption transparently
- `toResponse` never exposes `encryptedKey`

---

### Phase 8 — Service

**14. New: `src/main/java/com/usuario/flashcards/service/UserApiKeyService.java`**

```java
@Service
public class UserApiKeyService {

    private final UserApiKeyRepository repository;
    private final UserRepository userRepo;
    private final UserApiKeyMapper mapper;

    // constructor injection

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getUserKeys(Long userId) { ... }

    @Transactional
    public ApiKeyResponse createKey(Long userId, CreateApiKeyRequest request) {
        // 1. Check unique constraint (user + provider)
        // 2. Load user reference
        // 3. Map + save
        // 4. Return response (no plaintext key)
    }

    @Transactional
    public void deleteKey(Long userId, Long keyId) {
        // findByIdAndUserId() → ownership check
    }

    @Transactional(readOnly = true)
    public String getDecryptedKey(Long userId, AiProvider provider) {
        // Returns plaintext for internal use (AI service)
    }

    // -- Admin methods (no userId ownership filter) --

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getAdminUserKeys(Long targetUserId) { ... }

    @Transactional
    public void adminDeleteKey(Long keyId) { ... }
}
```

- Ownership enforced via `findByIdAndUserId()` (existing project pattern)
- `getDecryptedKey()` is the seam where future AI services will call to obtain the key
- No `@PreAuthorize` — authorization via service-layer checks + URL segregation

---

### Phase 9 — Controllers

**15. New: `src/main/java/com/usuario/flashcards/controller/user/UserApiKeyController.java`**

```java
@RestController
@RequestMapping("/users/me/api-keys")
@Tag(name = "User API Keys")
public class UserApiKeyController {

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(
            @AuthenticationPrincipal SecurityUser user) { ... }

    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(
            @AuthenticationPrincipal SecurityUser user,
            @RequestBody @Valid CreateApiKeyRequest request) { ... }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> deleteKey(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long keyId) { ... }
}
```

**16. New: `src/main/java/com/usuario/flashcards/controller/admin/AdminApiKeyController.java`**

```java
@RestController
@RequestMapping("/admin/users/{userId}/api-keys")
@Tag(name = "Admin API Keys")
public class AdminApiKeyController {

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listUserKeys(
            @PathVariable Long userId) { ... }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> deleteUserKey(
            @PathVariable Long keyId) { ... }
}
```

---

## Security

- `/users/me/api-keys/**` — already covered by existing `authenticated` matcher (`/users/**`)
- `/admin/users/{userId}/api-keys/**` — already covered by existing `hasRole('ADMIN')` matcher (`/admin/**`)
- **No changes to `SecurityConfig.java`**

---

## Key Constraints

| Rule | Enforcement |
|------|-------------|
| One key per user per provider | DB unique constraint `uk_user_provider` |
| Master key never in code/repo/DB | Environment variable only |
| Plaintext key never in API response | DTO omits `encryptedKey` field |
| User A cannot see User B's keys | Service-layer `findByIdAndUserId()` check |
| Encrypted key is logged? | Never — converter + service prevent this |
| Admin can manage any key | Separate admin controller + service methods |

---

## Files Summary

### New files (12)

| # | File | Phase |
|---|------|-------|
| 1 | `.env.example` (modified) | 0 |
| 2 | `src/main/resources/application.yaml` (modified) | 0 |
| 3 | `src/main/resources/application-dev.yaml` (modified) | 0 |
| 4 | `docker-compose.yml` (modified) | 0 |
| 5 | `src/main/java/com/usuario/flashcards/util/AiProvider.java` | 1 |
| 6 | `src/main/resources/db/migration/V8__user_api_keys.sql` | 2 |
| 7 | `src/main/java/com/usuario/flashcards/config/properties/EncryptionProperties.java` | 3 |
| 8 | `src/main/java/com/usuario/flashcards/config/encryption/AesEncryptionConverter.java` | 3 |
| 9 | `src/main/java/com/usuario/flashcards/entity/UserApiKey.java` | 4 |
| 10 | `src/main/java/com/usuario/flashcards/repo/UserApiKeyRepository.java` | 5 |
| 11 | `src/main/java/com/usuario/flashcards/dto/request/CreateApiKeyRequest.java` | 6 |
| 12 | `src/main/java/com/usuario/flashcards/dto/response/ApiKeyResponse.java` | 6 |
| 13 | `src/main/java/com/usuario/flashcards/mapper/UserApiKeyMapper.java` | 7 |
| 14 | `src/main/java/com/usuario/flashcards/service/UserApiKeyService.java` | 8 |
| 15 | `src/main/java/com/usuario/flashcards/controller/user/UserApiKeyController.java` | 9 |
| 16 | `src/main/java/com/usuario/flashcards/controller/admin/AdminApiKeyController.java` | 9 |
| 17 | `doc/api-key-encryption-implementation-plan.md` | — |
