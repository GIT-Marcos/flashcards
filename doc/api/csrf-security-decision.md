# CSRF Security Decision

**Date:** 2026-06-02
**Status:** Accepted

## ADR-002: Profile-Aware SameSite Cookie

**Date:** 2026-06-02
**Status:** Accepted (supersedes ADR-001 SameSite logic)

**Context:** The API runs on Render (backend) and Vercel (SPA frontend) in production.
These are different origins. `SameSite=Strict` prevents the browser from sending the
refresh-token cookie on cross-site requests, breaking the auth refresh flow.

**Decision:** All profiles now use the same cross-origin cookie configuration:

- `dev` profile (local development): `SameSite=None; Secure` (cross-origin, required for SPA on `localhost:5173`)
- `prod` profile (cross-origin): `SameSite=None; Secure` (required for Vercel frontend)

**Implementation:** `AuthController.createCookie()` uses two independent properties:
`secureCookie` (boolean) for the `Secure` flag, and `sameSite` (string) for the SameSite
attribute. Both are uniform across all profiles: `sameSite` is `None` and `secureCookie` is `true`.

**Security Impact:**

- `SameSite=None` reduces CSRF protection for the refresh token cookie in production
- Mitigation: The cookie already has `Secure` + `HttpOnly` flags
- All other API endpoints use `Authorization: Bearer <token>` header (not cookies)
- Only 2 cookie-dependent endpoints exist: `/auth/refresh-token` and `/auth/logout`
- CSRF on `/auth/logout` is low-impact (user can re-login)
- CSRF on `/auth/refresh-token` is theoretically possible but:
    1. Requires victim to be logged in (cookie present)
    2. Attacker cannot read the cookie value (HttpOnly)
    3. Refresh endpoint only returns a new token pair, no state mutation
- This is an acceptable risk for cross-origin functionality

**References:**

- [MDN: SameSite cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)
- [OWASP: Cross-Site Request Forgery](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

---

## ADR-001: Original CSRF Decision (2026-05-14)

**Context:** AuthController cookie-based refresh token authentication

### Problem

The application uses HttpOnly cookies to store refresh tokens (`/auth/refresh-token`, `/auth/logout`). CSRF protection
is disabled in `SecurityConfig`. This creates a potential attack surface where a malicious site could force
authenticated users to perform unintended actions.

### Decision

**CSRF protection remains disabled.** Mitigation relied on `SameSite=None; Secure` cookie attribute.

## Rationale

### Why SameSite attribute is sufficient (profile-aware)

1. **Only 2 endpoints use cookies:**
    - `POST /auth/refresh-token` - reads refresh token from cookie
    - `POST /auth/logout` - clears refresh token cookie

2. **SameSite behavior by profile (uniform across all):**
    - `dev`, `prod` (SameSite=None; Secure): Browser will send the cookie on cross-site requests, required for
      SPA
      frontend on different origin (localhost:5173 for dev, Vercel for prod). The `Secure` flag ensures it's only
      sent over HTTPS.

3. **All other endpoints use Authorization header:**
    - JWT access token is sent via `Authorization: Bearer <token>` header
    - Browsers do NOT auto-send custom headers on cross-site requests
    - CSRF attacks cannot forge custom headers without XSS

4. **Impact analysis:**
    - CSRF on `/auth/logout`: Low impact (user gets logged out, can re-login)
    - CSRF on `/auth/refresh-token`: Allowed in all profiles (SameSite=None) but attacker cannot read
      cookie value (HttpOnly) and no state is mutated
    - CSRF on other endpoints: Not possible (uses Authorization header)

### Current Cookie Configuration

```java
ResponseCookie.from("refresh_token",token)
    .

httpOnly(true)        // Prevents XSS from reading cookie
    .

secure(secureCookie)  // Only sent over HTTPS (true for all profiles)
    .

path("/")             // Available to all paths
    .

maxAge(604800)        // 7 days
    .

sameSite(sameSite)    // "None" for all profiles (separate config property)
    .

build();
```

## Tradeoffs

### Pros

- No frontend changes required
- No additional server-side state (CSRF tokens)
- Defense-in-depth: HttpOnly + Secure + SameSite
- Stateless API remains stateless
- Works with SPA on different origin (Vercel) and API (Render)

### Cons

- `SameSite=None` reduces CSRF protection for the refresh token cookie in production
- No protection against XSS (mitigated by HttpOnly)
- Legacy browsers (<2018) may not respect SameSite
- Requires HTTPS for `SameSite=None` to work (already enforced in prod)

## When to Revisit

Consider enabling CSRF with `CookieCsrfTokenRepository` or switching to same-origin deployment if:

1. Application needs to support legacy browsers without SameSite
2. Regulatory requirements mandate explicit CSRF tokens
3. Frontend architecture changes to use cookies for access tokens
4. CSRF attack surface becomes unacceptable (e.g., refresh endpoint gains state-mutating capabilities)

## References

- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [MDN SameSite Cookie Attribute](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)
- [Can I Use: SameSite](https://caniuse.com/same-site-cookie-attribute)

## Related Code

- `AuthController.java` - Cookie creation with profile-aware SameSite
- `SecurityConfig.java` - CSRF disabled with comment
- `AuthControllerTest.java` - Cookie security attribute tests
- `SecurityBoundaryTest.java` - Security boundary tests (MockMvcTester)
