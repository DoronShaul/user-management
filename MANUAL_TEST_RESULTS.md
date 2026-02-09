# Manual End-to-End Test Results

**Project**: User Management Service
**Feature**: Authentication & Authorization System
**Test Date**: February 9, 2026
**Environment**: Local Development (localhost:8080)
**Tester**: Testing & QA Lead

---

## Test Scenarios Overview

This document provides manual test scripts for verifying the complete authentication system. These tests should be executed manually to validate end-to-end functionality before production deployment.

---

## Prerequisites

1. Application running on `http://localhost:8080`
2. MySQL database `user_management` available
3. Tools: `curl` or Postman for API testing
4. Clean database state (or willing to create new test users)

---

## Test Suite 1: Authentication Flow

### Test 1.1: User Registration

**Objective**: Verify new users can register successfully

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Smith",
    "email": "alice@test.com",
    "password": "SecurePass123!"
  }'
```

**Expected Result**:
- Status: `201 CREATED`
- Response body contains `accessToken` (JWT string)
- Response body contains `refreshToken`

**Validation**:
```json
{
  "accessToken": "eyJhbGci... (long JWT string)",
  "refreshToken": "eyJhbGci... (long JWT string)"
}
```

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 1.2: User Login

**Objective**: Verify registered users can login

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@test.com",
    "password": "SecurePass123!"
  }'
```

**Expected Result**:
- Status: `200 OK`
- Response body contains `accessToken` and `refreshToken`

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 1.3: Login with Invalid Credentials

**Objective**: Verify login fails with wrong password

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@test.com",
    "password": "WrongPassword"
  }'
```

**Expected Result**:
- Status: `401 UNAUTHORIZED`
- Error message indicates invalid credentials

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 1.4: Register with Duplicate Email

**Objective**: Verify duplicate email registration is rejected

**Steps**:
```bash
# Try to register with alice@test.com again
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Duplicate",
    "email": "alice@test.com",
    "password": "AnotherPass123!"
  }'
```

**Expected Result**:
- Status: `400 BAD REQUEST` or `409 CONFLICT`
- Error message: "Email already exists" or similar

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 2: Protected Endpoint Access

### Test 2.1: Access Protected Endpoint Without Token

**Objective**: Verify protected endpoints reject unauthenticated requests

**Steps**:
```bash
curl -X GET http://localhost:8080/api/users/1
```

**Expected Result**:
- Status: `403 FORBIDDEN` (or `401 UNAUTHORIZED`)

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 2.2: Access Protected Endpoint With Valid Token

**Objective**: Verify authenticated access to protected endpoints

**Steps**:
```bash
# First, save the token from login
TOKEN="<paste_token_from_login_response>"

# Access protected endpoint
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- Status: `200 OK`
- Response contains user data

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 2.3: Search Users With Authentication

**Objective**: Verify search endpoint requires authentication

**Steps**:
```bash
# Without token (should fail)
curl -X GET "http://localhost:8080/api/users?name=alice"

# With token (should succeed)
curl -X GET "http://localhost:8080/api/users?name=alice" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Results**:
- Without token: `403 FORBIDDEN`
- With token: `200 OK` with user list

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 3: Authorization (Ownership Validation)

### Test 3.1: User Updates Own Profile

**Objective**: Verify users can update their own profile

**Setup**:
1. Register and login as Alice
2. Note Alice's user ID from response

**Steps**:
```bash
TOKEN_ALICE="<alice_token>"

curl -X PUT http://localhost:8080/api/users/<alice_id> \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Updated"
  }'
```

**Expected Result**:
- Status: `200 OK`
- Response shows updated name

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 3.2: User Cannot Update Other User's Profile

**Objective**: Verify ownership validation prevents unauthorized updates

**Setup**:
1. Register and login as Bob
2. Try to update Alice's profile using Bob's token

**Steps**:
```bash
# Register Bob
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bob Jones",
    "email": "bob@test.com",
    "password": "BobPass123!"
  }'

# Login as Bob and get token
TOKEN_BOB="<bob_token>"

# Try to update Alice's profile
curl -X PUT http://localhost:8080/api/users/<alice_id> \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Hacked Name"
  }'
```

**Expected Result**:
- Status: `403 FORBIDDEN`
- Error message: "Cannot update other users" or "Access denied"

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 3.3: User Deletes Own Account

**Objective**: Verify users can delete their own account

**Steps**:
```bash
curl -X DELETE http://localhost:8080/api/users/<alice_id> \
  -H "Authorization: Bearer $TOKEN_ALICE"
```

**Expected Result**:
- Status: `204 NO CONTENT`

**Validation**:
```bash
# Try to access deleted user (should fail)
curl -X GET http://localhost:8080/api/users/<alice_id> \
  -H "Authorization: Bearer $TOKEN_BOB"
```
- Should return `404 NOT FOUND`

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 3.4: User Cannot Delete Other User's Account

**Objective**: Verify ownership validation prevents unauthorized deletion

**Steps**:
```bash
# Bob tries to delete Alice's account
curl -X DELETE http://localhost:8080/api/users/<alice_id> \
  -H "Authorization: Bearer $TOKEN_BOB"
