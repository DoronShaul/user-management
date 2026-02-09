# Authentication System Integration Plan

## Executive Summary
This document outlines the comprehensive integration strategy for adding JWT-based authentication to the existing User Management Spring Boot service. The plan prioritizes minimal disruption to existing functionality while maintaining code quality standards.

---

## 1. Current System Analysis

### 1.1 Existing API Endpoints
**Location**: `/Users/doronshaul/IdeaProjects/user-management/src/main/java/com/doron/shaul/usermanagement/controller/UserController.java`

| Endpoint | Method | Current Access | Proposed Security |
|----------|--------|----------------|-------------------|
| `/api/users` | POST | Public | Public (user registration) |
| `/api/users` | GET | Public | Authenticated (search users) |
| `/api/users/{id}` | GET | Public | Authenticated |
| `/api/users/{id}` | PUT | Public | Authenticated (owner or admin) |
| `/api/users/{id}` | DELETE | Public | Authenticated (owner or admin) |

**Actuator Endpoints**:
- `/actuator/health` - Should remain public for monitoring systems

### 1.2 Current Exception Handling
**Location**: `/Users/doronshaul/IdeaProjects/user-management/src/main/java/com/doron/shaul/usermanagement/exception/GlobalExceptionHandler.java`

**Current Coverage**:
- `IllegalArgumentException` → 400 Bad Request

**Gaps**:
- No handling for authentication/authorization exceptions
- No handling for JWT-specific exceptions
- Limited error response structure

### 1.3 Current Dependencies (pom.xml)
**Missing Required Dependencies**:
- `spring-boot-starter-security`
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT library)

---

## 2. Endpoint Security Strategy

### 2.1 Security Classification

#### Public Endpoints (No Authentication Required)
```
POST /api/auth/register    - New endpoint for user registration
POST /api/auth/login       - New endpoint for authentication
GET  /actuator/health      - Health check
```

#### Authenticated Endpoints (Valid JWT Required)
```
GET    /api/users          - Search users by name
GET    /api/users/{id}     - Get user details
PUT    /api/users/{id}     - Update user (ownership validation)
DELETE /api/users/{id}     - Delete user (ownership validation)
```

### 2.2 Implementation Approach

**Option Selected**: Spring Security with `SecurityFilterChain` (Modern approach for Spring Boot 3.x)

