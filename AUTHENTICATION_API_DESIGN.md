# Authentication API Design Specification

## Overview
This document defines the authentication API for the User Management Service. The design follows Spring Boot best practices with JWT-based stateless authentication, including user registration, login, token refresh, and password reset flows.

---

## Technology Stack
- **Authentication Method**: JWT (JSON Web Tokens)
- **Password Encoding**: BCrypt
- **Token Storage**: In-memory blacklist for logout (Redis recommended for production)
- **Security**: Spring Security 6.x

---

## API Endpoints Overview

| Endpoint | Method | Description | Authentication Required |
|----------|--------|-------------|------------------------|
| `/api/auth/register` | POST | Register a new user | No |
| `/api/auth/login` | POST | Authenticate and get tokens | No |
| `/api/auth/refresh` | POST | Refresh access token | Yes (Refresh Token) |
| `/api/auth/logout` | POST | Invalidate tokens | Yes (Access Token) |
| `/api/auth/password-reset/request` | POST | Request password reset | No |
| `/api/auth/password-reset/confirm` | POST | Confirm password reset | No |
| `/api/auth/password-change` | POST | Change password (authenticated) | Yes (Access Token) |
| `/api/auth/me` | GET | Get current user info | Yes (Access Token) |

---

## 1. User Registration

### Endpoint
```
POST /api/auth/register
```

### Request DTO: `RegisterRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
```

### Response DTO: `AuthResponse`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn; // seconds
    private UserInfo user;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
}
```

### Success Response
**Status Code**: `201 CREATED`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "createdAt": "2026-02-09T10:30:00"
  }
}
```

### Error Responses

#### Validation Error
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "password",
      "message": "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
    },
    {
      "field": "confirmPassword",
      "message": "Passwords do not match"
    }
  ],
  "path": "/api/auth/register"
}
```

#### Email Already Exists
**Status Code**: `409 CONFLICT`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 409,
  "error": "Conflict",
  "message": "Email already registered",
  "path": "/api/auth/register"
}
```

### Validation Rules
- Passwords must match (`password` == `confirmPassword`)
- Email must be unique in the system
- Password strength validation enforced
- All fields are required

---

## 2. User Login

### Endpoint
```
POST /api/auth/login
```

### Request DTO: `LoginRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

### Response DTO: `AuthResponse`
(Same as registration response)

### Success Response
**Status Code**: `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "createdAt": "2026-02-09T10:30:00"
  }
}
```

### Error Responses

#### Invalid Credentials
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/auth/login"
}
```

#### Validation Error
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Email must be valid"
    }
  ],
  "path": "/api/auth/login"
}
```

### JWT Token Details
- **Access Token**: Short-lived (15 minutes - 1 hour)
- **Refresh Token**: Long-lived (7-30 days)
- **Algorithm**: HS256 (HMAC with SHA-256)
- **Claims**: userId, email, issued at, expiration

---

## 3. Token Refresh

### Endpoint
```
POST /api/auth/refresh
```

### Request DTO: `RefreshTokenRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

### Response DTO: `TokenRefreshResponse`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRefreshResponse {
    private String accessToken;
    private String refreshToken; // Optional: rotate refresh token
    private String tokenType = "Bearer";
    private Long expiresIn;
}
```

### Success Response
**Status Code**: `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### Error Responses

#### Invalid or Expired Refresh Token
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired refresh token",
  "path": "/api/auth/refresh"
}
```

#### Token Revoked
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token has been revoked",
  "path": "/api/auth/refresh"
}
```

---

## 4. Logout

### Endpoint
```
POST /api/auth/logout
```

### Request Header
```
Authorization: Bearer {accessToken}
```

### Request Body
(Optional - can be empty or include refresh token for blacklisting)

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {
    private String refreshToken; // Optional: to blacklist refresh token
}
```

### Success Response
**Status Code**: `204 NO CONTENT`

(No response body)

### Error Responses

#### Missing or Invalid Token
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid authentication token",
  "path": "/api/auth/logout"
}
```

