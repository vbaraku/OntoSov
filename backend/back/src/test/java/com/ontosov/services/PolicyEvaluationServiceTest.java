package com.ontosov.services;

import com.ontosov.dto.*;
import com.ontosov.models.User;
import com.ontosov.models.UserRole;
import com.ontosov.repositories.UserRepo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for PolicyEvaluationService
 *
 * Tests the Policy Decision Point (PDP) for correct access control decisions
 * across all policy enforcement scenarios.
 *
 * @author Vijon Baraku
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyEvaluationService Test Suite")
class PolicyEvaluationServiceTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private PolicyGroupService policyGroupService;

    @Mock
    private ODRLService odrlService;

    @Mock
    private DatabaseConfigService databaseConfigService;

    @InjectMocks
    private PolicyEvaluationService policyEvaluationService;

    // Test constants
    private static final String TEST_TAX_ID = "999999999";
    private static final Long TEST_SUBJECT_ID = 9999L;
    private static final Long TEST_CONTROLLER_ID = 9998L;
    private static final String TEST_CONTROLLER_NAME = "TestController";
    private static final String TEST_DATABASE_UUID = "test-db-uuid-12345";
    private static final String TEST_DATABASE_NAME = "testdb";
    private static final String TEST_DATA_SOURCE = TEST_CONTROLLER_NAME + " - " + TEST_DATABASE_NAME;
    private static final String TEST_TABLE_NAME = "users";
    private static final String TEST_DATA_PROPERTY = "email";
    private static final String TEST_SCHEMA_PROPERTY = "http://schema.org/email";
    private static final String TEST_ENTITY_ID = "http://example.org/resource#User/1";
    private static final String TEST_PURPOSE = "Service Provision";
    private static final String TEST_AI_ALGORITHM = "RandomForest";
    private static final String TEST_POLICY_GROUP_ID = "pg-test-12345";

    // Reusable test objects
    private User testSubject;
    private User testController;
    private DatabaseConfigDTO testDatabase;
    private PolicyGroupDTO testPolicyGroup;

    @BeforeEach
    void setUp() throws IOException {
        // Create test subject
        testSubject = new User();
        testSubject.setId(TEST_SUBJECT_ID);
        testSubject.setTaxid(TEST_TAX_ID);
        testSubject.setRole(UserRole.SUBJECT);
        testSubject.setEmail("subject@test.com");
        testSubject.setPasswordHash("hash");

        // Create test controller
        testController = new User();
        testController.setId(TEST_CONTROLLER_ID);
        testController.setName(TEST_CONTROLLER_NAME);
        testController.setRole(UserRole.CONTROLLER);
        testController.setEmail("controller@test.com");
        testController.setPasswordHash("hash");

        // Create test database config
        testDatabase = new DatabaseConfigDTO();
        testDatabase.setId(TEST_DATABASE_UUID);
        testDatabase.setDatabaseName(TEST_DATABASE_NAME);

        // Create default policy group
        testPolicyGroup = createDefaultPolicyGroup();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Creates an AccessRequestDTO for property-level access
     */
    private AccessRequestDTO createPropertyAccessRequest(String action, String purpose) {
        AccessRequestDTO request = new AccessRequestDTO();
        request.setSubjectTaxId(TEST_TAX_ID);
        request.setControllerId(TEST_CONTROLLER_ID);
        request.setDataSource(TEST_DATABASE_UUID);
        request.setTableName(TEST_TABLE_NAME);
        request.setDataProperty(TEST_DATA_PROPERTY);
        request.setAction(action);
        request.setPurpose(purpose);
        return request;
    }

    /**
     * Creates an AccessRequestDTO for entity-level access
     */
    private AccessRequestDTO createEntityAccessRequest(String action, String purpose) {
        AccessRequestDTO request = new AccessRequestDTO();
        request.setSubjectTaxId(TEST_TAX_ID);
        request.setControllerId(TEST_CONTROLLER_ID);
        request.setDataSource(TEST_DATABASE_UUID);
        request.setTableName(TEST_TABLE_NAME);
        request.setRecordId("1");
        request.setAction(action);
        request.setPurpose(purpose);
        return request;
    }

    /**
     * Creates a PolicyGroupDTO with specified permissions
     */
    private PolicyGroupDTO createPolicyGroup(Map<String, Boolean> permissions,
                                             Map<String, Object> constraints,
                                             Map<String, Object> aiRestrictions) {
        PolicyGroupDTO policy = new PolicyGroupDTO();
        policy.setId(TEST_POLICY_GROUP_ID);
        policy.setName("Test Policy");
        policy.setDescription("Test policy description");
        policy.setPermissions(permissions != null ? permissions : new HashMap<>());
        policy.setConstraints(constraints != null ? constraints : new HashMap<>());
        policy.setConsequences(new HashMap<>());
        policy.setAiRestrictions(aiRestrictions != null ? aiRestrictions : new HashMap<>());
        policy.setTransformations(new ArrayList<>());
        return policy;
    }

    /**
     * Creates a default policy group with all permissions enabled
     */
    private PolicyGroupDTO createDefaultPolicyGroup() {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", true);
        permissions.put("share", true);
        permissions.put("aggregate", true);
        permissions.put("modify", true);

        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", true);

        return createPolicyGroup(permissions, new HashMap<>(), aiRestrictions);
    }

    /**
     * Creates a policy group with only specific action permitted
     */
    private PolicyGroupDTO createSingleActionPolicy(String action, boolean permitted) {
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", false);
        permissions.put("use", false);
        permissions.put("share", false);
        permissions.put("aggregate", false);
        permissions.put("modify", false);

        if (!action.equals("aiTraining")) {
            permissions.put(action, permitted);
        }

        Map<String, Object> aiRestrictions = new HashMap<>();
        if (action.equals("aiTraining")) {
            aiRestrictions.put("allowAiTraining", permitted);
        }

        return createPolicyGroup(permissions, new HashMap<>(), aiRestrictions);
    }

    /**
     * Sets up common mocks for property access tests
     */
    private void setupPropertyAccessMocks() throws IOException {
        when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
        when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
        when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                .thenReturn(Collections.singletonList(testDatabase));
        when(databaseConfigService.resolveSchemaOrgProperty(
                eq(TEST_CONTROLLER_ID), eq(TEST_DATABASE_UUID), eq(TEST_TABLE_NAME), eq(TEST_DATA_PROPERTY)))
                .thenReturn(TEST_SCHEMA_PROPERTY);
    }

    /**
     * Sets up common mocks for entity access tests
     */
    private void setupEntityAccessMocks() throws IOException {
        when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
        when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
        when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                .thenReturn(Collections.singletonList(testDatabase));
        when(databaseConfigService.resolveEntityTypeFromTable(
                eq(TEST_CONTROLLER_ID), eq(TEST_DATABASE_UUID), eq(TEST_TABLE_NAME)))
                .thenReturn("User");
    }

    // ==================== CATEGORY 1: BASIC PERMISSION TESTS ====================

    @Nested
    @DisplayName("Category 1: Basic Permission Tests")
    class BasicPermissionTests {

        @ParameterizedTest
        @ValueSource(strings = {"read", "use", "share", "aggregate", "modify"})
        @DisplayName("Test: Action permitted should return PERMIT")
        void testActionPermitted_shouldReturnPermit(String action) throws IOException {
            // Setup
            setupPropertyAccessMocks();
            PolicyGroupDTO policy = createSingleActionPolicy(action, true);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY), eq(action)))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));

            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(TEST_POLICY_GROUP_ID), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest(action, TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Action '" + action + "' should be PERMITTED when policy allows it");
            assertNotNull(decision.getReason());
        }

        @ParameterizedTest
        @ValueSource(strings = {"read", "use", "share", "aggregate", "modify"})
        @DisplayName("Test: Action prohibited should return DENY")
        void testActionProhibited_shouldReturnDeny(String action) throws IOException {
            // Setup
            setupPropertyAccessMocks();

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY), eq(action)))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest(action, TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Action '" + action + "' should be DENIED when policy prohibits it");
            assertTrue(decision.getReason().contains("No policy permits"),
                    "Reason should indicate action not permitted");
        }

        @ParameterizedTest
        @ValueSource(strings = {"read", "use", "share", "aggregate", "modify"})
        @DisplayName("Test: No policy assigned should return PERMIT (default allow)")
        void testNoPolicyAssigned_shouldReturnPermit(String action) throws IOException {
            // Setup
            setupPropertyAccessMocks();

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest(action, TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Action '" + action + "' should be PERMITTED when no policy is assigned");
            assertTrue(decision.getReason().contains("No policy assigned"),
                    "Reason should indicate no policy assigned");
        }

        @Test
        @DisplayName("Test: AI Training permitted should return PERMIT")
        void testAiTrainingPermitted_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));

            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED when policy allows it");
        }

        @Test
        @DisplayName("Test: AI Training prohibited should return DENY")
        void testAiTrainingProhibited_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY), eq("aiTraining")))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "AI Training should be DENIED when policy prohibits it");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupPolicyAssignments(String policyGroupId) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }

    // ==================== CATEGORY 2: CONSTRAINT TESTS ====================

    @Nested
    @DisplayName("Category 2: Constraint Tests")
    class ConstraintTests {

        @Test
        @DisplayName("Test: Action permitted, purpose matches should return PERMIT")
        void testPurposeMatches_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", TEST_PURPOSE);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when purpose matches");
        }

        @Test
        @DisplayName("Test: Action permitted, purpose doesn't match should return DENY")
        void testPurposeDoesNotMatch_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Marketing");  // Policy requires Marketing
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - request with different purpose
            AccessRequestDTO request = createPropertyAccessRequest("read", "Service Provision");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when purpose doesn't match");
            assertTrue(decision.getReason().contains("constraints not satisfied"),
                    "Reason should mention constraints not satisfied");
        }

        @Test
        @DisplayName("Test: Action permitted, no purpose constraint should return PERMIT")
        void testNoPurposeConstraint_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", "Any Purpose");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when no purpose constraint exists");
        }

        @Test
        @DisplayName("Test: Action permitted, purpose required but not provided should return DENY")
        void testPurposeRequiredButNotProvided_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Service Provision");
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - request with null purpose
            AccessRequestDTO request = createPropertyAccessRequest("read", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when purpose is required but not provided");
        }

        @Test
        @DisplayName("Test: Action permitted, not expired should return PERMIT")
        void testNotExpired_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            // Set expiration to 1 year in the future
            String futureDate = LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            constraints.put("expiration", futureDate);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when policy is not expired");
        }

        @Test
        @DisplayName("Test: Action permitted, expired should return DENY")
        void testExpired_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            // Set expiration to 1 year in the past
            String pastDate = LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            constraints.put("expiration", pastDate);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when policy is expired");
        }

        @Test
        @DisplayName("Test: Action permitted, no expiration should return PERMIT")
        void testNoExpiration_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when no expiration is set");
        }

        @Test
        @DisplayName("Test: Action permitted, purpose matches, not expired should return PERMIT")
        void testPurposeMatchesAndNotExpired_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", TEST_PURPOSE);
            constraints.put("expiration", LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when purpose matches and not expired");
        }

        @Test
        @DisplayName("Test: Action permitted, purpose matches, expired should return DENY")
        void testPurposeMatchesButExpired_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", TEST_PURPOSE);
            constraints.put("expiration", LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when expired even if purpose matches");
        }

        @Test
        @DisplayName("Test: Action permitted, purpose wrong, not expired should return DENY")
        void testPurposeWrongNotExpired_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Marketing");
            constraints.put("expiration", LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", "Service Provision");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when purpose doesn't match even if not expired");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupPolicyAssignments(String policyGroupId) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }

    // ==================== CATEGORY 3: AI RESTRICTION TESTS ====================

    @Nested
    @DisplayName("Category 3: AI Restriction Tests")
    class AIRestrictionTests {

        @Test
        @DisplayName("Test: AI training permitted, no restrictions should return PERMIT")
        void testAiTrainingNoRestrictions_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED when allowed with no restrictions");
        }

        @Test
        @DisplayName("Test: AI training prohibited should return DENY")
        void testAiTrainingProhibited_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "AI Training should be DENIED when prohibited");
        }

        @Test
        @DisplayName("Test: AI training permitted, algorithm matches should return PERMIT")
        void testAiTrainingAlgorithmMatches_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            aiRestrictions.put("aiAlgorithm", TEST_AI_ALGORITHM);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            request.setAiAlgorithm(TEST_AI_ALGORITHM);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED when algorithm matches");
        }

        @Test
        @DisplayName("Test: AI training permitted, algorithm doesn't match should return DENY")
        void testAiTrainingAlgorithmDoesNotMatch_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            aiRestrictions.put("aiAlgorithm", "RandomForest");  // Required algorithm
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - using different algorithm
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            request.setAiAlgorithm("NeuralNetwork");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "AI Training should be DENIED when algorithm doesn't match");
        }

        @Test
        @DisplayName("Test: AI training permitted, no algorithm specified in request should return DENY when policy requires specific algorithm")
        void testAiTrainingNoAlgorithmSpecified_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            aiRestrictions.put("aiAlgorithm", "RandomForest");  // Required algorithm
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - no algorithm specified
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "AI Training should be DENIED when no algorithm specified but policy requires one");
        }

        @Test
        @DisplayName("Test: AI training allowed but no algorithm specified in policy should return PERMIT")
        void testAiTrainingNoAlgorithmInPolicy_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            // No specific algorithm required
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            request.setAiAlgorithm("AnyAlgorithm");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED when no specific algorithm required");
        }

        @Test
        @DisplayName("Test: Regular action (read) when AI training is prohibited should return PERMIT")
        void testReadWhenAiProhibited_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", false);  // AI training prohibited
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - reading should still work
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Read action should be PERMITTED even when AI training is prohibited");
        }

        @Test
        @DisplayName("Test: AI training with empty algorithm restriction should return PERMIT")
        void testAiTrainingEmptyAlgorithmRestriction_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            aiRestrictions.put("aiAlgorithm", "");  // Empty algorithm
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            request.setAiAlgorithm("AnyAlgorithm");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED when algorithm restriction is empty");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupPolicyAssignments(String policyGroupId) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }

    // ==================== CATEGORY 4: MULTIPLE POLICY TESTS ====================

    @Nested
    @DisplayName("Category 4: Multiple Policy Tests")
    class MultiplePolicyTests {

        @Test
        @DisplayName("Test: Two policies both PERMIT same action should return PERMIT")
        void testTwoPoliciesBothPermit_shouldReturnPermit() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            PolicyGroupDTO policy1 = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Policy 1");

            PolicyGroupDTO policy2 = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Policy 2");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2));

            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2"));

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED when both policies allow the action");
        }

        @Test
        @DisplayName("Test: One PERMIT, one DENY should return DENY (most restrictive wins)")
        void testOnePermitOneDeny_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            // Policy 1: Permits read
            PolicyGroupDTO policy1 = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Policy 1");

            // Policy 2: Denies read (purpose constraint that won't match)
            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Marketing");  // Different from request purpose
            PolicyGroupDTO policy2 = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Policy 2");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2));

            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2"));

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", "Service Provision");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when any policy denies (most restrictive wins)");
        }

        @Test
        @DisplayName("Test: Two policies PERMIT different actions should work for each")
        void testTwoPoliciesPermitDifferentActions() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            // Policy 1: Permits only read
            Map<String, Boolean> perms1 = new HashMap<>();
            perms1.put("read", true);
            perms1.put("use", false);
            perms1.put("share", false);
            perms1.put("aggregate", false);
            perms1.put("modify", false);
            PolicyGroupDTO policy1 = createPolicyGroup(perms1, new HashMap<>(), new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Policy 1");

            // Policy 2: Permits only use
            Map<String, Boolean> perms2 = new HashMap<>();
            perms2.put("read", false);
            perms2.put("use", true);
            perms2.put("share", false);
            perms2.put("aggregate", false);
            perms2.put("modify", false);
            PolicyGroupDTO policy2 = createPolicyGroup(perms2, new HashMap<>(), new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Policy 2");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2));
            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2"));

            // Test read action
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);

            AccessRequestDTO readRequest = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO readDecision = policyEvaluationService.evaluateAccess(readRequest);

            assertEquals(DecisionResult.PERMIT, readDecision.getResult(),
                    "Read should be PERMITTED by policy 1");

            // Test use action
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("use")))
                    .thenReturn(true);

            AccessRequestDTO useRequest = createPropertyAccessRequest("use", TEST_PURPOSE);
            PolicyDecisionDTO useDecision = policyEvaluationService.evaluateAccess(useRequest);

            assertEquals(DecisionResult.PERMIT, useDecision.getResult(),
                    "Use should be PERMITTED by policy 2");
        }

        @Test
        @DisplayName("Test: Policy 1 permits with purpose A, Policy 2 permits with purpose B")
        void testDifferentPurposeConstraints() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            // Policy 1: Requires Service Provision
            Map<String, Object> constraints1 = new HashMap<>();
            constraints1.put("purpose", "Service Provision");
            PolicyGroupDTO policy1 = createPolicyGroup(createAllPermissions(), constraints1, new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Policy 1");

            // Policy 2: Requires Marketing
            Map<String, Object> constraints2 = new HashMap<>();
            constraints2.put("purpose", "Marketing");
            PolicyGroupDTO policy2 = createPolicyGroup(createAllPermissions(), constraints2, new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Policy 2");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2));
            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2"));

            // Test with Service Provision - should fail because Policy 2 won't match
            AccessRequestDTO request1 = createPropertyAccessRequest("read", "Service Provision");
            PolicyDecisionDTO decision1 = policyEvaluationService.evaluateAccess(request1);

            assertEquals(DecisionResult.DENY, decision1.getResult(),
                    "Should be DENIED because Policy 2 requires Marketing purpose");

            // Test with Marketing - should fail because Policy 1 won't match
            AccessRequestDTO request2 = createPropertyAccessRequest("read", "Marketing");
            PolicyDecisionDTO decision2 = policyEvaluationService.evaluateAccess(request2);

            assertEquals(DecisionResult.DENY, decision2.getResult(),
                    "Should be DENIED because Policy 1 requires Service Provision purpose");
        }

        @Test
        @DisplayName("Test: Multiple policies with different expirations - one expired")
        void testMultiplePoliciesDifferentExpirations() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            // Policy 1: Not expired
            Map<String, Object> constraints1 = new HashMap<>();
            constraints1.put("expiration", LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy1 = createPolicyGroup(createAllPermissions(), constraints1, new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Valid Policy");

            // Policy 2: Expired
            Map<String, Object> constraints2 = new HashMap<>();
            constraints2.put("expiration", LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy2 = createPolicyGroup(createAllPermissions(), constraints2, new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Expired Policy");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2));
            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2"));

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert - should deny because one policy is expired (most restrictive wins)
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED because one policy is expired");
        }

        @Test
        @DisplayName("Test: Three policies - two permit, one denies should return DENY")
        void testThreePoliciesTwoPermitOneDenies_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            PolicyGroupDTO policy1 = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());
            policy1.setId("pg-1");
            policy1.setName("Policy 1");

            PolicyGroupDTO policy2 = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());
            policy2.setId("pg-2");
            policy2.setName("Policy 2");

            // Policy 3: Has expired
            Map<String, Object> constraints3 = new HashMap<>();
            constraints3.put("expiration", LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy3 = createPolicyGroup(createAllPermissions(), constraints3, new HashMap<>());
            policy3.setId("pg-3");
            policy3.setName("Expired Policy");

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Arrays.asList(policy1, policy2, policy3));
            setupMultiplePolicyAssignments(Arrays.asList("pg-1", "pg-2", "pg-3"));

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when even one policy denies access");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupMultiplePolicyAssignments(List<String> policyIds) {
            for (String policyId : policyIds) {
                Map<String, Object> assignments = new HashMap<>();
                Map<String, Set<String>> propertyAssignments = new HashMap<>();
                propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
                assignments.put("propertyAssignments", propertyAssignments);
                assignments.put("entityAssignments", new HashMap<>());

                when(odrlService.getAssignmentsForPolicyGroup(eq(policyId), eq(TEST_SUBJECT_ID)))
                        .thenReturn(assignments);
            }
        }
    }

    // ==================== CATEGORY 5: ENTITY-LEVEL PRIVACY TESTS ====================

    @Nested
    @DisplayName("Category 5: Entity-Level Privacy Tests")
    class EntityLevelPrivacyTests {

        @Test
        @DisplayName("Test: Entity policy permits action should return PERMIT")
        void testEntityPolicyPermits_shouldReturnPermit() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(entityUri), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupEntityPolicyAssignments(policy.getId(), entityUri);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Entity access should be PERMITTED when policy allows");
        }

        @Test
        @DisplayName("Test: Entity policy denies action should return DENY")
        void testEntityPolicyDenies_shouldReturnDeny() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(entityUri), eq("read")))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Entity access should be DENIED when policy prohibits");
        }

        @Test
        @DisplayName("Test: No entity policy assigned should return PERMIT")
        void testNoEntityPolicy_shouldReturnPermit() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Entity access should be PERMITTED when no policy exists");
            assertTrue(decision.getReason().contains("No policy assigned"),
                    "Reason should indicate no policy assigned");
        }

        @Test
        @DisplayName("Test: Entity access with purpose constraint")
        void testEntityAccessWithPurposeConstraint() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", TEST_PURPOSE);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupEntityPolicyAssignments(policy.getId(), entityUri);

            // Execute with matching purpose
            AccessRequestDTO request = createEntityAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Entity access should be PERMITTED when purpose matches");
        }

        @Test
        @DisplayName("Test: Entity access with expired policy")
        void testEntityAccessExpiredPolicy() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("expiration", LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupEntityPolicyAssignments(policy.getId(), entityUri);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Entity access should be DENIED when policy is expired");
        }

        @Test
        @DisplayName("Test: Entity AI training access")
        void testEntityAiTrainingAccess() throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), aiRestrictions);

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupEntityPolicyAssignments(policy.getId(), entityUri);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Entity AI training should be PERMITTED when allowed");
        }

        @ParameterizedTest
        @ValueSource(strings = {"read", "use", "share", "aggregate", "modify"})
        @DisplayName("Test: Entity access for various actions")
        void testEntityAccessVariousActions(String action) throws IOException {
            // Setup
            setupEntityAccessMocks();

            String entityUri = "http://example.org/resource#User/1";
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), new HashMap<>(), new HashMap<>());

            when(odrlService.policyExistsForEntity(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(entityUri)))
                    .thenReturn(true);
            when(odrlService.checkEntityAccess(eq(TEST_SUBJECT_ID), eq(TEST_CONTROLLER_ID),
                    eq(TEST_DATA_SOURCE), eq(entityUri), eq(action)))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupEntityPolicyAssignments(policy.getId(), entityUri);

            // Execute
            AccessRequestDTO request = createEntityAccessRequest(action, TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Entity action '" + action + "' should be PERMITTED when policy allows");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupEntityPolicyAssignments(String policyGroupId, String entityUri) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> entityAssignments = new HashMap<>();
            entityAssignments.put(TEST_DATA_SOURCE, Collections.singleton(entityUri));
            assignments.put("propertyAssignments", new HashMap<>());
            assignments.put("entityAssignments", entityAssignments);

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }

    // ==================== CATEGORY 6: EDGE CASES ====================

    @Nested
    @DisplayName("Category 6: Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Test: Subject doesn't exist should return DENY")
        void testSubjectNotFound_shouldReturnDeny() throws IOException {
            // Setup
            when(userRepo.findByTaxid("nonexistent")).thenReturn(null);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            request.setSubjectTaxId("nonexistent");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when subject doesn't exist");
            assertTrue(decision.getReason().contains("Subject not found"),
                    "Reason should indicate subject not found");
        }

        @Test
        @DisplayName("Test: Controller doesn't exist should return DENY")
        void testControllerNotFound_shouldReturnDeny() throws IOException {
            // Setup
            when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
            when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.empty());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when controller doesn't exist");
            assertTrue(decision.getReason().contains("Controller not found"),
                    "Reason should indicate controller not found");
        }

        @Test
        @DisplayName("Test: DataSource doesn't exist should return DENY")
        void testDatabaseNotFound_shouldReturnDeny() throws IOException {
            // Setup
            when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
            when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
            when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                    .thenReturn(Collections.emptyList());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when database doesn't exist");
            assertTrue(decision.getReason().contains("Database not found"),
                    "Reason should indicate database not found");
        }

        @Test
        @DisplayName("Test: Null subject tax ID should return DENY")
        void testNullSubjectTaxId_shouldReturnDeny() {
            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            request.setSubjectTaxId(null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when subject tax ID is null");
            assertTrue(decision.getReason().contains("Invalid request"),
                    "Reason should indicate invalid request");
        }

        @Test
        @DisplayName("Test: Null action should return DENY")
        void testNullAction_shouldReturnDeny() {
            // Execute
            AccessRequestDTO request = createPropertyAccessRequest(null, TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when action is null");
            assertTrue(decision.getReason().contains("Invalid request"),
                    "Reason should indicate invalid request");
        }

        @Test
        @DisplayName("Test: Neither dataProperty nor recordId specified should return DENY")
        void testNoDataPropertyOrRecordId_shouldReturnDeny() {
            // Execute
            AccessRequestDTO request = new AccessRequestDTO();
            request.setSubjectTaxId(TEST_TAX_ID);
            request.setControllerId(TEST_CONTROLLER_ID);
            request.setAction("read");
            // Neither dataProperty nor recordId set
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when neither dataProperty nor recordId specified");
            assertTrue(decision.getReason().contains("must specify either dataProperty"),
                    "Reason should indicate missing property");
        }

        @Test
        @DisplayName("Test: Both dataProperty and recordId specified should return DENY")
        void testBothDataPropertyAndRecordId_shouldReturnDeny() {
            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            request.setRecordId("1");  // Also set recordId
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when both dataProperty and recordId specified");
            assertTrue(decision.getReason().contains("cannot specify both"),
                    "Reason should indicate cannot specify both");
        }

        @Test
        @DisplayName("Test: Unmapped property should return PERMIT (default)")
        void testUnmappedProperty_shouldReturnPermit() throws IOException {
            // Setup
            when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
            when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
            when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                    .thenReturn(Collections.singletonList(testDatabase));
            when(databaseConfigService.resolveSchemaOrgProperty(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(null);  // Unmapped

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should be PERMITTED for unmapped data");
            assertTrue(decision.getReason().contains("Unmapped"),
                    "Reason should indicate unmapped data");
        }

        @Test
        @DisplayName("Test: Empty purpose string with purpose constraint should return DENY")
        void testEmptyPurposeString_shouldReturnDeny() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Service Provision");
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute with empty purpose
            AccessRequestDTO request = createPropertyAccessRequest("read", "");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when purpose is empty but required");
        }

        @Test
        @DisplayName("Test: Purpose not required for aiTraining action")
        void testAiTrainingNoPurposeRequired() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Service Provision");  // Has purpose constraint

            Map<String, Object> aiRestrictions = new HashMap<>();
            aiRestrictions.put("allowAiTraining", true);

            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, aiRestrictions);

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("aiTraining")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute - aiTraining with no purpose (should be allowed)
            AccessRequestDTO request = createPropertyAccessRequest("aiTraining", null);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "AI Training should be PERMITTED even without purpose");
        }

        @Test
        @DisplayName("Test: Very long purpose string should handle gracefully")
        void testVeryLongPurposeString() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "Service");
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute with very long purpose that contains "Service"
            String longPurpose = "Service " + "x".repeat(10000);
            AccessRequestDTO request = createPropertyAccessRequest("read", longPurpose);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert - should handle gracefully and permit since it contains "Service"
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "Should handle very long purpose string gracefully");
        }

        @Test
        @DisplayName("Test: Special characters in dataProperty should handle gracefully")
        void testSpecialCharactersInDataProperty() throws IOException {
            // Setup
            when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
            when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
            when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                    .thenReturn(Collections.singletonList(testDatabase));
            when(databaseConfigService.resolveSchemaOrgProperty(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn("http://schema.org/special_prop");

            String specialProperty = "http://schema.org/special_prop";
            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(specialProperty)))
                    .thenReturn(false);

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            request.setDataProperty("special@prop#with$chars");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert - should handle gracefully
            assertNotNull(decision, "Should return a decision even with special characters");
        }

        @Test
        @DisplayName("Test: IOException during schema resolution should return DENY")
        void testSchemaResolutionIOException() throws IOException {
            // Setup
            when(userRepo.findByTaxid(TEST_TAX_ID)).thenReturn(testSubject);
            when(userRepo.findById(TEST_CONTROLLER_ID)).thenReturn(Optional.of(testController));
            when(databaseConfigService.getDatabasesForController(TEST_CONTROLLER_ID))
                    .thenReturn(Collections.singletonList(testDatabase));
            when(databaseConfigService.resolveSchemaOrgProperty(anyLong(), anyString(), anyString(), anyString()))
                    .thenThrow(new IOException("Test IO Error"));

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when IO exception occurs");
            assertTrue(decision.getReason().contains("Error resolving mappings"),
                    "Reason should indicate mapping error");
        }

        @Test
        @DisplayName("Test: Policy exists but no groups returned should return DENY")
        void testPolicyExistsButNoGroups() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.emptyList());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.DENY, decision.getResult(),
                    "Should be DENIED when policy exists but group details unavailable");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupPolicyAssignments(String policyGroupId) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }

    // ==================== ADDITIONAL INTEGRATION-STYLE TESTS ====================

    @Nested
    @DisplayName("Additional Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Test: Complete permit flow with obligations")
        void testCompletePermitFlowWithObligations() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("requiresNotification", true);

            Map<String, Object> consequences = new HashMap<>();
            consequences.put("notificationType", "email");
            consequences.put("compensationAmount", "10.00");

            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());
            policy.setConsequences(consequences);
            policy.setTransformations(Arrays.asList("anonymize", "encrypt"));

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute
            AccessRequestDTO request = createPropertyAccessRequest("read", TEST_PURPOSE);
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert
            assertEquals(DecisionResult.PERMIT, decision.getResult());
            assertNotNull(decision.getObligations());
            assertTrue(decision.getObligations().size() > 0,
                    "Should have obligations for notification, compensation, and transformations");
        }

        @Test
        @DisplayName("Test: DPV URI purpose should match")
        void testDpvUriPurposeMatching() throws IOException {
            // Setup
            setupPropertyAccessMocks();

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("purpose", "https://w3id.org/dpv#ServiceProvision");
            PolicyGroupDTO policy = createPolicyGroup(createAllPermissions(), constraints, new HashMap<>());

            when(odrlService.policyExistsForProperty(eq(TEST_SUBJECT_ID), eq(TEST_DATA_SOURCE), eq(TEST_SCHEMA_PROPERTY)))
                    .thenReturn(true);
            when(odrlService.checkPropertyAccess(anyLong(), anyLong(), anyString(), anyString(), eq("read")))
                    .thenReturn(true);
            when(policyGroupService.getPolicyGroupsBySubject(TEST_SUBJECT_ID))
                    .thenReturn(Collections.singletonList(policy));
            setupPolicyAssignments(policy.getId());

            // Execute with label that maps to the URI
            AccessRequestDTO request = createPropertyAccessRequest("read", "Service Provision");
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // Assert - should match because DPVPurpose handles the conversion
            assertEquals(DecisionResult.PERMIT, decision.getResult(),
                    "DPV URI and label should match via normalization");
        }

        private Map<String, Boolean> createAllPermissions() {
            Map<String, Boolean> permissions = new HashMap<>();
            permissions.put("read", true);
            permissions.put("use", true);
            permissions.put("share", true);
            permissions.put("aggregate", true);
            permissions.put("modify", true);
            return permissions;
        }

        private void setupPolicyAssignments(String policyGroupId) {
            Map<String, Object> assignments = new HashMap<>();
            Map<String, Set<String>> propertyAssignments = new HashMap<>();
            propertyAssignments.put(TEST_DATA_SOURCE, Collections.singleton(TEST_SCHEMA_PROPERTY));
            assignments.put("propertyAssignments", propertyAssignments);
            assignments.put("entityAssignments", new HashMap<>());

            when(odrlService.getAssignmentsForPolicyGroup(eq(policyGroupId), eq(TEST_SUBJECT_ID)))
                    .thenReturn(assignments);
        }
    }
}
