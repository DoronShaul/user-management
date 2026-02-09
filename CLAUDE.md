# User Management Service

## Overview
A Spring Boot REST API for managing users. This is a learning project for building Claude Code agent workflows.

## Technology Stack
- **Java**: 17
- **Framework**: Spring Boot 3.2.1
- **Build Tool**: Maven
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA with Hibernate
- **Validation**: Jakarta Validation (spring-boot-starter-validation)
- **Utilities**: Lombok
- **Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc

## Project Structure
```
src/
├── main/java/com/doron/shaul/usermanagement/
│   ├── UserManagementApplication.java    # Main application class
│   ├── controller/                        # REST controllers
│   │   └── *Controller.java
│   ├── service/                           # Business logic
│   │   └── *Service.java
│   ├── repository/                        # Data access layer
│   │   └── *Repository.java
│   ├── model/                             # JPA entities
│   │   └── *.java
│   └── exception/                         # Exception handling
│       └── GlobalExceptionHandler.java
└── test/java/com/doron/shaul/usermanagement/
    ├── controller/                        # Controller tests (@WebMvcTest)
    │   └── *ControllerTest.java
    └── service/                           # Service unit tests
        └── *ServiceTest.java
```

## Coding Standards

### General Principles
- Use Lombok to reduce boilerplate (`@Data`, `@RequiredArgsConstructor`, etc.)
- Use constructor injection via `@RequiredArgsConstructor` (NOT field injection)
- All validation using Jakarta Validation annotations (`@NotBlank`, `@Email`, etc.)
- Validation messages must be descriptive

### Entity Classes (model/)
```java
@Entity
@Table(name = "table_name")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityName {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Validation annotations on fields
    @NotBlank(message = "Field is required")
    @Column(nullable = false)
    private String fieldName;

    // Timestamps with @PrePersist
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Repository Interfaces (repository/)
```java
@Repository
public interface EntityRepository extends JpaRepository<Entity, Long> {
    // Custom query methods using Spring Data naming conventions
    boolean existsByEmail(String email);
}
```

### Service Classes (service/)
```java
@Service
@RequiredArgsConstructor
public class EntityService {
    private final EntityRepository entityRepository;

    @Transactional
    public Entity createEntity(Entity entity) {
        // Business validation
        if (entityRepository.existsByEmail(entity.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        return entityRepository.save(entity);
    }
}
```

### Controller Classes (controller/)
```java
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class EntityController {
    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Entity> createEntity(@Valid @RequestBody Entity entity) {
        Entity created = entityService.createEntity(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Entity> getEntity(@PathVariable Long id) {
        return entityService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

### Exception Handling
- Use `@RestControllerAdvice` for global exception handling
- Return appropriate HTTP status codes:
  - `400 Bad Request` for validation/business rule violations
  - `404 Not Found` for missing resources
  - `409 Conflict` for duplicate resources
  - `500 Internal Server Error` for unexpected errors

## API Conventions
- **Base path**: `/api/{resource}` (plural, lowercase)
- **HTTP Methods**:
  - `GET` - retrieve resources
  - `POST` - create resources
  - `PUT` - full update
  - `PATCH` - partial update
  - `DELETE` - remove resources
- **Responses**: Return `ResponseEntity<T>` with appropriate status codes
- **Validation**: Use `@Valid` on request bodies

## Testing Standards

### Service Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class EntityServiceTest {
    @Mock
    private EntityRepository entityRepository;

    @InjectMocks
    private EntityService entityService;

    private Entity testEntity;

    @BeforeEach
    void setUp() {
        testEntity = new Entity();
        // Initialize test data
    }

    @Test
    void methodName_givenCondition_expectedBehavior() {
        // given
        when(repository.method()).thenReturn(value);

        // when
        Result result = service.method();

        // then
        assertNotNull(result);
        verify(repository, times(1)).method();
    }
}
```

### Controller Tests
```java
@WebMvcTest(EntityController.class)
class EntityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EntityService entityService;

    @Test
    void endpoint_givenCondition_expectedStatus() throws Exception {
        // given
        when(entityService.method(any())).thenReturn(entity);

        // when/then
        mockMvc.perform(post("/api/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.field").value("expected"));
    }
}
```

### Test Naming Convention
- `methodName_givenCondition_expectedBehavior()`
- Example: `createUser_EmailAlreadyExists_ThrowsException()`

### Test Coverage Requirements
- Minimum 80% code coverage for new code
- Every public method in Service classes must have tests
- Every endpoint in Controller classes must have tests
- Test both success and error scenarios

## Build Commands
```bash
# Maven path (IntelliJ bundled)
MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"

# Compile the project
"$MVN" compile

# Run tests
"$MVN" test

# Run specific test class
"$MVN" test -Dtest=UserServiceTest

# Package the application
"$MVN" package

# Run the application
"$MVN" spring-boot:run

# Clean build
"$MVN" clean install
```

## Database Configuration
- Development uses MySQL on localhost:3306
- Database name: `user_management`
- DDL auto-update enabled for development

## Naming Conventions
| Type | Convention | Example |
|------|------------|---------|
| Entity | Singular noun | `User`, `Product`, `Order` |
| Repository | EntityRepository | `UserRepository` |
| Service | EntityService | `UserService` |
| Controller | EntityController | `UserController` |
| Test | ClassTest | `UserServiceTest`, `UserControllerTest` |
| Table | Plural, snake_case | `users`, `order_items` |
| Column | snake_case | `created_at`, `first_name` |

## Things to Avoid
- Field injection (`@Autowired` on fields) - use constructor injection
- Business logic in controllers - keep in services
- Catching generic `Exception` - catch specific exceptions
- Hardcoded values - use constants or configuration
- Missing validation - always validate input
- N+1 queries - use appropriate fetch strategies

## Git Workflow
- Branch naming: `feature/description` or `bugfix/description`
- Commit messages: Clear, descriptive, present tense
- Always create PR for review before merging to main
