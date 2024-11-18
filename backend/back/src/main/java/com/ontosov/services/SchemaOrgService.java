package com.ontosov.services;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaOrgService {
    private static final String SCHEMA_NS = "http://schema.org/";
    private Map<String, Set<String>> classPropertiesCache = new HashMap<>();
    private Set<String> classesCache = new HashSet<>();

    @Value("${schema.rdf.path}")
    private String schemaRdfPath;  // Set this in application.properties

    public SchemaOrgService() {
        initializeSchemaData();
    }

    private void initializeSchemaData() {
        try {
            Model model = ModelFactory.createDefaultModel();
            try (InputStream is = getClass().getResourceAsStream("/ontop/schema/schemaorg-current-https.rdf")) {
                if (is == null) {
                    throw new IllegalStateException("Could not find schema RDF file in classpath");
                }
                model.read(is, null, "RDF/XML");
            }

            // Get all classes
            ResIterator classIter = model.listResourcesWithProperty(RDF.type, RDFS.Class);
            while (classIter.hasNext()) {
                Resource classResource = classIter.next();
                if (classResource.getURI() != null && classResource.getURI().startsWith(SCHEMA_NS)) {
                    String className = classResource.getURI().replace(SCHEMA_NS, "");
                    classesCache.add(className);
                }
            }

            // Get properties and their domains
            ResIterator propertyIter = model.listResourcesWithProperty(RDF.type, RDF.Property);
            while (propertyIter.hasNext()) {
                Resource propertyResource = propertyIter.next();
                if (propertyResource.getURI() != null && propertyResource.getURI().startsWith(SCHEMA_NS)) {
                    String propertyName = propertyResource.getURI().replace(SCHEMA_NS, "");

                    // Get domains (classes) for this property
                    StmtIterator domainIter = propertyResource.listProperties(RDFS.domain);
                    while (domainIter.hasNext()) {
                        Statement domainStmt = domainIter.next();
                        Resource domain = domainStmt.getResource();
                        if (domain.getURI() != null && domain.getURI().startsWith(SCHEMA_NS)) {
                            String className = domain.getURI().replace(SCHEMA_NS, "");
                            classPropertiesCache
                                    .computeIfAbsent(className, k -> new HashSet<>())
                                    .add(propertyName);
                        }
                    }
                }
            }

            // If no properties were found for a class, initialize with empty set
            classesCache.forEach(className ->
                    classPropertiesCache.putIfAbsent(className, new HashSet<>())
            );

        } catch (Exception e) {
            e.printStackTrace();
            loadDefaultSchema();
        }
    }

    private void loadDefaultSchema() {
        // Fallback with most common types if file loading fails
        classesCache.addAll(Arrays.asList("Person", "Product", "Organization", "Place", "Event"));
        classPropertiesCache.put("Person", new HashSet<>(Arrays.asList("name", "email", "telephone")));
        classPropertiesCache.put("Product", new HashSet<>(Arrays.asList("name", "description", "price")));
    }

    public List<String> searchClasses(String query) {
        String lowercaseQuery = query.toLowerCase();
        return classesCache.stream()
                .filter(className -> className.toLowerCase().contains(lowercaseQuery))
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getPropertiesForClass(String className) {
        return new ArrayList<>(classPropertiesCache.getOrDefault(className, new HashSet<>()));
    }

    // Helper method for debugging
    public void printStats() {
        System.out.println("Total classes loaded: " + classesCache.size());
        System.out.println("Classes with properties: " + classPropertiesCache.size());
        // Print first few classes and their properties
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : classPropertiesCache.entrySet()) {
            if (count++ < 5) {
                System.out.println(entry.getKey() + ": " + entry.getValue().size() + " properties");
            }
        }
    }
}