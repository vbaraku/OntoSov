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

    // The former POST /record-policy and POST /log-access test endpoints were removed: they let any
    // caller write fixed entries to the contracts outside the policy and access-request flows.
}
