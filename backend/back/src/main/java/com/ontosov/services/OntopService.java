package com.ontosov.services;

import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class OntopService {
    private static final Logger log = LoggerFactory.getLogger(OntopService.class);
    private static final String ONTOP_DIR = "src/main/resources/ontop/controllers/";

    public List<Map<String, String>> executeQuery(Long controllerId, String taxId, String sparqlQuery) {
        List<Map<String, String>> results = new ArrayList<>();

        try {
            // Load configurations
            Properties properties = loadDatabaseProperties(controllerId);
            Map<String, DatabaseConfig> dbConfigs = parseDatabaseConfigs(properties);

            // Find the database we want to query
            String obdaFileName = "db_AControllerDB_mappings.obda";
            String dbName = "AControllerDB"; // For now hardcoded, should be extracted from OBDA filename
            DatabaseConfig dbConfig = dbConfigs.values().stream()
                    .filter(config -> dbName.equals(config.name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Database config not found for: " + dbName));

            log.info("Using database configuration:");
            log.info("Name: {}", dbConfig.name);
            log.info("JDBC URL: {}", dbConfig.jdbcUrl);
            log.info("Username: {}", dbConfig.username);

            // Load OBDA file
            Path obdaPath = Paths.get(ONTOP_DIR, controllerId.toString(), obdaFileName);
            if (!Files.exists(obdaPath)) {
                throw new RuntimeException("OBDA file not found: " + obdaPath);
            }

            String mappingPath = obdaPath.toUri().toString();
            log.info("Using mapping file: {}", mappingPath);

            // Configure Ontop
            OntopSQLOWLAPIConfiguration config = OntopSQLOWLAPIConfiguration.defaultBuilder()
                    .nativeOntopMappingFile(mappingPath)
                    .jdbcUrl(dbConfig.jdbcUrl)
                    .jdbcUser(dbConfig.username)
                    .jdbcPassword(dbConfig.password)
                    .enableTestMode()
                    .build();

            // Create repository and execute query
            OntopRepository repository = OntopRepository.defaultRepository(config);

            // Replace parameter in query
            sparqlQuery = sparqlQuery.replace("?taxIdParam", "\"" + taxId + "\"");
            log.info("Executing SPARQL query: {}", sparqlQuery);

            try (RepositoryConnection conn = repository.getConnection()) {
                TupleQuery query = conn.prepareTupleQuery(sparqlQuery);

                try (TupleQueryResult rs = query.evaluate()) {
                    while (rs.hasNext()) {
                        var bindingSet = rs.next();
                        Map<String, String> resultRow = new HashMap<>();
                        resultRow.put("property", bindingSet.getValue("property").stringValue());
                        resultRow.put("value", bindingSet.getValue("value").stringValue());
                        results.add(resultRow);
                    }
                }
            } finally {
                repository.shutDown();
            }
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
            throw new RuntimeException("Error executing SPARQL query", e);
        }

        return results;
    }

    private Properties loadDatabaseProperties(Long controllerId) {
        Properties properties = new Properties();
        Path propertiesPath = Paths.get(ONTOP_DIR, controllerId.toString(), "database_configs.properties");

        try {
            properties.load(Files.newBufferedReader(propertiesPath));
            log.info("Loaded {} properties", properties.size());
        } catch (Exception e) {
            log.error("Error loading database properties for controller: {}", controllerId, e);
            throw new RuntimeException("Error loading database properties for controller: " + controllerId, e);
        }

        return properties;
    }

    private Map<String, DatabaseConfig> parseDatabaseConfigs(Properties properties) {
        Map<String, DatabaseConfig> configs = new HashMap<>();

        // First, identify all database IDs
        Set<String> databaseIds = new HashSet<>();
        properties.stringPropertyNames().forEach(key -> {
            String[] parts = key.split("\\.");
            if (parts.length >= 2) {
                databaseIds.add(parts[1]);
            }
        });

        // Then process each database
        for (String dbId : databaseIds) {
            DatabaseConfig config = new DatabaseConfig();
            String prefix = "db." + dbId + ".";

            // Basic properties
            config.name = properties.getProperty(prefix + "name");
            config.type = properties.getProperty(prefix + "type");

            // JDBC properties
            config.jdbcUrl = properties.getProperty(prefix + "jdbc.url").replace("\\:", ":");  // Unescape colons
            config.username = properties.getProperty(prefix + "jdbc.user");
            config.password = properties.getProperty(prefix + "jdbc.password");

            log.info("Parsed config for database {} -> Name: {}, Type: {}, URL: {}, User: {}",
                    dbId, config.name, config.type, config.jdbcUrl, config.username);

            configs.put(dbId, config);
        }

        return configs;
    }

    public String getPersonDataQuery() {
        return """
            PREFIX schema: <http://schema.org/>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            
            SELECT ?property ?value
            WHERE {
                ?person a schema:Person ;
                        schema:taxID ?taxId .
                ?person ?property ?value .
                FILTER (?property != rdf:type && ?taxId = ?taxIdParam)
            }
            """;
    }

    private static class DatabaseConfig {
        String name;
        String type;
        String jdbcUrl;
        String username;
        String password;
    }
}