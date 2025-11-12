package com.ontosov.controllers;

import com.ontosov.services.BlockchainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

@RestController
@RequestMapping("/api/blockchain/test")
public class BlockchainController {

    @Autowired
    private BlockchainService blockchainService;

    @GetMapping("/block-number")
    public ResponseEntity<?> getBlockNumber() {
        BigInteger blockNumber = blockchainService.getCurrentBlockNumber();
        return ResponseEntity.ok("Current block: " + blockNumber);
    }

    @PostMapping("/record-policy")
    public ResponseEntity<?> testRecordPolicy() {
        String testAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        String policyId = "test-policy-" + System.currentTimeMillis();
        byte[] hash = blockchainService.hashPolicy("test policy content");

        String txHash = blockchainService.recordPolicy(testAddress, policyId, hash);

        if (txHash != null) {
            return ResponseEntity.ok("Policy recorded! TX: " + txHash);
        } else {
            return ResponseEntity.internalServerError().body("Failed to record policy");
        }
    }

    @PostMapping("/log-access")
    public ResponseEntity<?> testLogAccess() {
        String controller = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        String subject = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

        BlockchainService.BlockchainLogResult result = blockchainService.logAccess(
                controller,
                subject,
                "Testing Purpose",
                "read",
                true,
                "test-policy-123",
                BigInteger.ONE
        );

        if (result != null && result.getTransactionHash() != null) {
            return ResponseEntity.ok("Access logged! TX: " + result.getTransactionHash() +
                                   ", Log Index: " + result.getLogIndex());
        } else {
            return ResponseEntity.internalServerError().body("Failed to log access");
        }
    }
}
