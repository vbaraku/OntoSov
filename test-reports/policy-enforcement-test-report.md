# Policy Enforcement Test Suite Report

**Date:** 2025-11-24
**Author:** Vijon Baraku
**Test Class:** `PolicyEvaluationServiceTest.java`
**Location:** `src/test/java/com/ontosov/services/PolicyEvaluationServiceTest.java`

---

## Executive Summary

A comprehensive test suite has been created for the `PolicyEvaluationService` - the Policy Decision Point (PDP) responsible for making access control decisions in the OntoSov framework.

### Test Statistics

| Category | Test Count | Description |
|----------|------------|-------------|
| Category 1: Basic Permission Tests | 12 | Tests for each action (read, use, share, aggregate, modify, aiTraining) with permit/deny scenarios |
| Category 2: Constraint Tests | 10 | Purpose and expiration constraint validation |
| Category 3: AI Restriction Tests | 8 | AI training permission and algorithm restrictions |
| Category 4: Multiple Policy Tests | 5 | Multi-policy evaluation with "most restrictive wins" logic |
| Category 5: Entity-Level Privacy Tests | 8 | Row/record level access control |
| Category 6: Edge Cases | 14 | Error handling and boundary conditions |
| Integration Tests | 2 | End-to-end flows with obligations |
| **Total** | **59** | Full coverage of policy evaluation logic |

---

## Test Categories Detail

### Category 1: Basic Permission Tests

Tests the fundamental permit/deny logic for each action type.

| Test Name | Action | Expected Result |
|-----------|--------|-----------------|
| `testActionPermitted_shouldReturnPermit` (parameterized) | read, use, share, aggregate, modify | PERMIT |
| `testActionProhibited_shouldReturnDeny` (parameterized) | read, use, share, aggregate, modify | DENY |
| `testNoPolicyAssigned_shouldReturnPermit` (parameterized) | read, use, share, aggregate, modify | PERMIT |
| `testAiTrainingPermitted_shouldReturnPermit` | aiTraining | PERMIT |
| `testAiTrainingProhibited_shouldReturnDeny` | aiTraining | DENY |

**Key Findings:**
- Default behavior (no policy): PERMIT
- Explicit prohibition: DENY
- Explicit permission: PERMIT

---

### Category 2: Constraint Tests

Tests purpose and expiration constraint validation.

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testPurposeMatches_shouldReturnPermit` | Purpose matches policy requirement | PERMIT |
| `testPurposeDoesNotMatch_shouldReturnDeny` | Purpose mismatch | DENY |
| `testNoPurposeConstraint_shouldReturnPermit` | No constraint set | PERMIT |
| `testPurposeRequiredButNotProvided_shouldReturnDeny` | Missing required purpose | DENY |
| `testNotExpired_shouldReturnPermit` | Valid policy | PERMIT |
| `testExpired_shouldReturnDeny` | Expired policy | DENY |
| `testNoExpiration_shouldReturnPermit` | No expiration set | PERMIT |
| `testPurposeMatchesAndNotExpired_shouldReturnPermit` | Both constraints valid | PERMIT |
| `testPurposeMatchesButExpired_shouldReturnDeny` | Expired with valid purpose | DENY |
| `testPurposeWrongNotExpired_shouldReturnDeny` | Wrong purpose, not expired | DENY |

**Key Findings:**
- Purpose constraint skipped for aiTraining action
- Expiration always checked regardless of action
- Both constraints must pass for PERMIT

---

### Category 3: AI Restriction Tests

Tests AI training specific restrictions.

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testAiTrainingNoRestrictions_shouldReturnPermit` | AI allowed, no algorithm | PERMIT |
| `testAiTrainingProhibited_shouldReturnDeny` | AI explicitly prohibited | DENY |
| `testAiTrainingAlgorithmMatches_shouldReturnPermit` | Algorithm matches policy | PERMIT |
| `testAiTrainingAlgorithmDoesNotMatch_shouldReturnDeny` | Algorithm mismatch | DENY |
| `testAiTrainingNoAlgorithmSpecified_shouldReturnDeny` | Required algorithm not provided | DENY |
| `testAiTrainingNoAlgorithmInPolicy_shouldReturnPermit` | No algorithm restriction | PERMIT |
| `testReadWhenAiProhibited_shouldReturnPermit` | Non-AI action unaffected | PERMIT |
| `testAiTrainingEmptyAlgorithmRestriction_shouldReturnPermit` | Empty string treated as no restriction | PERMIT |