### Notes
- Blacklists the access token (until expiry)
- Optionally blacklists the refresh token
- Client must delete tokens from storage

---

## 5. Password Reset Request

### Endpoint
```
POST /api/auth/password-reset/request
```

### Request DTO: `PasswordResetRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
```

### Success Response
**Status Code**: `200 OK`
```json
{
  "message": "If the email exists, a password reset link has been sent"
}
```

### Notes
- Always returns success (security best practice - no user enumeration)
- Generates a secure reset token (UUID, expires in 1 hour)
- Sends email with reset link: `https://example.com/reset-password?token={token}`
- Token stored in database with expiration

### Error Responses
(None - always returns 200 OK to prevent email enumeration)

---

## 6. Password Reset Confirmation

### Endpoint
```
POST /api/auth/password-reset/confirm
```

### Request DTO: `PasswordResetConfirmRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmRequest {
    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
```

### Success Response
**Status Code**: `200 OK`
```json
{
  "message": "Password has been reset successfully"
}
```

### Error Responses

#### Invalid or Expired Token
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid or expired reset token",
  "path": "/api/auth/password-reset/confirm"
}
```

#### Passwords Don't Match
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Passwords do not match",
  "path": "/api/auth/password-reset/confirm"
}
```

### Notes
- Token is single-use (deleted after successful reset)
- All existing sessions/tokens for the user should be invalidated
- Password must meet strength requirements

---

## 7. Password Change (Authenticated)

### Endpoint
```
POST /api/auth/password-change
```

### Request Header
```
Authorization: Bearer {accessToken}
```

### Request DTO: `PasswordChangeRequest`
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
```

### Success Response
**Status Code**: `200 OK`
```json
{
  "message": "Password changed successfully"
}
```

### Error Responses

#### Current Password Incorrect
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Current password is incorrect",
  "path": "/api/auth/password-change"
}
```

#### Passwords Don't Match
**Status Code**: `400 BAD REQUEST`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "New passwords do not match",
  "path": "/api/auth/password-change"
}
```

#### Unauthorized
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid authentication token",
  "path": "/api/auth/password-change"
}
```

### Notes
- Requires valid access token
- Must verify current password before changing
- New password must be different from current password (optional validation)
- Consider invalidating other sessions after password change

---

## 8. Get Current User

### Endpoint
```
GET /api/auth/me
```

### Request Header
```
Authorization: Bearer {accessToken}
```

### Success Response
**Status Code**: `200 OK`
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john.doe@example.com",
  "createdAt": "2026-02-09T10:30:00"
}
```

### Error Responses

#### Unauthorized
**Status Code**: `401 UNAUTHORIZED`
```json
{
  "timestamp": "2026-02-09T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid authentication token",
  "path": "/api/auth/me"
}
```

---

## Common Error Response Structure

