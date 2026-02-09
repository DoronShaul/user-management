# Authentication System - Master Implementation Plan

**Project**: User Management Service Authentication
**Date**: 2026-02-09
**Status**: Ready for Implementation
**Team**: 5 Agent Planning Team (Architecture, Security, Database, API, Integration)

---

## Executive Summary

This master plan consolidates findings from 5 specialized planning agents to deliver a production-ready JWT-based authentication system for the User Management Service. The design prioritizes security, follows Spring Boot 3.2.1 best practices, and maintains alignment with existing project standards.

**Key Decision**: JWT-based stateless authentication with refresh tokens, BCrypt password hashing, and RBAC support.

**Timeline**: 10-day implementation across 6 phases
**Test Coverage**: 43+ new tests, 80%+ coverage target
**Components**: 9 new + 6 modified components

---

## 1. Technology Stack Summary

### Core Technologies
- **Authentication Method**: JWT (JSON Web Tokens) with refresh tokens
- **Password Hashing**: BCrypt (strength 12)
- **Security Framework**: Spring Security 6.2.x
- **Token Library**: JJWT 0.12.3 (Java 17 compatible)
- **Rate Limiting**: Bucket4j 8.7.0
- **Token Storage**: Database (refresh tokens) + Redis (blacklist)
- **Database Migration**: Flyway (versioned migrations)

### Key Dependencies
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

<!-- Rate Limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Flyway Database Migration -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

---

## 2. Architecture Overview

### 2.1 Authentication Flow

```
User Registration
├─> POST /api/auth/register
├─> Validate input (email, password strength)
├─> Hash password (BCrypt strength 12)
├─> Save user to database
├─> Assign default role (ROLE_USER)
├─> Generate JWT tokens (access + refresh)
└─> Return tokens + user info

User Login
├─> POST /api/auth/login
├─> Validate credentials
├─> Increment failed attempts counter
├─> Lock account after 10 failures
├─> Generate JWT tokens
├─> Update last_login_at
└─> Return tokens + user info

Protected Request
├─> Extract JWT from Authorization header
├─> Validate signature & expiration
├─> Set SecurityContext
├─> Check endpoint authorization
├─> Validate resource ownership (if needed)
└─> Process request
```

### 2.2 Token Strategy

**Access Token (JWT)**:
- Lifespan: 15 minutes
- Algorithm: HS256 (HMAC-SHA256)
- Payload: userId, email, roles, iat, exp
- Storage: Client memory (not localStorage)
- Cannot be revoked (short TTL mitigates risk)

**Refresh Token**:
- Lifespan: 7 days
- Format: Secure random UUID (512 bytes)
- Storage: Database + client storage
- Revocable: Yes (delete from DB)
- Rotation: Optional (issue new on each use)

---

## 3. Database Schema

### 3.1 Updated User Table

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,           -- BCrypt hash
    account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    password_changed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_email (email),
    INDEX idx_account_status (account_enabled, account_locked)
);
```

### 3.2 RBAC Schema

```sql
-- Roles table
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at DATETIME NOT NULL,
    INDEX idx_role_name (name)
);

-- Permissions table
CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_resource_action (resource, action)
);

-- Junction tables
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
```

### 3.3 Token Management

```sql
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_expiry_revoked (expiry_date, is_revoked)
);
```

### 3.4 Audit Logging

```sql
CREATE TABLE auth_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    event_status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    failure_reason VARCHAR(500),
    additional_data TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);
```

**Default Seed Data**:
- Roles: ROLE_ADMIN, ROLE_USER, ROLE_MODERATOR
- Permissions: USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE, ROLE_READ, ROLE_MANAGE

### 3.5 Flyway Migration Strategy

**Migration Tool**: Flyway for versioned database migrations

**Directory Structure**:
```
src/main/resources/db/migration/
├── V1__baseline_users_table.sql          # Baseline existing users table
├── V2__add_authentication_fields.sql     # Add auth fields to users
├── V3__create_roles_table.sql            # Create roles table
├── V4__create_permissions_table.sql      # Create permissions table
├── V5__create_user_roles_junction.sql    # User-roles relationship
├── V6__create_role_permissions_junction.sql  # Role-permissions relationship
├── V7__create_refresh_tokens_table.sql   # Refresh tokens storage
├── V8__create_auth_audit_logs_table.sql  # Audit logging
└── V9__insert_default_roles_permissions.sql  # Seed data
```

**Configuration** (`application.properties`):
```properties
# Flyway Configuration
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration

