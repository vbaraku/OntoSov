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
import java.util.stream.Collectors;

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

        // 2. Determine request type and route to appropriate handler
        boolean isEntityRequest = request.getRecordId() != null && !request.getRecordId().trim().isEmpty();
        boolean isPropertyRequest = request.getDataProperty() != null && !request.getDataProperty().trim().isEmpty();

        if (!isEntityRequest && !isPropertyRequest) {
            return createDenyDecision("Invalid request: must specify either dataProperty (for property check) or recordId (for entity check)");
        }

        if (isEntityRequest && isPropertyRequest) {
            return createDenyDecision("Invalid request: cannot specify both dataProperty and recordId");
        }

        // Route to appropriate evaluation method
        if (isEntityRequest) {
            return evaluateEntityAccess(request);
        } else {
            return evaluatePropertyAccess(request);
        }
    }

    /**
     * Evaluates property-level access (column access)
     */
    private PolicyDecisionDTO evaluatePropertyAccess(AccessRequestDTO request) {

        // 1. Validate data-specific fields
        if (request.getDataSource() == null || request.getDataProperty() == null || request.getTableName() == null) {
            return createDenyDecision("Invalid request: must specify dataSource, tableName, and dataProperty");
        }

        // 2. Find the subject by tax ID
        User subject = userRepo.findByTaxid(request.getSubjectTaxId());
        if (subject == null) {
            return createDenyDecision("Subject not found with tax ID: " + request.getSubjectTaxId());
        }

        // 3. Resolve identifiers to match the format used in policy storage
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

            // Resolve column to Schema.org property (4 parameters!)
            schemaOrgProperty = databaseConfigService.resolveSchemaOrgProperty(
                    request.getControllerId(),
                    request.getDataSource(),
                    request.getTableName(),
                    request.getDataProperty()
            );

            if (schemaOrgProperty == null) {
                // Unmapped data - PERMIT by default
                PolicyDecisionDTO decision = new PolicyDecisionDTO();
                decision.setResult(DecisionResult.PERMIT);
                decision.setReason("Unmapped data is not governed by subject policies - access permitted by default.");
                decision.setObligations(new ArrayList<>());
                return decision;
            }

            System.out.println("Resolved identifiers:");
            System.out.println("  Controller name: " + controllerName);
            System.out.println("  Database name: " + databaseName);
            System.out.println("  DataSource identifier: " + dataSourceIdentifier);
            System.out.println("  Column -> Schema.org: " + request.getDataProperty() + " -> " + schemaOrgProperty);

        } catch (IOException e) {
            return createDenyDecision("Error resolving mappings: " + e.getMessage());
        }

        // 4. Check if any policy exists for this property
        if (!odrlService.policyExistsForProperty(subject.getId(), dataSourceIdentifier, schemaOrgProperty)) {
            // No policy assigned - PERMIT by default
            PolicyDecisionDTO decision = new PolicyDecisionDTO();
            decision.setResult(DecisionResult.PERMIT);
            decision.setReason("No policy assigned to this data - access permitted by default");
            decision.setObligations(new ArrayList<>());
            return decision;
        }

        // 5. Check policies using the identifiers
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
                    "No policy permits '" + request.getAction() + "' access to " +
                            schemaOrgProperty + " from " + dataSourceIdentifier
            );
        }

        // 6. Find ALL applicable policy groups for this data element
        List<PolicyGroupDTO> applicablePolicies = findApplicablePolicyGroups(
                subject.getId(),
                dataSourceIdentifier,
                schemaOrgProperty
        );

        if (applicablePolicies.isEmpty()) {
            return createDenyDecision("Policy found but group details unavailable");
        }

        System.out.println("Found " + applicablePolicies.size() + " applicable policies for evaluation");

        // 7. Evaluate ALL policies - collect denials and permits
        List<String> denyReasons = new ArrayList<>();
        List<PolicyGroupDTO> permitPolicies = new ArrayList<>();
        List<ObligationDTO> allObligations = new ArrayList<>();

        for (PolicyGroupDTO policy : applicablePolicies) {
            System.out.println("Evaluating policy: " + policy.getName());

            // Check if this specific policy permits the action
            boolean policyPermitsAction = odrlService.checkPropertyAccess(
                    subject.getId(),
                    request.getControllerId(),
                    dataSourceIdentifier,
                    schemaOrgProperty,
                    request.getAction()
            );

            if (!policyPermitsAction) {
                String reason = "Policy '" + policy.getName() + "' does not permit action '" + request.getAction() + "'";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // Check constraints (purpose, expiration)
            if (!checkConstraints(policy, request)) {
                String reason = "Policy '" + policy.getName() + "' constraints not satisfied (purpose/expiration)";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // Check AI restrictions if applicable
            if (!checkAiRestrictions(policy, request)) {
                String reason = "Policy '" + policy.getName() + "' AI training restrictions not satisfied";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // This policy permits - collect obligations
            System.out.println("  PERMIT: Policy '" + policy.getName() + "' allows access");
            permitPolicies.add(policy);
            allObligations.addAll(collectObligations(policy));
        }

        // 8. Apply "most restrictive wins" logic
        if (!denyReasons.isEmpty()) {
            String combinedReason = "Access denied by " + denyReasons.size() + " policy/policies:\n" +
                    String.join("\n", denyReasons);
            System.out.println("FINAL DECISION: DENY (" + denyReasons.size() + " policies denied)");
            return createDenyDecision(combinedReason);
        }

        // 9. All policies permit - return PERMIT with merged obligations
        System.out.println("FINAL DECISION: PERMIT (all " + permitPolicies.size() + " policies allow access)");
        return createPermitDecisionForMultiplePolicies(permitPolicies, allObligations);
    }

    /**
     * Evaluates entity-level access (row/record access)
     */
    private PolicyDecisionDTO evaluateEntityAccess(AccessRequestDTO request) {

        // 1. Validate data-specific fields
        if (request.getDataSource() == null || request.getRecordId() == null || request.getTableName() == null) {
            return createDenyDecision("Invalid request: must specify dataSource, tableName, and recordId");
        }

        // 2. Find the subject by tax ID
        User subject = userRepo.findByTaxid(request.getSubjectTaxId());
        if (subject == null) {
            return createDenyDecision("Subject not found with tax ID: " + request.getSubjectTaxId());
        }

        // 3. Resolve identifiers to match the format used in policy storage
        String databaseName;
        String controllerName;
        String dataSourceIdentifier;
        String entityUri;

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

            // Resolve table to entity type and construct full URI
            String entityType = databaseConfigService.resolveEntityTypeFromTable(
                    request.getControllerId(),
                    request.getDataSource(),
                    request.getTableName()
            );

            if (entityType == null) {
                // Unmapped table - PERMIT by default
                PolicyDecisionDTO decision = new PolicyDecisionDTO();
                decision.setResult(DecisionResult.PERMIT);
                decision.setReason("Unmapped table is not governed by subject policies - access permitted by default.");
                decision.setObligations(new ArrayList<>());
                return decision;
            }

            // Construct full entity URI: http://example.org/resource#EntityType/recordId
            entityUri = "http://example.org/resource#" + entityType + "/" + request.getRecordId();

            System.out.println("Resolved identifiers for entity access:");
            System.out.println("  Controller name: " + controllerName);
            System.out.println("  Database name: " + databaseName);
            System.out.println("  DataSource identifier: " + dataSourceIdentifier);
            System.out.println("  Table -> EntityType: " + request.getTableName() + " -> " + entityType);
            System.out.println("  Entity URI: " + entityUri);

        } catch (IOException e) {
            return createDenyDecision("Error resolving mappings: " + e.getMessage());
        }

        // 4. Check if any policy exists for this entity
        if (!odrlService.policyExistsForEntity(subject.getId(), dataSourceIdentifier, entityUri)) {
            // No policy assigned - PERMIT by default
            PolicyDecisionDTO decision = new PolicyDecisionDTO();
            decision.setResult(DecisionResult.PERMIT);
            decision.setReason("No policy assigned to this entity - access permitted by default");
            decision.setObligations(new ArrayList<>());
            return decision;
        }

        // 5. Check policies using the identifiers
        boolean hasAccessPermission = odrlService.checkEntityAccess(
                subject.getId(),
                request.getControllerId(),
                dataSourceIdentifier,
                entityUri,
                request.getAction()
        );

        System.out.println("Checking entity policy access with:");
        System.out.println("  subjectId: " + subject.getId());
        System.out.println("  controllerId: " + request.getControllerId());
        System.out.println("  dataSource: " + dataSourceIdentifier);
        System.out.println("  entityUri: " + entityUri);
        System.out.println("  action: " + request.getAction());

        if (!hasAccessPermission) {
            return createDenyDecision(
                    "No policy permits '" + request.getAction() + "' access to entity " +
                            entityUri + " from " + dataSourceIdentifier
            );
        }

        // 6. Find ALL applicable policy groups for this entity
        List<PolicyGroupDTO> applicablePolicies = findApplicablePolicyGroupsForEntity(
                subject.getId(),
                dataSourceIdentifier,
                entityUri
        );

        if (applicablePolicies.isEmpty()) {
            return createDenyDecision("Policy found but group details unavailable");
        }

        System.out.println("Found " + applicablePolicies.size() + " applicable policies for entity evaluation");

        // 7. Evaluate ALL policies - collect denials and permits
        List<String> denyReasons = new ArrayList<>();
        List<PolicyGroupDTO> permitPolicies = new ArrayList<>();
        List<ObligationDTO> allObligations = new ArrayList<>();

        for (PolicyGroupDTO policy : applicablePolicies) {
            System.out.println("Evaluating policy: " + policy.getName());

            // Check if this specific policy permits the action
            boolean policyPermitsAction = odrlService.checkEntityAccess(
                    subject.getId(),
                    request.getControllerId(),
                    dataSourceIdentifier,
                    entityUri,
                    request.getAction()
            );

            if (!policyPermitsAction) {
                String reason = "Policy '" + policy.getName() + "' does not permit action '" + request.getAction() + "'";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // Check constraints (purpose, expiration)
            if (!checkConstraints(policy, request)) {
                String reason = "Policy '" + policy.getName() + "' constraints not satisfied (purpose/expiration)";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // Check AI restrictions if applicable
            if (!checkAiRestrictions(policy, request)) {
                String reason = "Policy '" + policy.getName() + "' AI training restrictions not satisfied";
                denyReasons.add(reason);
                System.out.println("  DENY: " + reason);
                continue;
            }

            // This policy permits - collect obligations
            System.out.println("  PERMIT: Policy '" + policy.getName() + "' allows access");
            permitPolicies.add(policy);
            allObligations.addAll(collectObligations(policy));
        }

        // 8. Apply "most restrictive wins" logic
        if (!denyReasons.isEmpty()) {
            String combinedReason = "Access denied by " + denyReasons.size() + " policy/policies:\n" +
                    String.join("\n", denyReasons);
            System.out.println("FINAL DECISION: DENY (" + denyReasons.size() + " policies denied)");
            return createDenyDecision(combinedReason);
        }

        // 9. All policies permit - return PERMIT with merged obligations
        System.out.println("FINAL DECISION: PERMIT (all " + permitPolicies.size() + " policies allow access)");
        return createPermitDecisionForMultiplePolicies(permitPolicies, allObligations);
    }

    /**
     * Find ALL policy groups assigned to this specific entity (entity-level)
     */
    private List<PolicyGroupDTO> findApplicablePolicyGroupsForEntity(Long subjectId, String dataSource, String entityId) {
        // Get all policy groups for this subject
        List<PolicyGroupDTO> allGroups = policyGroupService.getPolicyGroupsBySubject(subjectId);
        List<PolicyGroupDTO> applicableGroups = new ArrayList<>();

        // Check each group to see if it's assigned to the requested entity
        for (PolicyGroupDTO group : allGroups) {
            // Get assignments for this group
            Map<String, Object> assignments = odrlService.getAssignmentsForPolicyGroup(group.getId(), subjectId);

            // Check entity assignments
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> entityAssignments =
                    (Map<String, Set<String>>) assignments.get("entityAssignments");

            if (entityAssignments != null &&
                    entityAssignments.containsKey(dataSource) &&
                    entityAssignments.get(dataSource).contains(entityId)) {
                applicableGroups.add(group);
            }
        }

        return applicableGroups;
    }

    /**
     * Find ALL policy groups assigned to this specific data element
     */
    private List<PolicyGroupDTO> findApplicablePolicyGroups(Long subjectId, String dataSource, String dataProperty) {
        // Get all policy groups for this subject
        List<PolicyGroupDTO> allGroups = policyGroupService.getPolicyGroupsBySubject(subjectId);
        List<PolicyGroupDTO> applicableGroups = new ArrayList<>();

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
                applicableGroups.add(group);
            }
        }

        return applicableGroups;
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
     * Creates a PERMIT decision for multiple policies with merged obligations
     */
    private PolicyDecisionDTO createPermitDecisionForMultiplePolicies(List<PolicyGroupDTO> policies, List<ObligationDTO> allObligations) {
        PolicyDecisionDTO decision = new PolicyDecisionDTO();
        decision.setResult(DecisionResult.PERMIT);

        // Create a detailed reason showing all policies that permitted access
        String policyNames = policies.stream()
                .map(PolicyGroupDTO::getName)
                .collect(Collectors.joining(", "));

        decision.setReason("Access permitted by " + policies.size() + " policy/policies: " + policyNames);

        // Set the first policy's ID for backward compatibility
        if (!policies.isEmpty()) {
            decision.setPolicyGroupId(policies.get(0).getId());
        }

        decision.setObligations(allObligations);
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