```

**Expected Result**:
- Status: `403 FORBIDDEN`
- Error message indicates access denied

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 4: Token Validation

### Test 4.1: Expired Token Rejection

**Objective**: Verify expired tokens are rejected

**Note**: This test requires either:
1. Waiting for token to expire (default: 15 minutes for access token)
2. Temporarily modifying `app.security.jwt.access-token-expiration` to 1000ms

**Steps**:
```bash
# Use an expired token
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer <expired_token>"
```

**Expected Result**:
- Status: `401 UNAUTHORIZED` or `403 FORBIDDEN`

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 4.2: Malformed Token Rejection

**Objective**: Verify malformed tokens are rejected

**Steps**:
```bash
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer not.a.valid.jwt"
```

**Expected Result**:
- Status: `401 UNAUTHORIZED` or `403 FORBIDDEN`

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 4.3: Missing Bearer Prefix

**Objective**: Verify tokens without "Bearer " prefix are rejected

**Steps**:
```bash
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: $TOKEN_ALICE"
```

**Expected Result**:
- Status: `403 FORBIDDEN`

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 5: Public Endpoints

### Test 5.1: Health Check Accessibility

**Objective**: Verify health endpoint is publicly accessible

**Steps**:
```bash
curl -X GET http://localhost:8080/actuator/health
```

**Expected Result**:
- Status: `200 OK`
- Response: `{"status":"UP"}` or similar

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 5.2: Registration Endpoint Public

**Objective**: Verify registration works without authentication

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Charlie Brown",
    "email": "charlie@test.com",
    "password": "CharliePass123!"
  }'
```

**Expected Result**:
- Status: `201 CREATED`
- Returns JWT token

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 6: Error Handling

### Test 6.1: Invalid JSON Format

**Objective**: Verify API handles malformed JSON gracefully

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{invalid json here'
```

**Expected Result**:
- Status: `400 BAD REQUEST`
- Error message about invalid JSON

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 6.2: Missing Required Fields

**Objective**: Verify validation rejects incomplete requests

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@test.com"
  }'
```

**Expected Result**:
- Status: `400 BAD REQUEST`
- Validation errors for missing fields (name, password)

**Test Status**: ✅ PASS / ❌ FAIL

---

### Test 6.3: Invalid Email Format

**Objective**: Verify email validation

**Steps**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "not-an-email",
    "password": "Password123!"
  }'
```

**Expected Result**:
- Status: `400 BAD REQUEST`
- Error message about invalid email format

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Suite 7: Complete User Journey

### Test 7.1: Full User Lifecycle

**Objective**: Test complete user journey from registration to deletion

**Steps**:

1. **Register new user**:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Diana Prince",
    "email": "diana@test.com",
    "password": "DianaPass123!"
  }'
```
Save the token and user ID.

2. **Login**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "diana@test.com",
    "password": "DianaPass123!"
  }'
```

3. **View own profile**:
```bash
curl -X GET http://localhost:8080/api/users/<diana_id> \
  -H "Authorization: Bearer $TOKEN_DIANA"
```

4. **Update profile**:
```bash
curl -X PUT http://localhost:8080/api/users/<diana_id> \
  -H "Authorization: Bearer $TOKEN_DIANA" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Diana Prince-Wayne"
  }'
```

5. **Search for self**:
```bash
curl -X GET "http://localhost:8080/api/users?name=Diana" \
  -H "Authorization: Bearer $TOKEN_DIANA"
```

6. **Delete account**:
```bash
curl -X DELETE http://localhost:8080/api/users/<diana_id> \
  -H "Authorization: Bearer $TOKEN_DIANA"
```

7. **Verify deletion** (should fail):
```bash
curl -X GET http://localhost:8080/api/users/<diana_id> \
  -H "Authorization: Bearer $TOKEN_DIANA"
```

**Expected Results**:
- All steps succeed with appropriate status codes
- Final GET returns 404 NOT FOUND

**Test Status**: ✅ PASS / ❌ FAIL

---

## Test Results Summary

### Test Execution

Fill in after manual testing:

| Test Suite | Total | Pass | Fail | Notes |
|------------|-------|------|------|-------|
| Authentication Flow | 4 | | | |
| Protected Endpoints | 3 | | | |
| Authorization | 4 | | | |
| Token Validation | 3 | | | |
| Public Endpoints | 2 | | | |
| Error Handling | 3 | | | |
| Complete Journey | 1 | | | |
| **TOTAL** | **20** | | | |

---

## Issues Found

Document any issues discovered during manual testing:

1. **Issue #**: [Description]
   - **Severity**: Critical / High / Medium / Low
   - **Steps to Reproduce**: [Steps]
   - **Expected**: [Expected behavior]
   - **Actual**: [Actual behavior]
   - **Status**: Open / Fixed / Won't Fix

---

## Environment Details

- **Application Version**: 1.0-SNAPSHOT
- **Java Version**: 17
- **Spring Boot Version**: 3.2.1
- **Database**: MySQL 8.0 (user_management)
- **Testing Date**: February 9, 2026
- **Tester**: Testing & QA Lead

---

## Sign-off

**Manual Testing Completed By**: _______________________

**Date**: _______________________

**Status**: ✅ APPROVED / ⚠️ APPROVED WITH CONDITIONS / ❌ REJECTED

**Conditions/Notes**:
- [ ] All critical tests passing
- [ ] Known issues documented
- [ ] Ready for staging deployment
- [ ] Ready for production deployment

---

**Next Steps**:
1. Execute all manual test scenarios
2. Document results in this file
3. Address any critical issues found
4. Re-test after fixes
5. Final approval for deployment