**Configuration Strategy**:
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                .requestMatchers("/api/users/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**Rationale**:
- Declarative security configuration
- No need for method-level annotations on existing controllers
- Centralized security rules
- Easy to modify and maintain

### 2.3 Authorization Rules

**Resource Ownership Pattern**:
- Users can view any user's information (authenticated)
- Users can only update/delete their own account
- Future: Admin role can manage any user

**Implementation Location**: Service layer validation
```java
// In UserService.updateUser()
public User updateUser(Long id, User user, Authentication auth) {
    String authenticatedEmail = auth.getName(); // JWT subject is email
    User existing = findById(id)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

    if (!existing.getEmail().equals(authenticatedEmail)) {
        throw new AccessDeniedException("Cannot update other users");
    }

    existing.setName(user.getName());
    return userRepository.save(existing);
}
```

---

## 3. Spring Security Filter Chain Design

### 3.1 Filter Chain Architecture

```
HTTP Request
     ↓
[Spring Security Filter Chain]
     ↓
1. CorsFilter (if configured)
     ↓
2. CsrfFilter (disabled for stateless JWT)
     ↓
3. JwtAuthenticationFilter (CUSTOM)
   - Extract JWT from Authorization header
   - Validate token signature
   - Parse claims
   - Set SecurityContext authentication
     ↓
4. AuthorizationFilter
   - Check SecurityContext authentication
   - Validate against SecurityFilterChain rules
     ↓
5. ExceptionTranslationFilter
   - Catch AuthenticationException
   - Catch AccessDeniedException
     ↓
[DispatcherServlet]
     ↓
[Controller]
```

### 3.2 Custom JWT Filter Implementation

**Location**: `src/main/java/com/doron/shaul/usermanagement/security/JwtAuthenticationFilter.java`

**Responsibilities**:
1. Extract JWT from `Authorization: Bearer <token>` header
2. Validate token using `JwtService`
3. Extract email (subject) from token
4. Load user details (minimal - just email for authentication)
5. Set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
6. Continue filter chain

**Key Design Decision**:
- Filter only validates JWT and sets authentication
- Does NOT load full User entity from database (performance optimization)
- Authorization checks happen in service layer when needed

---

## 4. Exception Handling Integration

### 4.1 New Exception Types to Handle

| Exception | HTTP Status | Trigger Scenario |
|-----------|-------------|------------------|
| `AuthenticationException` (Spring Security) | 401 Unauthorized | Invalid/expired JWT, missing credentials |
| `AccessDeniedException` (Spring Security) | 403 Forbidden | Valid user, insufficient permissions |
| `BadCredentialsException` | 401 Unauthorized | Invalid login credentials |
| `JwtException` (JJWT) | 401 Unauthorized | Malformed JWT, signature failure |
| `ExpiredJwtException` (JJWT) | 401 Unauthorized | Token expired |

### 4.2 Enhanced GlobalExceptionHandler

**Location**: `/Users/doronshaul/IdeaProjects/user-management/src/main/java/com/doron/shaul/usermanagement/exception/GlobalExceptionHandler.java`

**Additions Required**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Existing handler
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    // NEW: Authentication failures
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(UNAUTHORIZED)
            .body(new ErrorResponse("AUTHENTICATION_FAILED", e.getMessage()));
    }

    // NEW: Authorization failures
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(FORBIDDEN)
            .body(new ErrorResponse("ACCESS_DENIED", "Insufficient permissions"));
    }

    // NEW: JWT-specific exceptions
    @ExceptionHandler({JwtException.class, MalformedJwtException.class})
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException e) {
        return ResponseEntity.status(UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_TOKEN", "Invalid or malformed token"));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwt(ExpiredJwtException e) {
        return ResponseEntity.status(UNAUTHORIZED)
            .body(new ErrorResponse("TOKEN_EXPIRED", "Token has expired"));
    }
}
```

**New Error Response Structure**:
```java
@Data
@AllArgsConstructor
public class ErrorResponse {
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
```

### 4.3 Filter-Level Exception Handling

**Challenge**: Exceptions in filters occur before `@RestControllerAdvice` handlers.

**Solution**: Add exception handling in `JwtAuthenticationFilter`:
```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
    try {
        String jwt = extractJwt(request);
        if (jwt != null && jwtService.isTokenValid(jwt)) {
            // Set authentication
        }
        filterChain.doFilter(request, response);
    } catch (ExpiredJwtException e) {
        handleJwtException(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token expired");
    } catch (JwtException e) {
        handleJwtException(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", e.getMessage());
    }
}

private void handleJwtException(HttpServletResponse response,
                               HttpStatus status,
                               String errorCode,
                               String message) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ErrorResponse error = new ErrorResponse(errorCode, message);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    response.getWriter().write(mapper.writeValueAsString(error));
}
```

---

## 5. Testing Strategy

### 5.1 Test Pyramid

```
                    /\
                   /  \
                  / E2E \ (Few)
                 /______\
                /        \
               / Integra- \
              /    tion    \ (Some)
             /_____________\
            /               \
           /      Unit       \
          /                   \ (Many)
         /_____________________\
```

### 5.2 Unit Tests

#### 5.2.1 JwtService Tests
**Location**: `src/test/java/com/doron/shaul/usermanagement/security/JwtServiceTest.java`

**Test Cases**:
```java
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Test
    void generateToken_ValidEmail_ReturnsValidToken() {
        // given: email
        // when: generate token
        // then: token not null, can be parsed
    }

