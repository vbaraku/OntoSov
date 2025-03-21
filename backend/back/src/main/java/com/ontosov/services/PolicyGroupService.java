package com.ontosov.services;

import com.ontosov.dto.PolicyGroupDTO;
import com.ontosov.dto.PolicyAssignmentDTO;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
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

    @Value("${ontosov.triplestore.path}")
    private String triplestorePath;

    public PolicyGroupService(@Value("${ontosov.triplestore.path:src/main/resources/triplestore}") String triplestorePath) {
        // Create directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(triplestorePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create triplestore directory", e);
        }

        this.dataset = TDB2Factory.connectDataset(triplestorePath);
        this.policyModel = dataset.getNamedModel(ONTOSOV_NS + "policies");

        // Define properties
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

            // Remove all statements about this policy group
            policyModel.removeAll(policyGroup, null, null);

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to delete policy group: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    public void assignDataToPolicy(String groupId, PolicyAssignmentDTO assignmentDTO, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            Resource policyGroup = policyModel.getResource(ONTOSOV_NS + groupId);

            // Check if group exists and belongs to the subject
            if (!policyGroup.hasProperty(RDF.type, policyGroupClass) ||
                    !policyGroup.hasProperty(ownerProperty, policyModel.createResource(ONTOSOV_NS + "subject-" + subjectId))) {
                throw new IllegalArgumentException("Policy group not found or access denied");
            }

            // Clear previous data assignments for this group
            Property hasDataAssignmentProperty = policyModel.createProperty(ONTOSOV_NS, "hasDataAssignment");
            policyGroup.removeAll(hasDataAssignmentProperty);

            // Create new data assignments
            for (Map.Entry<String, Set<String>> entry : assignmentDTO.getDataAssignments().entrySet()) {
                String dataSource = entry.getKey();
                Set<String> properties = entry.getValue();

                for (String property : properties) {
                    // Create a resource representing this data assignment
                    Resource dataAssignment = policyModel.createResource();
                    dataAssignment.addProperty(policyModel.createProperty(ONTOSOV_NS, "dataSource"), dataSource);
                    dataAssignment.addProperty(policyModel.createProperty(ONTOSOV_NS, "dataProperty"), property);

                    // Link it to the policy group
                    policyGroup.addProperty(hasDataAssignmentProperty, dataAssignment);
                }
            }

            // Update modified timestamp
            updateProperty(policyGroup, modifiedProperty,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to assign data to policy group: " + e.getMessage(), e);
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
}