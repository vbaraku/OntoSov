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
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import jakarta.annotation.PostConstruct;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class BlockchainService {

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
    public String logAccess(
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

            System.out.println("Access logged on blockchain:");
            System.out.println("  Controller: " + controllerAddress);
            System.out.println("  Subject: " + subjectAddress);
            System.out.println("  Action: " + action);
            System.out.println("  Permitted: " + permitted);
            System.out.println("  Transaction: " + txHash);

            return txHash;

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
     * Helper: Generate a simple hash of ODRL policy for blockchain storage
     */
    public byte[] hashPolicy(String policyContent) {
        return hashString(policyContent);
    }
}
