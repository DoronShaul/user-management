# Authentication System Database Design

## Executive Summary
This document provides a comprehensive database schema design for adding authentication and role-based access control (RBAC) to the User Management Service. The design follows Spring Boot JPA best practices, uses BCrypt for password hashing (no separate salt needed), and includes refresh token management and audit logging.

## Design Principles
- **Password Security**: BCrypt hashing (built-in salt)
- **RBAC Model**: Users → Roles → Permissions (many-to-many relationships)
- **Token Management**: Refresh tokens with expiry and revocation support
- **Audit Trail**: Comprehensive logging of authentication events
- **Database**: MySQL 8.0 with InnoDB engine
- **ORM**: Spring Data JPA with Hibernate
- **Conventions**: Follow existing project standards (Lombok, validation, snake_case columns)

---

## 1. Updated User Entity

### Changes to Existing User Table
The existing `User` entity needs the following modifications:

**New Fields:**
- `password_hash` - BCrypt hashed password (VARCHAR(60))
- `account_enabled` - Account status flag (BOOLEAN)
- `account_locked` - Lock status for security (BOOLEAN)
- `failed_login_attempts` - Counter for lockout logic (INT)
- `last_login_at` - Track last successful login (DATETIME)
- `password_changed_at` - Track password changes (DATETIME)
- `updated_at` - Track entity updates (DATETIME)

### Entity Definition

```java
package com.doron.shaul.usermanagement.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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

    // Authentication fields
    @NotBlank(message = "Password is required")
    @Size(min = 60, max = 60, message = "Password hash must be 60 characters (BCrypt)")
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "account_enabled", nullable = false)
    private Boolean accountEnabled = true;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // Relationships
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    private Set<Role> roles = new HashSet<>();

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (passwordChangedAt == null) {
            passwordChangedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

### MySQL Table Definition

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    password_changed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_email (email),
    INDEX idx_account_status (account_enabled, account_locked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 2. Role-Based Access Control (RBAC) Schema

### 2.1 Role Entity

```java
package com.doron.shaul.usermanagement.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Role name is required")
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id", referencedColumnName = "id")
    )
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### MySQL Table Definition

```sql
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at DATETIME NOT NULL,
    INDEX idx_role_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.2 Permission Entity

```java
package com.doron.shaul.usermanagement.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Permission name is required")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @NotBlank(message = "Resource is required")
    @Column(nullable = false, length = 50)
    private String resource;

    @NotBlank(message = "Action is required")
    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### MySQL Table Definition

```sql
CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_permission_name (name),
    INDEX idx_resource_action (resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.3 Join Tables

#### user_roles (Many-to-Many: Users ↔ Roles)

```sql
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### role_permissions (Many-to-Many: Roles ↔ Permissions)