All error responses follow this standardized format:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<ValidationError> errors; // Optional, for validation errors
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    private String field;
    private String message;
}
```

---

## HTTP Status Code Reference

| Status Code | Usage |
|------------|-------|
| `200 OK` | Successful GET, login, password reset, token refresh |
| `201 CREATED` | Successful user registration |
| `204 NO CONTENT` | Successful logout |
| `400 BAD REQUEST` | Validation errors, business rule violations, invalid tokens |
| `401 UNAUTHORIZED` | Invalid credentials, missing/invalid authentication token |
| `403 FORBIDDEN` | Valid token but insufficient permissions (future use) |
| `404 NOT FOUND` | Resource not found |
| `409 CONFLICT` | Email already exists |
| `500 INTERNAL SERVER ERROR` | Unexpected server errors |

---

## Security Considerations

### Password Requirements
- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- Special characters recommended (optional enforcement)

### Token Security
- **Access Token**: Short expiry (15-60 minutes)
- **Refresh Token**: Longer expiry (7-30 days)
- Tokens signed with strong secret key (minimum 256 bits)
- Token rotation on refresh (recommended)
- Blacklist mechanism for logout

### Password Storage
- BCrypt with strength 10-12
- Never store passwords in plain text
- Never log passwords

### Rate Limiting
- Login attempts: 5 per 15 minutes per IP/email
- Password reset requests: 3 per hour per email
- Token refresh: 10 per minute per user

### Additional Security
- HTTPS required in production
- CORS properly configured
- CSRF protection for cookie-based auth (if used)
- Input sanitization to prevent injection attacks
- Account lockout after failed login attempts (optional)

---

## Database Schema Requirements

### Users Table Enhancement
```sql
ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL;
ALTER TABLE users ADD COLUMN account_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP NULL;
```

### Password Reset Tokens Table
```sql
CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_expiry (expiry_date)
);
```

### Refresh Tokens Table (Optional - for DB storage)
```sql
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id)
);
```

### Token Blacklist Table (for logout)
```sql
CREATE TABLE token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_expiry (expiry_date)
);
```

---

## OpenAPI/Swagger Documentation Plan

### Configuration
```yaml
openapi: 3.0.1
info:
  title: User Management Service - Authentication API
  description: JWT-based authentication API for user management
  version: 1.0.0
  contact:
    name: API Support
    email: support@example.com

servers:
  - url: http://localhost:8080
    description: Development server
  - url: https://api.example.com
    description: Production server

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: JWT access token obtained from /api/auth/login or /api/auth/register

security:
  - bearerAuth: []
```

### Annotations for Controllers
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and authorization endpoints")
public class AuthController {

    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns access and refresh tokens"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error or passwords don't match",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // Implementation
    }
}
```

### Swagger UI Access
- **URL**: `http://localhost:8080/swagger-ui.html`
- **API Docs JSON**: `http://localhost:8080/v3/api-docs`

---

## Implementation Checklist

### Phase 1: Core Authentication
- [ ] Create DTOs (request/response objects)
- [ ] Update User entity with password field
- [ ] Create PasswordResetToken entity
- [ ] Create RefreshToken entity (optional)
- [ ] Create TokenBlacklist entity
- [ ] Implement JWT utility class (token generation/validation)
- [ ] Implement UserDetailsService for Spring Security
- [ ] Configure Spring Security (SecurityConfig)
- [ ] Implement AuthService (business logic)
- [ ] Implement AuthController (REST endpoints)
- [ ] Update GlobalExceptionHandler for auth errors

### Phase 2: Security Features
- [ ] Implement password encoding (BCrypt)
- [ ] Implement token blacklist mechanism
- [ ] Implement rate limiting (Spring AOP or external library)
- [ ] Add account lockout logic
- [ ] Configure CORS
- [ ] Add security headers

### Phase 3: Password Reset
- [ ] Implement email service (JavaMailSender)
- [ ] Create password reset email templates
- [ ] Implement password reset token generation
- [ ] Implement password reset token validation
- [ ] Add cleanup job for expired tokens

### Phase 4: Testing
- [ ] Unit tests for AuthService
- [ ] Controller tests for AuthController
- [ ] Integration tests for authentication flow
- [ ] Security tests (invalid tokens, expired tokens, etc.)
- [ ] Rate limiting tests

### Phase 5: Documentation
- [ ] Add Swagger/OpenAPI dependencies
- [ ] Configure Swagger
- [ ] Annotate controllers with OpenAPI annotations
- [ ] Add API examples and descriptions
- [ ] Generate and review API documentation

---

## Example Request/Response Flows

### Typical Registration Flow
```
1. Client: POST /api/auth/register
   Body: { name, email, password, confirmPassword }

2. Server: Validates input
          Checks if email exists (409 if exists)
          Hashes password with BCrypt
          Saves user to database
          Generates JWT tokens
          Returns 201 with tokens and user info

3. Client: Stores tokens in localStorage/sessionStorage
          Adds Authorization header to subsequent requests
```