    @Test
    void extractEmail_ValidToken_ReturnsEmail() {
        // given: token with email
        // when: extract email
        // then: correct email returned
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        // given: fresh token
        // when: validate
        // then: returns true
    }

    @Test
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        // given: expired token (manipulate clock)
        // when: validate
        // then: returns false
    }

    @Test
    void isTokenValid_MalformedToken_ThrowsJwtException() {
        // given: corrupted token
        // when: validate
        // then: throws exception
    }

    @Test
    void isTokenValid_WrongSignature_ThrowsJwtException() {
        // given: token signed with different key
        // when: validate
        // then: throws exception
    }
}
```

#### 5.2.2 AuthService Tests
**Location**: `src/test/java/com/doron/shaul/usermanagement/service/AuthServiceTest.java`

**Test Cases**:
```java
@Test
void register_NewUser_CreatesUserAndReturnsToken() {
    // given: new registration request
    // when: register
    // then: user created, token returned
}

@Test
void register_ExistingEmail_ThrowsIllegalArgumentException() {
    // given: existing email
    // when: register
    // then: throws exception
}

@Test
void login_ValidCredentials_ReturnsToken() {
    // given: existing user with matching password
    // when: login
    // then: returns valid token
}

@Test
void login_InvalidEmail_ThrowsBadCredentialsException() {
    // given: non-existent email
    // when: login
    // then: throws exception
}

@Test
void login_InvalidPassword_ThrowsBadCredentialsException() {
    // given: valid email, wrong password
    // when: login
    // then: throws exception
}
```

#### 5.2.3 Updated UserService Tests
**Location**: `src/test/java/com/doron/shaul/usermanagement/service/UserServiceTest.java`

**New Test Cases**:
```java
@Test
void updateUser_AuthenticatedAsOwner_Success() {
    // given: authenticated user updating own profile
    // when: update
    // then: success
}

@Test
void updateUser_AuthenticatedAsOtherUser_ThrowsAccessDeniedException() {
    // given: authenticated user trying to update another user
    // when: update
    // then: throws AccessDeniedException
}

@Test
void deleteUser_AuthenticatedAsOwner_Success() {
    // given: authenticated user deleting own account
    // when: delete
    // then: success
}

@Test
void deleteUser_AuthenticatedAsOtherUser_ThrowsAccessDeniedException() {
    // given: authenticated user trying to delete another user
    // when: delete
    // then: throws AccessDeniedException
}
```

### 5.3 Controller Tests

#### 5.3.1 AuthController Tests
**Location**: `src/test/java/com/doron/shaul/usermanagement/controller/AuthControllerTest.java`

**Configuration**: `@WebMvcTest(AuthController.class)` with `@MockBean(AuthService.class)`

**Test Cases**:
```java
@Test
void register_ValidRequest_ReturnsCreatedWithToken() throws Exception {
    // Verify 201 CREATED, body contains token
}

@Test
void register_MissingFields_ReturnsBadRequest() throws Exception {
    // Verify 400 BAD_REQUEST for validation failures
}

@Test
void register_ExistingEmail_ReturnsBadRequest() throws Exception {
    // Verify 400 BAD_REQUEST with appropriate message
}

@Test
void login_ValidCredentials_ReturnsOkWithToken() throws Exception {
    // Verify 200 OK, body contains token
}

@Test
void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
    // Verify 401 UNAUTHORIZED
}
```

#### 5.3.2 Updated UserController Tests
**Location**: `src/test/java/com/doron/shaul/usermanagement/controller/UserControllerTest.java`

**Challenge**: `@WebMvcTest` does NOT load full Spring Security config by default.

**Solution**: Add `@Import(SecurityConfig.class)` and mock JWT filter.

**New Test Cases**:
```java
@Test
void getUser_WithValidToken_ReturnsOk() throws Exception {
    // given: valid JWT in header
    // when: GET /api/users/1
    // then: 200 OK
}

@Test
void getUser_WithoutToken_ReturnsUnauthorized() throws Exception {
    // given: no Authorization header
    // when: GET /api/users/1
    // then: 401 UNAUTHORIZED
}

