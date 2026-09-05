# Password Recovery Decision

## Problem

When a user forgets their password, they need a secure, self-service mechanism to regain access to their account. This
prevents:

- Locked-out users requiring manual administrative intervention
- Users creating duplicate accounts after losing access
- Support burden from password reset requests
- Security risks from weak or shared recovery methods (e.g., security questions)

## Decision: JWT-based Stateless Reset with Hash Validation

We chose a **stateless JWT-based password reset** where the reset token contains the current `passwordHash`. The account
password is updated only after verifying the token hash matches the stored hash.

### Why stateless JWT and not a DB-stored token?

- **No new table**: The token is self-contained (JWT), eliminating the need for a `password_reset_tokens` table and a
  Flyway migration.
- **Architectural consistency**: The signup flow uses the same pattern (JWT with embedded `passwordHash`).
- **Auto-invalidation on password change**: When the password is updated, the hash changes. Any existing reset token
  becomes automatically invalid because the hash no longer matches.
- **No pending state to clean up**: Expired tokens don't accumulate in the database.

### Why frontend-based UI and not Thymeleaf (like email verification)?

- **Consistency**: The existing app UI is a frontend SPA. Mixing Thymeleaf pages for password reset creates a disjointed
  user experience.
- **Separation of concerns**: The API remains a pure JSON API; the frontend owns the UI layer.
- **Flexibility**: The frontend can implement show/hide password, strength indicators, and error handling within its own
  component library.

### Alternative considered: Thymeleaf-based flow (rejected)

A flow identical to email verification (GET renders a form, POST processes it) was rejected because:

- The email verification flow uses Thymeleaf because it's the user's **first interaction** with the app (pre-account).
- Password reset users are **existing users** who should return to the familiar frontend experience.
- Maintaining two UI rendering strategies (`@Controller` for reset + SPA) adds unnecessary complexity.

## Flow

```mermaid
sequenceDiagram
    actor User
    participant Frontend as Frontend (SPA)
    participant API as API (Spring Boot)
    participant Maileroo as Maileroo (Email)
    Note over User, API: 1. Request reset
    User ->> Frontend: Click "Forgot password"
    Frontend ->> API: POST /auth/forgot-password { email }
    API ->> API: Look up user by email,<br/>generate JWT with passwordHash
    API ->> Maileroo: Send password reset email<br/>(retry 3x + metrics)
    API -->> Frontend: 202 Accepted (generic response)
    Frontend -->> User: "Check your email"
    Note over User, Maileroo: 2. Email delivery
    Maileroo -->> User: Email with reset link
    Note over User, API: 3. User clicks link
    User ->> Frontend: Click link → /auth/reset-password?token=JWT
    Frontend ->> Frontend: Read token, remove from URL (history.replaceState)
    Frontend -->> User: Show "New password" form
    Note over User, API: 4. Reset password
    User ->> Frontend: Enter new password, click submit
    Frontend ->> API: POST /auth/reset-password { token, newPassword }
    API ->> API: Validate JWT, verify hash match,<br/>encode new password, update DB
    API -->> Frontend: 200 OK "Password reset successfully"
    Frontend -->> User: Redirect to login page
    Note over User, API: 5. Login with new password
    User ->> Frontend: Enter new credentials
    Frontend ->> API: POST /auth/login
    API -->> Frontend: JWT tokens
    Frontend -->> User: Redirect to app
```

## JWT Structure

The reset token (`TokenType.PASSWORD_RESET`) is a JWT signed with HMAC-SHA256 (same key as access/refresh tokens):

| Claim          | Value                     |
|----------------|---------------------------|
| `tokenType`    | `"PASSWORD_RESET"`        |
| `sub`          | `userId` (Long)           |
| `email`        | User email                |
| `passwordHash` | BCrypt hash (strength 12) |
| `exp`          | 15 min from issuance      |
| `iat`          | Issuance timestamp        |

## Security Considerations

- **Password hash in JWT**: The BCrypt hash is included in the JWT payload (base64-encoded, not encrypted). This is an
  acknowledged weakness shared with the email verification flow. Mitigations:
    - BCrypt with cost factor 12 is computationally expensive to crack.
    - Token expires in 15 minutes (tight window).
    - Token is transmitted exclusively over HTTPS.
    - The hash is invalidated as soon as the password changes.
- **User enumeration**: `forgot-password` always returns HTTP 202 with the same message regardless of whether the email
  exists: *"If an account with that email exists, a password reset link has been sent."*
- **Auto-invalidation on password change**: If the user or an attacker changes the password using any token, all other
  tokens become invalid because the stored hash no longer matches.
- **Rate limiting**: `forgot-password` (3 requests / 10 min per IP) and `reset-password` (5 requests / min per IP) are
  rate-limited via Bucket4j.
- **Token in URL query parameter**: The reset token is present in the URL when the user clicks the email link. Best
  practice requires the frontend to remove the token from the URL using `history.replaceState()` immediately after
  reading it.
- **No refresh token invalidation**: Resetting the password does not invalidate existing refresh tokens. This is
  intentional — the user is recovering their own account, not responding to a compromise. A dedicated "log out of all
  devices" feature would handle that separately.
- **Expired/invalid token**: Both `isResetPasswordToken()` and `extractResetPasswordData()` catch all exceptions and
  return a generic error: *"The reset link is invalid or has expired. Please request a new one."*

## Files Modified/Created

### New files (6 source + 1 doc)

- `dto/request/ForgotPasswordRequest.java`
- `dto/request/ResetPasswordRequest.java`
- `dto/response/ForgotPasswordResponse.java`
- `dto/response/ResetPasswordResponse.java`
- `exception/domain/InvalidResetPasswordTokenException.java`
- `templates/email/password-reset.html`
- `doc/password-recovery-decision.md`

### Modified files (7)

- `util/TokenType.java` — added `PASSWORD_RESET`
- `service/JwtService.java` — added `generatePasswordResetToken()`, `extractResetPasswordData()`,
  `isResetPasswordToken()`
- `service/AuthService.java` — added `forgotPassword()` and `resetPassword()`
- `controller/auth/AuthController.java` — added `POST /auth/forgot-password` and `POST /auth/reset-password`
- `service/notification/EmailService.java` — added `sendPasswordResetEmail()`
- `config/RateLimitingConfig.java` — added `forgotPassword` and `resetPassword` endpoint configs
- `application.yaml` — added `reset-token-expiration: 900000` and rate limits
