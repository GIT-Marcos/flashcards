# API Key Encryption Decision

## Problem

The application will support AI-powered card generation. Each user can bring their
own API key (e.g., OpenAI, Anthropic). These keys must be stored securely because:

- A leaked key could be used to make API calls at the user's expense
- Users trust the application with sensitive credentials
- Regulatory/compliance requirements may apply (data protection)

## Decision: Application-Level AES-256-GCM via JPA AttributeConverter

We chose **application-level encryption** using AES-256-GCM, implemented as a JPA
`AttributeConverter`, with the master key sourced from an **environment variable**.

### Why not Supabase Vault?

Supabase Vault was considered because it is already installed. It was rejected because:

- The app connects as `postgres` (superuser), which can read `vault.decrypted_secrets`
- Vault protects against disk/backup theft but not against runtime DB access
- The master key would still need to exist somewhere — adding Vault for key storage
  puts the key and data in the same database (violates separation of concerns)

### Why application-level encryption?

- **Defense in depth**: An attacker needs to compromise two independent systems
  (database + app server) to obtain plaintext keys
- **Portable**: Works identically on any database provider (Render, Supabase, local)
- **Transparent to JPA**: The converter encrypts/decrypts automatically; the rest of
  the codebase works with plaintext `String` fields
- **Proven algorithm**: AES-256-GCM provides confidentiality + authentication (integrity)

## Data Model

The API keys are stored in a dedicated table:

```sql
CREATE TABLE user_api_keys
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider      VARCHAR(20) NOT NULL, -- 'openai', 'anthropic', etc.
    key_alias     VARCHAR(20) NOT NULL, -- "sk-...aB3x" (last 4 chars)
    encrypted_key TEXT        NOT NULL, -- AES-256-GCM ciphertext
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uk_user_provider UNIQUE (user_id, provider)
);
```

- `encrypted_key` stores only the ciphertext (base64-encoded IV + ciphertext)
- `key_alias` stores a masked version for UI display (e.g., `"sk-...aB3x"`)
- One key per user per provider

## Encryption Flow

```
┌──────────────────────────────────────┐
│  Environment Variable                │
│  API_KEY_ENCRYPTION_SECRET (base64)  │  ← NEVER in DB, NEVER in repo
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  @Value("${api-key.encryption.secret}")│
│  injected into AesEncryptionConverter  │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  JPA AttributeConverter              │
│                                      │
│  encrypt():                           │
│    1. Generate random 12-byte IV      │
│    2. AES-256-GCM(plaintext, key, iv) │
│    3. Return base64(iv + ciphertext)  │
│                                      │
│  decrypt():                           │
│    1. Decode base64 to bytes          │
│    2. Extract IV (first 12 bytes)     │
│    3. AES-256-GCM decrypt remainder   │
│    4. Return plaintext String         │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  user_api_keys.encrypted_key         │
│  "gAAAAABn0KH..." ← ilegible en BD  │
└──────────────────────────────────────┘
```

## Key Management (Production — Render)

The master key is set as a Render Environment Variable:

```
Render Dashboard → Environment Variables
  API_KEY_ENCRYPTION_SECRET = <base64-encoded-256-bit-key>
```

- Render encrypts env vars at rest on the platform side
- The key is never in the Git repository, Docker image, or database
- Generate with: `openssl rand -base64 32`

## Security Considerations

- **Master key location**: Environment variable on the app server (Render), never in
  the database or codebase
- **Encryption algorithm**: AES-256-GCM (authenticated) — protects confidentiality
  and integrity; detects tampering
- **IV management**: 12-byte random IV generated per encryption, prepended to the
  ciphertext — no IV reuse
- **Key rotation**: Changing the master key requires re-encrypting all stored keys
  via a batch job
- **Logging**: The encrypted field is never logged; a dedicated `@JsonIgnore` on
  the `encryptedKey` entity field prevents serialization
- **API responses**: Only `keyAlias` is exposed to the client (e.g., `"sk-...aB3x"`),
  never the full key
- **Authorization**: Spring Security ensures User A can only access their own keys
  (controller-level `@PreAuthorize` or service-level ownership check)
- **HTTPS/TLS**: Already enforced across all profiles

## Tradeoffs

### Pros

- Master key isolated from the database (two-system compromise required)
- No external dependencies (Vault, KMS, etc.)
- Transparent to JPA — minimal code footprint
- Works on any database provider
- AES-256-GCM is battle-tested and FIPS-compliant

### Cons

- Key rotation invalidates all stored keys until re-encryption
- Cannot query by key value in SQL (not a use case)
- Master key management is the app operator's responsibility
- No automatic key rotation (must be scripted)

## When to Revisit

Consider migrating to a managed KMS (AWS KMS, GCP Cloud KSM) or HashiCorp Vault if:

1. The platform scales beyond a single Render instance
2. Compliance requirements mandate HSM-backed key storage
3. Automated key rotation with zero downtime is required
4. The team grows and separation of duties for key management becomes necessary

## Files to Modify/Create

### New files

- `entity/UserApiKey.java` — JPA entity with `@Convert(converter = ...)`
- `repository/UserApiKeyRepository.java` — Spring Data JPA repository
- `service/UserApiKeyService.java` — CRUD with authorization + encryption
- `controller/UserApiKeyController.java` — REST endpoints for key management
- `dto/request/CreateApiKeyRequest.java` — receives provider + plaintext key
- `dto/response/ApiKeyResponse.java` — exposes only `id`, `provider`, `keyAlias`, `createdAt`
- `config/encryption/AesEncryptionConverter.java` — JPA AttributeConverter
- `doc/api-key-encryption-decision.md` — this document

### Modified files

- `application.yaml` — add `api-key.encryption.secret: ${API_KEY_ENCRYPTION_SECRET}`
- `application-dev.yaml` — add placeholder with env variable reference
- `.env.example` — add `API_KEY_ENCRYPTION_SECRET=` with generation instructions
- `docker-compose.yml` — add `API_KEY_ENCRYPTION_SECRET` env variable (dev)