@Test
void getUser_WithExpiredToken_ReturnsUnauthorized() throws Exception {
    // given: expired JWT
    // when: GET /api/users/1
    // then: 401 UNAUTHORIZED
}

@Test
void updateUser_AsOwner_ReturnsOk() throws Exception {
    // given: JWT with email matching user ID
    // when: PUT /api/users/1
    // then: 200 OK
}

@Test
void updateUser_AsOtherUser_ReturnsForbidden() throws Exception {
    // given: JWT with different email
    // when: PUT /api/users/1
    // then: 403 FORBIDDEN
}
```

### 5.4 Integration Tests

**Location**: `src/test/java/com/doron/shaul/usermanagement/integration/AuthenticationIntegrationTest.java`

**Configuration**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Transactional
```

**Test Scenarios**:
```java
@Test
void fullAuthenticationFlow_RegisterLoginAndAccessProtectedEndpoint() {
    // 1. Register new user → get token
    // 2. Login → get new token
    // 3. Use token to GET /api/users → verify success
    // 4. Use token to update own profile → verify success
    // 5. Use token to update other user → verify 403
}

@Test
void tokenExpiration_ExpiredToken_ReturnsUnauthorized() {
    // Generate token with 1 second expiry, wait, attempt access
}

@Test
void invalidToken_TamperedToken_ReturnsUnauthorized() {
    // Modify token payload, attempt access
}
```

### 5.5 Security-Specific Tests

**Location**: `src/test/java/com/doron/shaul/usermanagement/security/SecurityConfigTest.java`

**Configuration**: `@SpringBootTest` with `@AutoConfigureMockMvc`

**Test Cases**:
```java
@Test
void publicEndpoints_NoAuthentication_Accessible() {
    // POST /api/auth/register → 200/201
    // POST /api/auth/login → 200/201
    // GET /actuator/health → 200
}

@Test
void protectedEndpoints_NoAuthentication_ReturnsUnauthorized() {
    // GET /api/users → 401
    // GET /api/users/1 → 401
    // PUT /api/users/1 → 401
    // DELETE /api/users/1 → 401
}

@Test
void protectedEndpoints_WithValidToken_Accessible() {
    // With valid JWT, all protected endpoints return 200/204
}
```

### 5.6 Test Data Strategy

**Mock JWT Tokens**:
```java
// Test utility class
public class TestJwtUtil {
    private static final String TEST_SECRET = "test-secret-key-for-jwt-testing-purposes-only";

    public static String generateTestToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(SignatureAlgorithm.HS256, TEST_SECRET)
            .compact();
    }

    public static String generateExpiredToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date(System.currentTimeMillis() - 7200000))
            .setExpiration(new Date(System.currentTimeMillis() - 3600000))
            .signWith(SignatureAlgorithm.HS256, TEST_SECRET)
            .compact();
    }
}
```

**Test User Data**:
```java
public class TestDataFactory {
    public static User createTestUser(Long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword("$2a$10$hash..."); // Pre-hashed test password
        return user;
    }

    public static RegisterRequest createRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("TestPassword123!");
        return request;
    }
}
```

### 5.7 Test Coverage Goals

| Component | Minimum Coverage | Priority |
|-----------|------------------|----------|
| JwtService | 95% | Critical |
| AuthService | 90% | Critical |
| JwtAuthenticationFilter | 85% | High |
| SecurityConfig | 80% | High |
| AuthController | 85% | High |
| Updated UserService | 85% | High |
| Updated UserController | 80% | Medium |
| GlobalExceptionHandler | 90% | High |

---

## 6. Backward Compatibility & Migration

### 6.1 Breaking Changes

**API Contract Changes**:
- Previously public endpoints now require authentication
- Error response format changed (String → ErrorResponse object)

### 6.2 Migration Strategy

