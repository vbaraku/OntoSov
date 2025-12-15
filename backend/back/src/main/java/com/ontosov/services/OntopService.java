package com.ontosov.services;

import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

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

    // Cache for database configurations per controller (controllerId -> configs)
    private final ConcurrentHashMap<Long, Map<String, DatabaseConfig>> controllerConfigCache = new ConcurrentHashMap<>();

    // Cache for OBDA file paths per controller (controllerId -> list of OBDA paths)
    private final ConcurrentHashMap<Long, List<Path>> obdaFilesCache = new ConcurrentHashMap<>();

    // Cache for OntopRepository instances (cacheKey -> repository)
    // Key format: "controllerId_databaseName"
    private final ConcurrentHashMap<String, OntopRepository> repositoryCache = new ConcurrentHashMap<>();

    /**
     * Invalidates all caches for a specific controller.
     * Call this when database configs or OBDA mappings change.
     */
    public void invalidateControllerCache(Long controllerId) {
        log.info("Invalidating cache for controller: {}", controllerId);

        // Remove config cache
        controllerConfigCache.remove(controllerId);

        // Remove OBDA files cache
        obdaFilesCache.remove(controllerId);

        // Remove all repository caches for this controller
        String prefix = controllerId + "_";
        repositoryCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                try {
                    entry.getValue().shutDown();
                } catch (Exception e) {
                    log.warn("Error shutting down cached repository: {}", e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Invalidates all caches. Use sparingly.
     */
    public void invalidateAllCaches() {
        log.info("Invalidating all caches");
        controllerConfigCache.clear();
        obdaFilesCache.clear();

        // Shutdown all cached repositories
        repositoryCache.forEach((key, repo) -> {
            try {
                repo.shutDown();
            } catch (Exception e) {
                log.warn("Error shutting down cached repository {}: {}", key, e.getMessage());
            }
        });
        repositoryCache.clear();
    }

    /**
     * Cleanup on service shutdown
     */
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down OntopService, cleaning up cached repositories");
        invalidateAllCaches();
        executorService.shutdown();
    }

    private Map<String, DatabaseConfig> getDatabaseConfigs(Long controllerId) {
        return controllerConfigCache.computeIfAbsent(controllerId, id -> {
            log.info("Cache MISS for controller {} configs - loading from file", id);
            Properties properties = loadDatabaseProperties(id);
            return parseDatabaseConfigs(properties);
        });
    }

    private List<Path> getObdaFiles(Long controllerId) {
        return obdaFilesCache.computeIfAbsent(controllerId, id -> {
            log.info("Cache MISS for controller {} OBDA files - scanning directory", id);
            try {
                Path controllerDir = Paths.get(ONTOP_DIR, id.toString());
                return Files.list(controllerDir)
                        .filter(path -> path.toString().endsWith(".obda"))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Error listing OBDA files for controller: {}", id, e);
                return Collections.emptyList();
            }
        });
    }

    private OntopRepository getOrCreateRepository(String cacheKey, DatabaseConfig config, Path obdaPath) {
        return repositoryCache.computeIfAbsent(cacheKey, key -> {
            log.info("Cache MISS for repository {} - creating new instance", key);
            try {
                String mappingPath = obdaPath.toUri().toString();
                OntopSQLOWLAPIConfiguration ontopConfig = OntopSQLOWLAPIConfiguration.defaultBuilder()
                        .nativeOntopMappingFile(mappingPath)
                        .jdbcUrl(config.jdbcUrl)
                        .jdbcUser(config.username)
                        .jdbcPassword(config.password)
                        .enableTestMode()
                        .build();

                OntopRepository repository = OntopRepository.defaultRepository(ontopConfig);
                repository.init();  // Initialize the repository
                return repository;
            } catch (Exception e) {
                log.error("Error creating OntopRepository for {}: {}", key, e.getMessage());
                throw new RuntimeException("Failed to create OntopRepository", e);
            }
        });
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
            Map<String, DatabaseConfig> dbConfigs = getDatabaseConfigs(controllerId);
            List<Path> obdaFiles = getObdaFiles(controllerId);

            List<Future<List<Map<String, String>>>> futures = new ArrayList<>();

            for (Path obdaPath : obdaFiles) {
                String dbName = extractDatabaseName(obdaPath);
                DatabaseConfig dbConfig = findDatabaseConfig(dbConfigs, dbName);

                if (dbConfig != null) {
                    futures.add(executorService.submit(() ->
                            queryDatabase(controllerId, dbConfig, obdaPath, sparqlQuery, taxId)));
                }
            }

            List<Map<String, String>> mergedResults = new ArrayList<>();
            Set<String> seenValues = new HashSet<>();

            for (Future<List<Map<String, String>>> future : futures) {
                try {
                    List<Map<String, String>> dbResults = future.get(30, TimeUnit.SECONDS);
                    for (Map<String, String> result : dbResults) {
                        String uniqueKey = result.get("entity") + "|" + result.get("property") + "|" + result.get("value");
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
            Long controllerId,
            DatabaseConfig config,
            Path obdaPath,
            String sparqlQuery,
            String taxId) {

        List<Map<String, String>> results = new ArrayList<>();
        String cacheKey = controllerId + "_" + config.name;

        try {
            OntopRepository repository = getOrCreateRepository(cacheKey, config, obdaPath);

            try (RepositoryConnection conn = repository.getConnection()) {
                String parameterizedQuery = sparqlQuery.replace("?taxIdParam", "\"" + taxId + "\"");

                log.info("Query: {}", parameterizedQuery);
                TupleQuery query = conn.prepareTupleQuery(parameterizedQuery);

                try (TupleQueryResult rs = query.evaluate()) {
                    while (rs.hasNext()) {
                        var bindingSet = rs.next();

                        if (bindingSet.getValue("entity") == null ||
                                bindingSet.getValue("parentEntity") == null ||
                                bindingSet.getValue("property") == null ||
                                bindingSet.getValue("value") == null) {
                            log.warn("Skipping result with null values: {}", bindingSet);
                            continue;
                        }

                        Map<String, String> resultRow = new HashMap<>();
                        resultRow.put("entity", bindingSet.getValue("entity").stringValue());
                        resultRow.put("parentEntity", bindingSet.getValue("parentEntity").stringValue());
                        resultRow.put("property", bindingSet.getValue("property").stringValue());
                        resultRow.put("value", bindingSet.getValue("value").stringValue());
                        resultRow.put("source", config.name);
                        results.add(resultRow);
                        log.info("Result row: {}", resultRow);
                    }
                    log.info("Results: {}", results);
                }
            }
        } catch (Exception e) {
            log.error("Error querying database: {}", config.name, e);
            // If repository fails, remove from cache and let next call recreate it
            repositoryCache.remove(cacheKey);
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
                
                SELECT ?entity ?parentEntity ?property ?value
                WHERE {
                    ?person a schema:Person ;
                            schema:taxID ?taxIdParam .
                
                    {
                        # Direct person properties - simplified binding
                        ?person ?property ?value .
                        BIND(?person AS ?entity)
                        BIND(?person AS ?parentEntity)
                        FILTER(?property != rdf:type && !isURI(?value))
                    }
                    UNION
                    {
                        # First-level entities (Orders, MedicalEntity, etc.)
                        ?entity ?relationToPerson ?person ;
                               ?property ?value .
                        BIND(?entity AS ?parentEntity)
                        FILTER(!isURI(?value) && ?property != ?relationToPerson)
                    }
                    UNION
                    {
                        # Second-level entities (Products in Orders)
                        ?firstLevel ?relationToPerson ?person .
                        ?firstLevel ?relationToSecondLevel ?entity .
                        ?entity ?property ?value .
                        BIND(?firstLevel AS ?parentEntity)
                        FILTER(!isURI(?value))
                        FILTER(?entity != ?person)
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