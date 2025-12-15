package com.ontosov.services;

import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.abi.datatypes.generated.Uint8;

import jakarta.annotation.PostConstruct;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class BlockchainService {

    /**
     * Result class for blockchain logging operations
     */
    public static class BlockchainLogResult {
        private final String transactionHash;
        private final Long logIndex;

        public BlockchainLogResult(String transactionHash, Long logIndex) {
            this.transactionHash = transactionHash;
            this.logIndex = logIndex;
        }

        public String getTransactionHash() {
            return transactionHash;
        }

        public Long getLogIndex() {
            return logIndex;
        }
    }

    private Web3j web3j;
    private Credentials credentials;
    private TransactionManager transactionManager;

    // Contract addresses from deployment
    private static final String POLICY_REGISTRY_ADDRESS = "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512";
    private static final String ACCESS_LOGGER_ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3";

    private static final String NODE_URL = "http://localhost:8545";
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    @PostConstruct
    public void init() {
        web3j = Web3j.build(new HttpService(NODE_URL));
        credentials = Credentials.create(PRIVATE_KEY);
        transactionManager = new RawTransactionManager(web3j, credentials);

        System.out.println("BlockchainService initialized");
        System.out.println("Connected to: " + NODE_URL);
        System.out.println("Using account: " + credentials.getAddress());
    }

    /**
     * Record a policy hash on the blockchain
     */
    public String recordPolicy(String subjectAddress, String policyGroupId, byte[] policyHashBytes) {
        try {
            // Create function: recordPolicy(address subject, string policyGroupId, bytes32 policyHash)
            Function function = new Function(
                    "recordPolicy",
                    Arrays.asList(
                            new Address(subjectAddress),
                            new Utf8String(policyGroupId),
                            new Bytes32(policyHashBytes)
                    ),
                    Collections.singletonList(new TypeReference<Uint256>() {
                    })
            );

            String encodedFunction = FunctionEncoder.encode(function);

            // Send transaction
            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                    DefaultGasProvider.GAS_PRICE,
                    DefaultGasProvider.GAS_LIMIT,
                    POLICY_REGISTRY_ADDRESS,
                    encodedFunction,
                    BigInteger.ZERO
            );

            String txHash = transactionResponse.getTransactionHash();

            System.out.println("Policy recorded on blockchain:");
            System.out.println("  Subject: " + subjectAddress);
            System.out.println("  Policy Group: " + policyGroupId);
            System.out.println("  Transaction: " + txHash);

            return txHash;

        } catch (Exception e) {
            System.err.println("Error recording policy: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Log an access attempt on the blockchain
     */
    public BlockchainLogResult logAccess(
            String controllerAddress,
            String subjectAddress,
            String purpose,
            String action,
            boolean permitted,
            String policyGroupId,
            BigInteger policyVersion
    ) {
        try {
            // Hash the purpose for privacy
            byte[] purposeHash = hashString(purpose);

            // Create function: logAccess(address controller, address subject, bytes32 purposeHash,
            //                           string action, bool permitted, string policyGroupId, uint256 policyVersion)
            Function function = new Function(
                    "logAccess",
                    Arrays.asList(
                            new Address(controllerAddress),
                            new Address(subjectAddress),
                            new Bytes32(purposeHash),
                            new Utf8String(action),
                            new Bool(permitted),
                            new Utf8String(policyGroupId != null ? policyGroupId : ""),
                            new Uint256(policyVersion != null ? policyVersion : BigInteger.ZERO)
                    ),
                    Collections.singletonList(new TypeReference<Uint256>() {
                    })
            );

            String encodedFunction = FunctionEncoder.encode(function);

            // Send transaction
            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                    DefaultGasProvider.GAS_PRICE,
                    DefaultGasProvider.GAS_LIMIT,
                    ACCESS_LOGGER_ADDRESS,
                    encodedFunction,
                    BigInteger.ZERO
            );

            String txHash = transactionResponse.getTransactionHash();

            // Wait for transaction receipt to get the log index
            Long logIndex = null;
            try {
                TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash)
                        .send()
                        .getTransactionReceipt()
                        .orElse(null);

                if (receipt != null && receipt.getLogs() != null && !receipt.getLogs().isEmpty()) {
                    // Parse the AccessLogged event to extract logIndex
                    // The logIndex is the first indexed parameter (topics[1])
                    for (Log log : receipt.getLogs()) {
                        if (log.getTopics() != null && log.getTopics().size() >= 2) {
                            // Extract logIndex from topics[1]
                            String logIndexHex = log.getTopics().get(1);
                            logIndex = new BigInteger(logIndexHex.substring(2), 16).longValue();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to extract log index: " + e.getMessage());
            }

            System.out.println("Access logged on blockchain:");
            System.out.println("  Controller: " + controllerAddress);
            System.out.println("  Subject: " + subjectAddress);
            System.out.println("  Action: " + action);
            System.out.println("  Permitted: " + permitted);
            System.out.println("  Transaction: " + txHash);
            System.out.println("  Log Index: " + logIndex);

            return new BlockchainLogResult(txHash, logIndex);

        } catch (Exception e) {
            System.err.println("Error logging access: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get current blockchain block number (for testing connection)
     */
    public BigInteger getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception e) {
            System.err.println("Error getting block number: " + e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Mark a policy as deleted on the blockchain
     */
    public String deletePolicy(String subjectAddress, String policyGroupId) {
        try {
            Function function = new Function(
                    "deletePolicy",
                    Arrays.asList(
                            new Address(subjectAddress),
                            new Utf8String(policyGroupId)
                    ),
                    Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                    DefaultGasProvider.GAS_PRICE,
                    DefaultGasProvider.GAS_LIMIT,
                    POLICY_REGISTRY_ADDRESS,
                    encodedFunction,
                    BigInteger.ZERO
            );

            String txHash = transactionResponse.getTransactionHash();
            System.out.println("Policy deleted on blockchain. TX: " + txHash);
            return txHash;

        } catch (Exception e) {
            System.err.println("Error deleting policy on blockchain: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper: Hash a string to bytes32 using SHA-256
     */
    private byte[] hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32]; // Return empty bytes if error
        }
    }

    /**
     * Result class for retrieved access logs
     */
    public static class AccessLogData {
        private final String controller;
        private final String subject;
        private final String action;
        private final boolean permitted;
        private final String policyGroupId;
        private final BigInteger policyVersion;
        private final BigInteger timestamp;

        public AccessLogData(String controller, String subject, String action,
                             boolean permitted, String policyGroupId,
                             BigInteger policyVersion, BigInteger timestamp) {
            this.controller = controller;
            this.subject = subject;
            this.action = action;
            this.permitted = permitted;
            this.policyGroupId = policyGroupId;
            this.policyVersion = policyVersion;
            this.timestamp = timestamp;
        }

        public String getController() { return controller; }
        public String getSubject() { return subject; }
        public String getAction() { return action; }
        public boolean isPermitted() { return permitted; }
        public String getPolicyGroupId() { return policyGroupId; }
        public BigInteger getPolicyVersion() { return policyVersion; }
        public BigInteger getTimestamp() { return timestamp; }
    }

    /**
     * Retrieve an access log entry by index from the blockchain
     */
    public AccessLogData getAccessLog(Long logIndex) {
        try {
            Function function = new Function(
                    "getLog",
                    Collections.singletonList(new Uint256(logIndex)),
                    Arrays.asList(
                            new TypeReference<Address>() {},    // controller
                            new TypeReference<Address>() {},    // subject
                            new TypeReference<Bytes32>() {},    // purposeHash
                            new TypeReference<Utf8String>() {}, // action
                            new TypeReference<Bool>() {},       // permitted
                            new TypeReference<Utf8String>() {}, // policyGroupId
                            new TypeReference<Uint256>() {},    // policyVersion
                            new TypeReference<Uint256>() {}     // timestamp
                    )
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            ACCESS_LOGGER_ADDRESS,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

            if (results.size() >= 8) {
                return new AccessLogData(
                        ((Address) results.get(0)).getValue(),
                        ((Address) results.get(1)).getValue(),
                        ((Utf8String) results.get(3)).getValue(),
                        ((Bool) results.get(4)).getValue(),
                        ((Utf8String) results.get(5)).getValue(),
                        ((Uint256) results.get(6)).getValue(),
                        ((Uint256) results.get(7)).getValue()
                );
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error retrieving access log: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the current version number for a policy from the blockchain
     */
    public BigInteger getPolicyVersion(String subjectAddress, String policyGroupId) {
        try {
            Function function = new Function(
                    "getCurrentVersion",
                    Arrays.asList(
                            new Address(subjectAddress),
                            new Utf8String(policyGroupId)
                    ),
                    Collections.singletonList(new TypeReference<Uint256>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            POLICY_REGISTRY_ADDRESS,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

            if (!results.isEmpty()) {
                return ((Uint256) results.get(0)).getValue();
            }

            return BigInteger.ZERO;
        } catch (Exception e) {
            System.err.println("Error getting policy version: " + e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Get the stored policy hash from the blockchain
     */
    public byte[] getPolicyHash(String subjectAddress, String policyGroupId) {
        try {
            // First get current version
            BigInteger version = getPolicyVersion(subjectAddress, policyGroupId);
            if (version.equals(BigInteger.ZERO)) {
                return null;
            }

            Function function = new Function(
                    "getPolicy",
                    Arrays.asList(
                            new Address(subjectAddress),
                            new Utf8String(policyGroupId),
                            new Uint256(version)
                    ),
                    Arrays.asList(
                            new TypeReference<Bytes32>() {},  // policyHash
                            new TypeReference<Uint256>() {},  // versionNumber
                            new TypeReference<Uint256>() {},  // timestamp
                            new TypeReference<Uint8>() {}     // status (enum)
                    )
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            POLICY_REGISTRY_ADDRESS,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

            if (!results.isEmpty()) {
                return ((Bytes32) results.get(0)).getValue();
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error getting policy hash: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper: Generate a simple hash of ODRL policy for blockchain storage
     */
    public byte[] hashPolicy(String policyContent) {
        return hashString(policyContent);
    }
}