# Hibernate - validate only (Flyway handles DDL)
spring.jpa.hibernate.ddl-auto=validate
```

**Key Migration Files**:

**V2__add_authentication_fields.sql**:
```sql
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(60) NOT NULL AFTER email,
    ADD COLUMN account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_login_at DATETIME NULL,
    ADD COLUMN password_changed_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL;

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_account_status ON users(account_enabled, account_locked);
```

**V9__insert_default_roles_permissions.sql**:
```sql
-- Insert default roles
INSERT INTO roles (name, description, created_at) VALUES
('ROLE_ADMIN', 'Administrator with full system access', NOW()),
('ROLE_USER', 'Standard user with basic access', NOW()),
('ROLE_MODERATOR', 'Moderator with elevated privileges', NOW());

-- Insert default permissions
INSERT INTO permissions (name, description, resource, action, created_at) VALUES
('USER_READ', 'Read user information', 'USER', 'READ', NOW()),
('USER_CREATE', 'Create new users', 'USER', 'CREATE', NOW()),
('USER_UPDATE', 'Update user information', 'USER', 'UPDATE', NOW()),
('USER_DELETE', 'Delete users', 'USER', 'DELETE', NOW()),
('ROLE_READ', 'Read role information', 'ROLE', 'READ', NOW()),
('ROLE_MANAGE', 'Manage roles and permissions', 'ROLE', 'MANAGE', NOW());

-- Assign permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('USER_READ', 'ROLE_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MODERATOR' AND p.name IN ('USER_READ', 'USER_UPDATE', 'ROLE_READ');
```

**Migration Execution**:
- Flyway runs automatically on application startup
- Migrations applied in version order (V1, V2, V3, ...)
- Each migration tracked in `flyway_schema_history` table
- Failed migrations prevent application startup

**Rollback Strategy**:
- Create undo migrations (e.g., `U2__undo_authentication_fields.sql`)
- Or maintain manual rollback scripts outside Flyway
- Recommended: Test migrations in staging before production

---

## 4. API Endpoints

### 4.1 Authentication Endpoints

| Endpoint | Method | Auth Required | Description |
|----------|--------|---------------|-------------|
| `/api/auth/register` | POST | No | User registration |
| `/api/auth/login` | POST | No | User authentication |
| `/api/auth/refresh` | POST | Yes (Refresh Token) | Refresh access token |
| `/api/auth/logout` | POST | Yes | Invalidate tokens |
| `/api/auth/password-reset/request` | POST | No | Request password reset |
| `/api/auth/password-reset/confirm` | POST | No | Confirm password reset |
| `/api/auth/password-change` | POST | Yes | Change password |
| `/api/auth/me` | GET | Yes | Get current user |

### 4.2 Request/Response Examples

**Registration**:
```json
POST /api/auth/register
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "confirmPassword": "SecurePass123!"
}

Response (201 Created):
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "a1b2c3d4...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "createdAt": "2026-02-09T10:30:00"
  }
}
```

**Login**:
```json
POST /api/auth/login
{
  "email": "john@example.com",
  "password": "SecurePass123!"
}

