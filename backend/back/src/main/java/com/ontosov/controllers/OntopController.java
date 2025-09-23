package com.ontosov.controllers;

import com.ontosov.services.OntopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ontop")
public class OntopController {

    @Autowired
    private OntopService ontopService;

    @GetMapping("/person/{taxId}/controller/{controllerId}")
    public ResponseEntity<Map<String, Map<String, Object>>> getPersonData(
            @PathVariable String taxId,
            @PathVariable Long controllerId) {
        try {
            String query = ontopService.getPersonDataQuery();
            List<Map<String, String>> results = ontopService.executeQuery(controllerId, taxId, query);

            Map<String, Map<String, Object>> formattedResults = new HashMap<>();

            // Group results by source
            Map<String, List<Map<String, String>>> resultsBySource = results.stream()
                    .collect(Collectors.groupingBy(m -> m.get("source")));

            for (Map.Entry<String, List<Map<String, String>>> sourceEntry : resultsBySource.entrySet()) {
                String source = sourceEntry.getKey();
                List<Map<String, String>> sourceResults = sourceEntry.getValue();

                Map<String, Object> sourceData = new HashMap<>();

                // Separate person data from transactional data
                Map<String, List<Map<String, String>>> personResults = new HashMap<>();
                Map<String, List<Map<String, String>>> transactionalResults = new HashMap<>();

                for (Map<String, String> result : sourceResults) {
                    String entityType = extractEntityType(result.get("entity"));
                    String parentEntityType = extractEntityType(result.get("parentEntity"));
                    String entityUri = result.get("entity");
                    String parentEntityUri = result.get("parentEntity");

                    // Add debugging
                    // System.out.println("DEBUG - Entity: " + entityUri + " -> Type: " + entityType);
                    // System.out.println("DEBUG - Parent: " + parentEntityUri + " -> Type: " + parentEntityType);
                    // System.out.println("DEBUG - Property: " + result.get("property") + " = " + result.get("value"));
                    // System.out.println("---");

                    // Handle Person properties correctly
                    if ("Person".equals(entityType) && "Person".equals(parentEntityType)) {
                        // Genuine Person properties
                        personResults.computeIfAbsent("Person", k -> new ArrayList<>()).add(result);
                    } else if ("Person".equals(entityType) && !"Person".equals(parentEntityType)) {
                        // Person entity appearing as child of Order/MedicalEntity - this is wrong, skip it
                        System.out.println("SKIPPING: Person property with non-Person parent: " + result.get("property"));
                        continue;
                    } else if (!entityUri.equals(parentEntityUri)) {
                        // Genuine second-level entity (Product in Order)
                        String parentKey = parentEntityType + ":" + parentEntityUri;
                        transactionalResults.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(result);
                    } else {
                        // First-level entity (Order, MedicalEntity)
                        String entityKey = entityType + ":" + entityUri;
                        transactionalResults.computeIfAbsent(entityKey, k -> new ArrayList<>()).add(result);
                    }
                }

                // Process person data
                if (personResults.containsKey("Person")) {
                    Map<String, Object> personProperties = new HashMap<>();
                    personResults.get("Person").forEach(item -> {
                        String propertyName = item.get("property").replace("http://schema.org/", "");
                        personProperties.put(propertyName, item.get("value"));
                    });
                    sourceData.put("Person", personProperties);
                }

                // Process and merge transactional data
                Map<String, List<Map<String, Object>>> entitiesByType = new HashMap<>();

                for (Map.Entry<String, List<Map<String, String>>> entry : transactionalResults.entrySet()) {
                    String key = entry.getKey();
                    String[] parts = key.split(":", 2);
                    String entityType = parts[0];
                    String entityId = parts[1];

                    // Create entity with merged properties
                    Map<String, Object> entity = new HashMap<>();
                    entity.put("entityId", entityId);

                    Map<String, String> properties = new HashMap<>();
                    entry.getValue().forEach(item -> {
                        String propertyName = item.get("property").replace("http://schema.org/", "");
                        properties.put(propertyName, item.get("value"));
                    });
                    entity.put("properties", properties);

                    entitiesByType.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entity);
                }

                // Add to source data
                entitiesByType.forEach((entityType, entities) -> {
                    sourceData.put(entityType, entities);
                });

                formattedResults.put(source, sourceData);
            }

            return ResponseEntity.ok(formattedResults);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractEntityType(String entityUri) {
        if (entityUri.contains("#")) {
            String afterHash = entityUri.substring(entityUri.lastIndexOf('#') + 1);
            if (afterHash.contains("/")) {
                return afterHash.substring(0, afterHash.indexOf("/"));
            }
            return afterHash;
        } else if (entityUri.contains("/")) {
            String[] parts = entityUri.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].matches("\\d+")) { // Skip numeric IDs
                    return parts[i];
                }
            }
        }
        return "Unknown";
    }
}