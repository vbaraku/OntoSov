package com.ontosov.services;

import com.ontosov.dto.*;
import com.ontosov.models.User;
import com.ontosov.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PolicyEvaluationService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PolicyGroupService policyGroupService;

    @Autowired
    private ODRLService odrlService;

    @Autowired
    private DatabaseConfigService databaseConfigService;

    /**
     * Main method: Evaluates if an access request should be permitted or denied
     */
    public PolicyDecisionDTO evaluateAccess(AccessRequestDTO request) {

        // 1. Validate basic request fields
        if (request.getSubjectTaxId() == null || request.getAction() == null) {
            return createDenyDecision("Invalid request: missing subject or action");
        }

        // 2. Validate data-specific fields
        if (request.getDataSource() == null || request.getDataProperty() == null) {
            return createDenyDecision("Invalid request: must specify dataSource and dataProperty");
        }

        // 3. Find the subject by tax ID
        User subject = userRepo.findByTaxid(request.getSubjectTaxId());
        if (subject == null) {
            return createDenyDecision("Subject not found with tax ID: " + request.getSubjectTaxId());
        }

        // 4. Resolve identifiers to match the format used in policy storage
        String databaseName;
        String schemaOrgProperty;
        String controllerName;
        String dataSourceIdentifier;

        try {
            // Get controller name
            User controller = userRepo.findById(request.getControllerId()).orElse(null);
            if (controller == null) {
                return createDenyDecision("Controller not found");
            }
            controllerName = controller.getName();

            // Get database name from UUID
            List<DatabaseConfigDTO> databases = databaseConfigService.getDatabasesForController(request.getControllerId());
            DatabaseConfigDTO database = databases.stream()
                    .filter(db -> db.getId().equals(request.getDataSource()))
                    .findFirst()
                    .orElse(null);

            if (database == null) {
                return createDenyDecision("Database not found: " + request.getDataSource());
            }

            databaseName = database.getDatabaseName();

            // Format dataSource the same way as in the Subject UI
            dataSourceIdentifier = controllerName + " - " + databaseName;

            // Resolve column to Schema.org property
            schemaOrgProperty = databaseConfigService.resolveSchemaOrgProperty(
                    request.getControllerId(),
                    request.getDataSource(),
                    request.getTableName(),
                    request.getDataProperty()
            );

            if (schemaOrgProperty == null) {
                return createDenyDecision("No Schema.org mapping found for column: " + request.getDataProperty());
            }

            System.out.println("Resolved identifiers:");
            System.out.println("  Controller name: " + controllerName);
            System.out.println("  Database name: " + databaseName);
            System.out.println("  DataSource identifier: " + dataSourceIdentifier);
            System.out.println("  Column -> Schema.org: " + request.getDataProperty() + " -> " + schemaOrgProperty);

        } catch (IOException e) {
            return createDenyDecision("Error resolving mappings: " + e.getMessage());
        }

        // 5. Check if ANY policy exists for this data element
        boolean policyExists = odrlService.policyExistsForProperty(
                subject.getId(),
                dataSourceIdentifier,
                schemaOrgProperty
        );

        // If NO policy exists, default to PERMIT
        if (!policyExists) {
            PolicyDecisionDTO decision = new PolicyDecisionDTO();
            decision.setResult(DecisionResult.PERMIT);
            decision.setReason("No policy assigned to this data - access permitted by default");
            decision.setObligations(new ArrayList<>());
            return decision;
        }

        // 6. Policy exists - check if it permits the requested action
        boolean hasAccessPermission = odrlService.checkPropertyAccess(
                subject.getId(),
                request.getControllerId(),
                dataSourceIdentifier,
                schemaOrgProperty,
                request.getAction()
        );

        System.out.println("Checking policy access with:");
        System.out.println("  subjectId: " + subject.getId());
        System.out.println("  controllerId: " + request.getControllerId());
        System.out.println("  dataSource: " + request.getDataSource());
        System.out.println("  schemaProperty: " + schemaOrgProperty);
        System.out.println("  action: " + request.getAction());

        if (!hasAccessPermission) {
            return createDenyDecision(
                    "Policy exists but does not permit '" + request.getAction() + "' access to " +
                            schemaOrgProperty + " from " + dataSourceIdentifier
            );
        }

        // 7. Policy exists and permits access - now find which policy group
        PolicyGroupDTO applicablePolicy = findApplicablePolicyGroup(
                subject.getId(),
                dataSourceIdentifier,
                schemaOrgProperty
        );

        if (applicablePolicy == null) {
            return createDenyDecision("Policy found but group details unavailable");
        }

        // 8. Check constraints (purpose, expiration)
        if (!checkConstraints(applicablePolicy, request)) {
            return createDenyDecision("Policy constraints not satisfied (purpose/expiration)");
        }

        // 9. Check AI restrictions if applicable
        if (!checkAiRestrictions(applicablePolicy, request)) {
            return createDenyDecision("AI training restrictions not satisfied");
        }

        // 10. All checks passed - PERMIT with obligations
        List<ObligationDTO> obligations = collectObligations(applicablePolicy);
        return createPermitDecision(applicablePolicy, obligations);
    }

    /**
     * Find which policy group is assigned to this specific data element
     */
    private PolicyGroupDTO findApplicablePolicyGroup(Long subjectId, String dataSource, String dataProperty) {
        // Get all policy groups for this subject
        List<PolicyGroupDTO> allGroups = policyGroupService.getPolicyGroupsBySubject(subjectId);

        // Check each group to see if it's assigned to the requested data
        for (PolicyGroupDTO group : allGroups) {
            // Get assignments for this group
            Map<String, Object> assignments = odrlService.getAssignmentsForPolicyGroup(group.getId(), subjectId);

            // Check property assignments
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> propertyAssignments =
                    (Map<String, Set<String>>) assignments.get("propertyAssignments");

            if (propertyAssignments != null &&
                    propertyAssignments.containsKey(dataSource) &&
                    propertyAssignments.get(dataSource).contains(dataProperty)) {
                return group;
            }
        }

        return null;
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
     * Checks if the request satisfies the policy's constraints
     */
    private boolean checkConstraints(PolicyGroupDTO policy, AccessRequestDTO request) {
        Map<String, Object> constraints = policy.getConstraints();

        if (constraints == null || constraints.isEmpty()) {
            return true;
        }

        // Check purpose constraint - SKIP for aiTraining action
        if (!"aiTraining".equals(request.getAction())) {
            if (constraints.containsKey("purpose")) {
                String requiredPurpose = constraints.get("purpose").toString().trim();
                if (!requiredPurpose.isEmpty()) {
                    String requestPurpose = request.getPurpose();
                    if (requestPurpose == null || requestPurpose.trim().isEmpty()) {
                        return false;
                    }
                    if (!requestPurpose.toLowerCase().contains(requiredPurpose.toLowerCase())) {
                        return false;
                    }
                }
            }
        }

        // Check expiration constraint (always check regardless of action)
        if (constraints.containsKey("expiration")) {
            String expirationStr = constraints.get("expiration").toString().trim();
            if (!expirationStr.isEmpty()) {
                try {
                    LocalDate expirationDate = LocalDate.parse(expirationStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    if (LocalDate.now().isAfter(expirationDate)) {
                        return false;
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing expiration date: " + e.getMessage());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if the request satisfies AI training restrictions
     */
    private boolean checkAiRestrictions(PolicyGroupDTO policy, AccessRequestDTO request) {
        // Only check AI restrictions if the action is aiTraining
        if (!"aiTraining".equals(request.getAction())) {
            return true; // No AI restrictions apply to non-AI actions
        }

        Map<String, Object> aiRestrictions = policy.getAiRestrictions();

        if (aiRestrictions == null || aiRestrictions.isEmpty()) {
            return true; // No AI restrictions defined
        }

        // Check if AI training is allowed at all
        if (aiRestrictions.containsKey("allowAiTraining")) {
            Boolean allowAiTraining = (Boolean) aiRestrictions.get("allowAiTraining");
            if (Boolean.FALSE.equals(allowAiTraining)) {
                return false; // AI training is explicitly prohibited
            }
        }

        // Check if a specific algorithm is required
        if (aiRestrictions.containsKey("aiAlgorithm")) {
            String requiredAlgorithm = aiRestrictions.get("aiAlgorithm").toString().trim();
            if (!requiredAlgorithm.isEmpty()) {
                String requestedAlgorithm = request.getAiAlgorithm();

                // If policy requires a specific algorithm but request doesn't specify one
                if (requestedAlgorithm == null || requestedAlgorithm.trim().isEmpty()) {
                    return false; // Must specify an algorithm
                }

                // Check if requested algorithm matches the required one
                if (!requiredAlgorithm.equalsIgnoreCase(requestedAlgorithm.trim())) {
                    return false; // Algorithm mismatch
                }
            }
        }

        return true;
    }

    /**
     * Helper: Determine if purpose is AI-related
     */
    private boolean isAiRelatedPurpose(String purpose) {
        if (purpose == null) {
            return false;
        }

        String purposeLower = purpose.toLowerCase();
        return purposeLower.contains("ai") ||
                purposeLower.contains("machine learning") ||
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

        // Check for notification requirement
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

        // Check for compensation
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