Response (200 OK):
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "a1b2c3d4...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { ... }
}
```

### 4.3 Protected Endpoint Security

**Existing Endpoints**:
- `/api/users` (GET) - Search users → **Authenticated**
- `/api/users/{id}` (GET) - Get user → **Authenticated**
- `/api/users/{id}` (PUT) - Update user → **Authenticated + Owner**
- `/api/users/{id}` (DELETE) - Delete user → **Authenticated + Owner**

**Authorization Header**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 5. Security Requirements

### 5.1 Password Policy

**Requirements**:
- Minimum 12 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 digit
- At least 1 special character (@$!%*?&)

**Storage**:
- BCrypt with strength 12 (~300ms hash time)
- Automatic per-password salting
- 60-character hash output

### 5.2 Threat Mitigation

| Threat | Mitigation Strategy |
|--------|-------------------|
| **Brute Force** | Rate limiting (5 attempts/15min), account lockout (10 failures), CAPTCHA |
| **Password DB Breach** | BCrypt hashing with strength 12 |
| **SQL Injection** | Spring Data JPA parameterized queries |
| **XSS** | Input validation, JSON auto-escaping, CSP headers |
| **CSRF** | Token validation (if cookie-based), stateless JWT in header |
| **Account Enumeration** | Generic error messages, timing attack prevention |
| **Token Theft** | Short access token TTL (15min), HTTPS only, HttpOnly cookies |

### 5.3 Rate Limiting

**Authentication Endpoints**:
- Login: 5 attempts per 15 minutes per IP
- Register: 3 registrations per hour per IP
- Password reset: 3 requests per hour per email

**Account Lockout**:
- Lock after 10 failed attempts within 1 hour
- Lockout duration: 30 minutes
- Email notification to account owner

### 5.4 Security Headers

```yaml
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
```

### 5.5 Audit Logging

**Events to Log**:
- LOGIN_SUCCESS, LOGIN_FAILURE (IP, user agent, timestamp)
- LOGOUT, TOKEN_REFRESH
- PASSWORD_CHANGE, PASSWORD_RESET_REQUEST
- ACCOUNT_LOCKED, ACCOUNT_UNLOCKED
- ACCESS_DENIED (attempted resource, user)

**Retention**: 90 days minimum
**Format**: Structured JSON with correlation IDs

---

## 6. Implementation Phases (10 Days)

### Phase 1: Foundation (Day 1-2)
**Deliverables**:
- [ ] Add Maven dependencies (spring-security, jjwt, bucket4j, redis, flyway-core, flyway-mysql)
- [ ] Configure Flyway in `application.properties`
- [ ] Create Flyway migration directory: `src/main/resources/db/migration/`
- [ ] Create migration files (V1-V9) for database schema
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` (Flyway handles DDL)
- [ ] Create `JwtService` (generate, validate tokens)
- [ ] Create `ErrorResponse` class
- [ ] Update `GlobalExceptionHandler` with auth exceptions
- [ ] Write unit tests for JwtService (8 tests)
- [ ] 90%+ coverage on JwtService

**Testing**: `mvn test -Dtest=JwtServiceTest`

**Database Migration**: Start application to verify Flyway migrations execute successfully

---

### Phase 2: Authentication Endpoints (Day 3-4)
**Deliverables**:
- [ ] Update `User` entity with password, roles, account status fields (matches V2 migration)
- [ ] Create new entities: `Role`, `Permission`, `RefreshToken`, `AuthAuditLog`
- [ ] Create DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`
- [ ] Implement `AuthService` (register, login with BCrypt)
- [ ] Create `AuthController` with `/api/auth/register` and `/api/auth/login`
- [ ] Write unit tests (AuthService: 5, AuthController: 6)
- [ ] 85%+ coverage

**Note**: Database schema already created by Flyway migrations (V1-V9) in Phase 1

**Validation**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@example.com","password":"SecurePass123!"}'
```

---

### Phase 3: Security Filter Chain (Day 5-6)
**Deliverables**:
- [ ] Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`
- [ ] Create `SecurityConfig` with `SecurityFilterChain` bean
- [ ] Configure public vs protected endpoints
- [ ] Write filter tests (4 tests)
- [ ] Write security config tests (3 tests)
- [ ] 85%+ coverage

**Validation**:
```bash
# Without token (should return 401)
curl http://localhost:8080/api/users/1

# With valid token (should return 200)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/1
```

---

### Phase 4: Authorization & Ownership (Day 7)
**Deliverables**:
- [ ] Modify `UserService.updateUser()` to validate ownership
- [ ] Modify `UserService.deleteUser()` to validate ownership
- [ ] Update `UserController` to inject `Authentication`
- [ ] Add `AccessDeniedException` handling
- [ ] Write ownership validation tests (6 tests)
- [ ] 85%+ coverage

**Validation**:
```bash
# User1 updates own profile (should succeed)
curl -X PUT http://localhost:8080/api/users/1 -H "Authorization: Bearer $TOKEN1" ...

