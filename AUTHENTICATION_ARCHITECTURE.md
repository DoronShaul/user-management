# Authentication Architecture Proposal

## Executive Summary

This document proposes a **JWT (JSON Web Token) based stateless authentication** system for the User Management Service using Spring Security 6.2.x (compatible with Spring Boot 3.2.1).

**Recommended Approach:** JWT with Refresh Token Pattern

---

## 1. Technology Comparison & Recommendation

### 1.1 Evaluated Options

| Approach | Pros | Cons | Fit for Project |
|----------|------|------|-----------------|
| **JWT (Stateless)** | - No server-side session storage<br>- Horizontally scalable<br>- Suitable for microservices<br>- Mobile-friendly<br>- Stateless architecture | - Cannot revoke tokens before expiry<br>- Larger payload in every request<br>- Token management complexity | **RECOMMENDED** - Best for learning modern authentication patterns |
| **Session-based** | - Simple to implement<br>- Easy token revocation<br>- Server controls state | - Requires server-side storage (Redis/DB)<br>- Not ideal for distributed systems<br>- Harder to scale horizontally | Not ideal - Adds infrastructure complexity (Redis) |
| **OAuth2/OIDC** | - Industry standard<br>- Third-party authentication<br>- Delegated authorization | - Overkill for internal API<br>- Complex setup<br>- Requires auth provider | Unnecessary - No third-party login needed |

### 1.2 Recommendation: JWT with Refresh Tokens

**Justification:**
1. **Scalability**: Stateless tokens enable horizontal scaling without session replication
2. **Learning Value**: JWT is industry-standard for REST APIs and microservices
3. **Simplicity**: No additional infrastructure (Redis) required
4. **Modern Stack**: Aligns with Spring Boot 3.2.1 and Spring Security 6.x best practices
5. **Security**: Short-lived access tokens + long-lived refresh tokens balance security and UX

---

## 2. Authentication Flow Design

### 2.1 Registration & Login Flow

```
┌──────────┐                    ┌──────────────┐                ┌──────────┐
│  Client  │                    │   REST API   │                │ Database │
└────┬─────┘                    └──────┬───────┘                └────┬─────┘
     │                                 │                             │
     │ POST /api/auth/register         │                             │
     │ {email, password, name}         │                             │
     ├────────────────────────────────>│                             │
     │                                 │ Hash password (BCrypt)      │
     │                                 │ Save user                   │
     │                                 ├────────────────────────────>│
     │                                 │<────────────────────────────┤
     │ 201 Created                     │                             │
     │ {message: "User registered"}    │                             │
     │<────────────────────────────────┤                             │
     │                                 │                             │
     │ POST /api/auth/login            │                             │
     │ {email, password}               │                             │
     ├────────────────────────────────>│                             │
     │                                 │ Validate credentials        │
     │                                 ├────────────────────────────>│
     │                                 │<────────────────────────────┤
     │                                 │ Generate Access Token (15m) │
     │                                 │ Generate Refresh Token (7d) │
     │                                 │ Store Refresh Token in DB   │
     │                                 ├────────────────────────────>│
     │ 200 OK                          │                             │
     │ {accessToken, refreshToken,     │                             │
     │  tokenType: "Bearer"}           │                             │
     │<────────────────────────────────┤                             │
```

### 2.2 Authenticated Request Flow

```
┌──────────┐                    ┌──────────────────┐            ┌──────────┐
│  Client  │                    │    REST API      │            │ Database │
└────┬─────┘                    └────────┬─────────┘            └────┬─────┘
     │                                   │                           │
     │ GET /api/users/{id}               │                           │
     │ Authorization: Bearer <JWT>       │                           │
     ├──────────────────────────────────>│                           │
     │                                   │ Validate JWT Signature    │
     │                                   │ Check Expiration          │
     │                                   │ Extract User ID from JWT  │
     │                                   │                           │
     │                          [If JWT Valid]                       │
     │                                   │ Fetch User Data           │
     │                                   ├──────────────────────────>│
     │                                   │<──────────────────────────┤
     │ 200 OK                            │                           │
     │ {user data}                       │                           │
     │<──────────────────────────────────┤                           │
     │                                   │                           │
     │                          [If JWT Invalid/Expired]             │
     │ 401 Unauthorized                  │                           │
     │<──────────────────────────────────┤                           │
```

### 2.3 Token Refresh Flow