**Key Findings:**
- AI restrictions only apply to aiTraining action
- Algorithm matching is case-insensitive
- Empty/null algorithm in policy = no restriction

---

### Category 4: Multiple Policy Tests

Tests multi-policy evaluation and "most restrictive wins" logic.

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testTwoPoliciesBothPermit_shouldReturnPermit` | All policies permit | PERMIT |
| `testOnePermitOneDeny_shouldReturnDeny` | Mixed decisions | DENY |
| `testTwoPoliciesPermitDifferentActions` | Different actions allowed | Varies by action |
| `testDifferentPurposeConstraints` | Conflicting purpose requirements | DENY |
| `testMultiplePoliciesDifferentExpirations` | One expired policy | DENY |
| `testThreePoliciesTwoPermitOneDenies_shouldReturnDeny` | Majority permit, one deny | DENY |

**Key Findings:**
- **Most Restrictive Wins**: Any DENY = Final DENY
- All applicable policies are evaluated
- Policy conflicts result in DENY

---

### Category 5: Entity-Level Privacy Tests

Tests row/record-level access control.

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testEntityPolicyPermits_shouldReturnPermit` | Policy permits | PERMIT |
| `testEntityPolicyDenies_shouldReturnDeny` | Policy denies | DENY |
| `testNoEntityPolicy_shouldReturnPermit` | No policy (default) | PERMIT |
| `testEntityAccessWithPurposeConstraint` | Purpose constraint | PERMIT |
| `testEntityAccessExpiredPolicy` | Expired policy | DENY |
| `testEntityAiTrainingAccess` | AI training on entity | PERMIT |
| `testEntityAccessVariousActions` (parameterized) | All actions | PERMIT |

**Key Findings:**
- Entity-level follows same logic as property-level
- Entity URIs constructed from table name + record ID
- Same constraint validation applies

---

### Category 6: Edge Cases

Tests error handling and boundary conditions.

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testSubjectNotFound_shouldReturnDeny` | Invalid subject | DENY |
| `testControllerNotFound_shouldReturnDeny` | Invalid controller | DENY |
| `testDatabaseNotFound_shouldReturnDeny` | Invalid database | DENY |
| `testNullSubjectTaxId_shouldReturnDeny` | Null tax ID | DENY |
| `testNullAction_shouldReturnDeny` | Null action | DENY |
| `testNoDataPropertyOrRecordId_shouldReturnDeny` | Missing both | DENY |
| `testBothDataPropertyAndRecordId_shouldReturnDeny` | Both specified | DENY |
| `testUnmappedProperty_shouldReturnPermit` | Unmapped schema property | PERMIT |
| `testEmptyPurposeString_shouldReturnDeny` | Empty purpose with constraint | DENY |
| `testAiTrainingNoPurposeRequired` | aiTraining skips purpose | PERMIT |
| `testVeryLongPurposeString` | String handling | PERMIT |
| `testSpecialCharactersInDataProperty` | Special chars | Handled |
| `testSchemaResolutionIOException` | IO error | DENY |
| `testPolicyExistsButNoGroups` | Policy without groups | DENY |

**Key Findings:**
- Defensive validation on all inputs
- Unmapped data gets PERMIT by default
- I/O errors result in DENY for safety

---

## Integration Tests

| Test Name | Scenario | Expected Result |
|-----------|----------|-----------------|
| `testCompletePermitFlowWithObligations` | Full flow with duties | PERMIT + Obligations |
| `testDpvUriPurposeMatching` | DPV URI normalization | PERMIT |

---

## Test Architecture

### Mocking Strategy

The test suite uses Mockito to isolate the `PolicyEvaluationService` from its dependencies:

```java
@Mock UserRepo userRepo;
@Mock PolicyGroupService policyGroupService;
@Mock ODRLService odrlService;
@Mock DatabaseConfigService databaseConfigService;
```

### Key Helper Methods

| Method | Purpose |
|--------|---------|
| `createPropertyAccessRequest()` | Creates property-level access request |
| `createEntityAccessRequest()` | Creates entity-level access request |
| `createPolicyGroup()` | Creates policy with custom permissions |
| `createSingleActionPolicy()` | Creates policy for one action only |
| `setupPropertyAccessMocks()` | Common mock setup for property tests |
| `setupEntityAccessMocks()` | Common mock setup for entity tests |

---

## Policy Decision Logic Summary

Based on the test suite, the policy evaluation follows this logic:

```
1. Validate Request Fields
   - Subject tax ID required
   - Action required
   - Either dataProperty OR recordId (not both)