# User1 updates User2 profile (should return 403)
curl -X PUT http://localhost:8080/api/users/2 -H "Authorization: Bearer $TOKEN1" ...
```

---

### Phase 5: Integration Testing (Day 8)
**Deliverables**:
- [ ] Full authentication flow integration tests (3 tests)
- [ ] Authorization scenario integration tests
- [ ] Edge case tests (expired tokens, malformed tokens)
- [ ] Update existing integration tests with authentication
- [ ] 80%+ overall coverage
- [ ] All tests passing

**Testing**: `mvn clean verify`

---

### Phase 6: Documentation & Rollout (Day 9-10)
**Deliverables**:
- [ ] Update API documentation
- [ ] Create migration guide for existing users
- [ ] Update CLAUDE.md with authentication patterns
- [ ] Manual testing of all endpoints
- [ ] Prepare rollout checklist
- [ ] Database migration scripts

---

## 7. Components Overview

### 7.1 New Components (9 total)

| Component | Package | Purpose |
|-----------|---------|---------|
| `JwtService` | `security` | Token generation/validation |
| `JwtAuthenticationFilter` | `security` | Request filtering |
| `SecurityConfig` | `config` | Spring Security setup |
| `AuthService` | `service` | Registration/login logic |
| `AuthController` | `controller` | Auth endpoints |
| `RegisterRequest` | `dto` | Registration payload |
| `LoginRequest` | `dto` | Login payload |
| `AuthResponse` | `dto` | Token response |
| `ErrorResponse` | `exception` | Standardized errors |

### 7.2 Modified Components (6 total)

| Component | Changes |
|-----------|---------|
| `User` entity | Add password, roles, account status fields |
| `UserService` | Add Authentication parameters to update/delete |
| `UserController` | Inject Authentication in methods |
| `GlobalExceptionHandler` | Add auth exception handlers |
| `pom.xml` | Add security dependencies |
| `application.properties` | Add JWT configuration |

### 7.3 New Entities (4 total)

- `Role` - User roles (ADMIN, USER, MODERATOR)
- `Permission` - Fine-grained permissions
- `RefreshToken` - Refresh token storage
- `AuthAuditLog` - Authentication audit trail

---

## 8. Testing Strategy

### 8.1 Test Coverage Summary

| Component | Tests | Coverage Target |
|-----------|-------|-----------------|
| JwtService | 8 | 95% |
| AuthService | 5 | 90% |
| AuthController | 6 | 85% |
| JwtAuthenticationFilter | 4 | 85% |
| SecurityConfig | 3 | 80% |
| Updated UserService | 6 | 85% |
| Updated UserController | 8 | 80% |
| Integration Tests | 3 | N/A |

**Total New Tests**: 43+
**Overall Coverage Target**: 80%+

### 8.2 Test Naming Convention

```
methodName_givenCondition_expectedBehavior()

Examples:
- generateToken_ValidEmail_ReturnsValidToken()
- login_InvalidCredentials_ThrowsBadCredentialsException()
- updateUser_AuthenticatedAsOwner_Success()
```

---

## 9. Configuration

### 9.1 Application Properties

```properties
# JWT Configuration
app.security.jwt.secret=${JWT_SECRET:change-this-in-production}
app.security.jwt.access-token-expiration=900000  # 15 minutes
app.security.jwt.refresh-token-expiration=604800000  # 7 days
app.security.bcrypt.strength=12

# Rate Limiting
app.security.rate-limit.login-attempts=5
app.security.rate-limit.login-window-minutes=15
app.security.rate-limit.account-lockout-attempts=10
app.security.rate-limit.account-lockout-duration-minutes=30

# Password Policy
app.security.password.min-length=12
app.security.password.require-uppercase=true
app.security.password.require-lowercase=true
app.security.password.require-digit=true
app.security.password.require-special-char=true

