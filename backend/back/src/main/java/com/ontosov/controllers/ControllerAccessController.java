package com.ontosov.controllers;

import com.ontosov.dto.AccessRequestDTO;
import com.ontosov.dto.PolicyDecisionDTO;
import com.ontosov.models.AccessLog;
import com.ontosov.models.User;
import com.ontosov.repositories.AccessLogRepo;
import com.ontosov.repositories.UserRepo;
import com.ontosov.services.BlockchainService;
import com.ontosov.services.PolicyEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/controller")
public class ControllerAccessController {

    @Autowired
    private PolicyEvaluationService policyEvaluationService;

    @Autowired
    private AccessLogRepo accessLogRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BlockchainService blockchainService;


    /**
     * Endpoint for controllers to check if access is permitted
     * This is the main "Policy Checker" endpoint
     */
    @PostMapping("/check-access")
    public ResponseEntity<?> checkAccess(@RequestBody AccessRequestDTO request) {
        try {
            // 1. Evaluate the access request using PDP
            PolicyDecisionDTO decision = policyEvaluationService.evaluateAccess(request);

            // 2. Log the access attempt to database
            AccessLog log = createAccessLog(request, decision);
            AccessLog savedLog = accessLogRepo.save(log);

            // 3. Log to blockchain
            try {
                String controllerAddress = "0x" + String.format("%040x", request.getControllerId());
                String subjectAddress = "0x" + String.format("%040x", savedLog.getSubjectId());

                BlockchainService.BlockchainLogResult result = blockchainService.logAccess(
                        controllerAddress,
                        subjectAddress,
                        request.getPurpose() != null ? request.getPurpose() : "",
                        request.getAction(),
                        decision.getResult().name().equals("PERMIT"),
                        decision.getPolicyGroupId(),
                        decision.getPolicyVersion() != null ? java.math.BigInteger.valueOf(decision.getPolicyVersion()) : java.math.BigInteger.ZERO
                );

                if (result != null && result.getTransactionHash() != null) {
                    savedLog.setBlockchainTxHash(result.getTransactionHash());
                    savedLog.setBlockchainLogIndex(result.getLogIndex());
                    accessLogRepo.save(savedLog);
                    System.out.println("Access logged to blockchain. TX: " + result.getTransactionHash() +
                                     ", Log Index: " + result.getLogIndex());
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to log to blockchain: " + e.getMessage());
            }

            // 4. Return the decision
            return ResponseEntity.ok(decision);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error processing access request: " + e.getMessage());
        }
    }

    /**
     * Endpoint to get access history for a specific controller
     * This powers the "Access History" page
     */
    @GetMapping("/{controllerId}/access-log")
    public ResponseEntity<?> getAccessLog(@PathVariable Long controllerId) {
        try {
            List<AccessLog> logs = accessLogRepo.findByControllerId(controllerId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error retrieving access logs: " + e.getMessage());
        }
    }

    /**
     * Endpoint to get access history for a specific subject
     * This allows subjects to see who accessed their data
     */
    @GetMapping("/subject/{subjectId}/access-log")
    public ResponseEntity<?> getSubjectAccessLog(@PathVariable Long subjectId) {
        try {
            List<AccessLog> logs = accessLogRepo.findBySubjectId(subjectId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error retrieving subject access logs: " + e.getMessage());
        }
    }

    /**
     * Endpoint to get statistics for a controller
     * Returns summary of access attempts (permitted vs denied)
     */
    @GetMapping("/{controllerId}/stats")
    public ResponseEntity<?> getAccessStats(@PathVariable Long controllerId) {
        try {
            List<AccessLog> logs = accessLogRepo.findByControllerId(controllerId);

            long totalRequests = logs.size();
            long permitted = logs.stream()
                    .filter(log -> log.getDecision().name().equals("PERMIT"))
                    .count();
            long denied = totalRequests - permitted;

            double complianceRate = totalRequests > 0
                    ? (permitted * 100.0 / totalRequests)
                    : 0.0;

            var stats = new java.util.HashMap<String, Object>();
            stats.put("totalRequests", totalRequests);
            stats.put("permitted", permitted);
            stats.put("denied", denied);
            stats.put("complianceRate", String.format("%.1f%%", complianceRate));

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error calculating statistics: " + e.getMessage());
        }
    }

    /**
     * Helper method to create AccessLog entity from request and decision
     */
    private AccessLog createAccessLog(AccessRequestDTO request, PolicyDecisionDTO decision) {
        AccessLog log = new AccessLog();

        // Set request details
        log.setControllerId(request.getControllerId());
        log.setAction(request.getAction());
        log.setPurpose(request.getPurpose());
        log.setDataDescription(request.getDataDescription());
        log.setRequestTime(LocalDateTime.now());
        log.setDataSource(request.getDataSource());
        log.setDataProperty(request.getDataProperty());

        // Find and set subject ID
        User subject = userRepo.findByTaxid(request.getSubjectTaxId());
        if (subject != null) {
            log.setSubjectId(subject.getId());
        }

        // Set decision details
        log.setDecision(decision.getResult());
        log.setReason(decision.getReason());
        log.setPolicyGroupId(decision.getPolicyGroupId());
        log.setPolicyVersion(decision.getPolicyVersion());

        // Blockchain hash will be set later when we integrate blockchain
        log.setBlockchainTxHash(null);

        return log;
    }
}