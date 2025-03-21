package com.ontosov.services;

import com.ontosov.dto.PolicyAssignmentDTO;
import com.ontosov.dto.PolicyGroupDTO;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ODRLService {
    private static final String ONTOSOV_NS = "http://ontosov.org/policy#";
    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";
    private static final String SCHEMA_NS = "http://schema.org/";

    private final Dataset dataset;
    private final Model odrlModel;

    // ODRL vocabulary
    private final Resource policyResource;
    private final Resource permissionResource;
    private final Resource constraintResource;
    private final Property targetProperty;
    private final Property actionProperty;
    private final Property assignerProperty;
    private final Property assigneeProperty;
    private final Property constraintProperty;
    private final Property purposeProperty;
    private final Property dateTimeProperty;
    private final Property groupProperty;
    private final Property notificationProperty;

    // ONTOSOV vocabulary
    private final Property dataSourceProperty;
    private final Property dataPropertyProperty;

    public ODRLService(@Value("${ontosov.triplestore.path:src/main/resources/triplestore}") String triplestorePath) {
        this.dataset = TDB2Factory.connectDataset(triplestorePath);
        this.odrlModel = dataset.getNamedModel(ODRL_NS + "policies");

        // Initialize ODRL vocabulary
        this.policyResource = odrlModel.createResource(ODRL_NS + "Policy");
        this.permissionResource = odrlModel.createResource(ODRL_NS + "Permission");
        this.constraintResource = odrlModel.createResource(ODRL_NS + "Constraint");
        this.targetProperty = odrlModel.createProperty(ODRL_NS, "target");
        this.actionProperty = odrlModel.createProperty(ODRL_NS, "action");
        this.assignerProperty = odrlModel.createProperty(ODRL_NS, "assigner");
        this.assigneeProperty = odrlModel.createProperty(ODRL_NS, "assignee");
        this.constraintProperty = odrlModel.createProperty(ODRL_NS, "constraint");
        this.purposeProperty = odrlModel.createProperty(ODRL_NS, "purpose");
        this.dateTimeProperty = odrlModel.createProperty(ODRL_NS, "dateTime");

        // Initialize custom properties
        this.groupProperty = odrlModel.createProperty(ONTOSOV_NS, "policyGroup");
        this.notificationProperty = odrlModel.createProperty(ONTOSOV_NS, "requiresNotification");
        this.dataSourceProperty = odrlModel.createProperty(ONTOSOV_NS, "dataSource");
        this.dataPropertyProperty = odrlModel.createProperty(ONTOSOV_NS, "dataProperty");
    }

    public void generatePoliciesFromAssignment(String policyGroupId, PolicyGroupDTO policyGroup,
                                               PolicyAssignmentDTO assignment, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            // First, let's remove any existing policies for this assignment
            removePoliciesForAssignment(policyGroupId, assignment, subjectId);

            // For each source and property, create appropriate ODRL policies
            for (Map.Entry<String, Set<String>> entry : assignment.getDataAssignments().entrySet()) {
                String dataSource = entry.getKey();
                Set<String> properties = entry.getValue();

                for (String property : properties) {
                    // For each permission in the policy group
                    for (Map.Entry<String, Boolean> permEntry : policyGroup.getPermissions().entrySet()) {
                        if (Boolean.TRUE.equals(permEntry.getValue())) {
                            String action = permEntry.getKey();
                            createPolicy(policyGroupId, subjectId, dataSource, property, action, policyGroup.getConstraints());
                        }
                    }
                }
            }

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to generate ODRL policies: " + e.getMessage(), e);
        } finally {
            dataset.end();
        }
    }

    private void createPolicy(String policyGroupId, Long subjectId, String dataSource,
                              String property, String action, Map<String, Object> constraints) {
        // Create a unique ID for the policy
        String policyId = "policy-" + UUID.randomUUID().toString();
        Resource policy = odrlModel.createResource(ODRL_NS + policyId);

        // Set basic policy properties
        policy.addProperty(RDF.type, policyResource);
        policy.addProperty(groupProperty, odrlModel.createResource(ONTOSOV_NS + policyGroupId));

        // Create permission
        Resource permission = odrlModel.createResource();
        permission.addProperty(RDF.type, permissionResource);

        // Set target
        // The target will be the data resource identified by subject ID, data source, and property
        String targetId = "data-" + subjectId + "-" +
                dataSource.replaceAll("[^a-zA-Z0-9]", "_") + "-" +
                property.replaceAll("[^a-zA-Z0-9]", "_");
        Resource target = odrlModel.createResource(ONTOSOV_NS + targetId);

        // Add metadata to the target
        target.addProperty(dataSourceProperty, dataSource);
        target.addProperty(dataPropertyProperty, property);

        permission.addProperty(targetProperty, target);

        // Set action
        permission.addProperty(actionProperty, odrlModel.createResource(ODRL_NS + action));

        // Set assigner (the subject)
        permission.addProperty(assignerProperty, odrlModel.createResource(ONTOSOV_NS + "subject-" + subjectId));

        // Set assignee (all controllers by default - can be refined later)
        permission.addProperty(assigneeProperty, odrlModel.createResource(ONTOSOV_NS + "allControllers"));

        // Add constraints if present
        if (constraints != null && !constraints.isEmpty()) {
            Resource constraint = odrlModel.createResource();
            constraint.addProperty(RDF.type, constraintResource);

            // Purpose constraint
            if (constraints.containsKey("purpose") && constraints.get("purpose") != null) {
                constraint.addProperty(purposeProperty, constraints.get("purpose").toString());
            }

            // Expiration constraint
            if (constraints.containsKey("expiration") && constraints.get("expiration") != null) {
                constraint.addProperty(dateTimeProperty, constraints.get("expiration").toString());
            }

            // Notification requirement
            if (constraints.containsKey("requiresNotification") &&
                    Boolean.TRUE.equals(constraints.get("requiresNotification"))) {
                constraint.addProperty(notificationProperty, "true");
            }

            permission.addProperty(constraintProperty, constraint);
        }

        // Add permission to policy
        policy.addProperty(odrlModel.createProperty(ODRL_NS, "permission"), permission);
    }

    private void removePoliciesForAssignment(String policyGroupId, PolicyAssignmentDTO assignment, Long subjectId) {
        // Query to find all policies related to this assignment
        String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                "PREFIX odrl: <" + ODRL_NS + ">\n" +
                "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                "SELECT ?policy ?permission ?target\n" +
                "WHERE {\n" +
                "  ?policy rdf:type odrl:Policy ;\n" +
                "          onto:policyGroup onto:" + policyGroupId + " ;\n" +
                "          odrl:permission ?permission .\n" +
                "  ?permission odrl:target ?target ;\n" +
                "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                "}";

        Query query = QueryFactory.create(queryString);

        List<Resource> policiesToRemove = new ArrayList<>();
        List<Resource> permissionsToRemove = new ArrayList<>();
        List<Resource> targetsToRemove = new ArrayList<>();

        try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution solution = rs.next();
                policiesToRemove.add(solution.getResource("policy"));
                permissionsToRemove.add(solution.getResource("permission"));
                targetsToRemove.add(solution.getResource("target"));
            }
        }

        // Remove all statements about these resources
        for (Resource policy : policiesToRemove) {
            odrlModel.removeAll(policy, null, null);
        }

        for (Resource permission : permissionsToRemove) {
            odrlModel.removeAll(permission, null, null);
        }

        for (Resource target : targetsToRemove) {
            odrlModel.removeAll(target, null, null);
        }
    }

    public boolean checkAccess(Long subjectId, Long controllerId, String dataSource, String property, String action) {
        dataset.begin(ReadWrite.READ);

        try {
            // Query to check if there's a policy allowing this access
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "ASK {\n" +
                    "  ?policy rdf:type odrl:Policy .\n" +
                    "  ?policy odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?target ;\n" +
                    "              odrl:action odrl:" + action + " ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " ;\n" +
                    "              odrl:assignee ?assignee .\n" +
                    "  ?target onto:dataSource \"" + dataSource + "\" ;\n" +
                    "          onto:dataProperty \"" + property + "\" .\n" +
                    "  FILTER(?assignee = onto:controller-" + controllerId + " || " +
                    "         ?assignee = onto:allControllers)\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                return qexec.execAsk();
            }
        } finally {
            dataset.end();
        }
    }

    public Map<String, Set<String>> getAssignmentsForPolicyGroup(String groupId, Long subjectId) {
        Map<String, Set<String>> result = new HashMap<>();
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?source ?property\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup onto:" + groupId + " ;\n" +
                    "          odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?target ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target onto:dataSource ?source ;\n" +
                    "          onto:dataProperty ?property .\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    String source = solution.getLiteral("source").getString();
                    String property = solution.getLiteral("property").getString();

                    if (!result.containsKey(source)) {
                        result.put(source, new HashSet<>());
                    }
                    result.get(source).add(property);
                }
            }

            return result;
        } finally {
            dataset.end();
        }
    }
    public Map<String, Map<String, Map<String, Set<String>>>> getSubjectPolicies(Long subjectId) {
        Map<String, Map<String, Map<String, Set<String>>>> result = new HashMap<>();
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?group ?source ?property ?action\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup ?group ;\n" +
                    "          odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?target ;\n" +
                    "              odrl:action ?action ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target onto:dataSource ?source ;\n" +
                    "          onto:dataProperty ?property .\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    String groupUri = solution.getResource("group").getURI();
                    String groupId = groupUri.substring(groupUri.lastIndexOf('/') + 1);

                    String source = solution.getLiteral("source").getString();
                    String property = solution.getLiteral("property").getString();
                    String actionUri = solution.getResource("action").getURI();
                    String action = actionUri.substring(actionUri.lastIndexOf('/') + 1);

                    // Make sure all levels of the map are initialized properly
                    if (!result.containsKey(groupId)) {
                        result.put(groupId, new HashMap<>());
                    }

                    Map<String, Map<String, Set<String>>> sourceMap = result.get(groupId);
                    if (!sourceMap.containsKey(source)) {
                        sourceMap.put(source, new HashMap<>());
                    }

                    Map<String, Set<String>> propertyMap = sourceMap.get(source);
                    if (!propertyMap.containsKey(property)) {
                        propertyMap.put(property, new HashSet<>());
                    }

                    // Now add the action
                    propertyMap.get(property).add(action);
                }
            }

            return result;
        } finally {
            dataset.end();
        }
    }
}