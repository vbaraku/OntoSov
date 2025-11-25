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

/**
 * Policy Performance Test Suite for OntoSov
 *
 * This test suite measures the performance of policy operations at different scales:
 * - Policy CRUD operations (10, 100, 1K, 10K items)
 * - Page load performance with varying policy counts (0, 100, 1K, 10K, 41K policies)
 *
 * Test Categories:
 * 1. Policy Assignment Performance - Time to assign policies to N items
 * 2. Policy Modification Performance - Time to update policies covering N items
 * 3. Policy Deletion Performance - Time to delete policies from N items
 * 4. Page Load with Policy Metadata - Federation time + Policy metadata loading time
 *
 * Prerequisites:
 * - Test data must be populated using the SQL scripts in test-data/
 * - Both ecommerce_db (PostgreSQL) and health_research_center (MySQL) must be accessible
 * - Triplestore must be accessible and empty before running tests
 *
 * @author Vijon Baraku
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("OntoSov Policy Performance Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyPerformanceTest {

    @Autowired
    private PolicyGroupService policyGroupService;

    @Autowired
    private ODRLService odrlService;

    @Autowired
    private OntopService ontopService;

    private static final Long CONTROLLER_ID = 8L;
    private static final Long TEST_SUBJECT_ID = 1L;
    private static final String RESULTS_DIR = "test-results";
    private static final String RESULTS_FILE = RESULTS_DIR + "/policy-performance.csv";

    // Test subject tax IDs (9 char limit for tax_id column)
    private static final String TAX_ID_SMALL = "TST000010";      // 78 items
    private static final String TAX_ID_MEDIUM = "TST000100";     // 558 items
    private static final String TAX_ID_LARGE = "TST001000";      // 4,308 items
    private static final String TAX_ID_XLARGE = "TST010000";     // 41,508 items

    // Data sources
    private static final String ECOMMERCE_SOURCE = "ecommerce_db";
    private static final String HEALTHCARE_SOURCE = "health_research_center";

    // Warmup and measurement runs
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 3;

    // Track created policy groups for cleanup
    private static final List<String> createdPolicyGroups = new ArrayList<>();

    @BeforeAll
    static void setupResultsDirectory() throws IOException {
        Path resultsPath = Paths.get(RESULTS_DIR);
        if (!Files.exists(resultsPath)) {
            Files.createDirectories(resultsPath);
        }

        Path csvPath = Paths.get(RESULTS_FILE);
        if (!Files.exists(csvPath)) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(RESULTS_FILE))) {
                writer.println("timestamp,test_type,scale,num_items,duration_ms,items_per_sec,metric,notes");
            }
        }
    }

    @AfterEach
    void cleanupPolicies() {
        System.out.println("Cleaning up " + createdPolicyGroups.size() + " policy groups...");
        for (String policyGroupId : createdPolicyGroups) {
            try {
                policyGroupService.deletePolicyGroup(policyGroupId, TEST_SUBJECT_ID);
            } catch (Exception e) {
                System.err.println("Failed to delete policy group " + policyGroupId + ": " + e.getMessage());
            }
        }
        createdPolicyGroups.clear();
        System.out.println("Cleanup complete");
    }

    // ==================== POLICY ASSIGNMENT PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Policy Assignment Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyAssignmentTests {

        @Test
        @Order(1)
        @DisplayName("Policy Assignment - 10 items")
        void testPolicyAssignment_10() {
            testPolicyAssignment(10, "10_Items");
        }

        @Test
        @Order(2)
        @DisplayName("Policy Assignment - 100 items")
        void testPolicyAssignment_100() {
            testPolicyAssignment(100, "100_Items");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Assignment - 1,000 items")
        void testPolicyAssignment_1000() {
            testPolicyAssignment(1000, "1000_Items");
        }

        @Test
        @Order(4)
        @DisplayName("Policy Assignment - 10,000 items")
        void testPolicyAssignment_10000() {
            testPolicyAssignment(10000, "10000_Items");
        }
    }

    // ==================== POLICY MODIFICATION PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Policy Modification Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyModificationTests {

        @Test
        @Order(1)
        @DisplayName("Policy Modification - 10 items")
        void testPolicyModification_10() {
            testPolicyModification(10, "10_Items");
        }

        @Test
        @Order(2)
        @DisplayName("Policy Modification - 100 items")
        void testPolicyModification_100() {
            testPolicyModification(100, "100_Items");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Modification - 1,000 items")
        void testPolicyModification_1000() {
            testPolicyModification(1000, "1000_Items");
        }

        @Test
        @Order(4)
        @DisplayName("Policy Modification - 10,000 items")
        void testPolicyModification_10000() {
            testPolicyModification(10000, "10000_Items");
        }
    }

    // ==================== POLICY DELETION PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Policy Deletion Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyDeletionTests {

        @Test
        @Order(1)
        @DisplayName("Policy Deletion - 10 items")
        void testPolicyDeletion_10() {
            testPolicyDeletion(10, "10_Items");
        }

        @Test
        @Order(2)
        @DisplayName("Policy Deletion - 100 items")
        void testPolicyDeletion_100() {
            testPolicyDeletion(100, "100_Items");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Deletion - 1,000 items")
        void testPolicyDeletion_1000() {
            testPolicyDeletion(1000, "1000_Items");
        }

        @Test
        @Order(4)
        @DisplayName("Policy Deletion - 10,000 items")
        void testPolicyDeletion_10000() {
            testPolicyDeletion(10000, "10000_Items");
        }
    }

    // ==================== PAGE LOAD WITH POLICY METADATA TESTS ====================

    @Nested
    @DisplayName("Page Load with Policy Metadata Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PageLoadWithPoliciesTests {

        @Test
        @Order(1)
        @DisplayName("Page Load - 0 policies (baseline)")
        void testPageLoadWithPolicies_0() {
            testPageLoadWithPolicies(0, "0_Policies", TAX_ID_XLARGE);
        }

        @Test
        @Order(2)
        @DisplayName("Page Load - 100 policies")
        void testPageLoadWithPolicies_100() {
            testPageLoadWithPolicies(100, "100_Policies", TAX_ID_XLARGE);
        }

        @Test
        @Order(3)
        @DisplayName("Page Load - 1,000 policies")
        void testPageLoadWithPolicies_1000() {
            testPageLoadWithPolicies(1000, "1000_Policies", TAX_ID_XLARGE);
        }

        @Test
        @Order(4)
        @DisplayName("Page Load - 10,000 policies")
        void testPageLoadWithPolicies_10000() {
            testPageLoadWithPolicies(10000, "10000_Policies", TAX_ID_XLARGE);
        }

        @Test
        @Order(5)
        @DisplayName("Page Load - 41,000 policies (max scale)")
        void testPageLoadWithPolicies_41000() {
            testPageLoadWithPolicies(41000, "41000_Policies", TAX_ID_XLARGE);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Tests policy assignment performance for a given scale
     */
    private void testPolicyAssignment(int numItems, String scale) {
        System.out.printf("%n=== Policy Assignment Test: %s ===%n", scale);

        // Create a test policy group
        PolicyGroupDTO testPolicy = createTestPolicyGroup("Assignment Test Policy");
        String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, TEST_SUBJECT_ID);
        createdPolicyGroups.add(policyGroupId);

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            PolicyAssignmentDTO assignment = createAssignment(numItems, ECOMMERCE_SOURCE);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            PolicyAssignmentDTO assignment = createAssignment(numItems, ECOMMERCE_SOURCE);

            long startTime = System.nanoTime();
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (numItems * 1000.0 / avgDuration) : 0;

        System.out.printf("Results - Scale: %s, Avg: %d ms, Min: %d ms, Max: %d ms, Throughput: %.2f items/sec%n",
                scale, avgDuration, minDuration, maxDuration, itemsPerSec);

        // Write results to CSV
        writeResult("Policy_Assignment", scale, numItems, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms", avgDuration, minDuration, maxDuration),
                "Jena TDB2 write performance");

        Assertions.assertTrue(avgDuration > 0, "Policy assignment should complete");
    }

    /**
     * Tests policy modification performance for a given scale
     */
    private void testPolicyModification(int numItems, String scale) {
        System.out.printf("%n=== Policy Modification Test: %s ===%n", scale);

        // Create and assign a test policy group
        PolicyGroupDTO testPolicy = createTestPolicyGroup("Modification Test Policy");
        String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, TEST_SUBJECT_ID);
        createdPolicyGroups.add(policyGroupId);

        PolicyAssignmentDTO assignment = createAssignment(numItems, ECOMMERCE_SOURCE);
        policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);

        // Modify the policy permissions
        PolicyGroupDTO modifiedPolicy = createTestPolicyGroup("Modified Policy");
        modifiedPolicy.getPermissions().put("share", true); // Enable sharing

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            policyGroupService.updatePolicyGroup(policyGroupId, modifiedPolicy, TEST_SUBJECT_ID);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            long startTime = System.nanoTime();
            policyGroupService.updatePolicyGroup(policyGroupId, modifiedPolicy, TEST_SUBJECT_ID);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (numItems * 1000.0 / avgDuration) : 0;

        System.out.printf("Results - Scale: %s, Avg: %d ms, Min: %d ms, Max: %d ms, Throughput: %.2f items/sec%n",
                scale, avgDuration, minDuration, maxDuration, itemsPerSec);

        // Write results to CSV
        writeResult("Policy_Modification", scale, numItems, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms", avgDuration, minDuration, maxDuration),
                "Update policy covering N items");

        Assertions.assertTrue(avgDuration > 0, "Policy modification should complete");
    }

    /**
     * Tests policy deletion performance for a given scale
     */
    private void testPolicyDeletion(int numItems, String scale) {
        System.out.printf("%n=== Policy Deletion Test: %s ===%n", scale);

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Deletion Warmup Policy");
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, TEST_SUBJECT_ID);
            PolicyAssignmentDTO assignment = createAssignment(numItems, ECOMMERCE_SOURCE);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);
            policyGroupService.deletePolicyGroup(policyGroupId, TEST_SUBJECT_ID);
        }

        // Measurement runs
        List<Long> durations = new ArrayList<>();
        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Create and assign policy
            PolicyGroupDTO testPolicy = createTestPolicyGroup("Deletion Test Policy");
            String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, TEST_SUBJECT_ID);
            PolicyAssignmentDTO assignment = createAssignment(numItems, ECOMMERCE_SOURCE);
            policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);

            // Measure deletion time
            long startTime = System.nanoTime();
            policyGroupService.deletePolicyGroup(policyGroupId, TEST_SUBJECT_ID);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            durations.add(duration);
            System.out.printf("  Run %d: %d ms%n", i + 1, duration);
        }

        // Calculate statistics
        long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        double itemsPerSec = avgDuration > 0 ? (numItems * 1000.0 / avgDuration) : 0;

        System.out.printf("Results - Scale: %s, Avg: %d ms, Min: %d ms, Max: %d ms, Throughput: %.2f items/sec%n",
                scale, avgDuration, minDuration, maxDuration, itemsPerSec);

        // Write results to CSV
        writeResult("Policy_Deletion", scale, numItems, avgDuration, itemsPerSec,
                String.format("Avg %d ms, Min %d ms, Max %d ms", avgDuration, minDuration, maxDuration),
                "Delete policy from N items");

        Assertions.assertTrue(avgDuration > 0, "Policy deletion should complete");
    }

    /**
     * Tests page load performance with varying numbers of policies
     */
    private void testPageLoadWithPolicies(int numPolicies, String scale, String taxId) {
        System.out.printf("%n=== Page Load with Policies Test: %s ===%n", scale);

        // Setup: Create policies if numPolicies > 0
        List<String> policyIds = new ArrayList<>();
        if (numPolicies > 0) {
            System.out.printf("Creating %d policies...%n", numPolicies);
            for (int i = 0; i < numPolicies; i++) {
                PolicyGroupDTO testPolicy = createTestPolicyGroup("Page Load Test Policy " + i);
                String policyGroupId = policyGroupService.createPolicyGroup(testPolicy, TEST_SUBJECT_ID);
                policyIds.add(policyGroupId);
                createdPolicyGroups.add(policyGroupId);

                // Assign to one property to make it realistic
                PolicyAssignmentDTO assignment = createAssignment(1, ECOMMERCE_SOURCE);
                policyGroupService.assignDataToPolicy(policyGroupId, assignment, testPolicy, TEST_SUBJECT_ID);

                if ((i + 1) % 1000 == 0) {
                    System.out.printf("  Created %d policies...%n", i + 1);
                }
            }
            System.out.printf("Policies created%n");
        }

        // Warmup runs
        System.out.printf("Performing %d warmup runs...%n", WARMUP_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            String sparqlQuery = ontopService.getPersonDataQuery();
            ontopService.executeQuery(CONTROLLER_ID, taxId, sparqlQuery);
            odrlService.getSubjectPolicies(TEST_SUBJECT_ID);
        }

        // Measurement runs
        List<Long> federationDurations = new ArrayList<>();
        List<Long> metadataDurations = new ArrayList<>();
        List<Long> totalDurations = new ArrayList<>();

        System.out.printf("Performing %d measurement runs...%n", MEASUREMENT_RUNS);
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            // Measure federation query time
            long fedStart = System.nanoTime();
            String sparqlQuery = ontopService.getPersonDataQuery();
            List<Map<String, String>> results = ontopService.executeQuery(CONTROLLER_ID, taxId, sparqlQuery);
            long fedDuration = (System.nanoTime() - fedStart) / 1_000_000;

            // Measure policy metadata loading time
            long metaStart = System.nanoTime();
            Map<String, Map<String, Map<String, Set<String>>>> policyStatus = odrlService.getSubjectPolicies(TEST_SUBJECT_ID);
            long metaDuration = (System.nanoTime() - metaStart) / 1_000_000;

            long totalDuration = fedDuration + metaDuration;

            federationDurations.add(fedDuration);
            metadataDurations.add(metaDuration);
            totalDurations.add(totalDuration);

            System.out.printf("  Run %d: Federation %d ms, Metadata %d ms, Total %d ms (%d results)%n",
                    i + 1, fedDuration, metaDuration, totalDuration, results.size());
        }

        // Calculate statistics
        long avgFederation = (long) federationDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgMetadata = (long) metadataDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgTotal = (long) totalDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        double overheadPercent = avgFederation > 0 ? ((double) avgMetadata / avgFederation) * 100 : 0;

        System.out.printf("Results - Scale: %s, Federation: %d ms, Metadata: %d ms, Total: %d ms, Overhead: %.1f%%%n",
                scale, avgFederation, avgMetadata, avgTotal, overheadPercent);

        // Write results to CSV
        writeResult("Page_Load_With_Policies", scale, numPolicies, avgTotal, 0,
                String.format("Fed %d ms, Meta %d ms, Overhead %.1f%%", avgFederation, avgMetadata, overheadPercent),
                "Federation + Policy metadata loading");

        Assertions.assertTrue(avgTotal > 0, "Page load should complete");
    }

    /**
     * Creates a test policy group with standard settings
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
     * Creates a policy assignment for N properties
     */
    private PolicyAssignmentDTO createAssignment(int numItems, String dataSource) {
        PolicyAssignmentDTO assignment = new PolicyAssignmentDTO();
        Map<String, Set<String>> propertyAssignments = new HashMap<>();
        Set<String> properties = new HashSet<>();

        for (int i = 0; i < numItems; i++) {
            properties.add("property_" + i);
        }

        propertyAssignments.put(dataSource, properties);
        assignment.setPropertyAssignments(propertyAssignments);

        return assignment;
    }

    /**
     * Writes a test result to the CSV file
     */
    private void writeResult(String testType, String scale, int numItems,
                             long durationMs, double itemsPerSec, String metric, String notes) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String csvLine = String.format("%s,%s,%s,%d,%d,%.2f,\"%s\",\"%s\"%n",
                timestamp, testType, scale, numItems, durationMs, itemsPerSec, metric, notes);

        try (FileWriter fw = new FileWriter(RESULTS_FILE, true)) {
            fw.write(csvLine);
        } catch (IOException e) {
            System.err.println("Failed to write result to CSV: " + e.getMessage());
        }

        // Also print to console for immediate visibility
        System.out.printf("[RESULT] %s | %s | Items: %d | Duration: %d ms | Throughput: %.2f items/sec%n",
                testType, scale, numItems, durationMs, itemsPerSec);
    }

    // ==================== PERFORMANCE METRICS SUMMARY ====================

    @Test
    @Order(100)
    @DisplayName("Generate Performance Summary")
    void generatePerformanceSummary() throws IOException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("POLICY PERFORMANCE TEST SUMMARY");
        System.out.println("=".repeat(70));

        Path csvPath = Paths.get(RESULTS_FILE);
        if (Files.exists(csvPath)) {
            List<String> lines = Files.readAllLines(csvPath);
            System.out.printf("Total test results recorded: %d%n", lines.size() - 1);

            // Display summary by test type
            System.out.println("\nTest Results Summary:");
            System.out.println("-".repeat(70));

            Map<String, List<String>> resultsByType = new LinkedHashMap<>();
            for (int i = 1; i < lines.size(); i++) { // Skip header
                String line = lines.get(i);
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String testType = parts[1];
                    resultsByType.computeIfAbsent(testType, k -> new ArrayList<>()).add(line);
                }
            }

            for (Map.Entry<String, List<String>> entry : resultsByType.entrySet()) {
                System.out.printf("\n%s (%d tests):%n", entry.getKey(), entry.getValue().size());
                for (String result : entry.getValue()) {
                    String[] parts = result.split(",");
                    if (parts.length >= 7) {
                        String scale = parts[2];
                        String duration = parts[4];
                        String throughput = parts[5];
                        System.out.printf("  %s: %s ms (%.2f items/sec)%n",
                                scale, duration, Double.parseDouble(throughput));
                    }
                }
            }
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Results saved to: " + RESULTS_FILE);
        System.out.println("=".repeat(70));
    }
}