**Phase 1: Soft Launch (Optional - if needed for production)**
- Add authentication system
- Keep endpoints public temporarily
- Add `X-API-Version: 2.0` header to indicate new auth-enabled version
- Monitor for integration issues

**Phase 2: Enforcement**
- Enable authentication requirement
- Update API documentation
- Notify API consumers

### 6.3 Database Migration

**User Table Changes**:
```sql
-- Add password column
ALTER TABLE users
ADD COLUMN password VARCHAR(255) NOT NULL AFTER email;

-- Existing users: generate temporary passwords, email users
UPDATE users
SET password = '$2a$10$default.bcrypt.hash.for.migration'
WHERE password IS NULL;
```

**Migration Considerations**:
- Existing User entities have no password field
- Need to update User model
- Need to handle existing test data

### 6.4 Impact on Existing Code

**Minimal Changes Required**:
1. **User entity**: Add `password` field
2. **UserService**: Modify `updateUser()` and `deleteUser()` signatures to accept `Authentication`
3. **UserController**: Inject `Authentication` parameter in update/delete methods
4. **Tests**: Update all controller tests to include JWT tokens

**No Changes Required**:
- UserRepository (no changes)
- Most of UserService business logic
- Database configuration
- Actuator configuration

---

## 7. Implementation Phases

### Phase 1: Foundation (Day 1-2)
**Objective**: Add dependencies and basic JWT infrastructure

**Tasks**:
1. Add Maven dependencies (spring-security, jjwt)
2. Create JWT configuration properties in `application.properties`
3. Implement `JwtService` (generate, parse, validate tokens)
4. Write unit tests for `JwtService`
5. Create `ErrorResponse` class
6. Update `GlobalExceptionHandler` with auth exceptions

**Deliverables**:
- JWT token generation/validation working
- 90%+ test coverage on `JwtService`
- Exception handling framework in place

**Testing**: Run `mvn test -Dtest=JwtServiceTest`

---

### Phase 2: Authentication Endpoints (Day 3-4)
**Objective**: Create registration and login functionality

**Tasks**:
1. Update `User` entity with `password` field
2. Create DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`
3. Implement `AuthService` (register, login with BCrypt)
4. Create `AuthController` with `/api/auth/register` and `/api/auth/login`
5. Write unit tests for `AuthService`
6. Write controller tests for `AuthController`

**Deliverables**:
- Working registration endpoint
- Working login endpoint
- Password encryption with BCrypt
- 85%+ test coverage

**Testing**: Run `mvn test -Dtest=AuthServiceTest,AuthControllerTest`

**Validation Checkpoint**:
```bash
# Start application
mvn spring-boot:run

# Test registration
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","password":"SecurePass123!"}'

# Should return: {"token": "eyJhbGci..."}

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecurePass123!"}'
```

---

### Phase 3: Security Filter Chain (Day 5-6)
**Objective**: Implement JWT validation filter and Spring Security configuration

**Tasks**:
1. Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`
2. Implement filter logic (extract JWT, validate, set SecurityContext)
3. Create `SecurityConfig` with `SecurityFilterChain` bean
4. Configure public vs protected endpoints
5. Write unit tests for filter
6. Write security configuration tests

**Deliverables**:
- JWT filter validates tokens on protected endpoints
- Public endpoints remain accessible
- 401 returned for missing/invalid tokens
- 85%+ test coverage

**Testing**: Run `mvn test -Dtest=JwtAuthenticationFilterTest,SecurityConfigTest`

**Validation Checkpoint**:
```bash
# Test public endpoint (should work)
curl http://localhost:8080/actuator/health

# Test protected endpoint without token (should return 401)
curl http://localhost:8080/api/users/1

# Test protected endpoint with valid token (should work)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecurePass123!"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/1
```

---

### Phase 4: Authorization & Ownership (Day 7)
**Objective**: Add ownership-based authorization to update/delete operations

