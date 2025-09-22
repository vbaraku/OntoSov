package com.ontosov.services;

import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class OntopService {
    private static final Logger log = LoggerFactory.getLogger(OntopService.class);
    private static final String ONTOP_DIR = "src/main/resources/ontop/controllers/";
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

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

        Set<String> databaseIds = new HashSet<>();
        properties.stringPropertyNames().forEach(key -> {
            String[] parts = key.split("\\.");
            if (parts.length >= 2) {
                databaseIds.add(parts[1]);
            }
        });

        for (String dbId : databaseIds) {
            DatabaseConfig config = new DatabaseConfig();
            String prefix = "db." + dbId + ".";

            config.name = properties.getProperty(prefix + "name");
            config.type = properties.getProperty(prefix + "type");
            config.jdbcUrl = properties.getProperty(prefix + "jdbc.url").replace("\\:", ":");
            config.username = properties.getProperty(prefix + "jdbc.user");
            config.password = properties.getProperty(prefix + "jdbc.password");

            log.info("Parsed config for database {} -> Name: {}, Type: {}, URL: {}, User: {}",
                    dbId, config.name, config.type, config.jdbcUrl, config.username);

            configs.put(dbId, config);
        }

        return configs;
    }

    public List<Map<String, String>> executeQuery(Long controllerId, String taxId, String sparqlQuery) {
        try {
            Properties properties = loadDatabaseProperties(controllerId);
            Map<String, DatabaseConfig> dbConfigs = parseDatabaseConfigs(properties);

            Path controllerDir = Paths.get(ONTOP_DIR, controllerId.toString());
            List<Path> obdaFiles = Files.list(controllerDir)
                    .filter(path -> path.toString().endsWith(".obda"))
                    .collect(Collectors.toList());

            List<Future<List<Map<String, String>>>> futures = new ArrayList<>();

            for (Path obdaPath : obdaFiles) {
                String dbName = extractDatabaseName(obdaPath);
                DatabaseConfig dbConfig = findDatabaseConfig(dbConfigs, dbName);

                if (dbConfig != null) {
                    futures.add(executorService.submit(() ->
                            queryDatabase(dbConfig, obdaPath, sparqlQuery, taxId)));
                }
            }

            List<Map<String, String>> mergedResults = new ArrayList<>();
            Set<String> seenValues = new HashSet<>();

            for (Future<List<Map<String, String>>> future : futures) {
                try {
                    List<Map<String, String>> dbResults = future.get(30, TimeUnit.SECONDS);
                    for (Map<String, String> result : dbResults) {
                        String uniqueKey = result.get("property") + "|" + result.get("value");
                        if (!seenValues.contains(uniqueKey)) {
                            seenValues.add(uniqueKey);
                            mergedResults.add(result);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error executing query on database", e);
                }
            }

            return mergedResults;

        } catch (Exception e) {
            log.error("Error executing federated query", e);
            throw new RuntimeException("Error executing federated query", e);
        }
    }

    private List<Map<String, String>> queryDatabase(
            DatabaseConfig config,
            Path obdaPath,
            String sparqlQuery,
            String taxId) {

        List<Map<String, String>> results = new ArrayList<>();
        String mappingPath = obdaPath.toUri().toString();

        try {
            OntopSQLOWLAPIConfiguration ontopConfig = OntopSQLOWLAPIConfiguration.defaultBuilder()
                    .nativeOntopMappingFile(mappingPath)
                    .jdbcUrl(config.jdbcUrl)
                    .jdbcUser(config.username)
                    .jdbcPassword(config.password)
                    .enableTestMode()
                    .build();

            try (OntopRepository repository = OntopRepository.defaultRepository(ontopConfig);
                 RepositoryConnection conn = repository.getConnection()) {

                String parameterizedQuery = sparqlQuery.replace("?taxIdParam", "\"" + taxId + "\"");

                log.info("Query: {}", parameterizedQuery);
                TupleQuery query = conn.prepareTupleQuery(parameterizedQuery);

                try (TupleQueryResult rs = query.evaluate()) {
                    while (rs.hasNext()) {
                        var bindingSet = rs.next();
                        Map<String, String> resultRow = new HashMap<>();
                        resultRow.put("property", bindingSet.getValue("property").stringValue());
                        resultRow.put("value", bindingSet.getValue("value").stringValue());
                        resultRow.put("source", config.name);
                        results.add(resultRow);
                        //add debug logs to this method to see the data
                        log.info("Result row: {}", resultRow);

                    }
                    //add debug log
                    log.info("Results: {}", results);
                }
            }
        } catch (Exception e) {
            log.error("Error querying database: {}", config.name, e);
        }

        return results;
    }

    private String extractDatabaseName(Path obdaPath) {
        String filename = obdaPath.getFileName().toString();
        return filename.substring(filename.indexOf("_") + 1, filename.lastIndexOf("_"));
    }

    private DatabaseConfig findDatabaseConfig(Map<String, DatabaseConfig> configs, String dbName) {
        return configs.values().stream()
                .filter(config -> config.name.equalsIgnoreCase(dbName))
                .findFirst()
                .orElse(null);
    }

    public String getPersonDataQuery() {
        return """
                PREFIX schema: <http://schema.org/>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

                SELECT ?property ?value
                WHERE {
                    ?person a schema:Person ;
                            schema:taxID ?taxIdParam .
                    
                    {
                        # Direct person properties
                        ?person ?property ?value .
                        FILTER(?property != rdf:type)
                        FILTER(!isURI(?value))
                    }
                    UNION
                    {
                        # Orders by this person
                        ?order ?relationToPerson ?person ;
                               ?property ?value .
                        FILTER(!isURI(?value))
                        FILTER(?property != ?relationToPerson)
                    }
                    UNION
                    {
                        # Products from orders by this person  
                        ?order ?relationToPerson ?person ;
                               ?relationToProduct ?product .
                        ?product ?property ?value .
                        FILTER(!isURI(?value))
                    }
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