2. Resolve Identifiers
   - Find subject by tax ID
   - Find controller by ID
   - Resolve database UUID to name
   - Map column to Schema.org property

3. Check Policy Existence
   - No policy → PERMIT (default allow)
   - Policy exists → Continue evaluation

4. Evaluate All Applicable Policies
   For each policy:
   a. Check action permission (ODRL query)
   b. Check constraints (purpose, expiration)
   c. Check AI restrictions (if aiTraining action)

5. Apply "Most Restrictive Wins"
   - Any DENY → Final DENY
   - All PERMIT → Final PERMIT

6. Collect Obligations (on PERMIT)
   - Notification duties
   - Compensation amounts
   - Transformation requirements (anonymize, pseudonymize, encrypt)
```

---

## Running the Tests

### Prerequisites
- JDK 21+
- Maven 3.9+
- Active network connection (for dependency resolution)

### Commands

```bash
# Run all PolicyEvaluationService tests
cd backend/back
mvn test -Dtest=PolicyEvaluationServiceTest

# Run specific category
mvn test -Dtest=PolicyEvaluationServiceTest\$BasicPermissionTests
mvn test -Dtest=PolicyEvaluationServiceTest\$ConstraintTests
mvn test -Dtest=PolicyEvaluationServiceTest\$AIRestrictionTests
mvn test -Dtest=PolicyEvaluationServiceTest\$MultiplePolicyTests
mvn test -Dtest=PolicyEvaluationServiceTest\$EntityLevelPrivacyTests
mvn test -Dtest=PolicyEvaluationServiceTest\$EdgeCaseTests

# Generate test report
mvn surefire-report:report
```

---

## Known Limitations

1. **Mocked Infrastructure**: Tests use mocked services, not actual triplestore
2. **Network Dependency**: Maven requires network for initial dependency download
3. **Blockchain Not Tested**: Blockchain recording is not covered in unit tests

---

## Recommendations

1. **Add Integration Tests**: Create tests with actual TDB2 triplestore
2. **Performance Tests**: Add load testing for policy evaluation
3. **Security Tests**: Add tests for injection vulnerabilities in SPARQL queries
4. **Test Coverage**: Aim for >90% line coverage with JaCoCo

---

## Conclusion

The test suite provides comprehensive coverage of the PolicyEvaluationService, validating:
- All 6 action types (read, use, share, aggregate, modify, aiTraining)
- Purpose and expiration constraints
- AI training restrictions with algorithm matching
- Multi-policy evaluation with "most restrictive wins"
- Both property-level and entity-level access control
- Edge cases and error handling

The tests are designed to be fast (using mocks) and maintainable (using parameterized tests where appropriate).