```sql
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 3. Token Management

### 3.1 RefreshToken Entity

```java
package com.doron.shaul.usermanagement.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Token is required")
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Expiry date is required")
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    public boolean isValid() {
        return !isRevoked && !isExpired();
    }
}
```

### MySQL Table Definition

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
    INDEX idx_user_id (user_id),
    INDEX idx_expiry_revoked (expiry_date, is_revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. Audit Logging

### 4.1 AuthAuditLog Entity

```java
package com.doron.shaul.usermanagement.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "username", length = 255)
    private String username;

    @NotBlank(message = "Event type is required")
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_status", nullable = false, length = 20)
    private String eventStatus;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### MySQL Table Definition

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
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at),
    INDEX idx_event_status (event_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Event Types
```
LOGIN_SUCCESS
LOGIN_FAILURE
LOGOUT
TOKEN_REFRESH
PASSWORD_CHANGE
PASSWORD_RESET_REQUEST
PASSWORD_RESET_SUCCESS
ACCOUNT_LOCKED
ACCOUNT_UNLOCKED
ROLE_ASSIGNED
ROLE_REMOVED
```

### Event Status
```
SUCCESS
FAILURE
PENDING
```

---

## 5. Entity Relationships Diagram

```
users (1) ←──→ (N) user_roles (N) ←──→ (1) roles
                                          ↓
                                          |
                                          | (1)
                                          ↓
                                      (N) role_permissions (N) ←──→ (1) permissions


users (1) ←──→ (N) refresh_tokens

users (1) ←──→ (N) auth_audit_logs
```

**Relationship Details:**
- **User ↔ Role**: Many-to-Many (via user_roles)
- **Role ↔ Permission**: Many-to-Many (via role_permissions)
- **User ↔ RefreshToken**: One-to-Many
- **User ↔ AuthAuditLog**: One-to-Many (nullable for failed login attempts)

---

## 6. Initial Data Seed

### Default Roles

```sql
INSERT INTO roles (name, description, created_at) VALUES
('ROLE_ADMIN', 'Administrator with full system access', NOW()),
('ROLE_USER', 'Standard user with basic access', NOW()),
('ROLE_MODERATOR', 'Moderator with elevated privileges', NOW());
```

### Default Permissions

```sql
INSERT INTO permissions (name, description, resource, action, created_at) VALUES
('USER_READ', 'Read user information', 'USER', 'READ', NOW()),
('USER_CREATE', 'Create new users', 'USER', 'CREATE', NOW()),
('USER_UPDATE', 'Update user information', 'USER', 'UPDATE', NOW()),
('USER_DELETE', 'Delete users', 'USER', 'DELETE', NOW()),
('ROLE_READ', 'Read role information', 'ROLE', 'READ', NOW()),
('ROLE_MANAGE', 'Manage roles and permissions', 'ROLE', 'MANAGE', NOW());
```

### Default Role-Permission Mappings

```sql
-- ROLE_ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

-- ROLE_USER gets read-only access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('USER_READ', 'ROLE_READ');

-- ROLE_MODERATOR gets read and update access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MODERATOR' AND p.name IN ('USER_READ', 'USER_UPDATE', 'ROLE_READ');
```

---

## 7. Migration Strategy

### Option 1: Hibernate Auto-Update (Development Only)
Current configuration uses `spring.jpa.hibernate.ddl-auto=update`. This will automatically add new columns to the `users` table and create new tables.

**Steps:**
1. Update User entity with new fields
2. Create new entities (Role, Permission, RefreshToken, AuthAuditLog)
3. Run application - Hibernate will execute DDL changes
4. Insert seed data manually or via data.sql

**Pros:** Fast for development
**Cons:** Not safe for production, no rollback capability

### Option 2: Flyway Migration (Recommended for Production)

**Add dependency to pom.xml:**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**Migration Files Structure:**
```
src/main/resources/db/migration/
├── V1__initial_users_table.sql (baseline existing table)
├── V2__add_authentication_fields.sql
├── V3__create_roles_table.sql
├── V4__create_permissions_table.sql
├── V5__create_user_roles_junction.sql
├── V6__create_role_permissions_junction.sql
├── V7__create_refresh_tokens_table.sql
├── V8__create_auth_audit_logs_table.sql
└── V9__insert_default_roles_permissions.sql
```

**application.properties changes:**
```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

### Option 3: Liquibase Migration

**Add dependency to pom.xml:**
```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

**Changelog Structure:**
```
src/main/resources/db/changelog/
├── db.changelog-master.xml
├── changes/
    ├── v1-initial-schema.xml
    ├── v2-add-authentication.xml
    ├── v3-add-rbac.xml
    └── v4-add-tokens-audit.xml
```

### Recommended Approach

**For this project (learning/development):** Use Hibernate auto-update with manual SQL seed file
**For production:** Use Flyway for better version control and rollback capability

---

## 8. Database Indexes Strategy

### Performance-Critical Indexes

```sql
-- User table
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_account_status ON users(account_enabled, account_locked);

-- Role table
CREATE INDEX idx_role_name ON roles(name);

-- Permission table
CREATE INDEX idx_permission_name ON permissions(name);
CREATE INDEX idx_resource_action ON permissions(resource, action);

-- Refresh tokens
CREATE INDEX idx_token ON refresh_tokens(token);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_expiry_revoked ON refresh_tokens(expiry_date, is_revoked);

-- Audit logs
CREATE INDEX idx_user_id ON auth_audit_logs(user_id);
CREATE INDEX idx_event_type ON auth_audit_logs(event_type);
CREATE INDEX idx_created_at ON auth_audit_logs(created_at);
CREATE INDEX idx_event_status ON auth_audit_logs(event_status);

-- Junction tables
CREATE INDEX idx_user_id ON user_roles(user_id);
CREATE INDEX idx_role_id ON user_roles(role_id);
CREATE INDEX idx_role_id ON role_permissions(role_id);
CREATE INDEX idx_permission_id ON role_permissions(permission_id);
```

---

## 9. Repository Interfaces

### UserRepository
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(String email);
}
```

### RoleRepository
```java
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT r FROM Role r JOIN FETCH r.permissions WHERE r.name = :name")
    Optional<Role> findByNameWithPermissions(String name);
}
```

### PermissionRepository
```java
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
    List<Permission> findByResource(String resource);
    Optional<Permission> findByResourceAndAction(String resource, String action);
}
```

### RefreshTokenRepository
```java
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now OR rt.isRevoked = true")
    void deleteExpiredAndRevokedTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true, rt.revokedAt = :now WHERE rt.user.id = :userId")
    void revokeAllUserTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