# CORS
app.security.cors.allowed-origins=${ALLOWED_ORIGINS:https://yourdomain.com}

# Redis
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

# Flyway Database Migration
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration

# Hibernate - validate only (Flyway handles DDL)
spring.jpa.hibernate.ddl-auto=validate
```

---

## 10. Deployment Checklist

### 10.1 Pre-Deployment

- [ ] All unit tests passing (`mvn test`)
- [ ] All integration tests passing (`mvn verify`)
- [ ] Code coverage ≥ 80%
- [ ] Flyway migration scripts created and tested
- [ ] JWT secret key configured in production
- [ ] Redis instance available
- [ ] API documentation updated
- [ ] Rollback plan documented

### 10.2 Deployment Steps

1. **Database Migration (Automated via Flyway)**
   - Flyway automatically applies migrations on application startup
   - Migrations applied in version order (V1 → V9)
   - Check `flyway_schema_history` table to verify successful migrations
   - Monitor logs for migration errors

2. **Application Deployment**
   - Deploy new version with authentication
   - Flyway runs migrations before application starts
   - Monitor logs for errors
   - Verify health endpoint accessible

3. **Smoke Testing**
   - Test registration flow
   - Test login flow
   - Test protected endpoint access with token
   - Test ownership validation

### 10.3 Monitoring

**Key Metrics**:
- 401/403 error rates
- Failed login attempts
- Token validation failures
- API response times (P95 < +50ms)

**Rollback Triggers**:
- Authentication blocking legitimate requests
- Critical functionality broken
- Database migration failures

---

## 11. Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Breaking existing API consumers | High | High | Phased rollout, API versioning |
| JWT secret exposure | Low | Critical | Environment variables, rotation |
| Performance degradation | Medium | Medium | Token validation caching |
| Existing users locked out | High | High | Temp passwords, migration guide |
| Test coverage gaps | Medium | Medium | Strict requirements, code review |

---

## 12. Success Criteria

### Functional
- [ ] Users can register with email/password
- [ ] Users can login and receive JWT
- [ ] Protected endpoints require valid JWT
- [ ] Users can only update/delete own accounts
- [ ] Public endpoints remain accessible
- [ ] Proper error responses for all auth failures

### Non-Functional
- [ ] Test coverage ≥ 80%
- [ ] No breaking changes to existing functionality
- [ ] API response time increase < 50ms (P95)
- [ ] All tests pass (`mvn clean verify`)
- [ ] Code follows CLAUDE.md standards
- [ ] Documentation complete

---

## 13. Agent Team Deliverables

All 5 planning agents have completed their comprehensive research and delivered:

1. **Architecture Lead**: `AUTHENTICATION_ARCHITECTURE.md`
   - JWT vs session-based comparison
   - Token lifecycle design
   - 6-phase implementation plan

2. **API Designer**: `AUTHENTICATION_API_DESIGN.md`
   - 8 authentication endpoints
   - Complete DTOs and validation rules
   - OpenAPI/Swagger documentation plan

3. **Database Designer**: `DATABASE_DESIGN.md`
   - Updated User entity with auth fields
   - RBAC schema (Role, Permission)
   - RefreshToken and AuthAuditLog entities
   - Flyway migration strategy

4. **Security Analyst**: `security-requirements.md`
   - BCrypt strength 12 recommendation
   - OWASP Top 10 threat mitigation
   - Rate limiting and account lockout
   - Security headers configuration

5. **Integration Coordinator**: `AUTHENTICATION_INTEGRATION_PLAN.md`
   - Spring Security filter chain design
   - 43 new test cases defined
   - 10-day phased implementation
   - Backward compatibility strategy

---

## 14. Next Steps

1. **Review this master plan** with the team
2. **Approve the architecture** and approach
3. **Begin Phase 1 implementation** (Foundation)
4. **Execute phases sequentially** with daily check-ins
5. **Complete all tests** and achieve 80%+ coverage
6. **Deploy to production** following rollout checklist

---

## 15. References

- **Detailed Architecture**: `AUTHENTICATION_ARCHITECTURE.md`
- **API Specification**: `AUTHENTICATION_API_DESIGN.md`
- **Database Design**: `DATABASE_DESIGN.md`
- **Security Requirements**: `.claude/projects/.../memory/security-requirements.md`
- **Integration Plan**: `AUTHENTICATION_INTEGRATION_PLAN.md`

---

**Plan Status**: ✅ Complete and Ready for Implementation
**Estimated Timeline**: 10 days (6 phases)
**Team Confidence**: High - comprehensive planning across all domains

**Prepared by**: 5-Agent Planning Team
**Date**: 2026-02-09
**Approval Required**: User/Product Owner