```
┌──────────┐                    ┌──────────────────┐            ┌──────────┐
│  Client  │                    │    REST API      │            │ Database │
└────┬─────┘                    └────────┬─────────┘            └────┬─────┘
     │                                   │                           │
     │ POST /api/auth/refresh            │                           │
     │ {refreshToken}                    │                           │
     ├──────────────────────────────────>│                           │
     │                                   │ Validate Refresh Token    │
     │                                   ├──────────────────────────>│
     │                                   │<──────────────────────────┤
     │                                   │ Generate New Access Token │
     │                                   │ (Optional: Rotate Refresh)│
     │ 200 OK                            │                           │
     │ {accessToken, refreshToken}       │                           │
     │<──────────────────────────────────┤                           │
```

### 2.4 Logout Flow

```
┌──────────┐                    ┌──────────────────┐            ┌──────────┐
│  Client  │                    │    REST API      │            │ Database │
└────┬─────┘                    └────────┬─────────┘            └────┬─────┘
     │                                   │                           │
     │ POST /api/auth/logout             │                           │
     │ Authorization: Bearer <JWT>       │                           │
     │ {refreshToken}                    │                           │
     ├──────────────────────────────────>│                           │
     │                                   │ Invalidate Refresh Token  │
     │                                   ├──────────────────────────>│
     │                                   │<──────────────────────────┤
     │ 200 OK                            │                           │
     │ {message: "Logged out"}           │                           │
     │<──────────────────────────────────┤                           │
     │ [Client discards tokens]          │                           │
```

---

## 3. Token Management Strategy

### 3.1 Access Token (JWT)

**Purpose:** Authorize API requests

**Specifications:**
- **Algorithm:** HS256 (HMAC with SHA-256)
- **Expiration:** 15 minutes
- **Storage:** Client-side (memory, not localStorage for security)
- **Payload:**
  ```json
  {
    "sub": "user@example.com",
    "userId": 123,
    "roles": ["USER"],
    "iat": 1706821200,
    "exp": 1706822100
  }
  ```

**Security Features:**
- Short lifespan minimizes exposure if compromised
- Stateless validation (no DB lookup per request)
- Signed with secret key

### 3.2 Refresh Token

**Purpose:** Obtain new access tokens without re-authentication

**Specifications:**
- **Format:** Secure random UUID (128-bit)
- **Expiration:** 7 days
- **Storage:**
  - Client: HttpOnly, Secure cookie OR client storage
  - Server: Database (with expiration timestamp)
- **Rotation:** Issue new refresh token on each use (optional, enhances security)

**Database Schema:**
```sql
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id)
);
```

### 3.3 Token Lifecycle

```
[User Login]
    │
    ├─> Generate Access Token (15m) ─> Client stores in memory
    │
    └─> Generate Refresh Token (7d) ─> Store in DB + send to client

[After 15 minutes]
    │
    └─> Access Token expires
        │
        └─> Client sends Refresh Token to /api/auth/refresh
            │
            ├─> Validate Refresh Token in DB
            │
            ├─> Generate new Access Token (15m)
            │
            ├─> [Optional] Rotate Refresh Token (7d)
            │
            └─> Return new tokens to client

[Logout or Token Expired]
    │
    └─> Delete Refresh Token from DB
```

### 3.4 Security Considerations

**Token Storage Best Practices:**
- **Access Token:** Store in JavaScript variable (short-lived, XSS risk acceptable)
- **Refresh Token:** HttpOnly cookie (prevents XSS) OR secure storage with explicit handling

**Revocation Strategy:**
- **Refresh Tokens:** Database-backed, can be revoked immediately
- **Access Tokens:** Cannot be revoked (stateless), but short expiration limits risk
- **Emergency Revocation:** Delete all user refresh tokens from DB (forces re-login)

**Protection Against Attacks:**
- **CSRF:** Not vulnerable if tokens sent in Authorization header
- **XSS:** Refresh tokens in HttpOnly cookies, short-lived access tokens
- **Token Theft:** HTTPS only, short access token TTL, refresh token rotation

---

## 4. Spring Security Architecture

### 4.1 Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Security Filter Chain             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  JwtAuthenticationFilter (Custom)                           │
│  - Extract JWT from Authorization header                    │
│  - Validate token signature and expiration                  │
│  - Load user details                                        │
│  - Set SecurityContext                                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  AuthenticationController                                   │
│  - /api/auth/register  → AuthService.register()            │
│  - /api/auth/login     → AuthService.authenticate()        │
│  - /api/auth/refresh   → AuthService.refreshToken()        │
│  - /api/auth/logout    → AuthService.logout()              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  AuthService                                                │
│  - Password hashing (BCryptPasswordEncoder)                │
│  - User validation                                          │
│  - Token generation (delegate to JwtService)               │
│  - Refresh token management                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────┬────────────────────────────────────┐
│  JwtService           │  RefreshTokenService               │
│  - generateToken()    │  - createRefreshToken()            │
│  - validateToken()    │  - validateRefreshToken()          │
│  - extractClaims()    │  - deleteRefreshToken()            │
└───────────────────────┴────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────┬────────────────────────────────────┐
│  UserRepository       │  RefreshTokenRepository            │
│  (existing)           │  (new)                             │
└───────────────────────┴────────────────────────────────────┘
```

### 4.2 Security Configuration

**SecurityFilterChain Structure:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Disable CSRF (stateless JWT)
    // Configure CORS
    // Define public endpoints: /api/auth/**
    // Define protected endpoints: /api/users/**
    // Add JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
    // Set session management to STATELESS
}
```