```

### AuthAuditLogRepository
```java
@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {
    List<AuthAuditLog> findByUserId(Long userId);
    List<AuthAuditLog> findByEventType(String eventType);
    List<AuthAuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.userId = :userId AND a.eventType = :eventType ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findRecentEventsByUser(@Param("userId") Long userId, @Param("eventType") String eventType, Pageable pageable);
}
```

---

## 10. Data Integrity and Constraints

### Foreign Key Constraints
- All foreign keys use `ON DELETE CASCADE` except audit logs
- Audit logs use `ON DELETE SET NULL` to preserve history when users are deleted
- Join tables cascade deletes to prevent orphaned records

### Unique Constraints
- `users.email` - UNIQUE (existing)
- `roles.name` - UNIQUE
- `permissions.name` - UNIQUE
- `refresh_tokens.token` - UNIQUE

### Check Constraints (MySQL 8.0.16+)
```sql
ALTER TABLE users ADD CONSTRAINT chk_failed_attempts
    CHECK (failed_login_attempts >= 0 AND failed_login_attempts <= 10);

ALTER TABLE refresh_tokens ADD CONSTRAINT chk_expiry_date
    CHECK (expiry_date > created_at);
```

---

## 11. Security Considerations

### Password Storage
- **BCrypt**: Use BCrypt with strength 12 for password hashing
- **No separate salt column**: BCrypt includes salt in the hash
- **Hash length**: Always 60 characters (fixed)

### Token Security
- **Refresh tokens**: 512-byte random secure tokens
- **Expiry**: Recommend 7-30 day expiry for refresh tokens
- **JWT access tokens**: Short-lived (15-60 minutes), stored client-side only
- **Revocation**: Support manual revocation via `is_revoked` flag

### Account Lockout
- Lock account after 5 failed login attempts
- Reset counter on successful login
- Admin can manually unlock accounts

### Audit Trail
- Log all authentication events
- Store IP address and user agent for forensics
- Never delete audit logs (retain for compliance)

---

## 12. Performance Considerations

### N+1 Query Prevention
- Use `@EntityGraph` or `JOIN FETCH` for loading users with roles
- Eager fetch roles and permissions together when needed
- Consider lazy loading for audit logs (potentially large dataset)

### Batch Operations
- Batch insert audit logs if logging is high-volume
- Use `@Modifying` queries for bulk token revocation
- Index all foreign key columns

### Cleanup Tasks
- Schedule job to delete expired tokens (weekly)
- Archive old audit logs (6-12 months retention)
- Monitor table sizes and consider partitioning for large tables

---

## 13. Testing Strategy

### Database Tests
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailWithRoles_UserExists_ReturnsUserWithRoles() {
        // Test entity relationships
    }
}
```

