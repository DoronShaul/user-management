## Plan: Health Check Endpoint with Spring Boot Actuator

### What
Add Spring Boot Actuator to provide production-ready health endpoints with automatic database health detection.

### Decisions Made
| Decision | Why |
|----------|-----|
| Spring Boot Actuator | User selected: production-ready, auto-detects DB health, minimal code, industry standard |
| Both app + DB health | User selected: complete health status including app liveness and database connectivity |
| Expose only health endpoint | Security best practice - expose minimum necessary endpoints |

### Files
| Action | File | Notes |
|--------|------|-------|
| Modify | `pom.xml` | Add `spring-boot-starter-actuator` dependency |
| Modify | `src/main/resources/application.properties` | Configure actuator endpoints and health details |
| Create | `src/test/java/com/doron/shaul/usermanagement/actuator/HealthEndpointTest.java` | Integration test for health endpoint |

### Key Details
- Actuator auto-detects `spring-boot-starter-data-jpa` and includes DB health check
- Health endpoint: `GET /actuator/health`
- Response shows aggregate status and individual component statuses (db, diskSpace)
- No custom code needed - Actuator handles everything

### Configuration to Add
```properties
# Actuator
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
```

### Expected Response
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": { ... }
    }
  }
}
```

### Tests to Write
1. Health endpoint returns 200 when application is healthy
2. Health response includes database status component
3. Health response includes overall status field
