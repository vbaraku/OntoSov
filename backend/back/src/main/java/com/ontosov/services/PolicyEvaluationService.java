package com.ontosov.services;

import com.ontosov.dto.*;
import com.ontosov.models.User;
import com.ontosov.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PolicyEvaluationService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PolicyGroupService policyGroupService;

    /**
     * Main method: Evaluates if an access request should be permitted or denied
     */
    public PolicyDecisionDTO evaluateAccess(AccessRequestDTO request) {

        // 1. Validate request
        if (request.getSubjectTaxId() == null || request.getAction() == null) {
            return createDenyDecision("Invalid request: missing subject or action");
        }

        // 2. Find the subject by tax ID
        User subject = userRepo.findByTaxid(request.getSubjectTaxId());
        if (subject == null) {
            return createDenyDecision("Subject not found with tax ID: " + request.getSubjectTaxId());
        }

        // 3. Get all policy groups for this subject
        List<PolicyGroupDTO> policies = policyGroupService.getPolicyGroupsBySubject(subject.getId());
        if (policies == null || policies.isEmpty()) {
            return createDenyDecision("No policies defined for subject (default deny)");
        }

        // 4. Find a policy that permits this action
        for (PolicyGroupDTO policy : policies) {

            // Check if this policy permits the requested action
            if (isPermitted(policy, request.getAction())) {

                // Check if constraints are satisfied
                if (!checkConstraints(policy, request)) {
                    return createDenyDecision("Policy constraints not satisfied (purpose/expiration)");
                }

                // Check if AI restrictions are satisfied
                if (!checkAiRestrictions(policy, request)) {
                    return createDenyDecision("AI training restrictions not satisfied");
                }

                // All checks passed - PERMIT
                List<ObligationDTO> obligations = collectObligations(policy);
                return createPermitDecision(policy, obligations);
            }
        }

        // 5. No policy permits this action
        return createDenyDecision("No policy permits action: " + request.getAction());
    }

    /**
     * Creates a DENY decision with a reason
     */
    private PolicyDecisionDTO createDenyDecision(String reason) {
        PolicyDecisionDTO decision = new PolicyDecisionDTO();
        decision.setResult(DecisionResult.DENY);
        decision.setReason(reason);
        decision.setObligations(new ArrayList<>());
        return decision;
    }

    /**
     * Creates a PERMIT decision with obligations
     */
    private PolicyDecisionDTO createPermitDecision(PolicyGroupDTO policy, List<ObligationDTO> obligations) {
        PolicyDecisionDTO decision = new PolicyDecisionDTO();
        decision.setResult(DecisionResult.PERMIT);
        decision.setReason("Access permitted by policy: " + policy.getName());
        decision.setPolicyGroupId(policy.getId());
        decision.setObligations(obligations);
        return decision;
    }

    /**
     * Checks if a policy permits a specific action
     */
    private boolean isPermitted(PolicyGroupDTO policy, String action) {
        Map<String, Boolean> permissions = policy.getPermissions();
        return permissions != null && permissions.getOrDefault(action, false);
    }

    /**
     * Checks if the request satisfies the policy's constraints
     */
    private boolean checkConstraints(PolicyGroupDTO policy, AccessRequestDTO request) {
        Map<String, Object> constraints = policy.getConstraints();

        if (constraints == null || constraints.isEmpty()) {
            return true;
        }

        // Check PURPOSE constraint
        if (constraints.containsKey("purpose") && constraints.get("purpose") != null) {
            String requiredPurpose = constraints.get("purpose").toString().trim();

            if (!requiredPurpose.isEmpty()) {
                if (request.getPurpose() == null || request.getPurpose().trim().isEmpty()) {
                    return false;
                }

                if (!requiredPurpose.equalsIgnoreCase(request.getPurpose().trim())) {
                    return false;
                }
            }
        }

        // Check EXPIRATION constraint
        if (constraints.containsKey("expiration") && constraints.get("expiration") != null) {
            String expirationStr = constraints.get("expiration").toString();

            if (!expirationStr.isEmpty()) {
                try {
                    LocalDate expirationDate = LocalDate.parse(expirationStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    LocalDate today = LocalDate.now();

                    if (today.isAfter(expirationDate)) {
                        return false;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse expiration date: " + expirationStr);
                }
            }
        }

        return true;
    }

    /**
     * Checks if the request satisfies AI restrictions
     */
    private boolean checkAiRestrictions(PolicyGroupDTO policy, AccessRequestDTO request) {
        Map<String, Object> aiRestrictions = policy.getAiRestrictions();

        if (aiRestrictions == null || aiRestrictions.isEmpty()) {
            return true;
        }

        boolean involvesAI = isAiRelatedPurpose(request.getPurpose());

        if (!involvesAI) {
            return true;
        }

        boolean allowAiTraining = true;
        if (aiRestrictions.containsKey("allowAiTraining")) {
            allowAiTraining = (Boolean) aiRestrictions.get("allowAiTraining");
        }

        if (!allowAiTraining) {
            return false;
        }

        if (aiRestrictions.containsKey("aiAlgorithm") && aiRestrictions.get("aiAlgorithm") != null) {
            String allowedAlgorithm = aiRestrictions.get("aiAlgorithm").toString().trim();
            if (!allowedAlgorithm.isEmpty()) {
                return true;
            }
        }

        return true;
    }

    /**
     * Helper: Determines if a purpose is AI-related
     */
    private boolean isAiRelatedPurpose(String purpose) {
        if (purpose == null || purpose.trim().isEmpty()) {
            return false;
        }

        String purposeLower = purpose.toLowerCase();

        return purposeLower.contains("ai") ||
                purposeLower.contains("artificial intelligence") ||
                purposeLower.contains("machine learning") ||
                purposeLower.contains("ml") ||
                purposeLower.contains("training") ||
                purposeLower.contains("model") ||
                purposeLower.contains("algorithm") ||
                purposeLower.contains("analytics") ||
                purposeLower.contains("prediction");
    }

    /**
     * Collects obligations from the policy
     */
    private List<ObligationDTO> collectObligations(PolicyGroupDTO policy) {
        List<ObligationDTO> obligations = new ArrayList<>();

        Map<String, Object> constraints = policy.getConstraints();
        Map<String, Object> consequences = policy.getConsequences();

        if (constraints != null && constraints.getOrDefault("requiresNotification", false).equals(true)) {
            ObligationDTO notification = new ObligationDTO();
            notification.setType("notify");

            String notificationType = "email";
            if (consequences != null && consequences.containsKey("notificationType")) {
                notificationType = consequences.get("notificationType").toString();
            }

            notification.getDetails().put("method", notificationType);
            obligations.add(notification);
        }

        if (consequences != null && consequences.containsKey("compensationAmount")) {
            String amount = consequences.get("compensationAmount").toString().trim();

            if (!amount.isEmpty()) {
                ObligationDTO compensation = new ObligationDTO();
                compensation.setType("compensate");
                compensation.getDetails().put("amount", amount);
                obligations.add(compensation);
            }
        }

        return obligations;
    }
}