### Test Data
- Use `@Sql` annotations to load test data
- Create test fixtures for common scenarios
- Test cascade operations (delete user → delete tokens)

---

## 14. Summary

### Tables Overview
| Table | Purpose | Relationships |
|-------|---------|---------------|
| users | User accounts with auth fields | Many-to-Many with roles |
| roles | User roles (ADMIN, USER, etc.) | Many-to-Many with users and permissions |
| permissions | Fine-grained permissions | Many-to-Many with roles |
| user_roles | Junction table | - |
| role_permissions | Junction table | - |
| refresh_tokens | JWT refresh tokens | Many-to-One with users |
| auth_audit_logs | Authentication audit trail | Many-to-One with users (nullable) |

### Key Design Decisions
1. **BCrypt for passwords** - No separate salt table needed
2. **Eager fetch for roles/permissions** - Balance between N+1 and performance
3. **Separate audit log entity** - Preserve history even when users deleted
4. **Token revocation support** - Security requirement for logout
5. **Account lockout mechanism** - Built into User entity
6. **MySQL InnoDB engine** - ACID compliance and foreign keys

### Next Steps for Implementation Team
1. **Backend Developer**: Implement entities as specified
2. **Security Engineer**: Configure Spring Security with BCrypt
3. **API Designer**: Design authentication endpoints (login, logout, refresh)
4. **Integration Coordinator**: Ensure all components work together
5. **Architecture Lead**: Review and approve final implementation

---

## Appendix: Complete Migration SQL

```sql
-- V2: Add authentication fields to users table
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(60) NOT NULL DEFAULT 'TEMP',
    ADD COLUMN account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_login_at DATETIME NULL,
    ADD COLUMN password_changed_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL;

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_account_status ON users(account_enabled, account_locked);

-- V3: Create roles table
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at DATETIME NOT NULL,
    INDEX idx_role_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V4: Create permissions table
CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_permission_name (name),
    INDEX idx_resource_action (resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V5: Create user_roles junction table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V6: Create role_permissions junction table
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V7: Create refresh_tokens table
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
    INDEX idx_user_id (user_id),
    INDEX idx_expiry_revoked (expiry_date, is_revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V8: Create auth_audit_logs table
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
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at),
    INDEX idx_event_status (event_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V9: Insert seed data
INSERT INTO roles (name, description, created_at) VALUES
('ROLE_ADMIN', 'Administrator with full system access', NOW()),
('ROLE_USER', 'Standard user with basic access', NOW()),
('ROLE_MODERATOR', 'Moderator with elevated privileges', NOW());

INSERT INTO permissions (name, description, resource, action, created_at) VALUES
('USER_READ', 'Read user information', 'USER', 'READ', NOW()),
('USER_CREATE', 'Create new users', 'USER', 'CREATE', NOW()),
('USER_UPDATE', 'Update user information', 'USER', 'UPDATE', NOW()),
('USER_DELETE', 'Delete users', 'USER', 'DELETE', NOW()),
('ROLE_READ', 'Read role information', 'ROLE', 'READ', NOW()),
('ROLE_MANAGE', 'Manage roles and permissions', 'ROLE', 'MANAGE', NOW());

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('USER_READ', 'ROLE_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MODERATOR' AND p.name IN ('USER_READ', 'USER_UPDATE', 'ROLE_READ');
```

---

**Document Version**: 1.0
**Last Updated**: 2026-02-09
**Author**: Database Designer Agent
**Status**: Ready for Review
