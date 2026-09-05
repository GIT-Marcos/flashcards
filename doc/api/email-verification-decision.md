# Email Verification Decision

## Problem

When a user registers, we need to verify that the email address belongs to them before creating an account. This
prevents:

- Accounts created with someone else's email without consent
- Signup spam with fake/non-existent emails
- Email notifications going to an inbox the user doesn't control

## Decision: Double Opt-In (POST-based confirmation)

We chose **Double Opt-In** with a **POST-based confirmation** flow. The account is created only after the user clicks a
confirmation button on a landing page.

### Why POST and not GET?

Email clients and security gateways routinely follow GET links in emails as a security scanning measure. This is known
as **link prefetching** or **link protection**:

| Service                                              | Behavior                                                      | Risk Level |
|------------------------------------------------------|---------------------------------------------------------------|------------|
| **Gmail** (web + mobile)                             | Google Safe Browsing scans and may detonate links in emails   | HIGH       |
| **Microsoft 365 / Outlook**                          | Defender Safe Links proactively follows URLs at delivery time | HIGH       |
| **Enterprise gateways** (Proofpoint, Mimecast, etc.) | Routinely follow all links for threat analysis                | VERY HIGH  |

These services collectively cover >60% of consumer and business email. If the confirmation were a GET that creates the
user, the account would be activated before the human ever clicks — defeating the purpose of double opt-in.

We follow the industry-standard approach used by GitHub, Slack, and Stripe:

1. The email link (`GET /auth/confirm?token=...`) renders an idempotent landing page with a button
2. The human must click the button to issue a **POST** request, which performs the actual confirmation
3. Email clients never automatically submit forms or execute POST requests

### Alternative considered: Auto-submit via JavaScript (Hybrid)

A hybrid approach where the landing page auto-submits the form via JavaScript on page load was considered. This offers
1-click UX (like GET) with the security of POST. It was rejected because:

- The landing page + button approach is simpler, works without JS, and matches the existing `UnsubscribeController`
  pattern exactly
- The emotional reassurance of seeing a confirmation page with a deliberate button is valuable for account creation
- The existing project (unsubscribe) already demonstrates that a clean button-based flow works well

## Flow

```mermaid
sequenceDiagram
    actor User
    participant Frontend as Frontend (React)
    participant API as API (Spring Boot)
    participant Maileroo as Maileroo (Email)
    Note over User, API: 1. Signup
    User ->> Frontend: Fill registration form
    Frontend ->> API: POST /auth/signup
    API ->> API: Validate, check uniqueness,<br/>hash password, generate JWT
    API ->> Maileroo: Send verification email<br/>(retry 3x + metrics)
    API -->> Frontend: 202 Accepted
    Frontend -->> User: "Check your email"
    Note over User, Maileroo: 2. Email delivery
    Maileroo -->> User: Email with confirmation link
    Note over User, API: 3. Email client prefetch (GET)
    User ->> API: (prefetch) GET /auth/confirm?token=JWT
    API -->> User: Landing page (confirm-email.html)<br/>— idempotent, no side effects
    Note over User, API: 4. User confirmation (POST)
    User ->> API: Click button → POST /auth/confirm
    API ->> API: Validate JWT, check uniqueness,<br/>create User via VerificationService
    API -->> User: Success page (email-verified.html)<br/>"You can now log in"
    Note over User, API: 5. Login
    User ->> Frontend: Navigate to login page
    Frontend ->> API: POST /auth/login
    API -->> Frontend: JWT tokens
    Frontend -->> User: Redirect to app
```

## JWT Structure

The verification token (`TokenType.VERIFY_EMAIL`) is a JWT signed with HMAC-SHA256 (same key as access/refresh tokens):

| Claim           | Value                     |
|-----------------|---------------------------|
| `tokenType`     | `"VERIFY_EMAIL"`          |
| `sub` (subject) | `username`                |
| `email`         | `email`                   |
| `passwordHash`  | BCrypt hash (strength 12) |
| `zoneInfo`      | IANA timezone string      |
| `exp`           | 24h from issuance         |
| `iat`           | Issuance timestamp        |

The JWT is the single source of truth — no DB storage is needed for pending registrations.

## Security Considerations

- **Password hash in JWT**: The BCrypt hash is computationally infeasible to reverse. The JWT is transmitted over HTTPS,
  signed (tamper-proof), and expires in 24h.
- **Replay prevention**: The username and email unique constraints ensure a token can only be used once. A second
  attempt fails because the user already exists.
- **Account enumeration**: Signup always returns the same generic message regardless of whether the email exists: *"Se
  ha enviado un email de verificación a ..."*
- **Rate limiting**: Signup (5 req/10s per IP) and Confirm (100 req/10s per IP) are rate-limited via Bucket4j.
- **Expired/invalid token**: Both the landing page (GET) and confirmation (POST) catch all exceptions and show a generic
  error message.

## Files Modified/Created

### New files (6 source + 1 doc)

- `service/VerificationService.java`
- `controller/verification/EmailVerificationController.java`
- `exception/domain/InvalidEmailVerificationException.java`
- `dto/response/SignupResponse.java`
- `templates/confirm-email.html`
- `templates/email-verified.html`
- `templates/email/email-verification.html`
- `doc/email-verification-decision.md`

### Modified files (7)

- `util/TokenType.java` — added `VERIFY_EMAIL`
- `service/JwtService.java` — added verification token generation + extraction
- `service/AuthService.java` — replaced `register()` with `signup()`
- `service/notification/EmailService.java` — added `sendVerificationEmail()`
- `controller/auth/AuthController.java` — replaced `/register` with `/signup`
- `config/RateLimitingConfig.java` — added `signup` and `confirm` endpoint configs
- `application.yaml` — added `verification-token-expiration` and rate limits