**Tasks**:
1. Modify `UserService.updateUser()` to accept `Authentication` and validate ownership
2. Modify `UserService.deleteUser()` to accept `Authentication` and validate ownership
3. Update `UserController` to inject `Authentication` parameters
4. Add `AccessDeniedException` handling
5. Write tests for ownership validation

**Deliverables**:
- Users can only update/delete their own accounts
- 403 returned when attempting to modify other users
- 85%+ test coverage on updated methods

**Testing**: Run `mvn test -Dtest=UserServiceTest,UserControllerTest`

**Validation Checkpoint**:
```bash
# Create two users
TOKEN_USER1=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"User1","email":"user1@example.com","password":"Pass123!"}' | jq -r .token)

TOKEN_USER2=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"User2","email":"user2@example.com","password":"Pass123!"}' | jq -r .token)

# User1 updates own profile (should succeed)
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $TOKEN_USER1" \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Name"}'

# User1 tries to update User2 (should return 403)
curl -X PUT http://localhost:8080/api/users/2 \
  -H "Authorization: Bearer $TOKEN_USER1" \
  -H "Content-Type: application/json" \
  -d '{"name":"Hacked Name"}'
```

---

### Phase 5: Integration Testing (Day 8)
**Objective**: Comprehensive end-to-end testing

**Tasks**:
1. Write integration tests for full authentication flow
2. Write integration tests for authorization scenarios
3. Test edge cases (expired tokens, malformed tokens)
4. Update existing integration tests to include authentication
5. Verify all test suites pass

**Deliverables**:
- Full integration test suite
- 80%+ overall code coverage
- All tests passing

**Testing**: Run `mvn clean verify`

---

### Phase 6: Documentation & Rollout (Day 9-10)
**Objective**: Finalize documentation and prepare for deployment

**Tasks**:
1. Update API documentation with authentication requirements
2. Create migration guide for existing users
3. Update CLAUDE.md with authentication patterns
4. Perform manual testing of all endpoints
5. Prepare rollout checklist

**Deliverables**:
- Complete API documentation
- Migration guide
- Updated coding standards
- Rollout checklist

---

## 8. Rollout Plan

### 8.1 Pre-Deployment Checklist

- [ ] All unit tests passing (`mvn test`)
- [ ] All integration tests passing (`mvn verify`)
- [ ] Code coverage ≥ 80%
- [ ] Database migration scripts prepared
- [ ] JWT secret key configured in production properties
- [ ] API documentation updated
- [ ] Rollback plan documented

### 8.2 Deployment Steps

1. **Database Migration** (if existing users exist)
   - Run SQL migration to add password column
   - Generate temporary passwords for existing users
   - Email users with password reset instructions

2. **Application Deployment**
   - Deploy new version with authentication enabled
   - Monitor logs for authentication errors
   - Verify health endpoint still accessible

3. **Smoke Testing**
   - Test registration flow
   - Test login flow
   - Test protected endpoint access
   - Test ownership validation

### 8.3 Monitoring & Rollback

**Key Metrics**:
- 401/403 error rates
- Failed login attempts
- Token validation failures
- API response times

**Rollback Triggers**:
- Authentication blocking legitimate requests
- Critical functionality broken
- Database migration failures

**Rollback Procedure**:
1. Revert to previous application version
2. Restore database backup (if migration applied)
3. Investigate issues in staging environment

---

## 9. Risk Analysis & Mitigation

### 9.1 Identified Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Breaking existing API consumers | High | High | Phased rollout, API versioning |
| JWT secret key exposure | Low | Critical | Use environment variables, rotate keys |
| Performance degradation from token validation | Medium | Medium | Cache token validations, optimize filter |
| Existing users locked out (no passwords) | High | High | Database migration with temp passwords |
| Test coverage gaps | Medium | Medium | Strict test requirements, code review |
| CORS issues with Authorization header | Medium | Low | Configure CORS properly in SecurityConfig |

### 9.2 Mitigation Strategies

**API Consumer Impact**:
- Provide advance notice of authentication requirement
- Maintain backward compatibility period
- Offer API versioning (`/api/v2/users`)

