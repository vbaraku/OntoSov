package com.ontosov.performance;

import com.ontosov.services.BlockchainService;
import com.ontosov.services.BlockchainService.BlockchainLogResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Blockchain Performance Test Suite
 *
 * This test suite measures blockchain operations performance:
 * - Policy registration (hashing + recording on-chain)
 * - Access logging (recording PERMIT/DENY decisions)
 * - Retrieval performance (fetching audit history)
 * - Gas cost analysis
 * - Data verification (on-chain vs computed hash)
 *
 * Test Scales:
 * - Single operations (baseline)
 * - Batch: 10, 50, 100 operations
 *
 * Prerequisites:
 * - Hardhat node running on localhost:8545
 * - Smart contracts deployed (PolicyRegistry, AccessLogger)
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("OntoSov Blockchain Performance Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlockchainPerformanceTest {

    @Autowired
    private BlockchainService blockchainService;

    private Web3j web3j;

    private static final String RESULTS_DIR = "test-results";
    private static final String RESULTS_FILE = RESULTS_DIR + "/blockchain-performance.csv";

    // Test addresses (Hardhat default accounts)
    private static final String TEST_SUBJECT_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String TEST_CONTROLLER_ADDRESS = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String TEST_SUBJECT_2_ADDRESS = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";

    // Warmup and measurement runs
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 3;

    // Track created log indices for retrieval tests
    private static final List<Long> createdLogIndices = Collections.synchronizedList(new ArrayList<>());

    // Track transaction hashes for gas analysis
    private static final List<String> transactionHashes = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void setupResultsDirectory() throws IOException {
        Path resultsPath = Paths.get(RESULTS_DIR);
        if (!Files.exists(resultsPath)) {
            Files.createDirectories(resultsPath);
        }

        Path csvPath = Paths.get(RESULTS_FILE);
        if (!Files.exists(csvPath)) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(RESULTS_FILE))) {
                writer.println("timestamp,test_type,scale,num_operations,duration_ms,ops_per_sec,avg_gas_used,total_gas,metric,notes");
            }
        }
    }

    @BeforeEach
    void setup() {
        web3j = Web3j.build(new HttpService("http://localhost:8545"));

        // Verify blockchain connection
        try {
            BigInteger blockNumber = blockchainService.getCurrentBlockNumber();
            System.out.println("Connected to blockchain. Current block: " + blockNumber);
        } catch (Exception e) {
            Assertions.fail("Cannot connect to blockchain: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanup() {
        createdLogIndices.clear();
        transactionHashes.clear();
        if (web3j != null) {
            web3j.shutdown();
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Records a result to the CSV file
     */
    private void recordResult(String testType, String scale, int numOps, long durationMs,
                              double opsPerSec, BigInteger avgGas, BigInteger totalGas,
                              String metric, String notes) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULTS_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.printf("%s,%s,%s,%d,%d,%.2f,%s,%s,%s,%s%n",
                    timestamp, testType, scale, numOps, durationMs, opsPerSec,
                    avgGas != null ? avgGas.toString() : "N/A",
                    totalGas != null ? totalGas.toString() : "N/A",
                    metric, notes);
        } catch (IOException e) {
            System.err.println("Failed to record result: " + e.getMessage());
        }
    }

    /**
     * Gets gas used from a transaction hash
     */
    private BigInteger getGasUsed(String txHash) {
        try {
            TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash)
                    .send()
                    .getTransactionReceipt()
                    .orElse(null);
            if (receipt != null) {
                return receipt.getGasUsed();
            }
        } catch (Exception e) {
            System.err.println("Failed to get gas for tx " + txHash + ": " + e.getMessage());
        }
        return BigInteger.ZERO;
    }

    /**
     * Generates unique policy content for testing
     */
    private String generatePolicyContent(int index) {
        return String.format("{\"policyId\":\"test-policy-%d\",\"timestamp\":%d,\"permissions\":[\"read\",\"use\"],\"prohibitions\":[\"share\"]}",
                index, System.currentTimeMillis());
    }

    /**
     * Generates unique policy group ID
     */
    private String generatePolicyGroupId(int index) {
        return "perf-test-policy-" + System.currentTimeMillis() + "-" + index;
    }

    // ==================== 1. POLICY REGISTRATION TESTS ====================

    @Nested
    @DisplayName("Policy Registration Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PolicyRegistrationTests {

        @Test
        @Order(1)
        @DisplayName("Single Policy Registration")
        void testSinglePolicyRegistration() {
            System.out.println("\n========== Single Policy Registration ==========");

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                String content = generatePolicyContent(i);
                byte[] hash = blockchainService.hashPolicy(content);
                blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, generatePolicyGroupId(i), hash);
            }

            // Measurement
            List<Long> durations = new ArrayList<>();
            List<BigInteger> gasUsages = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                String policyGroupId = generatePolicyGroupId(100 + i);
                String content = generatePolicyContent(100 + i);
                byte[] hash = blockchainService.hashPolicy(content);

                long start = System.currentTimeMillis();
                String txHash = blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash);
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                if (txHash != null) {
                    transactionHashes.add(txHash);
                    gasUsages.add(getGasUsed(txHash));
                }

                System.out.printf("  Run %d: %dms, TX: %s%n", i + 1, duration, txHash);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            BigInteger avgGas = gasUsages.isEmpty() ? BigInteger.ZERO :
                    gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add)
                            .divide(BigInteger.valueOf(gasUsages.size()));

            System.out.printf("  Average: %.2fms, Avg Gas: %s%n", avgDuration, avgGas);

            recordResult("policy_registration", "single", 1, (long) avgDuration,
                    1000.0 / avgDuration, avgGas, avgGas, "baseline", "Single policy hash + record");
        }

        @Test
        @Order(2)
        @DisplayName("Batch Policy Registration - 10 policies")
        void testBatchPolicyRegistration_10() {
            testBatchPolicyRegistration(10, "10_policies");
        }

        @Test
        @Order(3)
        @DisplayName("Batch Policy Registration - 50 policies")
        void testBatchPolicyRegistration_50() {
            testBatchPolicyRegistration(50, "50_policies");
        }

        @Test
        @Order(4)
        @DisplayName("Batch Policy Registration - 100 policies")
        void testBatchPolicyRegistration_100() {
            testBatchPolicyRegistration(100, "100_policies");
        }

        private void testBatchPolicyRegistration(int count, String scale) {
            System.out.println("\n========== Batch Policy Registration: " + count + " ==========");

            // Warmup with smaller batch
            for (int i = 0; i < Math.min(5, count); i++) {
                String content = generatePolicyContent(i);
                byte[] hash = blockchainService.hashPolicy(content);
                blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, generatePolicyGroupId(i), hash);
            }

            // Measurement
            List<Long> durations = new ArrayList<>();
            List<BigInteger> totalGasPerRun = new ArrayList<>();

            for (int run = 0; run < MEASUREMENT_RUNS; run++) {
                List<BigInteger> runGasUsages = new ArrayList<>();
                int baseIndex = (run + 1) * 1000;

                long start = System.currentTimeMillis();
                for (int i = 0; i < count; i++) {
                    String policyGroupId = generatePolicyGroupId(baseIndex + i);
                    String content = generatePolicyContent(baseIndex + i);
                    byte[] hash = blockchainService.hashPolicy(content);
                    String txHash = blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash);

                    if (txHash != null) {
                        runGasUsages.add(getGasUsed(txHash));
                    }
                }
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                BigInteger totalGas = runGasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add);
                totalGasPerRun.add(totalGas);

                System.out.printf("  Run %d: %dms for %d policies (%.2f ops/sec), Total Gas: %s%n",
                        run + 1, duration, count, (count * 1000.0) / duration, totalGas);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgOpsPerSec = (count * 1000.0) / avgDuration;
            BigInteger avgTotalGas = totalGasPerRun.isEmpty() ? BigInteger.ZERO :
                    totalGasPerRun.stream().reduce(BigInteger.ZERO, BigInteger::add)
                            .divide(BigInteger.valueOf(totalGasPerRun.size()));
            BigInteger avgGasPerOp = avgTotalGas.divide(BigInteger.valueOf(count));

            System.out.printf("  Average: %.2fms, %.2f ops/sec, Avg Gas/Op: %s%n",
                    avgDuration, avgOpsPerSec, avgGasPerOp);

            recordResult("policy_registration", scale, count, (long) avgDuration,
                    avgOpsPerSec, avgGasPerOp, avgTotalGas, "batch",
                    count + " sequential policy registrations");
        }
    }

    // ==================== 2. ACCESS LOGGING TESTS ====================

    @Nested
    @DisplayName("Access Logging Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AccessLoggingTests {

        @Test
        @Order(1)
        @DisplayName("Single Access Log - PERMIT")
        void testSingleAccessLog_Permit() {
            testSingleAccessLog(true, "permit");
        }

        @Test
        @Order(2)
        @DisplayName("Single Access Log - DENY")
        void testSingleAccessLog_Deny() {
            testSingleAccessLog(false, "deny");
        }

        private void testSingleAccessLog(boolean permitted, String type) {
            System.out.println("\n========== Single Access Log (" + type + ") ==========");

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                blockchainService.logAccess(
                        TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                        "warmup-purpose-" + i, "read", permitted,
                        "warmup-policy-" + i, BigInteger.valueOf(i));
            }

            // Measurement
            List<Long> durations = new ArrayList<>();
            List<BigInteger> gasUsages = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                long start = System.currentTimeMillis();
                BlockchainLogResult result = blockchainService.logAccess(
                        TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                        "test-purpose-" + i, "read", permitted,
                        "test-policy-" + i, BigInteger.valueOf(i + 1));
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                if (result != null && result.getTransactionHash() != null) {
                    gasUsages.add(getGasUsed(result.getTransactionHash()));
                    if (result.getLogIndex() != null) {
                        createdLogIndices.add(result.getLogIndex());
                    }
                }

                System.out.printf("  Run %d: %dms, LogIndex: %s%n", i + 1, duration,
                        result != null ? result.getLogIndex() : "N/A");
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            BigInteger avgGas = gasUsages.isEmpty() ? BigInteger.ZERO :
                    gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add)
                            .divide(BigInteger.valueOf(gasUsages.size()));

            System.out.printf("  Average: %.2fms, Avg Gas: %s%n", avgDuration, avgGas);

            recordResult("access_logging", "single_" + type, 1, (long) avgDuration,
                    1000.0 / avgDuration, avgGas, avgGas, "baseline",
                    "Single " + type.toUpperCase() + " log entry");
        }

        @Test
        @Order(3)
        @DisplayName("Batch Access Logging - 10 entries")
        void testBatchAccessLogging_10() {
            testBatchAccessLogging(10, "10_entries");
        }

        @Test
        @Order(4)
        @DisplayName("Batch Access Logging - 50 entries")
        void testBatchAccessLogging_50() {
            testBatchAccessLogging(50, "50_entries");
        }

        @Test
        @Order(5)
        @DisplayName("Batch Access Logging - 100 entries")
        void testBatchAccessLogging_100() {
            testBatchAccessLogging(100, "100_entries");
        }

        @Test
        @Order(6)
        @DisplayName("Batch Access Logging - 500 entries")
        void testBatchAccessLogging_500() {
            testBatchAccessLogging(500, "500_entries");
        }

        private void testBatchAccessLogging(int count, String scale) {
            System.out.println("\n========== Batch Access Logging: " + count + " ==========");

            String[] actions = {"read", "use", "share", "aggregate", "aiTraining"};
            String[] purposes = {"ServiceProvision", "Marketing", "Research", "Analytics"};

            // Measurement (skip warmup for large batches to save time)
            List<Long> durations = new ArrayList<>();
            List<BigInteger> totalGasPerRun = new ArrayList<>();

            for (int run = 0; run < MEASUREMENT_RUNS; run++) {
                List<BigInteger> runGasUsages = new ArrayList<>();

                long start = System.currentTimeMillis();
                for (int i = 0; i < count; i++) {
                    boolean permitted = (i % 3 != 0); // Mix of permit/deny
                    String action = actions[i % actions.length];
                    String purpose = purposes[i % purposes.length];

                    BlockchainLogResult result = blockchainService.logAccess(
                            TEST_CONTROLLER_ADDRESS,
                            (i % 2 == 0) ? TEST_SUBJECT_ADDRESS : TEST_SUBJECT_2_ADDRESS,
                            purpose, action, permitted,
                            "batch-policy-" + i, BigInteger.valueOf(i % 10 + 1));

                    if (result != null && result.getTransactionHash() != null) {
                        runGasUsages.add(getGasUsed(result.getTransactionHash()));
                        if (result.getLogIndex() != null) {
                            createdLogIndices.add(result.getLogIndex());
                        }
                    }
                }
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                BigInteger totalGas = runGasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add);
                totalGasPerRun.add(totalGas);

                System.out.printf("  Run %d: %dms for %d logs (%.2f ops/sec), Total Gas: %s%n",
                        run + 1, duration, count, (count * 1000.0) / duration, totalGas);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgOpsPerSec = (count * 1000.0) / avgDuration;
            BigInteger avgTotalGas = totalGasPerRun.isEmpty() ? BigInteger.ZERO :
                    totalGasPerRun.stream().reduce(BigInteger.ZERO, BigInteger::add)
                            .divide(BigInteger.valueOf(totalGasPerRun.size()));
            BigInteger avgGasPerOp = count > 0 ? avgTotalGas.divide(BigInteger.valueOf(count)) : BigInteger.ZERO;

            System.out.printf("  Average: %.2fms, %.2f ops/sec, Avg Gas/Op: %s%n",
                    avgDuration, avgOpsPerSec, avgGasPerOp);

            recordResult("access_logging", scale, count, (long) avgDuration,
                    avgOpsPerSec, avgGasPerOp, avgTotalGas, "batch",
                    count + " mixed access log entries");
        }
    }

    // ==================== 3. RETRIEVAL TESTS ====================

    @Nested
    @DisplayName("Retrieval Performance Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RetrievalTests {

        @Test
        @Order(1)
        @DisplayName("Setup - Create logs for retrieval")
        void setupLogsForRetrieval() {
            System.out.println("\n========== Setting up logs for retrieval tests ==========");

            // Create 100 log entries for retrieval testing
            for (int i = 0; i < 100; i++) {
                BlockchainLogResult result = blockchainService.logAccess(
                        TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                        "retrieval-test-purpose", "read", true,
                        "retrieval-policy-" + i, BigInteger.valueOf(i + 1));

                if (result != null && result.getLogIndex() != null) {
                    createdLogIndices.add(result.getLogIndex());
                }
            }

            System.out.println("Created " + createdLogIndices.size() + " log entries for retrieval");
        }

        @Test
        @Order(2)
        @DisplayName("Single Log Retrieval")
        void testSingleLogRetrieval() {
            // First create a log to retrieve
            BlockchainLogResult result = blockchainService.logAccess(
                    TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                    "retrieval-purpose", "read", true,
                    "retrieval-policy", BigInteger.ONE);

            if (result == null || result.getLogIndex() == null) {
                System.out.println("Skipping retrieval test - no log index available");
                return;
            }

            Long logIndex = result.getLogIndex();
            System.out.println("\n========== Single Log Retrieval (index: " + logIndex + ") ==========");

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                blockchainService.getAccessLog(logIndex);
            }

            // Measurement
            List<Long> durations = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                long start = System.currentTimeMillis();
                var log = blockchainService.getAccessLog(logIndex);
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                System.out.printf("  Run %d: %dms%n", i + 1, duration);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            System.out.printf("  Average: %.2fms%n", avgDuration);

            recordResult("log_retrieval", "single", 1, (long) avgDuration,
                    1000.0 / avgDuration, null, null, "read_only", "Single log retrieval by index");
        }

        @Test
        @Order(3)
        @DisplayName("Batch Log Retrieval - 10 logs")
        void testBatchRetrieval_10() {
            testBatchRetrieval(10, "10_logs");
        }

        @Test
        @Order(4)
        @DisplayName("Batch Log Retrieval - 50 logs")
        void testBatchRetrieval_50() {
            testBatchRetrieval(50, "50_logs");
        }

        @Test
        @Order(5)
        @DisplayName("Batch Log Retrieval - 100 logs")
        void testBatchRetrieval_100() {
            testBatchRetrieval(100, "100_logs");
        }

        private void testBatchRetrieval(int count, String scale) {
            System.out.println("\n========== Batch Log Retrieval: " + count + " ==========");

            // Create logs if we don't have enough
            while (createdLogIndices.size() < count) {
                BlockchainLogResult result = blockchainService.logAccess(
                        TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                        "batch-retrieval-purpose", "read", true,
                        "batch-policy", BigInteger.ONE);
                if (result != null && result.getLogIndex() != null) {
                    createdLogIndices.add(result.getLogIndex());
                }
            }

            List<Long> indicesToRetrieve = createdLogIndices.subList(0, Math.min(count, createdLogIndices.size()));

            // Measurement
            List<Long> durations = new ArrayList<>();

            for (int run = 0; run < MEASUREMENT_RUNS; run++) {
                long start = System.currentTimeMillis();
                for (Long index : indicesToRetrieve) {
                    blockchainService.getAccessLog(index);
                }
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                System.out.printf("  Run %d: %dms for %d retrievals (%.2f ops/sec)%n",
                        run + 1, duration, indicesToRetrieve.size(),
                        (indicesToRetrieve.size() * 1000.0) / duration);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgOpsPerSec = (indicesToRetrieve.size() * 1000.0) / avgDuration;

            System.out.printf("  Average: %.2fms, %.2f retrievals/sec%n", avgDuration, avgOpsPerSec);

            recordResult("log_retrieval", scale, indicesToRetrieve.size(), (long) avgDuration,
                    avgOpsPerSec, null, null, "read_only", count + " sequential log retrievals");
        }

        @Test
        @Order(6)
        @DisplayName("Get Policy Version")
        void testGetPolicyVersion() {
            System.out.println("\n========== Policy Version Retrieval ==========");

            // First register a policy to get version for
            String policyGroupId = "version-test-" + System.currentTimeMillis();
            byte[] hash = blockchainService.hashPolicy("version test content");
            blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash);

            // Update it to create version 2
            byte[] hash2 = blockchainService.hashPolicy("version test content v2");
            blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash2);

            // Warmup
            for (int i = 0; i < WARMUP_RUNS; i++) {
                blockchainService.getPolicyVersion(TEST_SUBJECT_ADDRESS, policyGroupId);
            }

            // Measurement
            List<Long> durations = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_RUNS; i++) {
                long start = System.currentTimeMillis();
                BigInteger version = blockchainService.getPolicyVersion(TEST_SUBJECT_ADDRESS, policyGroupId);
                long duration = System.currentTimeMillis() - start;

                durations.add(duration);
                System.out.printf("  Run %d: %dms, Version: %s%n", i + 1, duration, version);
            }

            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            System.out.printf("  Average: %.2fms%n", avgDuration);

            recordResult("policy_version", "single", 1, (long) avgDuration,
                    1000.0 / avgDuration, null, null, "read_only", "Policy version lookup");
        }
    }

    // ==================== 4. GAS COST ANALYSIS ====================

    @Nested
    @DisplayName("Gas Cost Analysis")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GasCostAnalysis {

        @Test
        @Order(1)
        @DisplayName("Gas Cost - Policy Registration")
        void analyzeGasCost_PolicyRegistration() {
            System.out.println("\n========== Gas Cost Analysis: Policy Registration ==========");

            List<BigInteger> gasUsages = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                String policyGroupId = "gas-test-policy-" + System.currentTimeMillis() + "-" + i;
                String content = generatePolicyContent(i);
                byte[] hash = blockchainService.hashPolicy(content);

                String txHash = blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash);
                if (txHash != null) {
                    BigInteger gas = getGasUsed(txHash);
                    gasUsages.add(gas);
                    System.out.printf("  Policy %d: Gas = %s%n", i + 1, gas);
                }
            }

            if (!gasUsages.isEmpty()) {
                BigInteger minGas = gasUsages.stream().min(BigInteger::compareTo).orElse(BigInteger.ZERO);
                BigInteger maxGas = gasUsages.stream().max(BigInteger::compareTo).orElse(BigInteger.ZERO);
                BigInteger avgGas = gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add)
                        .divide(BigInteger.valueOf(gasUsages.size()));

                System.out.printf("  Min: %s, Max: %s, Avg: %s%n", minGas, maxGas, avgGas);

                recordResult("gas_analysis", "policy_registration", gasUsages.size(), 0,
                        0, avgGas, gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add),
                        "gas_cost", "Min: " + minGas + ", Max: " + maxGas);
            }
        }

        @Test
        @Order(2)
        @DisplayName("Gas Cost - Access Logging")
        void analyzeGasCost_AccessLogging() {
            System.out.println("\n========== Gas Cost Analysis: Access Logging ==========");

            List<BigInteger> gasUsages = new ArrayList<>();
            String[] actions = {"read", "use", "share", "aggregate", "aiTraining"};

            for (int i = 0; i < 10; i++) {
                BlockchainLogResult result = blockchainService.logAccess(
                        TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                        "gas-test-purpose-" + i, actions[i % actions.length], i % 2 == 0,
                        "gas-test-policy-" + i, BigInteger.valueOf(i + 1));

                if (result != null && result.getTransactionHash() != null) {
                    BigInteger gas = getGasUsed(result.getTransactionHash());
                    gasUsages.add(gas);
                    System.out.printf("  Log %d (%s): Gas = %s%n", i + 1, actions[i % actions.length], gas);
                }
            }

            if (!gasUsages.isEmpty()) {
                BigInteger minGas = gasUsages.stream().min(BigInteger::compareTo).orElse(BigInteger.ZERO);
                BigInteger maxGas = gasUsages.stream().max(BigInteger::compareTo).orElse(BigInteger.ZERO);
                BigInteger avgGas = gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add)
                        .divide(BigInteger.valueOf(gasUsages.size()));

                System.out.printf("  Min: %s, Max: %s, Avg: %s%n", minGas, maxGas, avgGas);

                recordResult("gas_analysis", "access_logging", gasUsages.size(), 0,
                        0, avgGas, gasUsages.stream().reduce(BigInteger.ZERO, BigInteger::add),
                        "gas_cost", "Min: " + minGas + ", Max: " + maxGas);
            }
        }

        @Test
        @Order(3)
        @DisplayName("Gas Cost - Variable Content Size")
        void analyzeGasCost_VariableSize() {
            System.out.println("\n========== Gas Cost Analysis: Variable Content Size ==========");

            int[] contentSizes = {100, 500, 1000, 2000, 5000};

            for (int size : contentSizes) {
                StringBuilder content = new StringBuilder();
                for (int i = 0; i < size; i++) {
                    content.append("x");
                }

                String policyGroupId = "size-test-" + size + "-" + System.currentTimeMillis();
                byte[] hash = blockchainService.hashPolicy(content.toString());

                String txHash = blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash);
                if (txHash != null) {
                    BigInteger gas = getGasUsed(txHash);
                    System.out.printf("  Content size %d chars: Gas = %s%n", size, gas);

                    recordResult("gas_analysis", "content_size_" + size, 1, 0,
                            0, gas, gas, "gas_cost", "Policy content size: " + size + " chars");
                }
            }
        }
    }

    // ==================== 5. VERIFICATION TESTS ====================

    @Nested
    @DisplayName("Data Verification Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class VerificationTests {

        @Test
        @Order(1)
        @DisplayName("Policy Hash Verification")
        void testPolicyHashVerification() {
            System.out.println("\n========== Policy Hash Verification ==========");

            String policyContent = "{\"permissions\":[\"read\",\"use\"],\"prohibitions\":[\"share\"],\"timestamp\":"
                    + System.currentTimeMillis() + "}";
            String policyGroupId = "verify-hash-" + System.currentTimeMillis();

            // Compute hash locally
            byte[] localHash = blockchainService.hashPolicy(policyContent);
            String localHashHex = bytesToHex(localHash);
            System.out.println("  Local hash: " + localHashHex);

            // Record on blockchain
            String txHash = blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, localHash);
            System.out.println("  Recorded TX: " + txHash);

            // Retrieve from blockchain and verify
            byte[] retrievedHash = blockchainService.getPolicyHash(TEST_SUBJECT_ADDRESS, policyGroupId);
            String retrievedHashHex = bytesToHex(retrievedHash);
            System.out.println("  Retrieved hash: " + retrievedHashHex);

            boolean hashesMatch = localHashHex.equals(retrievedHashHex);
            System.out.println("  Hashes match: " + hashesMatch);

            Assertions.assertTrue(hashesMatch, "Policy hash on blockchain should match local computation");

            recordResult("verification", "policy_hash", 1, 0, 0, null, null,
                    hashesMatch ? "PASS" : "FAIL", "Policy hash verification");
        }

        @Test
        @Order(2)
        @DisplayName("Access Log Verification")
        void testAccessLogVerification() {
            System.out.println("\n========== Access Log Verification ==========");

            String purpose = "verification-purpose-" + System.currentTimeMillis();
            String action = "read";
            boolean permitted = true;
            String policyGroupId = "verify-log-policy";
            BigInteger policyVersion = BigInteger.valueOf(42);

            // Log access
            BlockchainLogResult result = blockchainService.logAccess(
                    TEST_CONTROLLER_ADDRESS, TEST_SUBJECT_ADDRESS,
                    purpose, action, permitted, policyGroupId, policyVersion);

            if (result == null || result.getLogIndex() == null) {
                System.out.println("  Skipping - no log index returned");
                return;
            }

            System.out.println("  Created log at index: " + result.getLogIndex());

            // Retrieve and verify
            var retrievedLog = blockchainService.getAccessLog(result.getLogIndex());

            if (retrievedLog == null) {
                System.out.println("  Skipping - could not retrieve log");
                return;
            }

            // Verify fields match
            boolean controllerMatch = TEST_CONTROLLER_ADDRESS.equalsIgnoreCase(retrievedLog.getController());
            boolean subjectMatch = TEST_SUBJECT_ADDRESS.equalsIgnoreCase(retrievedLog.getSubject());
            boolean actionMatch = action.equals(retrievedLog.getAction());
            boolean permittedMatch = permitted == retrievedLog.isPermitted();
            boolean policyIdMatch = policyGroupId.equals(retrievedLog.getPolicyGroupId());

            System.out.println("  Controller match: " + controllerMatch);
            System.out.println("  Subject match: " + subjectMatch);
            System.out.println("  Action match: " + actionMatch);
            System.out.println("  Permitted match: " + permittedMatch);
            System.out.println("  PolicyGroupId match: " + policyIdMatch);

            boolean allMatch = controllerMatch && subjectMatch && actionMatch && permittedMatch && policyIdMatch;
            System.out.println("  All fields match: " + allMatch);

            Assertions.assertTrue(allMatch, "Access log on blockchain should match submitted data");

            recordResult("verification", "access_log", 1, 0, 0, null, null,
                    allMatch ? "PASS" : "FAIL", "Access log field verification");
        }

        @Test
        @Order(3)
        @DisplayName("Policy Version Increment Verification")
        void testPolicyVersionIncrement() {
            System.out.println("\n========== Policy Version Increment Verification ==========");

            String policyGroupId = "version-verify-" + System.currentTimeMillis();

            // Record version 1
            byte[] hash1 = blockchainService.hashPolicy("content v1");
            blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash1);
            BigInteger version1 = blockchainService.getPolicyVersion(TEST_SUBJECT_ADDRESS, policyGroupId);
            System.out.println("  After first record, version: " + version1);

            // Record version 2
            byte[] hash2 = blockchainService.hashPolicy("content v2");
            blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash2);
            BigInteger version2 = blockchainService.getPolicyVersion(TEST_SUBJECT_ADDRESS, policyGroupId);
            System.out.println("  After second record, version: " + version2);

            // Record version 3
            byte[] hash3 = blockchainService.hashPolicy("content v3");
            blockchainService.recordPolicy(TEST_SUBJECT_ADDRESS, policyGroupId, hash3);
            BigInteger version3 = blockchainService.getPolicyVersion(TEST_SUBJECT_ADDRESS, policyGroupId);
            System.out.println("  After third record, version: " + version3);

            boolean correctIncrement = version1.equals(BigInteger.ONE)
                    && version2.equals(BigInteger.valueOf(2))
                    && version3.equals(BigInteger.valueOf(3));

            System.out.println("  Versions increment correctly: " + correctIncrement);

            Assertions.assertTrue(correctIncrement, "Policy versions should increment: 1 -> 2 -> 3");

            recordResult("verification", "version_increment", 3, 0, 0, null, null,
                    correctIncrement ? "PASS" : "FAIL", "Policy version increment verification");
        }

        private String bytesToHex(byte[] bytes) {
            if (bytes == null) return "null";
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    // ==================== SUMMARY TEST ====================

    @Test
    @Order(999)
    @DisplayName("Print Test Summary")
    void printSummary() {
        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║         BLOCKCHAIN PERFORMANCE TEST COMPLETE              ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  Results saved to: " + RESULTS_FILE + "              ║");
        System.out.println("║                                                           ║");
        System.out.println("║  Tests Completed:                                         ║");
        System.out.println("║    - Policy Registration (single + batch)                 ║");
        System.out.println("║    - Access Logging (PERMIT/DENY + batch)                 ║");
        System.out.println("║    - Retrieval Performance                                ║");
        System.out.println("║    - Gas Cost Analysis                                    ║");
        System.out.println("║    - Data Verification                                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }
}