**Endpoint Security Matrix:**

| Endpoint | Access Level | Authentication Required |
|----------|--------------|-------------------------|
| `POST /api/auth/register` | Public | No |
| `POST /api/auth/login` | Public | No |
| `POST /api/auth/refresh` | Public | No (requires refresh token) |
| `POST /api/auth/logout` | Protected | Yes |
| `GET /api/users/**` | Protected | Yes |
| `POST /api/users` | Protected | Yes |
| `PUT /api/users/**` | Protected | Yes |
| `DELETE /api/users/**` | Protected | Yes |
| `GET /actuator/health` | Public | No |

---

## 5. Data Model Changes

### 5.1 Updated User Entity

**Required Changes to `/src/main/java/com/doron/shaul/usermanagement/model/User.java`:**

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Column(nullable = false, unique = true)
    private String email;

    // NEW: Password field
    @NotBlank(message = "Password is required")
    @Column(nullable = false)
    private String password; // Will store BCrypt hash

    // NEW: Role field (default USER)
    @Column(nullable = false)
    private String role = "USER";

    // NEW: Account status fields
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean accountNonLocked = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // NEW: Last login timestamp
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

**Database Migration:**
```sql
ALTER TABLE users
ADD COLUMN password VARCHAR(255) NOT NULL,
ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'USER',
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN last_login_at DATETIME;
```

### 5.2 New RefreshToken Entity

**New Entity:** `/src/main/java/com/doron/shaul/usermanagement/model/RefreshToken.java`

```java
@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
```

---

## 6. Required Dependencies

### 6.1 Maven Dependencies (pom.xml)

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- Spring Security Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Version Note:** JJWT 0.12.3 is the latest stable version compatible with Spring Boot 3.2.1 and Java 17.

### 6.2 Configuration Properties

**New properties in `application.properties`:**

```properties
# JWT Configuration
jwt.secret=your-256-bit-secret-key-change-in-production
jwt.access-token-expiration=900000
jwt.refresh-token-expiration=604800000

# Security
spring.security.user.name=admin
spring.security.user.password=
```

**Security Note:** The JWT secret should be:
- At least 256 bits (32 bytes) for HS256
- Stored as environment variable in production
- Generated securely (not hardcoded)

---

## 7. Implementation Plan

### Phase 1: Foundation (Security Setup)
1. Add Spring Security and JWT dependencies to `pom.xml`
2. Update User entity with password and role fields
3. Create RefreshToken entity and repository
4. Create database migration script
5. Implement BCryptPasswordEncoder configuration

### Phase 2: JWT Infrastructure
1. Create JwtService (token generation and validation)
2. Create RefreshTokenService (refresh token CRUD)
3. Create JwtAuthenticationFilter (custom filter)
4. Configure SecurityFilterChain

### Phase 3: Authentication Endpoints
1. Create AuthController with endpoints:
   - POST /api/auth/register
   - POST /api/auth/login
   - POST /api/auth/refresh
   - POST /api/auth/logout
2. Create AuthService with business logic
3. Create DTOs (LoginRequest, RegisterRequest, AuthResponse)

### Phase 4: Endpoint Protection
1. Update UserController to require authentication
2. Add method-level security annotations where needed
3. Test all endpoints with JWT authentication

### Phase 5: Testing
1. Unit tests for JwtService
2. Unit tests for AuthService
3. Integration tests for AuthController
4. Security tests for protected endpoints
5. Test token expiration and refresh flow

### Phase 6: Documentation & Refinement
1. Update API documentation
2. Add error handling for authentication failures
3. Implement rate limiting for login attempts (optional)
4. Add logging and monitoring

---

## 8. Security Best Practices

### 8.1 Password Security
- **Hashing Algorithm:** BCrypt with strength 10-12
- **Never store plaintext passwords**
- **Minimum password requirements:** 8 characters, mix of letters/numbers/symbols

