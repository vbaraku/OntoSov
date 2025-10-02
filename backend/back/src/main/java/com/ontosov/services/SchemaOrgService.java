package com.ontosov.services;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaOrgService {
    private static final String SCHEMA_NS = "https://schema.org/";
    private final Set<String> classesCache;
    private final Map<String, Set<String>> classPropertiesCache;

    @Value("${schema.rdf.path}")
    private String schemaRdfPath;

    public SchemaOrgService() {
        // Initialize collections in constructor
        this.classesCache = new HashSet<>();
        this.classPropertiesCache = new HashMap<>();
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

            System.out.println("\nScanning for classes...");

            // First find all classes
            StmtIterator statements = model.listStatements();
            while (statements.hasNext()) {
                Statement stmt = statements.next();

                // Check if this statement defines a class
                if (stmt.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
                        && stmt.getObject().toString().equals("http://www.w3.org/2000/01/rdf-schema#Class")) {

                    String uri = stmt.getSubject().getURI();
                    if (uri != null && uri.startsWith(SCHEMA_NS)) {
                        String className = uri.replace(SCHEMA_NS, "");
                        if (!className.endsWith("Enumeration") && !className.equals("DataType")) {
                            this.classesCache.add(className);
                            // System.out.println("Found class: " + className);
                        }
                    }
                }
            }

            // Then find properties for each class
            System.out.println("\nScanning for properties...");
            Property domainIncludes = model.createProperty(SCHEMA_NS + "domainIncludes");
            statements = model.listStatements(null, domainIncludes, (RDFNode) null);

            while (statements.hasNext()) {
                Statement stmt = statements.next();
                String propertyUri = stmt.getSubject().getURI();
                String domainUri = stmt.getObject().asResource().getURI();

                if (propertyUri != null && domainUri != null
                        && propertyUri.startsWith(SCHEMA_NS)
                        && domainUri.startsWith(SCHEMA_NS)) {

                    String propertyName = propertyUri.replace(SCHEMA_NS, "");
                    String className = domainUri.replace(SCHEMA_NS, "");

                    if (this.classesCache.contains(className)) {
                        this.classPropertiesCache
                                .computeIfAbsent(className, k -> new HashSet<>())
                                .add(propertyName);
                        // System.out.println("Added property " + propertyName + " to class " + className);
                    }
                }
            }

//            System.out.println("\n=== Schema.org Data Loading Stats ===");
//            System.out.println("Classes in cache: " + this.classesCache.size());
//            System.out.println("Classes with properties: " + this.classPropertiesCache.size());

            if (this.classesCache.isEmpty()) {
                System.out.println("\nWARNING: No classes were loaded!");
                System.out.println("Checking if Person class exists in model:");
                Resource person = model.createResource(SCHEMA_NS + "Person");
                StmtIterator personStmts = model.listStatements(person, null, (RDFNode) null);
                while (personStmts.hasNext()) {
                    System.out.println(personStmts.next());
                }
            }

        } catch (Exception e) {
            System.out.println("Error loading schema: " + e.getMessage());
            e.printStackTrace();
            loadDefaultSchema();
        }
    }

    public List<String> searchClasses(String query) {
        String lowercaseQuery = query.toLowerCase();
        return new ArrayList<>(classesCache).stream()
                .filter(className -> className.toLowerCase().contains(lowercaseQuery))
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getPropertiesForClass(String className) {
        Set<String> properties = new HashSet<>();

        // Add properties specifically found for this class
        properties.addAll(classPropertiesCache.getOrDefault(className, new HashSet<>()));

        // Add ALL properties discovered from Schema.org (generic approach)
        properties.addAll(getAllDiscoveredProperties());

        return properties.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    private Set<String> getAllDiscoveredProperties() {
        return classPropertiesCache.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private void loadDefaultSchema() {
        classesCache.addAll(Arrays.asList("Person", "Organization", "Place", "Event", "Product"));
        classPropertiesCache.put("Person", new HashSet<>(Arrays.asList("name", "email", "telephone")));
        classPropertiesCache.put("Organization", new HashSet<>(Arrays.asList("name", "address", "telephone")));
        System.out.println("Loaded default schema with " + classesCache.size() + " classes");
    }
}