package com.ontosov.services;

import it.unibz.inf.ontop.exception.OntopConnectionException;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OntopService {

    private static final Logger log = LoggerFactory.getLogger(OntopService.class);

    @Value("${spring.datasource.url}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username}")
    private String defaultUsername;

    @Value("${spring.datasource.password}")
    private String defaultPassword;

    public List<Map<String, String>> executeQuery(Long controllerId, String taxId, String sparqlQuery) {
        List<Map<String, String>> results = new ArrayList<>();

        try {
            // Find OBDA files in classpath
            ClassPathResource controllerDir = new ClassPathResource("ontop/controllers/" + controllerId);
            File dir = controllerDir.getFile();

            List<File> obdaFiles = Arrays.stream(dir.listFiles())
                    .filter(file -> file.getName().endsWith(".obda"))
                    .collect(Collectors.toList());

            if (obdaFiles.isEmpty()) {
                throw new RuntimeException("No mappings found for controller: " + controllerId);
            }

            // Load all database configs
            Properties properties = loadDatabaseProperties(controllerId);
            log.debug("Loaded properties: {}", properties);

            Map<String, DatabaseConfig> dbConfigs = parseDatabaseConfigs(properties);
            log.debug("Parsed {} database configurations", dbConfigs.size());

            // Execute query for each OBDA file
            for (File obdaFile : obdaFiles) {
                String dbName = extractDatabaseName(obdaFile.getName());
                DatabaseConfig dbConfig = findDatabaseConfigByName(dbConfigs, dbName);

                if (dbConfig == null) {
                    log.warn("Could not find database configuration for: {}", dbName);
                    continue;
                }

                log.info("Executing query on database: {} using mapping file: {}", dbName, obdaFile.getName());

                // Read OBDA content
                String obdaContent = new BufferedReader(new InputStreamReader(new FileInputStream(obdaFile)))
                        .lines()
                        .collect(Collectors.joining("\n"));

                // Configure Ontop
                String mappingPath = "file:///" + obdaFile.getAbsolutePath().replace("\\", "/").replace(" ", "%20");
                log.info("Using mapping file path: {}", mappingPath);

                OntopSQLOWLAPIConfiguration config = OntopSQLOWLAPIConfiguration.defaultBuilder()
                        .nativeOntopMappingFile(mappingPath)
                        .jdbcUrl(dbConfig.jdbcUrl)
                        .jdbcUser(dbConfig.username)
                        .jdbcPassword(dbConfig.password)
                        .enableTestMode() // Remove this in production
                        .build();

                // Create repository
                OntopRepository repository = OntopRepository.defaultRepository(config);

                // Replace parameter in query
                String parameterizedQuery = sparqlQuery.replace("?taxIdParam", "\"" + taxId + "\"");
                log.debug("Executing SPARQL query: {}", parameterizedQuery);

                // Execute query
                try (RepositoryConnection conn = repository.getConnection()) {
                    TupleQuery query = conn.prepareTupleQuery(parameterizedQuery);

                    try (TupleQueryResult rs = query.evaluate()) {
                        while (rs.hasNext()) {
                            var bindingSet = rs.next();
                            Map<String, String> resultRow = new HashMap<>();

                            resultRow.put("property", bindingSet.getValue("property").stringValue());
                            resultRow.put("value", bindingSet.getValue("value").stringValue());
                            resultRow.put("source", dbName);

                            results.add(resultRow);
                        }
                    }
                } finally {
                    repository.shutDown();
                }
            }
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
            throw new RuntimeException("Error executing SPARQL query", e);
        }

        return results;
    }

    private String extractDatabaseName(String filename) {
        log.debug("Extracting database name from filename: {}", filename);

        if (filename.startsWith("db_") && filename.endsWith("_mappings.obda")) {
            String dbName = filename.substring(3, filename.length() - 13); // Remove "db_" and "_mappings.obda"
            // Remove any trailing underscores
            dbName = dbName.replaceAll("_+$", "");
            log.debug("Extracted database name: {}", dbName);
            return dbName;
        }
        return filename;
    }

    private DatabaseConfig findDatabaseConfigByName(Map<String, DatabaseConfig> configs, String dbName) {
        log.debug("Looking for database configuration with name: {}", dbName);

        DatabaseConfig config = configs.values().stream()
                .filter(c -> c.name != null && c.name.equalsIgnoreCase(dbName))
                .findFirst()
                .orElse(null);

        if (config == null) {
            log.debug("Available database names: {}",
                    configs.values().stream()
                            .map(c -> c.name)
                            .toList());
        }

        return config;
    }

    private Properties loadDatabaseProperties(Long controllerId) {
        Properties properties = new Properties();
        ClassPathResource propertiesResource = new ClassPathResource(
                "ontop/controllers/" + controllerId + "/database_configs.properties");

        try {
            properties.load(propertiesResource.getInputStream());
        } catch (Exception e) {
            log.error("Error loading database properties for controller: {}", controllerId, e);
            throw new RuntimeException("Error loading database properties for controller: " + controllerId, e);
        }

        return properties;
    }

    private Map<String, DatabaseConfig> parseDatabaseConfigs(Properties properties) {
        Map<String, DatabaseConfig> configs = new HashMap<>();

        // Group properties by database ID
        properties.stringPropertyNames().forEach(key -> {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                String dbId = parts[1];
                configs.computeIfAbsent(dbId, k -> new DatabaseConfig())
                        .setProperty(parts[2], properties.getProperty(key));
            }
        });

        // Log found configurations
        configs.forEach((id, config) -> {
            log.debug("Found database configuration - ID: {}, Name: {}, URL: {}",
                    id, config.name, config.jdbcUrl);
        });

        return configs;
    }

    public String getPersonDataQuery() {
        return """
            PREFIX schema: <http://schema.org/>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT DISTINCT ?property ?value
            WHERE {
                ?person a schema:Person ;
                        schema:taxID ?taxId .
                ?person ?property ?value .
                FILTER (?taxId = ?taxIdParam)
            }
            """;
    }

    private static class DatabaseConfig {
        String name;
        String jdbcUrl;
        String username;
        String password;

        void setProperty(String key, String value) {
            log.debug("Setting property - Key: {}, Value: {}", key, value);
            switch (key) {
                case "name" -> name = value;
                case "jdbc.url" -> jdbcUrl = value;
                case "jdbc.user" -> username = value;
                case "jdbc.password" -> password = value;
            }
            log.debug("Current state - Name: {}, JDBC URL: {}, Username: {}", name, jdbcUrl, username);
        }
    }
}