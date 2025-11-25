package com.ontosov.performance;

import com.ontosov.dto.PolicyAssignmentDTO;
import com.ontosov.dto.PolicyGroupDTO;
import com.ontosov.services.ODRLService;
import com.ontosov.services.OntopService;
import com.ontosov.services.PolicyGroupService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Policy Performance Test Suite for OntoSoV
 *
 * This test suite measures REAL policy operations using actual federated data:
 * - Entity policy assignment performance (assigning policies to N real orders)
 * - Policy modification performance (updating policies covering N entities)
 * - Policy deletion performance (removing policies from N entities)
 * - Page load with policy metadata (federation time + policy lookup time)
 *
 * Test Scales:
 * - TST000010: ~78 items (Small)
 * - TST000100: ~558 items (Medium)
 * - TST001000: ~4,308 items (Large)
 * - TST010000: ~41,508 items (XLarge)
 *
 * Prerequisites:
 * - Test data must be populated using the SQL scripts in test-data/
 * - ecommerce_db (PostgreSQL) with order_history table containing test orders
 * - health_research_center (MySQL) with medical_records table
 * - Triplestore must be accessible
 *
 * @author Vijon Baraku
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("OntoSov Policy Performance Test Suite - Real Data")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyPerformanceTest {

    @Autowired
    private PolicyGroupService policyGroupService;

    @Autowired
    private ODRLService odrlService;

    @Autowired
    private OntopService ontopService;

    private static final Long CONTROLLER_ID = 8L;
    private static final String RESULTS_DIR = "test-results";
    private static final String RESULTS_FILE = RESULTS_DIR + "/policy-performance.csv";

    // Test subjects with different data scales
    // Format: taxId -> expected approximate item count
    private static final Map<String, Integer> TEST_SUBJECTS = new LinkedHashMap<>();
    static {
        TEST_SUBJECTS.put("TST000010", 78);      // Small scale
        TEST_SUBJECTS.put("TST000100", 558);     // Medium scale
        TEST_SUBJECTS.put("TST001000", 4308);    // Large scale
        TEST_SUBJECTS.put("TST010000", 41508);   // XLarge scale
    }

    // Data source identifiers - need to match format used in policy storage
    // Format: "Controller Name - database_name"
    // These are populated dynamically based on actual controller setup
    private String ecommerceDataSource = null;
    private String healthcareDataSource = null;

    // Warmup and measurement runs
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 3;

    // Track created policy groups for cleanup
    private static final List<String> createdPolicyGroups = Collections.synchronizedList(new ArrayList<>());

    // Cache for fetched entity URIs (to avoid re-querying federation)
    private static final Map<String, List<String>> entityUriCache = new HashMap<>();

    // Subject ID cache (taxId -> subjectId in database)
    // Populated dynamically by querying the database
    private static final Map<String, Long> subjectIdCache = new HashMap<>();

    @Autowired
    private com.ontosov.repositories.UserRepo userRepo;

    @BeforeAll
    static void setupResultsDirectory() throws IOException {
        Path resultsPath = Paths.get(RESULTS_DIR);
        if (!Files.exists(resultsPath)) {
            Files.createDirectories(resultsPath);
        }

        Path csvPath = Paths.get(RESULTS_FILE);
        if (!Files.exists(csvPath)) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(RESULTS_FILE))) {
                writer.println("timestamp,test_type,scale,tax_id,num_items,duration_ms,items_per_sec,metric,notes");
            }
        }
    }

    @AfterEach
    void cleanupPolicies() {
        System.out.println("Cleaning up " + createdPolicyGroups.size() + " policy groups...");
        for (String policyGroupId : new ArrayList<>(createdPolicyGroups)) {
            try {
                // Try each cached subject ID since we don't track which one owns which policy
                for (Long subjectId : subjectIdCache.values()) {
                    try {
                        policyGroupService.deletePolicyGroup(policyGroupId, subjectId);
                        break; // Success, move to next policy
                    } catch (Exception ignored) {
                        // Try next subject
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to delete policy group " + policyGroupId + ": " + e.getMessage());
            }
        }
        createdPolicyGroups.clear();
        System.out.println("Cleanup complete");
    }

    // ==================== HELPER: GET SUBJECT ID ====================

    /**
     * Gets the subject ID from the database for a given tax ID.
     * Caches the result for reuse.
     */
    private Long getSubjectId(String taxId) {
        // Check cache first
        if (subjectIdCache.containsKey(taxId)) {
            return subjectIdCache.get(taxId);
        }

        // Query database
        com.ontosov.models.User user = userRepo.findByTaxid(taxId);
        if (user == null) {
            System.err.println("WARNING: No user found for taxId: " + taxId);
            System.err.println("Make sure test data is populated!");
            return null;
        }

        Long subjectId = user.getId();
        subjectIdCache.put(taxId, subjectId);
        System.out.println("Resolved taxId " + taxId + " -> subjectId " + subjectId);

        return subjectId;
    }

    /**
     * Gets the data source identifier in the format used for policy storage.
     * Format: "Controller Name - database_name"
     */
    private String getDataSourceIdentifier(String databaseName) {
        if (ecommerceDataSource == null) {
            // Look up controller name from database
            com.ontosov.models.User controller = userRepo.findById(CONTROLLER_ID).orElse(null);
            if (controller != null) {
                String controllerName = controller.getName();
                ecommerceDataSource = controllerName + " - ecommerce_db";
                healthcareDataSource = controllerName + " - health_research_center";
                System.out.println("Resolved data source identifiers:");
                System.out.println("  Ecommerce: " + ecommerceDataSource);
                System.out.println("  Healthcare: " + healthcareDataSource);
            } else {
                // Fallback to simple names if controller not found
                System.err.println("WARNING: Controller " + CONTROLLER_ID + " not found, using simple database names");
                ecommerceDataSource = "ecommerce_db";
                healthcareDataSource = "health_research_center";
            }
        }

        if ("ecommerce_db".equals(databaseName)) {
            return ecommerceDataSource;
        } else if ("health_research_center".equals(databaseName)) {
            return healthcareDataSource;
        }

        return databaseName;
    }

    // ==================== HELPER: FETCH REAL ENTITY URIs ====================

    /**
     * Fetches real entity URIs from federated data for a given subject.
     * This queries the actual database through Ontop to get real Order URIs.
     *
     * @param taxId The subject's tax ID
     * @return List of entity URIs (e.g., "http://example.org/resource#Order/12345")
     */
    private List<String> fetchRealEntityUris(String taxId) {
        // Check cache first
        if (entityUriCache.containsKey(taxId)) {
            System.out.println("Using cached entity URIs for " + taxId);
            return entityUriCache.get(taxId);
        }

        System.out.println("Fetching real entity URIs for subject: " + taxId);

        String sparqlQuery = ontopService.getPersonDataQuery();
        List<Map<String, String>> results = ontopService.executeQuery(CONTROLLER_ID, taxId, sparqlQuery);

        // Extract unique entity URIs (Orders, MedicalEntities, etc.)
        Set<String> entityUris = new LinkedHashSet<>();

        for (Map<String, String> result : results) {
            String entityUri = result.get("entity");
            if (entityUri != null && !entityUri.isEmpty()) {
                // Filter to only include Order entities (transactional data)
                // Skip Person entities as those use property-level policies
                if (entityUri.contains("#Order/") || entityUri.contains("#MedicalEntity/")) {
                    entityUris.add(entityUri);
                }
            }
        }

        List<String> uriList = new ArrayList<>(entityUris);
        System.out.println("Found " + uriList.size() + " entity URIs for " + taxId);

        // Cache for reuse
        entityUriCache.put(taxId, uriList);

        return uriList;
    }

    /**
     * Gets a subset of entity URIs for testing at specific scales.
     */
    private List<String> getEntityUrisForScale(String taxId, int targetCount) {
        List<String> allUris = fetchRealEntityUris(taxId);

        if (allUris.size() <= targetCount) {
            return allUris;
        }

        // Return first N URIs for consistent testing
        return allUris.subList(0, targetCount);
    }

    // ==================== POLICY ASSIGNMENT PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Entity Policy Assignment Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EntityPolicyAssignmentTests {

        @Test
        @Order(1)
        @DisplayName("Entity Policy Assignment - ~10 entities")
        void testEntityAssignment_10() {
            testEntityPolicyAssignment("TST000010", 10, "10_Entities");
        }

        @Test
        @Order(2)
        @DisplayName("Entity Policy Assignment - ~100 entities")
        void testEntityAssignment_100() {
            testEntityPolicyAssignment("TST000100", 100, "100_Entities");
        }

        @Test
        @Order(3)
        @DisplayName("Entity Policy Assignment - ~1,000 entities")
        void testEntityAssignment_1000() {
            testEntityPolicyAssignment("TST001000", 1000, "1000_Entities");
        }

        @Test
        @Order(4)
        @DisplayName("Entity Policy Assignment - ~10,000 entities")
        void testEntityAssignment_10000() {
            testEntityPolicyAssignment("TST010000", 10000, "10000_Entities");
        }

        @Test
        @Order(5)
        @DisplayName("Entity Policy Assignment - Full scale (~41K entities)")
        void testEntityAssignment_Full() {
            testEntityPolicyAssignment("TST010000", Integer.MAX_VALUE, "Full_Scale");
        }
    }

    // ==================== POLICY MODIFICATION PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Policy Modification Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyModificationTests {

        @Test
        @Order(1)
        @DisplayName("Policy Modification - ~10 entities")
        void testPolicyModification_10() {
            testPolicyModification("TST000010", 10, "10_Entities");
        }

        @Test
        @Order(2)
        @DisplayName("Policy Modification - ~100 entities")
        void testPolicyModification_100() {
            testPolicyModification("TST000100", 100, "100_Entities");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Modification - ~1,000 entities")
        void testPolicyModification_1000() {
            testPolicyModification("TST001000", 1000, "1000_Entities");
        }

        @Test
        @Order(4)
        @DisplayName("Policy Modification - ~10,000 entities")
        void testPolicyModification_10000() {
            testPolicyModification("TST010000", 10000, "10000_Entities");
        }
    }

    // ==================== POLICY DELETION PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Policy Deletion Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyDeletionTests {

        @Test
        @Order(1)
        @DisplayName("Policy Deletion - ~10 entities")
        void testPolicyDeletion_10() {
            testPolicyDeletion("TST000010", 10, "10_Entities");
        }

        @Test
        @Order(2)
        @DisplayName("Policy Deletion - ~100 entities")
        void testPolicyDeletion_100() {
            testPolicyDeletion("TST000100", 100, "100_Entities");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Deletion - ~1,000 entities")
        void testPolicyDeletion_1000() {
            testPolicyDeletion("TST001000", 1000, "1000_Entities");
        }

        @Test
        @Order(4)
        @DisplayName("Policy Deletion - ~10,000 entities")
        void testPolicyDeletion_10000() {
            testPolicyDeletion("TST010000", 10000, "10000_Entities");
        }
    }

    // ==================== PAGE LOAD WITH POLICY METADATA TESTS ====================

    @Nested
    @DisplayName("Page Load with Policy Metadata Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PageLoadWithPoliciesTests {

        @Test
        @Order(1)
        @DisplayName("Page Load - Baseline (no policies)")
        void testPageLoad_Baseline() {
            testPageLoadWithPolicies("TST000010", 0, "Baseline_No_Policies");
        }

        @Test
        @Order(2)
        @DisplayName("Page Load - With ~10 entity policies")
        void testPageLoad_10Policies() {
            testPageLoadWithPolicies("TST000010", 10, "10_Entity_Policies");
        }

        @Test
        @Order(3)
        @DisplayName("Page Load - With ~100 entity policies")
        void testPageLoad_100Policies() {
            testPageLoadWithPolicies("TST000100", 100, "100_Entity_Policies");
        }

        @Test
        @Order(4)
        @DisplayName("Page Load - With ~1,000 entity policies")
        void testPageLoad_1000Policies() {
            testPageLoadWithPolicies("TST001000", 1000, "1000_Entity_Policies");
        }

        @Test
        @Order(5)
        @DisplayName("Page Load - With ~10,000 entity policies")
        void testPageLoad_10000Policies() {
            testPageLoadWithPolicies("TST010000", 10000, "10000_Entity_Policies");
        }
    }

    // ==================== TEST IMPLEMENTATION METHODS ====================

    /**
     * Tests entity policy assignment performance using REAL entity URIs.
     */
    private void testEntityPolicyAssignment(String taxId, int targetCount, String scale) {
        System.out.printf("%n=== Entity Policy Assignment Test: %s (taxId: %s) ===%n", scale, taxId);

        Long subjectId = getSubjectId(taxId);
        if (subjectId == null) {
            System.err.println("Skipping test - no subject found for taxId: " + taxId);
            Assertions.fail("Subject not found for " + taxId + " - ensure test data is populated");
            return;
        }

        // Fetch real entity URIs
        List<String> entityUris = getEntityUrisForScale(taxId, targetCount);
        int actualCount = entityUris.size();

        if (actualCount == 0) {
            System.err.println("No entity URIs found for " + taxId);
            Assertions.fail("No entities found for testing");
            return;
        }

        System.out.printf("Using %d real entity URIs for testing%n", actualCount);

        // Create assignment DTO with real entity URIs
        PolicyAssignmentDTO assignment = new PolicyAssignmentDTO();
        Map<String, Set<String>> entityAssignments = new HashMap<>();
        String dataSourceId = getDataSourceIdentifier("ecommerce_db");
        entityAssignments.put(dataSourceId, new HashSet<>(entityUris));
        assignment.setEntityAssignments(entityAssignments);

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Warmup Assignment " + i);
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, subjectId);
            createdPolicyGroups.add(policyGroupId);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, subjectId);
            // Cleanup warmup policies immediately
            policyGroupService.deletePolicyGroup(policyGroupId, subjectId);
            createdPolicyGroups.remove(policyGroupId);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);

        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Create fresh policy group for each run
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Assignment Test " + i);
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, subjectId);
            createdPolicyGroups.add(policyGroupId);

            // Measure assignment time
            long startTime = System.nanoTime();
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, subjectId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (actualCount * 1000.0 / avgDuration) : 0;
        double msPerItem = actualCount > 0 ? (double) avgDuration / actualCount : 0;

        System.out.printf("Results - Scale: %s, Items: %d, Avg: %d ms, Min: %d ms, Max: %d ms%n",
                scale, actualCount, avgDuration, minDuration, maxDuration);
        System.out.printf("Throughput: %.2f items/sec, %.3f ms/item%n", itemsPerSec, msPerItem);

        // Write results to CSV
        writeResult("Entity_Policy_Assignment", scale, taxId, actualCount, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms, %.3f ms/item",
                        avgDuration, minDuration, maxDuration, msPerItem),
                "Real entity URIs from federation");

        Assertions.assertTrue(avgDuration > 0, "Policy assignment should complete");
    }

    /**
     * Tests policy modification performance.
     */
    private void testPolicyModification(String taxId, int targetCount, String scale) {
        System.out.printf("%n=== Policy Modification Test: %s (taxId: %s) ===%n", scale, taxId);

        Long subjectId = getSubjectId(taxId);
        if (subjectId == null) {
            Assertions.fail("Subject not found for " + taxId);
            return;
        }

        // Fetch real entity URIs
        List<String> entityUris = getEntityUrisForScale(taxId, targetCount);
        int actualCount = entityUris.size();

        if (actualCount == 0) {
            Assertions.fail("No entities found for testing");
            return;
        }

        System.out.printf("Using %d real entity URIs for testing%n", actualCount);

        // Create and assign initial policy
        PolicyGroupDTO initialPolicy = createTestPolicyGroup("Modification Test Initial");
        String policyGroupId = policyGroupService.createPolicyGroup(initialPolicy, subjectId);
        createdPolicyGroups.add(policyGroupId);

        PolicyAssignmentDTO assignment = new PolicyAssignmentDTO();
        Map<String, Set<String>> entityAssignments = new HashMap<>();
        String dataSourceId = getDataSourceIdentifier("ecommerce_db");
        entityAssignments.put(dataSourceId, new HashSet<>(entityUris));
        assignment.setEntityAssignments(entityAssignments);
        policyGroupService.assignDataToPolicy(policyGroupId, assignment, initialPolicy, subjectId);

        // Create modified policy (change permissions)
        PolicyGroupDTO modifiedPolicy = createTestPolicyGroup("Modification Test Modified");
        modifiedPolicy.getPermissions().put("share", true); // Enable sharing
        modifiedPolicy.getPermissions().put("aggregate", true); // Enable aggregation

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            policyGroupService.updatePolicyGroup(policyGroupId, modifiedPolicy, subjectId);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);

        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Alternate between modified and initial to ensure actual updates
            PolicyGroupDTO policyToApply = (i % 2 == 0) ? modifiedPolicy : initialPolicy;

            long startTime = System.nanoTime();
            policyGroupService.updatePolicyGroup(policyGroupId, policyToApply, subjectId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (actualCount * 1000.0 / avgDuration) : 0;

        System.out.printf("Results - Scale: %s, Items: %d, Avg: %d ms, Min: %d ms, Max: %d ms%n",
                scale, actualCount, avgDuration, minDuration, maxDuration);

        writeResult("Policy_Modification", scale, taxId, actualCount, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms", avgDuration, minDuration, maxDuration),
                "Update policy covering N entities");

        Assertions.assertTrue(avgDuration > 0, "Policy modification should complete");
    }

    /**
     * Tests policy deletion performance.
     */
    private void testPolicyDeletion(String taxId, int targetCount, String scale) {
        System.out.printf("%n=== Policy Deletion Test: %s (taxId: %s) ===%n", scale, taxId);

        Long subjectId = getSubjectId(taxId);
        if (subjectId == null) {
            Assertions.fail("Subject not found for " + taxId);
            return;
        }

        // Fetch real entity URIs
        List<String> entityUris = getEntityUrisForScale(taxId, targetCount);
        int actualCount = entityUris.size();

        if (actualCount == 0) {
            Assertions.fail("No entities found for testing");
            return;
        }

        System.out.printf("Using %d real entity URIs for testing%n", actualCount);

        PolicyAssignmentDTO assignment = new PolicyAssignmentDTO();
        Map<String, Set<String>> entityAssignments = new HashMap<>();
        String dataSourceId = getDataSourceIdentifier("ecommerce_db");
        entityAssignments.put(dataSourceId, new HashSet<>(entityUris));
        assignment.setEntityAssignments(entityAssignments);

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Deletion Warmup " + i);
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, subjectId);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, subjectId);
            policyGroupService.deletePolicyGroup(policyGroupId, subjectId);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);

        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Create and assign policy
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Deletion Test " + i);
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, subjectId);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, subjectId);

            // Measure deletion time
            long startTime = System.nanoTime();
            policyGroupService.deletePolicyGroup(policyGroupId, subjectId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (actualCount * 1000.0 / avgDuration) : 0;

        System.out.printf("Results - Scale: %s, Items: %d, Avg: %d ms, Min: %d ms, Max: %d ms%n",
                scale, actualCount, avgDuration, minDuration, maxDuration);

        writeResult("Policy_Deletion", scale, taxId, actualCount, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms", avgDuration, minDuration, maxDuration),
                "Delete policy from N entities");

        Assertions.assertTrue(avgDuration > 0, "Policy deletion should complete");
    }

    /**
     * Tests page load performance with varying numbers of entity policies.
     * Measures: Federation query time + Policy metadata lookup time
     */
    private void testPageLoadWithPolicies(String taxId, int numPoliciesToCreate, String scale) {
        System.out.printf("%n=== Page Load with Policies Test: %s (taxId: %s) ===%n", scale, taxId);

        Long subjectId = getSubjectId(taxId);
        if (subjectId == null) {
            Assertions.fail("Subject not found for " + taxId);
            return;
        }

        // Create policies if requested
        String policyGroupId = null;
        int actualPoliciesCreated = 0;

        if (numPoliciesToCreate > 0) {
            List<String> entityUris = getEntityUrisForScale(taxId, numPoliciesToCreate);
            actualPoliciesCreated = entityUris.size();

            if (actualPoliciesCreated > 0) {
                System.out.printf("Creating policies for %d entities...%n", actualPoliciesCreated);

                PolicyGroupDTO testPolicy = createTestPolicyGroup("Page Load Test");
                policyGroupId = policyGroupService.createPolicyGroup(testPolicy, subjectId);
                createdPolicyGroups.add(policyGroupId);

                PolicyAssignmentDTO assignment = new PolicyAssignmentDTO();
                Map<String, Set<String>> entityAssignments = new HashMap<>();
                String dataSourceId = getDataSourceIdentifier("ecommerce_db");
                entityAssignments.put(dataSourceId, new HashSet<>(entityUris));
                assignment.setEntityAssignments(entityAssignments);

                policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, subjectId);
                System.out.println("Policies created");
            }
        }

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            String sparqlQuery = ontopService.getPersonDataQuery();
            ontopService.executeQuery(CONTROLLER_ID, taxId, sparqlQuery);
            odrlService.getSubjectPolicies(subjectId);
        }

        // Measurement runs
        List<Long> federationDurations = new ArrayList<>();
        List<Long> metadataDurations = new ArrayList<>();
        List<Long> totalDurations = new ArrayList<>();
        List<Integer> resultCounts = new ArrayList<>();

        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Measure federation query time
            long fedStart = System.nanoTime();
            String sparqlQuery = ontopService.getPersonDataQuery();
            List<Map<String, String>> results = ontopService.executeQuery(CONTROLLER_ID, taxId, sparqlQuery);
            long fedDuration = (System.nanoTime() - fedStart) / 1_000_000;

            // Measure policy metadata loading time
            long metaStart = System.nanoTime();
            Map<String, Map<String, Map<String, Set<String>>>> policyStatus =
                    odrlService.getSubjectPolicies(subjectId);
            long metaDuration = (System.nanoTime() - metaStart) / 1_000_000;

            long totalDuration = fedDuration + metaDuration;

            federationDurations.add(fedDuration);
            metadataDurations.add(metaDuration);
            totalDurations.add(totalDuration);
            resultCounts.add(results.size());

            System.out.printf("  Run %d: Federation %d ms, Metadata %d ms, Total %d ms (%d results)%n",
                    i + 1, fedDuration, metaDuration, totalDuration, results.size());
        }

        // Calculate statistics
        long avgFederation = (long) federationDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgMetadata = (long) metadataDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgTotal = (long) totalDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        int avgResults = (int) resultCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        double overheadPercent = avgFederation > 0 ? ((double) avgMetadata / avgFederation) * 100 : 0;

        System.out.printf("Results - Scale: %s%n", scale);
        System.out.printf("  Federation: %d ms, Metadata: %d ms, Total: %d ms%n",
                avgFederation, avgMetadata, avgTotal);
        System.out.printf("  Policy overhead: %.1f%%, Results: %d%n", overheadPercent, avgResults);

        writeResult("Page_Load_With_Policies", scale, taxId, actualPoliciesCreated, avgTotal, 0,
                String.format("Fed %d ms, Meta %d ms, Overhead %.1f%%, Results %d",
                        avgFederation, avgMetadata, overheadPercent, avgResults),
                "Federation + Policy metadata loading");

        Assertions.assertTrue(avgTotal > 0, "Page load should complete");
    }

    // ==================== HELPER METHODS ====================

    /**
     * Creates a test policy group with standard settings.
     */
    private PolicyGroupDTO createTestPolicyGroup(String name) {
        PolicyGroupDTO policy = new PolicyGroupDTO();
        policy.setName(name);
        policy.setDescription("Test policy for performance testing");

        // Set permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", false);
        permissions.put("share", false);
        permissions.put("aggregate", false);
        permissions.put("modify", false);
        policy.setPermissions(permissions);

        // Set constraints
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("purpose", "Research");
        constraints.put("expiration", "2025-12-31");
        constraints.put("requiresNotification", true);
        policy.setConstraints(constraints);

        // Set consequences
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("notificationType", "email");
        consequences.put("compensationAmount", "0");
        policy.setConsequences(consequences);

        // Set AI restrictions
        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", false);
        aiRestrictions.put("aiAlgorithm", "");
        policy.setAiRestrictions(aiRestrictions);

        // Set transformations
        policy.setTransformations(List.of("anonymize"));

        return policy;
    }

    /**
     * Writes a test result to the CSV file.
     */
    private void writeResult(String testType, String scale, String taxId, int numItems,
                             long durationMs, double itemsPerSec, String metric, String notes) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String csvLine = String.format("%s,%s,%s,%s,%d,%d,%.2f,\"%s\",\"%s\"%n",
                timestamp, testType, scale, taxId, numItems, durationMs, itemsPerSec, metric, notes);

        try (FileWriter fw = new FileWriter(RESULTS_FILE, true)) {
            fw.write(csvLine);
        } catch (IOException e) {
            System.err.println("Failed to write result to CSV: " + e.getMessage());
        }

        // Also print to console for immediate visibility
        System.out.printf("[RESULT] %s | %s | TaxID: %s | Items: %d | Duration: %d ms%n",
                testType, scale, taxId, numItems, durationMs);
    }

    // ==================== PERFORMANCE SUMMARY ====================

    @Test
    @Order(100)
    @DisplayName("Generate Performance Summary")
    void generatePerformanceSummary() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("POLICY PERFORMANCE TEST SUMMARY - REAL DATA");
        System.out.println("=".repeat(80));

        Path csvPath = Paths.get(RESULTS_FILE);
        if (Files.exists(csvPath)) {
            List<String> lines = Files.readAllLines(csvPath);
            System.out.printf("Total test results recorded: %d%n", lines.size() - 1);

            System.out.println("\nTest Results by Category:");
            System.out.println("-".repeat(80));

            Map<String, List<String>> resultsByType = new LinkedHashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                // Use regex to split CSV properly (handles quoted fields)
                String[] parts = parseCSVLine(line);
                if (parts.length >= 6) {
                    String testType = parts[1];
                    resultsByType.computeIfAbsent(testType, k -> new ArrayList<>()).add(line);
                }
            }

            for (Map.Entry<String, List<String>> entry : resultsByType.entrySet()) {
                System.out.printf("\n%s (%d tests):%n", entry.getKey(), entry.getValue().size());
                for (String result : entry.getValue()) {
                    String[] parts = parseCSVLine(result);
                    if (parts.length >= 7) {
                        String scale = parts[2];
                        String taxId = parts[3];
                        String numItems = parts[4];
                        String duration = parts[5];
                        String throughput = parts[6];
                        try {
                            System.out.printf("  %s (TaxID: %s): %s items in %s ms (%.2f items/sec)%n",
                                    scale, taxId, numItems, duration, Double.parseDouble(throughput));
                        } catch (NumberFormatException e) {
                            System.out.printf("  %s (TaxID: %s): %s items in %s ms%n",
                                    scale, taxId, numItems, duration);
                        }
                    }
                }
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Results saved to: " + RESULTS_FILE);
        System.out.println("=".repeat(80));
    }

    /**
     * Parses a CSV line handling quoted fields with commas.
     */
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());

        return result.toArray(new String[0]);
    }
}