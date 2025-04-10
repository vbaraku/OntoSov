package com.ontosov.services;

import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ontosov.dto.PolicyAssignmentDTO;
import com.ontosov.dto.PolicyGroupDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ODRLService {
    private static final Logger log = LoggerFactory.getLogger(ODRLService.class);
    private static final String ONTOP_DIR = "src/main/resources/ontop/controllers/";
    private static final String ONTOSOV_NS = "http://ontosov.org/policy#";
    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";
    private static final String SCHEMA_NS = "http://schema.org/";

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Dataset dataset;
    private final Model odrlModel;

    // ODRL vocabulary
    private final Resource policyResource;
    private final Resource permissionResource;
    private final Resource prohibitionResource;
    private final Resource dutyResource;
    private final Resource constraintResource;
    private final Property targetProperty;
    private final Property actionProperty;
    private final Property assignerProperty;
    private final Property assigneeProperty;
    private final Property constraintProperty;
    private final Property purposeProperty;
    private final Property dateTimeProperty;
    private final Property groupProperty;

    // New ODRL properties for consequences
    private final Property consequenceProperty;
    private final Property compensationProperty;
    private final Property notificationTypeProperty;

    // New ODRL properties for AI restrictions
    private final Resource aiTrainingAction;
    private final Property aiAlgorithmProperty;
    private final Property allowAiTrainingProperty;
    private final Property leftOperandProperty;
    private final Property operatorProperty;
    private final Property rightOperandProperty;

    // ONTOSOV vocabulary
    private final Property dataSourceProperty;
    private final Property dataPropertyProperty;

    public ODRLService() {
        // Initialize dataset and model
        String triplestorePath = "src/main/resources/triplestore";
        try {
            Files.createDirectories(Paths.get(triplestorePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create triplestore directory", e);
        }

        this.dataset = TDB2Factory.connectDataset(triplestorePath);
        this.odrlModel = dataset.getNamedModel(ODRL_NS + "policies");

        // Initialize ODRL vocabulary
        this.policyResource = odrlModel.createResource(ODRL_NS + "Policy");
        this.permissionResource = odrlModel.createResource(ODRL_NS + "Permission");
        this.prohibitionResource = odrlModel.createResource(ODRL_NS + "Prohibition");
        this.dutyResource = odrlModel.createResource(ODRL_NS + "Duty");
        this.constraintResource = odrlModel.createResource(ODRL_NS + "Constraint");
        this.targetProperty = odrlModel.createProperty(ODRL_NS, "target");
        this.actionProperty = odrlModel.createProperty(ODRL_NS, "action");
        this.assignerProperty = odrlModel.createProperty(ODRL_NS, "assigner");
        this.assigneeProperty = odrlModel.createProperty(ODRL_NS, "assignee");
        this.constraintProperty = odrlModel.createProperty(ODRL_NS, "constraint");
        this.purposeProperty = odrlModel.createProperty(ODRL_NS, "purpose");
        this.dateTimeProperty = odrlModel.createProperty(ODRL_NS, "dateTime");
        this.groupProperty = odrlModel.createProperty(ONTOSOV_NS, "policyGroup");

        // Initialize consequence properties
        this.consequenceProperty = odrlModel.createProperty(ODRL_NS, "consequence");
        this.compensationProperty = odrlModel.createProperty(ODRL_NS, "compensation");
        this.notificationTypeProperty = odrlModel.createProperty(ONTOSOV_NS, "notificationType");

        // Initialize AI restriction properties
        this.aiTrainingAction = odrlModel.createResource(ONTOSOV_NS + "aiTraining");
        this.aiAlgorithmProperty = odrlModel.createProperty(ONTOSOV_NS, "aiAlgorithm");
        this.allowAiTrainingProperty = odrlModel.createProperty(ONTOSOV_NS, "allowAiTraining");
        this.leftOperandProperty = odrlModel.createProperty(ODRL_NS, "leftOperand");
        this.operatorProperty = odrlModel.createProperty(ODRL_NS, "operator");
        this.rightOperandProperty = odrlModel.createProperty(ODRL_NS, "rightOperand");

        // Initialize custom properties
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
                            createPolicy(policyGroupId, subjectId, dataSource, property, action,
                                    policyGroup.getConstraints(),
                                    policyGroup.getConsequences(),
                                    policyGroup.getAiRestrictions());
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
                              String property, String action, Map<String, Object> constraints,
                              Map<String, Object> consequences, Map<String, Object> aiRestrictions) {
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
            if (constraints.containsKey("purpose") && constraints.get("purpose") != null &&
                    !constraints.get("purpose").toString().isEmpty()) {
                constraint.addProperty(purposeProperty, constraints.get("purpose").toString());
            }

            // Expiration constraint
            if (constraints.containsKey("expiration") && constraints.get("expiration") != null) {
                constraint.addProperty(dateTimeProperty, constraints.get("expiration").toString());
            }

            // Only add the constraint if it has any properties
            if (constraint.listProperties().hasNext()) {
                permission.addProperty(constraintProperty, constraint);
            }
        }

        // Add consequences if present
        if (consequences != null && !consequences.isEmpty()) {
            String notificationType = consequences.containsKey("notificationType") &&
                    consequences.get("notificationType") != null ?
                    consequences.get("notificationType").toString() : null;

            String compensationAmount = consequences.containsKey("compensationAmount") &&
                    consequences.get("compensationAmount") != null &&
                    !consequences.get("compensationAmount").toString().isEmpty() ?
                    consequences.get("compensationAmount").toString() : null;

            if (notificationType != null || compensationAmount != null) {
                Resource consequence = odrlModel.createResource();
                consequence.addProperty(RDF.type, dutyResource);

                // Add notification type if specified
                if (notificationType != null) {
                    consequence.addProperty(actionProperty, odrlModel.createResource(ODRL_NS + "notify"));
                    // Use the target here to specify who to notify (the subject)
                    consequence.addProperty(targetProperty,
                            odrlModel.createResource(ONTOSOV_NS + "subject-" + subjectId));
                    // Store the notification type as a custom property
                    consequence.addProperty(notificationTypeProperty, notificationType);
                }

                // Add compensation if specified
                if (compensationAmount != null) {
                    // If there's already a notification action, create another duty for compensation
                    Resource compensationDuty = notificationType != null ?
                            odrlModel.createResource() : consequence;

                    if (notificationType != null) {
                        compensationDuty.addProperty(RDF.type, dutyResource);
                    }

                    compensationDuty.addProperty(actionProperty, odrlModel.createResource(ODRL_NS + "compensate"));
                    compensationDuty.addProperty(compensationProperty, compensationAmount);

                    if (notificationType != null) {
                        permission.addProperty(consequenceProperty, compensationDuty);
                    }
                }

                permission.addProperty(consequenceProperty, consequence);
            }
        }

        // Add permission to policy
        policy.addProperty(odrlModel.createProperty(ODRL_NS, "permission"), permission);

        // Handle AI training restrictions
        if (aiRestrictions != null) {
            Boolean allowAiTraining = aiRestrictions.containsKey("allowAiTraining") ?
                    (Boolean) aiRestrictions.get("allowAiTraining") : true;

            String aiAlgorithm = aiRestrictions.containsKey("aiAlgorithm") &&
                    aiRestrictions.get("aiAlgorithm") != null ?
                    aiRestrictions.get("aiAlgorithm").toString() : null;

            if (Boolean.FALSE.equals(allowAiTraining)) {
                // Create prohibition for AI training
                Resource prohibition = odrlModel.createResource();
                prohibition.addProperty(RDF.type, prohibitionResource);
                prohibition.addProperty(targetProperty, target);
                prohibition.addProperty(actionProperty, aiTrainingAction);
                prohibition.addProperty(assignerProperty,
                        odrlModel.createResource(ONTOSOV_NS + "subject-" + subjectId));
                prohibition.addProperty(assigneeProperty,
                        odrlModel.createResource(ONTOSOV_NS + "allControllers"));

                // Add prohibition to policy
                policy.addProperty(odrlModel.createProperty(ODRL_NS, "prohibition"), prohibition);
            }
            else if (aiAlgorithm != null && !aiAlgorithm.isEmpty()) {
                // Create permission with constraint for specific AI algorithm
                Resource aiConstraint = odrlModel.createResource();
                aiConstraint.addProperty(RDF.type, constraintResource);
                aiConstraint.addProperty(leftOperandProperty, aiAlgorithmProperty);
                aiConstraint.addProperty(operatorProperty, odrlModel.createResource(ODRL_NS + "eq"));
                aiConstraint.addProperty(rightOperandProperty, aiAlgorithm);

                Resource aiPermission = odrlModel.createResource();
                aiPermission.addProperty(RDF.type, permissionResource);
                aiPermission.addProperty(targetProperty, target);
                aiPermission.addProperty(actionProperty, aiTrainingAction);
                aiPermission.addProperty(constraintProperty, aiConstraint);
                aiPermission.addProperty(assignerProperty,
                        odrlModel.createResource(ONTOSOV_NS + "subject-" + subjectId));
                aiPermission.addProperty(assigneeProperty,
                        odrlModel.createResource(ONTOSOV_NS + "allControllers"));

                // Add permission to policy
                policy.addProperty(odrlModel.createProperty(ODRL_NS, "permission"), aiPermission);
            }
        }
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

    public void cleanupPoliciesForGroup(String groupId, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);

        try {
            // Only select policies related to this group
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?policy ?permission\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup onto:" + groupId + " ;\n" +
                    "          odrl:permission ?permission .\n" +
                    "}";

            List<Resource> policiesToRemove = new ArrayList<>();
            List<Resource> permissionsToRemove = new ArrayList<>();

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();
                    policiesToRemove.add(solution.getResource("policy"));
                    permissionsToRemove.add(solution.getResource("permission"));
                }
            }

            // Remove the policies and permissions, but NOT the targets
            // since targets could be used by other policy groups
            for (Resource policy : policiesToRemove) {
                odrlModel.removeAll(policy, null, null);
            }

            for (Resource permission : permissionsToRemove) {
                odrlModel.removeAll(permission, null, null);
            }

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw new RuntimeException("Failed to cleanup ODRL policies: " + e.getMessage(), e);
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

                    // Extract action name without namespace
                    String action;
                    if (actionUri.contains("#")) {
                        action = actionUri.substring(actionUri.lastIndexOf('#') + 1);
                    } else if (actionUri.contains("/")) {
                        action = actionUri.substring(actionUri.lastIndexOf('/') + 1);
                    } else {
                        action = actionUri; // Fallback case
                    }

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

            // Also query for prohibitions (especially for AI training)
            String prohibitionQuery = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?group ?source ?property ?action\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup ?group ;\n" +
                    "          odrl:prohibition ?prohibition .\n" +
                    "  ?prohibition odrl:target ?target ;\n" +
                    "              odrl:action ?action ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target onto:dataSource ?source ;\n" +
                    "          onto:dataProperty ?property .\n" +
                    "}";

            Query prohibQuery = QueryFactory.create(prohibitionQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(prohibQuery, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    String groupUri = solution.getResource("group").getURI();
                    String groupId = groupUri.substring(groupUri.lastIndexOf('/') + 1);

                    String source = solution.getLiteral("source").getString();
                    String property = solution.getLiteral("property").getString();
                    String actionUri = solution.getResource("action").getURI();

                    // Extract action name without namespace
                    String action;
                    if (actionUri.contains("#")) {
                        action = actionUri.substring(actionUri.lastIndexOf('#') + 1);
                    } else if (actionUri.contains("/")) {
                        action = actionUri.substring(actionUri.lastIndexOf('/') + 1);
                    } else {
                        action = actionUri; // Fallback case
                    }

                    // For prohibitions, we'll add them with "prohibit-" prefix to distinguish
                    String prohibitAction = "prohibit-" + action;

                    // Initialize maps as needed
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

                    // Add the prohibition action
                    propertyMap.get(property).add(prohibitAction);
                }
            }

            // For each policy group, fetch and add the constraints, consequences, and AI restrictions
            for (String groupId : result.keySet()) {
                try {
                    // Get the policy group details
                    Resource policyGroup = odrlModel.getResource(ONTOSOV_NS + groupId);

                    // We need to query from the policyModel in PolicyGroupService, not the odrlModel
                    // Since this data is stored in a different way, we'll add special actions to the result
                    // that will be interpreted by the UI

                    // Add a special action with metadata about this policy group that the UI can use
                    // This is a hack, but it allows us to pass this information to the UI
                    for (Map<String, Map<String, Set<String>>> sourceMap : result.values()) {
                        for (Map<String, Set<String>> propertyMap : sourceMap.values()) {
                            for (Set<String> actions : propertyMap.values()) {
                                // Add policy group ID as a special action
                                actions.add("__policyGroupId:" + groupId);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue processing
                    log.error("Error processing policy group details: " + e.getMessage(), e);
                }
            }

            return result;
        } finally {
            dataset.end();
        }
    }
}