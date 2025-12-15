package com.ontosov.services;

import com.ontosov.dto.PolicyGroupDTO;
import com.ontosov.dto.PolicyAssignmentDTO;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PolicyGroupService {
    private static final String ONTOSOV_NS = "http://ontosov.org/policy#";
    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";

    private final Dataset dataset;
    private final Model policyModel;

    // Basic properties
    private final Property nameProperty;
    private final Property descriptionProperty;
    private final Property ownerProperty;
    private final Property permissionProperty;
    private final Property constraintProperty;
    private final Property purposeProperty;
    private final Property expirationProperty;
    private final Property notificationProperty;
    private final Property createdProperty;
    private final Property modifiedProperty;
    private final Resource policyGroupClass;

    // Consequence properties
    private final Property consequenceProperty;
    private final Property notificationTypeProperty;
    private final Property compensationAmountProperty;

    // AI restriction properties
    private final Property aiRestrictionsProperty;
    private final Property allowAiTrainingProperty;
    private final Property aiAlgorithmProperty;

    // Transformation properties (ODS)
    private final Property transformationsProperty;

    private final Property hasDataAssignmentProperty;
    private final Property dataSourcePropertyCached;
    private final Property dataPropertyPropertyCached;
    private final Property entityIdPropertyCached;
    private final Property assignmentTypeProperty;

    @Autowired
    private ODRLService odrlService;
    @Autowired
    private BlockchainService blockchainService;

    public PolicyGroupService(@Value("${ontosov.triplestore.path:src/main/resources/triplestore}") String triplestorePath) {
        // Create directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(triplestorePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create triplestore directory", e);
        }

        this.dataset = TDB2Factory.connectDataset(triplestorePath);
        this.policyModel = dataset.getNamedModel(ONTOSOV_NS + "policies");

        // Define basic properties
        this.nameProperty = policyModel.createProperty(ONTOSOV_NS, "name");
        this.descriptionProperty = policyModel.createProperty(ONTOSOV_NS, "description");
        this.ownerProperty = policyModel.createProperty(ONTOSOV_NS, "owner");
        this.permissionProperty = policyModel.createProperty(ONTOSOV_NS, "permission");
        this.constraintProperty = policyModel.createProperty(ONTOSOV_NS, "constraint");
        this.purposeProperty = policyModel.createProperty(ONTOSOV_NS, "purpose");
        this.expirationProperty = policyModel.createProperty(ONTOSOV_NS, "expiration");
        this.notificationProperty = policyModel.createProperty(ONTOSOV_NS, "requiresNotification");
        this.createdProperty = policyModel.createProperty(ONTOSOV_NS, "created");
        this.modifiedProperty = policyModel.createProperty(ONTOSOV_NS, "modified");
        this.policyGroupClass = policyModel.createResource(ONTOSOV_NS + "PolicyGroup");

        // Define consequence properties
        this.consequenceProperty = policyModel.createProperty(ONTOSOV_NS, "consequence");
        this.notificationTypeProperty = policyModel.createProperty(ONTOSOV_NS, "notificationType");
        this.compensationAmountProperty = policyModel.createProperty(ONTOSOV_NS, "compensationAmount");

        // Define AI restriction properties
        this.aiRestrictionsProperty = policyModel.createProperty(ONTOSOV_NS, "aiRestrictions");
        this.allowAiTrainingProperty = policyModel.createProperty(ONTOSOV_NS, "allowAiTraining");
        this.aiAlgorithmProperty = policyModel.createProperty(ONTOSOV_NS, "aiAlgorithm");

        // Define transformation properties
        this.transformationsProperty = policyModel.createProperty(ONTOSOV_NS, "transformations");

        this.hasDataAssignmentProperty = policyModel.createProperty(ONTOSOV_NS, "hasDataAssignment");
        this.dataSourcePropertyCached = policyModel.createProperty(ONTOSOV_NS, "dataSource");
        this.dataPropertyPropertyCached = policyModel.createProperty(ONTOSOV_NS, "dataProperty");
        this.entityIdPropertyCached = policyModel.createProperty(ONTOSOV_NS, "entityId");
        this.assignmentTypeProperty = policyModel.createProperty(ONTOSOV_NS, "assignmentType");
    }

    public String createPolicyGroup(PolicyGroupDTO policyGroupDTO, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);
        try {
            // Create a unique ID for the policy group
            String policyGroupId = "pg-" + UUID.randomUUID().toString();
            Resource policyGroup = policyModel.createResource(ONTOSOV_NS + policyGroupId);

            // Set basic properties
            policyGroup.addProperty(RDF.type, policyGroupClass);
            policyGroup.addProperty(nameProperty, policyGroupDTO.getName());
            policyGroup.addProperty(descriptionProperty, policyGroupDTO.getDescription());
            policyGroup.addProperty(ownerProperty, policyModel.createResource(ONTOSOV_NS + "subject-" + subjectId));

            // Set timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            policyGroup.addProperty(createdProperty, timestamp);
            policyGroup.addProperty(modifiedProperty, timestamp);

            // Add permissions
            for (Map.Entry<String, Boolean> entry : policyGroupDTO.getPermissions().entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    Resource permission = policyModel.createResource();
                    permission.addProperty(RDF.type, policyModel.createResource(ODRL_NS + "Permission"));
                    permission.addProperty(policyModel.createProperty(ODRL_NS, "action"),
                            policyModel.createResource(ODRL_NS + entry.getKey()));
                    policyGroup.addProperty(permissionProperty, permission);
                }
            }

            // Add constraints
            if (policyGroupDTO.getConstraints() != null) {
                Resource constraint = policyModel.createResource();

                // Purpose
                if (policyGroupDTO.getConstraints().containsKey("purpose") &&
                        policyGroupDTO.getConstraints().get("purpose") != null &&
                        !policyGroupDTO.getConstraints().get("purpose").toString().isEmpty()) {
                    constraint.addProperty(purposeProperty, policyGroupDTO.getConstraints().get("purpose").toString());
                }

                // Expiration
                if (policyGroupDTO.getConstraints().containsKey("expiration") &&
                        policyGroupDTO.getConstraints().get("expiration") != null) {
                    constraint.addProperty(expirationProperty, policyGroupDTO.getConstraints().get("expiration").toString());
                }

                // Notification
                if (policyGroupDTO.getConstraints().containsKey("requiresNotification") &&
                        Boolean.TRUE.equals(policyGroupDTO.getConstraints().get("requiresNotification"))) {
                    constraint.addProperty(notificationProperty, "true");
                }

                policyGroup.addProperty(constraintProperty, constraint);
            }

            // Add consequences
            if (policyGroupDTO.getConsequences() != null && !policyGroupDTO.getConsequences().isEmpty()) {
                Resource consequence = policyModel.createResource();

                // Notification type
                if (policyGroupDTO.getConsequences().containsKey("notificationType") &&
                        policyGroupDTO.getConsequences().get("notificationType") != null &&
                        !policyGroupDTO.getConsequences().get("notificationType").toString().isEmpty()) {
                    consequence.addProperty(notificationTypeProperty,
                            policyGroupDTO.getConsequences().get("notificationType").toString());
                }

                // Compensation amount
                if (policyGroupDTO.getConsequences().containsKey("compensationAmount") &&
                        policyGroupDTO.getConsequences().get("compensationAmount") != null &&
                        !policyGroupDTO.getConsequences().get("compensationAmount").toString().isEmpty()) {
                    consequence.addProperty(compensationAmountProperty,
                            policyGroupDTO.getConsequences().get("compensationAmount").toString());
                }

                policyGroup.addProperty(consequenceProperty, consequence);
            }

            // Add AI restrictions
            if (policyGroupDTO.getAiRestrictions() != null && !policyGroupDTO.getAiRestrictions().isEmpty()) {
                Resource aiRestriction = policyModel.createResource();

                // Allow AI training flag
                if (policyGroupDTO.getAiRestrictions().containsKey("allowAiTraining")) {
                    boolean allowAiTraining = Boolean.TRUE.equals(
                            policyGroupDTO.getAiRestrictions().get("allowAiTraining"));
                    aiRestriction.addProperty(allowAiTrainingProperty, String.valueOf(allowAiTraining));
                }

                // AI algorithm specification
                if (policyGroupDTO.getAiRestrictions().containsKey("aiAlgorithm") &&
                        policyGroupDTO.getAiRestrictions().get("aiAlgorithm") != null &&
                        !policyGroupDTO.getAiRestrictions().get("aiAlgorithm").toString().isEmpty()) {
                    aiRestriction.addProperty(aiAlgorithmProperty,
                            policyGroupDTO.getAiRestrictions().get("aiAlgorithm").toString());
                }

                policyGroup.addProperty(aiRestrictionsProperty, aiRestriction);
            }

            // Add transformations (ODS actions)
            if (policyGroupDTO.getTransformations() != null && !policyGroupDTO.getTransformations().isEmpty()) {
                for (String transformation : policyGroupDTO.getTransformations()) {
                    policyGroup.addProperty(transformationsProperty, transformation);
                }
            }

            // Record policy on blockchain
            try {
                recordPolicyOnBlockchain(policyGroupId, policyModel, subjectId);
            } catch (Exception e) {
                System.err.println("Warning: Failed to record policy on blockchain: " + e.getMessage());
                // Don't fail the whole operation if blockchain fails
            }

            dataset.commit();
            return policyGroupId;
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to create policy group: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    public List<PolicyGroupDTO> getPolicyGroupsBySubject(Long subjectId) {
        List<PolicyGroupDTO> result = new ArrayList<>();
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "SELECT ?group ?name ?description ?created ?modified\n" +
                    "WHERE {\n" +
                    "  ?group rdf:type onto:PolicyGroup ;\n" +
                    "         onto:owner onto:subject-" + subjectId + " ;\n" +
                    "         onto:name ?name ;\n" +
                    "         onto:description ?description ;\n" +
                    "         onto:created ?created ;\n" +
                    "         onto:modified ?modified .\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, policyModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    PolicyGroupDTO dto = new PolicyGroupDTO();
                    String groupUri = solution.getResource("group").getURI();
                    int index = groupUri.lastIndexOf('#');
                    if (index == -1) {
                        index = groupUri.lastIndexOf('/');
                    }
                    dto.setId(groupUri.substring(index + 1));
                    dto.setName(solution.getLiteral("name").getString());
                    dto.setDescription(solution.getLiteral("description").getString());

                    // Get permissions for this group
                    Map<String, Boolean> permissions = new HashMap<>();
                    permissions.put("read", false);
                    permissions.put("use", false);
                    permissions.put("share", false);
                    permissions.put("aggregate", false);
                    permissions.put("modify", false);

                    Resource groupResource = solution.getResource("group");
                    StmtIterator permissionIterator = policyModel.listStatements(
                            groupResource,
                            permissionProperty,
                            (RDFNode) null
                    );

                    while (permissionIterator.hasNext()) {
                        Statement stmt = permissionIterator.next();
                        Resource permissionResource = stmt.getObject().asResource();

                        StmtIterator actionIterator = policyModel.listStatements(
                                permissionResource,
                                policyModel.createProperty(ODRL_NS, "action"),
                                (RDFNode) null
                        );

                        while (actionIterator.hasNext()) {
                            Statement actionStmt = actionIterator.next();
                            String actionUri = actionStmt.getObject().asResource().getURI();
                            String action = actionUri.substring(actionUri.lastIndexOf('/') + 1);
                            permissions.put(action, true);
                        }
                    }

                    dto.setPermissions(permissions);

                    // Get constraints
                    Map<String, Object> constraints = new HashMap<>();

                    StmtIterator constraintIterator = policyModel.listStatements(
                            groupResource,
                            constraintProperty,
                            (RDFNode) null
                    );

                    if (constraintIterator.hasNext()) {
                        Resource constraintResource = constraintIterator.next().getObject().asResource();

                        // Purpose
                        Statement purposeStmt = constraintResource.getProperty(purposeProperty);
                        if (purposeStmt != null) {
                            constraints.put("purpose", purposeStmt.getString());
                        }

                        // Expiration
                        Statement expirationStmt = constraintResource.getProperty(expirationProperty);
                        if (expirationStmt != null) {
                            constraints.put("expiration", expirationStmt.getString());
                        }

                        // Notification
                        Statement notificationStmt = constraintResource.getProperty(notificationProperty);
                        constraints.put("requiresNotification", notificationStmt != null &&
                                "true".equals(notificationStmt.getString()));
                    }

                    dto.setConstraints(constraints);

                    // Get consequences
                    Map<String, Object> consequences = new HashMap<>();

                    StmtIterator consequenceIterator = policyModel.listStatements(
                            groupResource,
                            consequenceProperty,
                            (RDFNode) null
                    );

                    if (consequenceIterator.hasNext()) {
                        Resource consequenceResource = consequenceIterator.next().getObject().asResource();

                        // Notification type
                        Statement notificationTypeStmt = consequenceResource.getProperty(notificationTypeProperty);
                        if (notificationTypeStmt != null) {
                            consequences.put("notificationType", notificationTypeStmt.getString());
                        }

                        // Compensation amount
                        Statement compensationAmountStmt = consequenceResource.getProperty(compensationAmountProperty);
                        if (compensationAmountStmt != null) {
                            consequences.put("compensationAmount", compensationAmountStmt.getString());
                        }
                    }

                    dto.setConsequences(consequences);

                    // Get AI restrictions
                    Map<String, Object> aiRestrictions = new HashMap<>();

                    StmtIterator aiRestrictionsIterator = policyModel.listStatements(
                            groupResource,
                            aiRestrictionsProperty,
                            (RDFNode) null
                    );

                    if (aiRestrictionsIterator.hasNext()) {
                        Resource aiRestrictionResource = aiRestrictionsIterator.next().getObject().asResource();

                        // AI training flag
                        Statement allowAiTrainingStmt = aiRestrictionResource.getProperty(allowAiTrainingProperty);
                        if (allowAiTrainingStmt != null) {
                            aiRestrictions.put("allowAiTraining", Boolean.parseBoolean(allowAiTrainingStmt.getString()));
                        } else {
                            // Default to true if not specified
                            aiRestrictions.put("allowAiTraining", true);
                        }

                        // AI algorithm
                        Statement aiAlgorithmStmt = aiRestrictionResource.getProperty(aiAlgorithmProperty);
                        if (aiAlgorithmStmt != null) {
                            aiRestrictions.put("aiAlgorithm", aiAlgorithmStmt.getString());
                        }
                    } else {
                        // Default values if no AI restrictions specified
                        aiRestrictions.put("allowAiTraining", true);
                        aiRestrictions.put("aiAlgorithm", "");
                    }

                    dto.setAiRestrictions(aiRestrictions);

                    // Get transformations
                    List<String> transformations = new ArrayList<>();
                    StmtIterator transformationsIterator = policyModel.listStatements(
                            groupResource,
                            transformationsProperty,
                            (RDFNode) null
                    );

                    while (transformationsIterator.hasNext()) {
                        Statement stmt = transformationsIterator.next();
                        transformations.add(stmt.getString());
                    }

                    dto.setTransformations(transformations);

                    result.add(dto);
                }
            }

            return result;
        } finally {
            dataset.end();
        }
    }

    public void updatePolicyGroup(String groupId, PolicyGroupDTO policyGroupDTO, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            Resource policyGroup = policyModel.getResource(ONTOSOV_NS + groupId);

            // Check if group exists and belongs to the subject
            if (!policyGroup.hasProperty(RDF.type, policyGroupClass) ||
                    !policyGroup.hasProperty(ownerProperty, policyModel.createResource(ONTOSOV_NS + "subject-" + subjectId))) {
                throw new IllegalArgumentException("Policy group not found or access denied");
            }

            // Update basic properties
            updateProperty(policyGroup, nameProperty, policyGroupDTO.getName());
            updateProperty(policyGroup, descriptionProperty, policyGroupDTO.getDescription());

            // Update modified timestamp
            updateProperty(policyGroup, modifiedProperty,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Remove old permissions
            policyGroup.removeAll(permissionProperty);

            // Add new permissions
            for (Map.Entry<String, Boolean> entry : policyGroupDTO.getPermissions().entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    Resource permission = policyModel.createResource();
                    permission.addProperty(RDF.type, policyModel.createResource(ODRL_NS + "Permission"));
                    permission.addProperty(policyModel.createProperty(ODRL_NS, "action"),
                            policyModel.createResource(ODRL_NS + entry.getKey()));
                    policyGroup.addProperty(permissionProperty, permission);
                }
            }

            // Remove old constraints
            policyGroup.removeAll(constraintProperty);

            // Add new constraints
            if (policyGroupDTO.getConstraints() != null) {
                Resource constraint = policyModel.createResource();

                // Purpose
                if (policyGroupDTO.getConstraints().containsKey("purpose") &&
                        policyGroupDTO.getConstraints().get("purpose") != null &&
                        !policyGroupDTO.getConstraints().get("purpose").toString().isEmpty()) {
                    constraint.addProperty(purposeProperty, policyGroupDTO.getConstraints().get("purpose").toString());
                }

                // Expiration
                if (policyGroupDTO.getConstraints().containsKey("expiration") &&
                        policyGroupDTO.getConstraints().get("expiration") != null) {
                    constraint.addProperty(expirationProperty, policyGroupDTO.getConstraints().get("expiration").toString());
                }

                // Notification
                if (policyGroupDTO.getConstraints().containsKey("requiresNotification") &&
                        Boolean.TRUE.equals(policyGroupDTO.getConstraints().get("requiresNotification"))) {
                    constraint.addProperty(notificationProperty, "true");
                }

                policyGroup.addProperty(constraintProperty, constraint);
            }

            // Remove old consequences
            policyGroup.removeAll(consequenceProperty);

            // Add new consequences
            if (policyGroupDTO.getConsequences() != null && !policyGroupDTO.getConsequences().isEmpty()) {
                Resource consequence = policyModel.createResource();

                // Notification type
                if (policyGroupDTO.getConsequences().containsKey("notificationType") &&
                        policyGroupDTO.getConsequences().get("notificationType") != null &&
                        !policyGroupDTO.getConsequences().get("notificationType").toString().isEmpty()) {
                    consequence.addProperty(notificationTypeProperty,
                            policyGroupDTO.getConsequences().get("notificationType").toString());
                }

                // Compensation amount
                if (policyGroupDTO.getConsequences().containsKey("compensationAmount") &&
                        policyGroupDTO.getConsequences().get("compensationAmount") != null &&
                        !policyGroupDTO.getConsequences().get("compensationAmount").toString().isEmpty()) {
                    consequence.addProperty(compensationAmountProperty,
                            policyGroupDTO.getConsequences().get("compensationAmount").toString());
                }

                policyGroup.addProperty(consequenceProperty, consequence);
            }

            // Remove old AI restrictions
            policyGroup.removeAll(aiRestrictionsProperty);

            // Add new AI restrictions
            if (policyGroupDTO.getAiRestrictions() != null && !policyGroupDTO.getAiRestrictions().isEmpty()) {
                Resource aiRestriction = policyModel.createResource();

                // Allow AI training flag
                if (policyGroupDTO.getAiRestrictions().containsKey("allowAiTraining")) {
                    boolean allowAiTraining = Boolean.TRUE.equals(
                            policyGroupDTO.getAiRestrictions().get("allowAiTraining"));
                    aiRestriction.addProperty(allowAiTrainingProperty, String.valueOf(allowAiTraining));
                }

                // AI algorithm specification
                if (policyGroupDTO.getAiRestrictions().containsKey("aiAlgorithm") &&
                        policyGroupDTO.getAiRestrictions().get("aiAlgorithm") != null &&
                        !policyGroupDTO.getAiRestrictions().get("aiAlgorithm").toString().isEmpty()) {
                    aiRestriction.addProperty(aiAlgorithmProperty,
                            policyGroupDTO.getAiRestrictions().get("aiAlgorithm").toString());
                }

                policyGroup.addProperty(aiRestrictionsProperty, aiRestriction);
            }
            // Record updated policy on blockchain
            try {
                recordPolicyOnBlockchain(groupId, policyModel, subjectId);
            } catch (Exception e) {
                System.err.println("Warning: Failed to record policy update on blockchain: " + e.getMessage());
            }

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to update policy group: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    public void deletePolicyGroup(String groupId, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            Resource policyGroup = policyModel.getResource(ONTOSOV_NS + groupId);

            // Check if group exists and belongs to the subject
            if (!policyGroup.hasProperty(RDF.type, policyGroupClass) ||
                    !policyGroup.hasProperty(ownerProperty, policyModel.createResource(ONTOSOV_NS + "subject-" + subjectId))) {
                throw new IllegalArgumentException("Policy group not found or access denied");
            }

            // FIRST: Clean up related ODRL policies (before deleting the policy group)
            // This needs to happen within the same transaction
            odrlService.cleanupPoliciesForGroupInTransaction(groupId, subjectId);

            // Remove all data assignments for this group
            StmtIterator assignmentIterator = policyModel.listStatements(policyGroup, hasDataAssignmentProperty, (RDFNode) null);
            while (assignmentIterator.hasNext()) {
                Statement stmt = assignmentIterator.next();
                Resource assignment = stmt.getObject().asResource();
                policyModel.removeAll(assignment, null, null); // Remove all statements about this assignment
            }

            // Remove all statements about this policy group
            policyModel.removeAll(policyGroup, null, null);

            // Now commit everything together
            dataset.commit();

            // Mark policy as deleted on blockchain
            try {
                String subjectAddress = "0x" + String.format("%040x", subjectId);
                blockchainService.deletePolicy(subjectAddress, groupId);
                System.out.println("Policy marked as deleted on blockchain: " + groupId);
            } catch (Exception e) {
                System.err.println("Warning: Failed to mark policy as deleted on blockchain: " + e.getMessage());
            }

        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to delete policy group: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    public void assignDataToPolicy(String groupId, PolicyAssignmentDTO assignmentDTO,
                                   PolicyGroupDTO policyGroup, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            Resource policyGroupResource = policyModel.getResource(ONTOSOV_NS + groupId);

            // Check if group exists and belongs to the subject
            if (!policyGroupResource.hasProperty(RDF.type, policyGroupClass) ||
                    !policyGroupResource.hasProperty(ownerProperty, policyModel.createResource(ONTOSOV_NS + "subject-" + subjectId))) {
                throw new IllegalArgumentException("Policy group not found or access denied");
            }

            // Clear previous data assignments for this group
            policyGroupResource.removeAll(hasDataAssignmentProperty);

            // Create new property assignments
            if (assignmentDTO.getPropertyAssignments() != null) {
                for (Map.Entry<String, Set<String>> entry : assignmentDTO.getPropertyAssignments().entrySet()) {
                    String dataSource = entry.getKey();
                    Set<String> properties = entry.getValue();

                    for (String property : properties) {
                        Resource dataAssignment = policyModel.createResource();
                        dataAssignment.addProperty(dataSourcePropertyCached, dataSource);
                        dataAssignment.addProperty(dataPropertyPropertyCached, property);
                        dataAssignment.addProperty(assignmentTypeProperty, "property");
                        policyGroupResource.addProperty(hasDataAssignmentProperty, dataAssignment);
                    }
                }
            }

            // Create new entity assignments
            if (assignmentDTO.getEntityAssignments() != null) {
                for (Map.Entry<String, Set<String>> entry : assignmentDTO.getEntityAssignments().entrySet()) {
                    String dataSource = entry.getKey();
                    Set<String> entityIds = entry.getValue();

                    for (String entityId : entityIds) {
                        Resource dataAssignment = policyModel.createResource();
                        dataAssignment.addProperty(dataSourcePropertyCached, dataSource);
                        dataAssignment.addProperty(entityIdPropertyCached, entityId);
                        dataAssignment.addProperty(assignmentTypeProperty, "entity");
                        policyGroupResource.addProperty(hasDataAssignmentProperty, dataAssignment);
                    }
                }
            }

            // Update modified timestamp
            updateProperty(policyGroupResource, modifiedProperty,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Generate ODRL policies within the same transaction
            odrlService.generatePoliciesFromAssignment(groupId, policyGroup, assignmentDTO, subjectId);
            System.out.println("=== ASSIGNING POLICY ===");
            System.out.println("Policy Group ID: " + groupId);
            System.out.println("Subject ID: " + subjectId);
            System.out.println("PropertyAssignments: " + assignmentDTO.getPropertyAssignments());

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to assign data and generate policies: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    private void updateProperty(Resource resource, Property property, String value) {
        resource.removeAll(property);
        if (value != null && !value.isEmpty()) {
            resource.addProperty(property, value);
        }
    }

    /**
     * Record a policy on the blockchain
     */
    private void recordPolicyOnBlockchain(String policyGroupId, Model policyModel, Long subjectId) {
        try {
            // Get the policy group resource
            Resource policyGroup = policyModel.getResource(ONTOSOV_NS + policyGroupId);

            // Serialize the policy to a string for hashing
            StringBuilder policyContent = new StringBuilder();
            policyContent.append("PolicyGroup:").append(policyGroupId).append(";");

            // Add permissions
            StmtIterator permIter = policyGroup.listProperties(permissionProperty);
            while (permIter.hasNext()) {
                Statement stmt = permIter.next();
                policyContent.append(stmt.toString()).append(";");
            }

            // Add constraints
            Statement constraintStmt = policyGroup.getProperty(constraintProperty);
            if (constraintStmt != null) {
                policyContent.append(constraintStmt.toString()).append(";");
            }

            // Calculate hash
            byte[] policyHash = blockchainService.hashPolicy(policyContent.toString());

            // Generate blockchain address for subject (simplified - using subject ID)
            // In production, you'd have a proper mapping of subject ID to blockchain address
            String subjectAddress = "0x" + String.format("%040x", subjectId);

            // Record on blockchain
            String txHash = blockchainService.recordPolicy(subjectAddress, policyGroupId, policyHash);

            if (txHash != null) {
                System.out.println("Policy recorded on blockchain. TX: " + txHash);
            } else {
                System.err.println("Failed to record policy on blockchain");
            }

        } catch (Exception e) {
            System.err.println("Error recording policy on blockchain: " + e.getMessage());
            e.printStackTrace();
        }
    }
}