### 8.2 Token Security
- **HTTPS Only:** All authentication endpoints must use HTTPS in production
- **Short Access Token TTL:** 15 minutes balances security and UX
- **Refresh Token Rotation:** Consider rotating refresh tokens on each use
- **Secure Secret Management:** Use environment variables or secret management service

### 8.3 Rate Limiting
- **Login Attempts:** Max 5 failed attempts per 15 minutes per email
- **Token Refresh:** Max 10 requests per minute per user
- **Registration:** Max 3 registrations per hour per IP

### 8.4 Monitoring & Logging
- **Log failed login attempts** (without exposing sensitive data)
- **Monitor token refresh patterns** (detect suspicious activity)
- **Alert on multiple failed authentications**

---

## 9. API Contract Examples

### 9.1 Register

**Request:**
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

**Response (201 Created):**
```json
{
  "message": "User registered successfully",
  "userId": 123
}
```

### 9.2 Login

**Request:**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### 9.3 Refresh Token

**Request:**
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### 9.4 Protected Endpoint Usage

**Request:**
```http
GET /api/users/123
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK):**
```json
{
  "id": 123,
  "name": "John Doe",
  "email": "john@example.com",
  "role": "USER",
  "createdAt": "2025-02-01T10:30:00"
}
```

---

## 10. Alignment with Project Standards

### 10.1 Adherence to CLAUDE.md Standards

**Coding Standards:**
- Use Lombok (@Data, @RequiredArgsConstructor) for all new classes
- Constructor injection for all services
- Jakarta Validation for request DTOs
- Transactional annotation for state-changing operations

**Testing Standards:**
- Unit tests for JwtService, AuthService, RefreshTokenService
- Integration tests for AuthController with MockMvc
- Test naming: `methodName_givenCondition_expectedBehavior()`
- Minimum 80% code coverage

**API Conventions:**
- Base path: `/api/auth` for authentication endpoints
- Use `ResponseEntity<T>` with appropriate HTTP status codes
- `@Valid` for request body validation

**Exception Handling:**
- Use existing `GlobalExceptionHandler` for authentication errors
- Return 401 for invalid credentials
- Return 403 for expired/invalid tokens

### 10.2 Technology Stack Compatibility

| Technology | Version | Compatibility |
|------------|---------|---------------|
| Spring Boot | 3.2.1 | Spring Security 6.2.x included |
| Java | 17 | Fully compatible |
| JJWT | 0.12.3 | Java 17 compatible |
| MySQL | 8.0 | Supports new schema |
| Lombok | (existing) | Compatible |

---

## 11. Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| **JWT Secret Exposure** | High - All tokens can be forged | Store in environment variables, rotate periodically |
| **Refresh Token Theft** | Medium - Long-term access | Implement token rotation, track usage patterns |
| **XSS Attacks** | Medium - Token theft from client | Store access tokens in memory, refresh tokens in HttpOnly cookies |
| **Brute Force Login** | High - Account compromise | Implement rate limiting, account lockout |
| **Token Not Revocable** | Low - Short TTL limits exposure | Use short access token expiration (15m) |
| **Database Performance** | Low - Token lookups add overhead | Index refresh_tokens table, clean expired tokens regularly |

---

## 12. Future Enhancements

### 12.1 Immediate Follow-ups (Not in Initial Implementation)
- Multi-factor authentication (MFA/2FA)
- Password reset flow with email verification
- Email verification on registration
- Remember me functionality (longer refresh token TTL)

### 12.2 Advanced Features
- Role-based access control (RBAC) with multiple roles
- OAuth2 integration for third-party login (Google, GitHub)
- JWT blacklist for emergency token revocation
- Audit logging for all authentication events

### 12.3 Performance Optimizations
- Cache user details to avoid DB lookups on every request
- Implement refresh token cleanup job (delete expired tokens)
- Redis for distributed session management (if scaling beyond single instance)

---

## 13. Conclusion

This architecture proposal recommends **JWT-based stateless authentication with refresh tokens** as the optimal solution for the User Management Service. It balances:

- **Security:** Strong password hashing, short-lived access tokens, database-backed refresh tokens
- **Scalability:** Stateless authentication enables horizontal scaling
- **Simplicity:** No additional infrastructure required (Redis, auth servers)
- **Standards Alignment:** Follows Spring Security 6.x best practices and project coding standards
- **Learning Value:** Modern authentication pattern used in production systems

The implementation can be completed in 6 phases with clear deliverables and comprehensive test coverage.

**Next Steps:**
1. Review and approve this architecture proposal
2. Begin Phase 1: Security setup and database schema changes
3. Iterate through phases with testing at each step
4. Conduct security review before production deployment

---

**Document Version:** 1.0
**Last Updated:** 2026-02-09
**Author:** Architecture Lead (Claude Agent)