**Security Best Practices**:
- Store JWT secret in environment variables (not properties files)
- Use strong, randomly generated secrets (256-bit minimum)
- Implement token rotation strategy
- Add rate limiting on login endpoint (future)

**Performance Optimization**:
- Filter only validates token signature, doesn't load full User entity
- Consider caching valid tokens (Redis) for high-traffic scenarios
- Monitor P95/P99 latencies after deployment

---

## 10. Future Enhancements (Out of Scope)

**Phase 2 Features** (Post-Initial Release):
1. **Refresh Tokens**: Long-lived tokens for re-authentication
2. **Role-Based Access Control (RBAC)**: Admin role, read-only role
3. **Password Reset Flow**: Email-based password recovery
4. **Rate Limiting**: Prevent brute-force attacks
5. **Audit Logging**: Track authentication events
6. **OAuth2 Integration**: Google/GitHub login
7. **Multi-Factor Authentication (MFA)**: TOTP-based 2FA

---

## 11. Key Integration Points Summary

### 11.1 New Components to Create

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

### 11.2 Components to Modify

| Component | Changes Required |
|-----------|------------------|
| `User` | Add `password` field |
| `UserService` | Add `Authentication` parameters to update/delete |
| `UserController` | Inject `Authentication` in methods |
| `GlobalExceptionHandler` | Add auth exception handlers |
| `pom.xml` | Add security dependencies |
| `application.properties` | Add JWT configuration |

### 11.3 Testing Requirements

| Test Type | Number of Tests | Priority |
|-----------|-----------------|----------|
| JWT Service Unit | ~8 tests | Critical |
| Auth Service Unit | ~5 tests | Critical |
| Auth Controller | ~6 tests | High |
| Updated User Service | ~6 tests | High |
| Updated User Controller | ~8 tests | Medium |
| Filter Unit Tests | ~4 tests | High |
| Integration Tests | ~3 tests | Critical |
| Security Config Tests | ~3 tests | High |

**Total New Tests**: ~43 tests

---

## 12. Success Criteria

**Functional Requirements**:
- [ ] Users can register with email/password
- [ ] Users can login and receive JWT
- [ ] Protected endpoints require valid JWT
- [ ] Users can only update/delete their own accounts
- [ ] Public endpoints remain accessible
- [ ] Proper error responses for all auth failures

**Non-Functional Requirements**:
- [ ] Test coverage ≥ 80%
- [ ] No breaking changes to existing functionality
- [ ] API response time increase < 50ms (P95)
- [ ] All tests pass (`mvn clean verify`)
- [ ] Code follows CLAUDE.md standards
- [ ] Documentation complete

**Quality Gates**:
- [ ] Code review passed
- [ ] Security review passed (JWT implementation)
- [ ] Performance testing passed
- [ ] Manual testing passed

---

## 13. Dependencies & Assumptions

**Dependencies**:
- Spring Boot 3.2.1 (already present)
- Java 17 (already present)
- MySQL database with existing user data

**Assumptions**:
1. No existing authentication system to migrate from
2. Email is unique identifier for users
3. Single JWT secret sufficient (not multi-tenant)
4. Stateless authentication (no session management needed)
5. Token expiration of 24 hours acceptable
6. BCrypt password hashing meets security requirements

**External Dependencies**:
- Maven Central (for dependency downloads)
- MySQL database availability
- No external authentication provider integration needed initially

---

## Contact & Sign-off

**Plan Prepared By**: Integration Coordinator Agent
**Date**: 2026-02-09
**Review Required By**: Team Lead, Security Analyst

**Approval**:
- [ ] Architecture Lead
- [ ] Security Analyst
- [ ] Database Designer
- [ ] API Designer
- [ ] Team Lead

**Next Steps**:
1. Review and approve integration plan
2. Assign implementation tasks to developers
3. Begin Phase 1 implementation
4. Schedule daily stand-ups to track progress
