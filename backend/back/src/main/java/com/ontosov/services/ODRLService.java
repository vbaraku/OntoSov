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

import com.ontosov.constants.DPVPurpose;
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
    private static final String ODS_NS = "https://w3id.org/ods/";
    private static final String DPV_NS = "https://w3id.org/dpv#";
    private static final String OAC_NS = "https://w3id.org/oac/";

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

    // ODS transformation actions
    private final Resource anonymizeAction;
    private final Resource pseudonymizeAction;
    private final Resource encryptAction;
    private final Resource transformAction;

    // DPV purpose property (OAC vocabulary)
    private final Property oacPurposeProperty;

    private final Resource readAction;
    private final Resource useAction;
    private final Resource shareAction;
    private final Resource aggregateAction;
    private final Resource modifyAction;
    private final Resource allControllersResource;
    private final Property odrlPermissionProperty;
    private final Property odrlProhibitionProperty;
    private final Property odrlDutyProperty;
    private final Property entityTypeProperty;
    private final Property entityIdProperty;

    private final Resource isAOperator;
    private final Resource eqOperator;

    // Asset collection: one per policy group; governed data elements are members via odrl:partOf
    private final Resource assetCollectionResource;
    private final Property partOfProperty;

    private static final List<String> REGULAR_ACTIONS = Arrays.asList("read", "use", "share", "aggregate", "modify");

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

        // Initialize ODS transformation actions
        // odrl:anonymize and odrl:transform come from the ODRL Common Vocabulary, ods:encrypt from the
        // ODS profile; ODS defines pseudonymisation only as a state, so the action is in our namespace.
        this.anonymizeAction = odrlModel.createResource(ODRL_NS + "anonymize");
        this.pseudonymizeAction = odrlModel.createResource(ONTOSOV_NS + "pseudonymize");
        this.encryptAction = odrlModel.createResource(ODS_NS + "encrypt");
        this.transformAction = odrlModel.createResource(ODRL_NS + "transform");

        // Initialize DPV purpose property (OAC vocabulary)
        this.oacPurposeProperty = odrlModel.createProperty(OAC_NS, "Purpose");

        // Action resources
        this.readAction = odrlModel.createResource(ODRL_NS + "read");
        this.useAction = odrlModel.createResource(ODRL_NS + "use");
        this.shareAction = odrlModel.createResource(ODRL_NS + "share");
        this.aggregateAction = odrlModel.createResource(ODRL_NS + "aggregate");
        this.modifyAction = odrlModel.createResource(ODRL_NS + "modify");

        // Common resources
        this.allControllersResource = odrlModel.createResource(ONTOSOV_NS + "allControllers");

        // ODRL properties
        this.odrlPermissionProperty = odrlModel.createProperty(ODRL_NS, "permission");
        this.odrlProhibitionProperty = odrlModel.createProperty(ODRL_NS, "prohibition");
        this.odrlDutyProperty = odrlModel.createProperty(ODRL_NS, "duty");
        this.entityTypeProperty = odrlModel.createProperty(ONTOSOV_NS, "entityType");
        this.entityIdProperty = odrlModel.createProperty(ONTOSOV_NS, "entityId");

        // Operator resources
        this.isAOperator = odrlModel.createResource(ODRL_NS + "isA");
        this.eqOperator = odrlModel.createResource(ODRL_NS + "eq");

        // Asset collection vocabulary
        this.assetCollectionResource = odrlModel.createResource(ODRL_NS + "AssetCollection");
        this.partOfProperty = odrlModel.createProperty(ODRL_NS, "partOf");
    }

    /**
     * SPARQL term for a requested action: AI training lives in the framework's own namespace,
     * every other action is an ODRL action.
     */
    private String actionTerm(String action) {
        return "aiTraining".equals(action) ? "onto:aiTraining" : "odrl:" + action;
    }

    private Resource getAssetCollection(String policyGroupId) {
        return odrlModel.createResource(ONTOSOV_NS + "assets-" + policyGroupId);
    }

    /**
     * Returns cached action resource for performance.
     * Avoids creating new Resource objects for every policy.
     */
    private Resource getCachedActionResource(String action) {
        return switch (action) {
            case "read" -> readAction;
            case "use" -> useAction;
            case "share" -> shareAction;
            case "aggregate" -> aggregateAction;
            case "modify" -> modifyAction;
            default -> odrlModel.createResource(ODRL_NS + action);
        };
    }

    /**
     * Generates the ODRL representation of a policy group and its assignments.
     * Each policy group is expressed as exactly one ODRL policy whose rules target the group's
     * odrl:AssetCollection; every assigned data element is a member of that collection via odrl:partOf.
     * Runs inside the caller's write transaction.
     */
    public void generatePoliciesFromAssignment(String policyGroupId, PolicyGroupDTO policyGroup,
                                               PolicyAssignmentDTO assignmentDTO, Long subjectId) {
        // Work within the existing transaction - no dataset.begin/end here
        try {
            // Clear the group's previous policy and collection membership (destructive replacement)
            cleanupPoliciesForGroupInTransaction(policyGroupId, subjectId);

            Resource collection = getAssetCollection(policyGroupId);
            collection.addProperty(RDF.type, assetCollectionResource);
            collection.addProperty(groupProperty, odrlModel.createResource(ONTOSOV_NS + policyGroupId));

            // Property-level members
            if (assignmentDTO.getPropertyAssignments() != null) {
                for (Map.Entry<String, Set<String>> sourceEntry : assignmentDTO.getPropertyAssignments().entrySet()) {
                    String dataSource = sourceEntry.getKey();
                    for (String property : sourceEntry.getValue()) {
                        Resource member = propertyTarget(subjectId, dataSource, property);
                        member.addProperty(partOfProperty, collection);
                    }
                }
            }

            // Entity-level members
            if (assignmentDTO.getEntityAssignments() != null) {
                for (Map.Entry<String, Set<String>> sourceEntry : assignmentDTO.getEntityAssignments().entrySet()) {
                    String dataSource = sourceEntry.getKey();
                    for (String entityId : sourceEntry.getValue()) {
                        String entityType = extractEntityTypeFromUri(entityId);
                        Resource member = entityTarget(dataSource, entityType, entityId);
                        member.addProperty(partOfProperty, collection);
                    }
                }
            }

            // The group's single policy, targeting the collection
            createGroupPolicy(policyGroupId, policyGroup, subjectId, collection);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ODRL policies: " + e.getMessage(), e);
        }
    }

    /**
     * Rebuilds a policy group's ODRL policy after its content has been amended, leaving the
     * asset collection and its members untouched. Cost is independent of how many elements the
     * group governs. Runs inside the caller's write transaction. Does nothing for a group that
     * has never been assigned to data.
     */
    public void rebuildGroupPolicyInTransaction(String policyGroupId, PolicyGroupDTO policyGroup, Long subjectId) {
        Resource collection = getAssetCollection(policyGroupId);
        if (!odrlModel.contains(collection, RDF.type, assetCollectionResource)) {
            return;
        }
        removeGroupPolicies(policyGroupId);
        createGroupPolicy(policyGroupId, policyGroup, subjectId, collection);
    }

    /** Property-level data element: onto:data-{subjectId}-{source}-{property}. */
    private Resource propertyTarget(Long subjectId, String dataSource, String property) {
        String targetId = "data-" + subjectId + "-" +
                dataSource.replaceAll("[^a-zA-Z0-9]", "_") + "-" +
                property.replaceAll("[^a-zA-Z0-9]", "_");
        Resource target = odrlModel.createResource(ONTOSOV_NS + targetId);
        if (!target.hasProperty(dataSourceProperty)) {
            target.addProperty(dataSourceProperty, dataSource);
            target.addProperty(dataPropertyProperty, property);
        }
        return target;
    }

    /** Entity-level data element: onto:entity-{entityType}-{entityIri}. */
    private Resource entityTarget(String dataSource, String entityType, String entityId) {
        Resource target = odrlModel.createResource(ONTOSOV_NS + "entity-" + entityType + "-" + entityId);
        if (!target.hasProperty(dataSourceProperty)) {
            target.addProperty(dataSourceProperty, dataSource);
            target.addProperty(entityTypeProperty, entityType);
            target.addProperty(entityIdProperty, entityId);
        }
        return target;
    }

    private Resource newRule(Resource ruleType, Resource target, Resource action, Resource assigner) {
        Resource rule = odrlModel.createResource();
        rule.addProperty(RDF.type, ruleType);
        rule.addProperty(targetProperty, target);
        rule.addProperty(actionProperty, action);
        rule.addProperty(assignerProperty, assigner);
        // All controllers by default; the ODRL structure supports per-controller assignees
        rule.addProperty(assigneeProperty, allControllersResource);
        return rule;
    }

    /**
     * Creates the single ODRL policy of a policy group: one permission per enabled action (each
     * carrying the group's constraints, consequences and transformation duties) and, if AI
     * restrictions are configured, one AI training rule. Every rule targets the asset collection.
     */
    private void createGroupPolicy(String policyGroupId, PolicyGroupDTO group, Long subjectId, Resource collection) {
        Resource policy = odrlModel.createResource(ODRL_NS + "policy-" + UUID.randomUUID());
        policy.addProperty(RDF.type, policyResource);
        policy.addProperty(groupProperty, odrlModel.createResource(ONTOSOV_NS + policyGroupId));

        Resource assigner = odrlModel.createResource(ONTOSOV_NS + "subject-" + subjectId);
        Map<String, Boolean> permissions = group.getPermissions() != null ? group.getPermissions() : Collections.emptyMap();

        for (String action : REGULAR_ACTIONS) {
            if (Boolean.TRUE.equals(permissions.get(action))) {
                Resource permission = newRule(permissionResource, collection, getCachedActionResource(action), assigner);
                addConstraints(permission, group.getConstraints());
                addConsequences(permission, group.getConsequences(), assigner);
                addTransformations(permission, group.getTransformations());
                policy.addProperty(odrlPermissionProperty, permission);
            }
        }

        Map<String, Object> ai = group.getAiRestrictions();
        if (ai != null && !ai.isEmpty()) {
            boolean allowAiTraining = !ai.containsKey("allowAiTraining") || ai.get("allowAiTraining") == null
                    || Boolean.parseBoolean(ai.get("allowAiTraining").toString());
            String aiAlgorithm = ai.get("aiAlgorithm") != null ? ai.get("aiAlgorithm").toString().trim() : "";

            if (!allowAiTraining) {
                Resource prohibition = newRule(prohibitionResource, collection, aiTrainingAction, assigner);
                policy.addProperty(odrlProhibitionProperty, prohibition);
            } else {
                Resource aiPermission = newRule(permissionResource, collection, aiTrainingAction, assigner);
                if (!aiAlgorithm.isEmpty()) {
                    Resource aiConstraint = odrlModel.createResource();
                    aiConstraint.addProperty(RDF.type, constraintResource);
                    aiConstraint.addProperty(leftOperandProperty, aiAlgorithmProperty);
                    aiConstraint.addProperty(operatorProperty, eqOperator);
                    aiConstraint.addProperty(rightOperandProperty, aiAlgorithm);
                    aiPermission.addProperty(constraintProperty, aiConstraint);
                }
                policy.addProperty(odrlPermissionProperty, aiPermission);
            }
        }
    }

    private void addConstraints(Resource permission, Map<String, Object> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return;
        }
        // Purpose constraint using the OAC pattern with a DPV purpose
        if (constraints.get("purpose") != null && !constraints.get("purpose").toString().isEmpty()) {
            String purposeValue = constraints.get("purpose").toString();
            String purposeUri = purposeValue;
            if (!purposeValue.startsWith("http")) {
                String dpvUri = DPVPurpose.getUri(purposeValue);
                if (dpvUri != null) {
                    purposeUri = dpvUri;
                }
            }
            Resource purposeConstraint = odrlModel.createResource();
            purposeConstraint.addProperty(RDF.type, constraintResource);
            purposeConstraint.addProperty(leftOperandProperty, oacPurposeProperty);
            purposeConstraint.addProperty(operatorProperty, isAOperator);
            purposeConstraint.addProperty(rightOperandProperty, odrlModel.createResource(purposeUri));
            permission.addProperty(constraintProperty, purposeConstraint);
        }
        // Expiration constraint
        if (constraints.get("expiration") != null && !constraints.get("expiration").toString().isEmpty()) {
            Resource expirationConstraint = odrlModel.createResource();
            expirationConstraint.addProperty(RDF.type, constraintResource);
            expirationConstraint.addProperty(dateTimeProperty, constraints.get("expiration").toString());
            permission.addProperty(constraintProperty, expirationConstraint);
        }
    }

    private void addConsequences(Resource permission, Map<String, Object> consequences, Resource subject) {
        if (consequences == null || consequences.isEmpty()) {
            return;
        }
        if (consequences.get("notificationType") != null && !consequences.get("notificationType").toString().isEmpty()) {
            Resource notifyDuty = odrlModel.createResource();
            notifyDuty.addProperty(RDF.type, dutyResource);
            notifyDuty.addProperty(actionProperty, odrlModel.createResource(ODRL_NS + "notify"));
            notifyDuty.addProperty(targetProperty, subject);
            notifyDuty.addProperty(notificationTypeProperty, consequences.get("notificationType").toString());
            permission.addProperty(consequenceProperty, notifyDuty);
        }
        if (consequences.get("compensationAmount") != null && !consequences.get("compensationAmount").toString().isEmpty()) {
            Resource compensationDuty = odrlModel.createResource();
            compensationDuty.addProperty(RDF.type, dutyResource);
            compensationDuty.addProperty(actionProperty, odrlModel.createResource(ODRL_NS + "compensate"));
            compensationDuty.addProperty(compensationProperty, consequences.get("compensationAmount").toString());
            permission.addProperty(consequenceProperty, compensationDuty);
        }
    }

    private void addTransformations(Resource permission, List<String> transformations) {
        if (transformations == null || transformations.isEmpty()) {
            return;
        }
        for (String transformation : transformations) {
            Resource transformationAction = switch (transformation.toLowerCase()) {
                case "anonymize" -> anonymizeAction;
                case "pseudonymize" -> pseudonymizeAction;
                case "encrypt" -> encryptAction;
                case "transform" -> transformAction;
                default -> null;
            };
            if (transformationAction == null) {
                log.warn("Unknown transformation action: " + transformation);
                continue;
            }
            Resource transformationDuty = odrlModel.createResource();
            transformationDuty.addProperty(RDF.type, dutyResource);
            transformationDuty.addProperty(actionProperty, transformationAction);
            permission.addProperty(odrlDutyProperty, transformationDuty);
        }
    }

    private String extractEntityTypeFromUri(String entityUri) {
        // Extract entity type from URI like "http://example.org/resource#Order/1" -> "Order"
        if (entityUri.contains("#")) {
            String afterHash = entityUri.substring(entityUri.lastIndexOf('#') + 1);
            if (afterHash.contains("/")) {
                return afterHash.substring(0, afterHash.indexOf("/"));
            }
        }
        return "Entity"; // Fallback
    }

    public boolean checkPropertyAccess(Long subjectId, Long controllerId, String dataSource, String property, String action) {
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "ASK {\n" +
                    "  ?policy rdf:type odrl:Policy .\n" +
                    "  ?policy odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?collection ;\n" +
                    "              odrl:action " + actionTerm(action) + " ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " ;\n" +
                    "              odrl:assignee ?assignee .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource \"" + dataSource + "\" ;\n" +
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

    public boolean checkEntityAccess(Long subjectId, Long controllerId, String dataSource, String entityId, String action) {
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "ASK {\n" +
                    "  ?policy rdf:type odrl:Policy .\n" +
                    "  ?policy odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?collection ;\n" +
                    "              odrl:action " + actionTerm(action) + " ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " ;\n" +
                    "              odrl:assignee ?assignee .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource \"" + dataSource + "\" ;\n" +
                    "          onto:entityId \"" + entityId + "\" .\n" +
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

    /**
     * Check if ANY policy exists for a given property (regardless of permissions)
     * Used to distinguish between "no policy" vs "policy denies access"
     */
    public boolean policyExistsForProperty(Long subjectId, String dataSource, String property) {
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "ASK {\n" +
                    "  ?policy rdf:type odrl:Policy .\n" +
                    "  ?policy ?permissionOrProhibition ?permOrProhib .\n" +
                    "  ?permOrProhib odrl:target ?collection ;\n" +
                    "                odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource \"" + dataSource + "\" ;\n" +
                    "          onto:dataProperty \"" + property + "\" .\n" +
                    "  FILTER(?permissionOrProhibition = odrl:permission || ?permissionOrProhibition = odrl:prohibition)\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                return qexec.execAsk();
            }
        } finally {
            dataset.end();
        }
    }

    /**
     * Check if ANY policy exists for a given entity (regardless of permissions)
     * Used to distinguish between "no policy" vs "policy denies access"
     */
    public boolean policyExistsForEntity(Long subjectId, String dataSource, String entityId) {
        dataset.begin(ReadWrite.READ);

        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "ASK {\n" +
                    "  ?policy rdf:type odrl:Policy .\n" +
                    "  ?policy ?permissionOrProhibition ?permOrProhib .\n" +
                    "  ?permOrProhib odrl:target ?collection ;\n" +
                    "                odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource \"" + dataSource + "\" ;\n" +
                    "          onto:entityId \"" + entityId + "\" .\n" +
                    "  FILTER(?permissionOrProhibition = odrl:permission || ?permissionOrProhibition = odrl:prohibition)\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                return qexec.execAsk();
            }
        } finally {
            dataset.end();
        }
    }

    public Map<String, Object> getAssignmentsForPolicyGroup(String groupId, Long subjectId) {
        Map<String, Set<String>> propertyAssignments = new HashMap<>();
        Map<String, Set<String>> entityAssignments = new HashMap<>();

        dataset.begin(ReadWrite.READ);
        try {
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT DISTINCT ?source ?property ?entityId\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup onto:" + groupId + " ;\n" +
                    "          ?ruleType ?rule .\n" +
                    "  ?rule odrl:target ?collection ;\n" +
                    "        odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  FILTER(?ruleType = odrl:permission || ?ruleType = odrl:prohibition)\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource ?source .\n" +
                    "  OPTIONAL { ?target onto:dataProperty ?property }\n" +
                    "  OPTIONAL { ?target onto:entityId ?entityId }\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();
                    String source = solution.getLiteral("source").getString();

                    // Check if this is a property assignment
                    if (solution.contains("property")) {
                        String property = solution.getLiteral("property").getString();
                        propertyAssignments.computeIfAbsent(source, k -> new HashSet<>()).add(property);
                    }

                    // Check if this is an entity assignment
                    if (solution.contains("entityId")) {
                        String entityId = solution.getLiteral("entityId").getString();
                        entityAssignments.computeIfAbsent(source, k -> new HashSet<>()).add(entityId);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("propertyAssignments", propertyAssignments);
            result.put("entityAssignments", entityAssignments);
            return result;

        } finally {
            dataset.end();
        }
    }

    /**
     * Removes a policy group's ODRL policy (including its nested rules, constraints and duties)
     * and the membership of its asset collection. Data elements that no longer belong to any
     * collection are removed as well. Runs inside the caller's write transaction.
     */
    public void cleanupPoliciesForGroupInTransaction(String groupId, Long subjectId) {
        try {
            removeGroupPolicies(groupId);

            Resource collection = getAssetCollection(groupId);
            List<Resource> members = odrlModel.listSubjectsWithProperty(partOfProperty, collection).toList();
            for (Resource member : members) {
                odrlModel.remove(member, partOfProperty, collection);
                if (!member.hasProperty(partOfProperty)) {
                    odrlModel.removeAll(member, null, null);
                }
            }
            odrlModel.removeAll(collection, null, null);

        } catch (Exception e) {
            throw new RuntimeException("Failed to cleanup ODRL policies: " + e.getMessage(), e);
        }
    }

    /** Removes every ODRL policy linked to the group, together with its nested rule resources. */
    private void removeGroupPolicies(String groupId) {
        Resource groupResource = odrlModel.createResource(ONTOSOV_NS + groupId);
        List<Resource> policies = odrlModel.listSubjectsWithProperty(groupProperty, groupResource).toList();
        for (Resource policy : policies) {
            if (!policy.hasProperty(RDF.type, policyResource)) {
                continue; // e.g. the asset collection, which also carries onto:policyGroup
            }
            List<RDFNode> rules = new ArrayList<>();
            rules.addAll(odrlModel.listObjectsOfProperty(policy, odrlPermissionProperty).toList());
            rules.addAll(odrlModel.listObjectsOfProperty(policy, odrlProhibitionProperty).toList());
            odrlModel.removeAll(policy, null, null);
            for (RDFNode rule : rules) {
                if (rule.isAnon()) {
                    removeAnonTree(rule.asResource());
                }
            }
        }
    }

    /** Removes a blank-node resource and every blank node reachable from it. */
    private void removeAnonTree(Resource node) {
        List<RDFNode> children = new ArrayList<>();
        node.listProperties().forEachRemaining(stmt -> children.add(stmt.getObject()));
        odrlModel.removeAll(node, null, null);
        for (RDFNode child : children) {
            if (child.isAnon()) {
                removeAnonTree(child.asResource());
            }
        }
    }

    public void cleanupPoliciesForGroup(String groupId, Long subjectId) {
        dataset.begin(ReadWrite.WRITE);
        try {
            cleanupPoliciesForGroupInTransaction(groupId, subjectId);
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.end();
        }
    }

    public Map<String, Map<String, Map<String, Set<String>>>> getSubjectPolicies(Long subjectId) {
        Map<String, Map<String, Map<String, Set<String>>>> result = new HashMap<>();
        dataset.begin(ReadWrite.READ);

        try {
            // Query for both property and entity policies using UNION
            String queryString = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?group ?source ?dataKey ?action\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup ?group ;\n" +
                    "          odrl:permission ?permission .\n" +
                    "  ?permission odrl:target ?collection ;\n" +
                    "              odrl:action ?action ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource ?source .\n" +
                    "  {\n" +
                    "    # Property policies\n" +
                    "    ?target onto:dataProperty ?dataKey .\n" +
                    "  }\n" +
                    "  UNION\n" +
                    "  {\n" +
                    "    # Entity policies  \n" +
                    "    ?target onto:entityId ?dataKey .\n" +
                    "  }\n" +
                    "}";

            Query query = QueryFactory.create(queryString);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    String groupUri = solution.getResource("group").getURI();
                    String groupId = groupUri.substring(groupUri.lastIndexOf('/') + 1);

                    String source = solution.getLiteral("source").getString();
                    String dataKey = solution.getLiteral("dataKey").getString(); // property or entityId
                    String actionUri = solution.getResource("action").getURI();

                    // Extract action name without namespace
                    String action;
                    if (actionUri.contains("#")) {
                        action = actionUri.substring(actionUri.lastIndexOf('#') + 1);
                    } else if (actionUri.contains("/")) {
                        action = actionUri.substring(actionUri.lastIndexOf('/') + 1);
                    } else {
                        action = actionUri;
                    }

                    // Initialize nested maps
                    if (!result.containsKey(groupId)) {
                        result.put(groupId, new HashMap<>());
                    }

                    Map<String, Map<String, Set<String>>> sourceMap = result.get(groupId);
                    if (!sourceMap.containsKey(source)) {
                        sourceMap.put(source, new HashMap<>());
                    }

                    Map<String, Set<String>> dataKeyMap = sourceMap.get(source);
                    if (!dataKeyMap.containsKey(dataKey)) {
                        dataKeyMap.put(dataKey, new HashSet<>());
                    }

                    // Add the action
                    dataKeyMap.get(dataKey).add(action);
                }
            }

            // Query for prohibitions (both property and entity)
            String prohibitionQuery = "PREFIX onto: <" + ONTOSOV_NS + ">\n" +
                    "PREFIX odrl: <" + ODRL_NS + ">\n" +
                    "PREFIX rdf: <" + RDF.getURI() + ">\n" +
                    "SELECT ?group ?source ?dataKey ?action\n" +
                    "WHERE {\n" +
                    "  ?policy rdf:type odrl:Policy ;\n" +
                    "          onto:policyGroup ?group ;\n" +
                    "          odrl:prohibition ?prohibition .\n" +
                    "  ?prohibition odrl:target ?collection ;\n" +
                    "              odrl:action ?action ;\n" +
                    "              odrl:assigner onto:subject-" + subjectId + " .\n" +
                    "  ?target odrl:partOf ?collection ;\n" +
                    "          onto:dataSource ?source .\n" +
                    "  {\n" +
                    "    # Property policies\n" +
                    "    ?target onto:dataProperty ?dataKey .\n" +
                    "  }\n" +
                    "  UNION\n" +
                    "  {\n" +
                    "    # Entity policies\n" +
                    "    ?target onto:entityId ?dataKey .\n" +
                    "  }\n" +
                    "}";

            Query prohibQuery = QueryFactory.create(prohibitionQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(prohibQuery, odrlModel)) {
                ResultSet rs = qexec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution solution = rs.next();

                    String groupUri = solution.getResource("group").getURI();
                    String groupId = groupUri.substring(groupUri.lastIndexOf('/') + 1);

                    String source = solution.getLiteral("source").getString();
                    String dataKey = solution.getLiteral("dataKey").getString();
                    String actionUri = solution.getResource("action").getURI();

                    String action;
                    if (actionUri.contains("#")) {
                        action = actionUri.substring(actionUri.lastIndexOf('#') + 1);
                    } else if (actionUri.contains("/")) {
                        action = actionUri.substring(actionUri.lastIndexOf('/') + 1);
                    } else {
                        action = actionUri;
                    }

                    String prohibitAction = "prohibit-" + action;

                    // Initialize maps as needed
                    if (!result.containsKey(groupId)) {
                        result.put(groupId, new HashMap<>());
                    }
                    Map<String, Map<String, Set<String>>> sourceMap = result.get(groupId);
                    if (!sourceMap.containsKey(source)) {
                        sourceMap.put(source, new HashMap<>());
                    }
                    Map<String, Set<String>> dataKeyMap = sourceMap.get(source);
                    if (!dataKeyMap.containsKey(dataKey)) {
                        dataKeyMap.put(dataKey, new HashSet<>());
                    }

                    dataKeyMap.get(dataKey).add(prohibitAction);
                }
            }

            // Add policy group metadata
            for (String groupId : result.keySet()) {
                try {
                    for (Map<String, Map<String, Set<String>>> sourceMap : result.values()) {
                        for (Map<String, Set<String>> dataKeyMap : sourceMap.values()) {
                            for (Set<String> actions : dataKeyMap.values()) {
                                actions.add("__policyGroupId:" + groupId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing policy group details: " + e.getMessage(), e);
                }
            }

            return result;
        } finally {
            dataset.end();
        }
    }
}