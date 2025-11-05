package com.ontosov.services;

import com.ontosov.dto.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DatabaseConfigService {
    private static final String ONTOP_DIR = "src/main/resources/ontop/controllers/";

    private String getControllerDir(Long controllerId) {
        return ONTOP_DIR + controllerId + "/";
    }

    private String getConfigPath(Long controllerId) {
        return getControllerDir(controllerId) + "database_configs.properties";
    }

    private String getObdaPath(Long controllerId, String databaseName) {
        return getControllerDir(controllerId) + "/db_" + databaseName + "_mappings.obda";
    }

    public void saveDatabaseConfiguration(DatabaseConfigDTO configDTO, Long controllerId) throws IOException {
        String configPath = getConfigPath(controllerId);
        Files.createDirectories(Paths.get(configPath).getParent());

        Properties properties = new Properties();
        if (Files.exists(Paths.get(configPath))) {
            try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
                properties.load(reader);
            }
        }

        // Check if this database already exists based on JDBC URL and name
        String existingId = findExistingDatabaseId(properties, configDTO);
        String dbId = existingId != null ? existingId : (configDTO.getId() != null ? configDTO.getId() : UUID.randomUUID().toString());

        // Remove any existing properties for this database
        removeExistingDatabase(properties, dbId);

        // Add or update properties for this database
        String prefix = "db." + dbId;
        properties.setProperty(prefix + ".name", configDTO.getDatabaseName());
        properties.setProperty(prefix + ".type", configDTO.getDatabaseType());
        properties.setProperty(prefix + ".host", configDTO.getHost());
        properties.setProperty(prefix + ".port", configDTO.getPort());
        properties.setProperty(prefix + ".jdbc.url", configDTO.getJdbcUrl());
        properties.setProperty(prefix + ".jdbc.driver", getJdbcDriver(configDTO.getDatabaseType()));
        properties.setProperty(prefix + ".jdbc.user", configDTO.getUsername());
        properties.setProperty(prefix + ".jdbc.password", configDTO.getPassword());

        // Save properties file
        try (Writer writer = Files.newBufferedWriter(Paths.get(configPath))) {
            properties.store(writer, "Database configurations for controller " + controllerId);
        }

        // Set the ID in the DTO for reference
        configDTO.setId(dbId);
    }

    private String findExistingDatabaseId(Properties properties, DatabaseConfigDTO newConfig) {
        Map<String, Map<String, String>> databaseConfigs = new HashMap<>();

        // Group properties by database ID
        for (String key : properties.stringPropertyNames()) {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                String dbId = parts[1];
                String propertyName = String.join(".", Arrays.copyOfRange(parts, 2, parts.length));
                databaseConfigs.computeIfAbsent(dbId, k -> new HashMap<>()).put(propertyName, properties.getProperty(key));
            }
        }

        // Look for matching database
        for (Map.Entry<String, Map<String, String>> entry : databaseConfigs.entrySet()) {
            Map<String, String> config = entry.getValue();
            if (config.get("jdbc.url").equals(newConfig.getJdbcUrl()) && config.get("name").equals(newConfig.getDatabaseName())) {
                return entry.getKey();
            }
        }

        return null;
    }

    private void removeExistingDatabase(Properties properties, String dbId) {
        String prefix = "db." + dbId + ".";
        properties.stringPropertyNames().stream().filter(key -> key.startsWith(prefix)).collect(Collectors.toList()) // Collect to avoid concurrent modification
                .forEach(properties::remove);
    }

    private String getJdbcDriver(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "postgresql" -> "org.postgresql.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    public void saveSchemaMappings(MappingRequestDTO request, Long controllerId) throws IOException {
        String databaseName = request.databaseConfig().getDatabaseName();
        StringBuilder obdaContent = new StringBuilder();

        // Add prefix declarations
        obdaContent.append("[PrefixDeclaration]\n").append(":       http://example.org/resource#\n").append("schema: http://schema.org/\n").append("xsd:    http://www.w3.org/2001/XMLSchema#\n\n").append("[MappingDeclaration] @collection [[\n");

        // Group mappings by main entity and identify relationships
        Map<String, List<SchemaMappingDTO>> propertyMappings = new HashMap<>();
        List<SchemaMappingDTO> relationshipMappings = new ArrayList<>();

        for (SchemaMappingDTO mapping : request.mappings()) {
            if (mapping.getTargetTable() != null) {
                relationshipMappings.add(mapping);
            } else {
                propertyMappings.computeIfAbsent(mapping.getDatabaseTable(), k -> new ArrayList<>()).add(mapping);
            }
        }

        // Generate entity mappings
        for (Map.Entry<String, List<SchemaMappingDTO>> entry : propertyMappings.entrySet()) {
            String tableName = entry.getKey();
            List<SchemaMappingDTO> mappings = entry.getValue();
            String primaryKey = getPrimaryKeyColumn(request.databaseConfig(), tableName);
            String schemaClass = mappings.get(0).getSchemaClass();

            generateEntityMapping(obdaContent, tableName, primaryKey, schemaClass, mappings);
        }

        // Generate relationship mappings
        for (SchemaMappingDTO mapping : relationshipMappings) {
            generateRelationshipMapping(obdaContent, mapping, request.databaseConfig(), propertyMappings);
        }

        obdaContent.append("]]");

        // Write to file
        String obdaPath = getObdaPath(controllerId, databaseName);
        Files.createDirectories(Paths.get(obdaPath).getParent());
        Files.writeString(Paths.get(obdaPath), obdaContent.toString());
    }

    private void generateEntityMapping(StringBuilder obdaContent, String tableName, String primaryKey, String schemaClass, List<SchemaMappingDTO> mappings) {
        StringBuilder target = new StringBuilder();
        target.append(String.format(":%s/{%s} a schema:%s", schemaClass, primaryKey, schemaClass));

        StringBuilder source = new StringBuilder();
        source.append("SELECT ").append(primaryKey);

        for (SchemaMappingDTO mapping : mappings) {
            target.append(String.format(" ; schema:%s {%s}", mapping.getSchemaProperty(), mapping.getDatabaseColumn()));
            source.append(", ").append(mapping.getDatabaseColumn());
        }
        target.append(" .\n");

        obdaContent.append(String.format("mappingId %s_mapping\n", tableName)).append("target  ").append(target).append("source  ").append(source).append(" FROM ").append(tableName).append("\n\n");
    }

    private void generateRelationshipMapping(StringBuilder obdaContent, SchemaMappingDTO mapping, DatabaseConfigDTO config, Map<String, List<SchemaMappingDTO>> propertyMappings) {
        String sourceTable = mapping.getDatabaseTable();
        String targetTable = mapping.getTargetTable();
        String sourcePrimaryKey = getPrimaryKeyColumn(config, sourceTable);
        String targetPrimaryKey = getPrimaryKeyColumn(config, targetTable);

        // Get schema classes for both tables
        String sourceSchemaClass = getSchemaClassForTable(sourceTable, propertyMappings);
        String targetSchemaClass = getSchemaClassForTable(targetTable, propertyMappings);

        String mappingId = String.format("%s_%s_rel", sourceTable, targetTable);

        obdaContent.append(String.format("mappingId %s\n", mappingId)).append(String.format("target  :%s/{%s} schema:%s :%s/{%s} .\n", sourceSchemaClass, sourcePrimaryKey, mapping.getSchemaProperty(), targetSchemaClass, targetPrimaryKey)).append("source  ").append(String.format("SELECT s.%s, t.%s FROM %s s JOIN %s t ON s.%s = t.%s", sourcePrimaryKey, targetPrimaryKey, sourceTable, targetTable, mapping.getDatabaseColumn(), mapping.getTargetKey())).append("\n\n");
    }

    private String getSchemaClassForTable(String tableName, Map<String, List<SchemaMappingDTO>> propertyMappings) {
        List<SchemaMappingDTO> mappings = propertyMappings.get(tableName);
        if (mappings != null && !mappings.isEmpty()) {
            return mappings.get(0).getSchemaClass();
        }
        throw new RuntimeException("No schema class found for table: " + tableName);
    }

    private boolean hasUserIdColumn(DatabaseConfigDTO config, String tableName) {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, "user_id");
            return columns.next();
        } catch (Exception e) {
            throw new RuntimeException("Failed to check for user_id column", e);
        }
    }

    public List<SchemaMappingDTO> getMappings(Long controllerId, String dbId) throws IOException {
        List<SchemaMappingDTO> mappings = new ArrayList<>();
        String obdaPath = getObdaPath(controllerId, dbId);
        if (!Files.exists(Paths.get(obdaPath))) {
            return mappings;
        }

        List<String> lines = Files.readAllLines(Paths.get(obdaPath));
        String currentTable = null;
        String currentClass = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[") || line.isEmpty()) continue;

            if (line.startsWith("mappingId")) {
                currentTable = null;
                currentClass = null;
                String targetLine = lines.get(++i).trim();

                if (targetLine.startsWith("target")) {
                    Pattern relationPattern = Pattern.compile(":Resource/\\{[^}]+\\} schema:([^ ]+) :Resource");
                    Matcher relationMatcher = relationPattern.matcher(targetLine);

                    if (relationMatcher.find()) {
                        // Handle relationship mapping
                        SchemaMappingDTO relationMapping = new SchemaMappingDTO();
                        relationMapping.setIsRelationship(true);
                        relationMapping.setSchemaProperty(relationMatcher.group(1));

                        String sourceLine = lines.get(++i).trim();
                        if (sourceLine.startsWith("source")) {
                            Pattern joinPattern = Pattern.compile("FROM ([^ ]+) s JOIN ([^ ]+) t ON s\\.([^ ]+) = t\\.([^ ]+)");
                            Matcher joinMatcher = joinPattern.matcher(sourceLine);
                            if (joinMatcher.find()) {
                                relationMapping.setDatabaseTable(joinMatcher.group(1));
                                relationMapping.setTargetTable(joinMatcher.group(2));
                                relationMapping.setDatabaseColumn(joinMatcher.group(3));
                                relationMapping.setTargetKey(joinMatcher.group(4));
                                // Find schema class from target table's mapping
                                relationMapping.setSchemaClass(findSchemaClassForTable(lines, joinMatcher.group(2)));
                                mappings.add(relationMapping);
                            }
                        }
                    } else {
                        // Handle property mappings
                        Pattern classPattern = Pattern.compile("a schema:([^ ]+)");
                        Matcher classMatcher = classPattern.matcher(targetLine);
                        if (classMatcher.find()) {
                            currentClass = classMatcher.group(1);
                        }

                        Pattern propertyPattern = Pattern.compile("schema:([^ ]+) \\{([^}]+)\\}");
                        Matcher propertyMatcher = propertyPattern.matcher(targetLine);

                        String sourceLine = lines.get(++i).trim();
                        if (sourceLine.startsWith("source")) {
                            Pattern tablePattern = Pattern.compile("FROM ([^ ]+)");
                            Matcher tableMatcher = tablePattern.matcher(sourceLine);
                            if (tableMatcher.find()) {
                                currentTable = tableMatcher.group(1);
                            }
                        }

                        while (propertyMatcher.find()) {
                            SchemaMappingDTO propertyMapping = new SchemaMappingDTO();
                            propertyMapping.setIsRelationship(false);
                            propertyMapping.setSchemaClass(currentClass);
                            propertyMapping.setDatabaseTable(currentTable);
                            propertyMapping.setSchemaProperty(propertyMatcher.group(1));
                            propertyMapping.setDatabaseColumn(propertyMatcher.group(2));
                            mappings.add(propertyMapping);
                        }
                    }
                }
            }
        }
        return mappings;
    }

    private String findSchemaClassForTable(List<String> lines, String tableName) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("mappingId") && line.contains(tableName)) {
                String targetLine = lines.get(i + 1).trim();
                Pattern classPattern = Pattern.compile("a schema:([^ ]+)");
                Matcher classMatcher = classPattern.matcher(targetLine);
                if (classMatcher.find()) {
                    return classMatcher.group(1);
                }
            }
        }
        return null;
    }

    public List<DatabaseConfigDTO> getDatabasesForController(Long controllerId) throws IOException {
        String configPath = getConfigPath(controllerId);
        if (!Files.exists(Paths.get(configPath))) {
            return new ArrayList<>();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
            properties.load(reader);
        }

        Map<String, DatabaseConfigDTO> configs = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                String dbId = parts[1];
                DatabaseConfigDTO config = configs.computeIfAbsent(dbId, k -> {
                    DatabaseConfigDTO dto = new DatabaseConfigDTO();
                    dto.setId(k);
                    return dto;
                });

                switch (parts[2]) {
                    case "name" -> config.setDatabaseName(properties.getProperty(key));
                    case "type" -> config.setDatabaseType(properties.getProperty(key));
                    case "host" -> config.setHost(properties.getProperty(key));
                    case "port" -> config.setPort(properties.getProperty(key));
                    case "jdbc" -> {
                        if (parts.length > 3) {
                            switch (parts[3]) {
                                case "url" -> config.setJdbcUrl(properties.getProperty(key));
                                case "user" -> config.setUsername(properties.getProperty(key));
                                case "password" -> {
                                    config.setPassword(properties.getProperty(key));
                                }
                            }
                        }
                    }
                }
            }
        }

        List<DatabaseConfigDTO> result = new ArrayList<>(configs.values());
        return result;
    }

    public List<TableMetadataDTO> getDatabaseTables(DatabaseConfigDTO config) {
        List<TableMetadataDTO> tables = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Fetch tables
            ResultSet rs = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"});

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                TableMetadataDTO tableMetadata = new TableMetadataDTO(tableName);

                // Fetch columns for each table
                ResultSet columns = metaData.getColumns(connection.getCatalog(), null, tableName, "%");

                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    tableMetadata.addColumn(new ColumnMetadataDTO(columnName, columnType));
                }

                tables.add(tableMetadata);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch database tables", e);
        }

        return tables;
    }

    private String getPrimaryKeyColumn(DatabaseConfigDTO config, String tableName) {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }
            throw new RuntimeException("No primary key found for table: " + tableName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get primary key for table: " + tableName, e);
        }
    }

    public boolean testDatabaseConnection(DatabaseConfigDTO config) {
        try {
            System.out.println("Testing connection with URL: " + config.getJdbcUrl());
            System.out.println("Username: " + config.getUsername());
            Class.forName(getJdbcDriver(config.getDatabaseType())); // Add this line to ensure driver is loaded
            Connection connection = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
            connection.close();
            return true;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public String resolveSchemaOrgProperty(
            Long controllerId,
            String databaseId,
            String tableName,
            String columnName
    ) throws IOException {
        String obdaPath = getObdaPath(controllerId, getDatabaseNameFromId(controllerId, databaseId));

        if (!Files.exists(Paths.get(obdaPath))) {
            return null;
        }

        List<String> lines = Files.readAllLines(Paths.get(obdaPath));

        // Look for the mapping for this specific table and column
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Check if this is a mappingId line for our table
            if (line.startsWith("mappingId") && line.contains(tableName)) {
                // Look at the target line (next line after mappingId)
                if (i + 1 < lines.size()) {
                    String targetLine = lines.get(i + 1).trim();

                    // Check if this mapping includes our column
                    if (targetLine.contains("{" + columnName + "}")) {
                        // Extract the Schema.org property
                        // Pattern: schema:propertyName {columnName}
                        Pattern pattern = Pattern.compile("schema:(\\w+)\\s+\\{" + columnName + "\\}");
                        Matcher matcher = pattern.matcher(targetLine);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Resolve table name to Schema.org entity type by parsing OBDA mapping
     * Example: "medical_records" table -> "MedicalEntity" entity type
     */
    public String resolveEntityTypeFromTable(
            Long controllerId,
            String databaseId,
            String tableName
    ) throws IOException {
        String obdaPath = getObdaPath(controllerId, getDatabaseNameFromId(controllerId, databaseId));

        if (!Files.exists(Paths.get(obdaPath))) {
            return null;
        }

        List<String> lines = Files.readAllLines(Paths.get(obdaPath));

        // Look for the mapping for this specific table
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Check if this is a mappingId line for our table
            if (line.startsWith("mappingId") && line.contains(tableName)) {
                // Look at the target line (next line after mappingId)
                if (i + 1 < lines.size()) {
                    String targetLine = lines.get(i + 1).trim();

                    // Extract entity type from target line
                    // Pattern: :EntityType/{primary_key} a schema:EntityType
                    // Example: :MedicalEntity/{record_id} a schema:MedicalEntity

                    // First pattern: :EntityType/{...}
                    Pattern pattern1 = Pattern.compile(":(\\w+)/\\{");
                    Matcher matcher1 = pattern1.matcher(targetLine);
                    if (matcher1.find()) {
                        return matcher1.group(1);
                    }

                    // Second pattern: a schema:EntityType
                    Pattern pattern2 = Pattern.compile("a schema:(\\w+)");
                    Matcher matcher2 = pattern2.matcher(targetLine);
                    if (matcher2.find()) {
                        return matcher2.group(1);
                    }
                }
            }
        }

        return null;
    }

    // Helper method to get database name from UUID
    private String getDatabaseNameFromId(Long controllerId, String databaseId) throws IOException {
        List<DatabaseConfigDTO> databases = getDatabasesForController(controllerId);
        return databases.stream()
                .filter(db -> db.getId().equals(databaseId))
                .findFirst()
                .map(DatabaseConfigDTO::getDatabaseName)
                .orElse(null);
    }

    public void deleteDatabaseConfig(String dbId, Long controllerId) throws IOException {
        String configPath = getConfigPath(controllerId);
        if (!Files.exists(Paths.get(configPath))) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
            properties.load(reader);
        }

        // Remove all properties for this database
        String prefix = "db." + dbId;
        List<String> keysToRemove = properties.stringPropertyNames().stream().filter(key -> key.startsWith(prefix)).collect(Collectors.toList());

        keysToRemove.forEach(properties::remove);

        // If properties is empty, delete the file, otherwise update it
        if (properties.isEmpty()) {
            Files.deleteIfExists(Paths.get(configPath));
        } else {
            try (Writer writer = Files.newBufferedWriter(Paths.get(configPath))) {
                properties.store(writer, "Database configurations for controller " + controllerId);
            }
        }
    }
}