### Typical Login Flow
```
1. Client: POST /api/auth/login
   Body: { email, password }

2. Server: Validates credentials
          Increments failed login attempts if invalid
          Locks account if too many failures
          Generates JWT tokens on success
          Updates last_login_at
          Returns 200 with tokens and user info

3. Client: Stores tokens
          Redirects to dashboard
```

### Token Refresh Flow
```
1. Client: Detects access token expiry (401 from API)
          POST /api/auth/refresh
          Body: { refreshToken }

2. Server: Validates refresh token
          Checks if token is not blacklisted
          Generates new access token
          Optionally rotates refresh token
          Returns 200 with new tokens

3. Client: Updates stored tokens
          Retries original failed request
```

### Password Reset Flow
```
1. Client: POST /api/auth/password-reset/request
   Body: { email }

2. Server: Generates reset token (UUID)
          Saves token with 1-hour expiry
          Sends email with reset link
          Returns 200 (always, to prevent enumeration)

3. User: Clicks email link
   Client: Opens password reset form

4. Client: POST /api/auth/password-reset/confirm
   Body: { token, newPassword, confirmPassword }

5. Server: Validates token (not expired, not used)
          Validates password strength
          Hashes and updates password
          Marks token as used
          Invalidates all user sessions
          Returns 200

6. Client: Redirects to login page
```

---

## Dependencies Required

### Maven Dependencies
```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Email (for password reset) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- OpenAPI/Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## Configuration Properties

### application.yml
```yaml
# JWT Configuration
app:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-change-this-in-production}
    access-token-expiration: 3600000  # 1 hour in milliseconds
    refresh-token-expiration: 2592000000  # 30 days in milliseconds

# Email Configuration
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${EMAIL_USERNAME}
    password: ${EMAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

# Password Reset
app:
  password-reset:
    token-expiration: 3600000  # 1 hour
    base-url: ${APP_BASE_URL:http://localhost:3000}

# Rate Limiting (if using Bucket4j)
bucket4j:
  enabled: true
  filters:
    - cache-name: rate-limit-buckets
      url: /api/auth/login.*
      rate-limits:
        - bandwidths:
            - capacity: 5
              time: 15
              unit: minutes
    - cache-name: rate-limit-buckets
      url: /api/auth/password-reset/request.*
      rate-limits:
        - bandwidths:
            - capacity: 3
              time: 1
              unit: hours
```

---

## Notes for Implementation Team

1. **Security First**: Always prioritize security over convenience
2. **Token Management**: Consider using Redis for token blacklist in production
3. **Email Service**: Configure proper SMTP settings or use service like SendGrid
4. **Testing**: Write comprehensive tests for all security scenarios
5. **Documentation**: Keep Swagger docs up-to-date with any API changes
6. **Environment Variables**: Never commit secrets (JWT secret, email credentials) to version control
7. **HTTPS**: Enforce HTTPS in production
8. **Monitoring**: Add logging for security events (failed logins, password resets)
9. **Compliance**: Ensure GDPR/privacy compliance for user data handling
10. **Performance**: Monitor JWT validation performance under load

---

## Future Enhancements

- OAuth2 integration (Google, GitHub, etc.)
- Two-factor authentication (2FA/MFA)
- Email verification on registration
- Remember me functionality
- Session management dashboard
- API key authentication for service-to-service calls
- Role-based access control (RBAC)
- Permission-based authorization
- IP whitelisting/blacklisting
- Device fingerprinting
- Suspicious activity detection

---

## Conclusion

This API design provides a comprehensive, secure, and scalable authentication system for the User Management Service. It follows industry best practices and Spring Boot conventions while maintaining consistency with the existing codebase patterns.

All endpoints are designed with security in mind, proper validation, clear error messages, and comprehensive documentation for easy integration and maintenance.
