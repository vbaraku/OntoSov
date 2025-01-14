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
        String dbId = existingId != null ? existingId :
                (configDTO.getId() != null ? configDTO.getId() :
                        UUID.randomUUID().toString());

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
                databaseConfigs.computeIfAbsent(dbId, k -> new HashMap<>())
                        .put(propertyName, properties.getProperty(key));
            }
        }

        // Look for matching database
        for (Map.Entry<String, Map<String, String>> entry : databaseConfigs.entrySet()) {
            Map<String, String> config = entry.getValue();
            if (config.get("jdbc.url").equals(newConfig.getJdbcUrl()) &&
                    config.get("name").equals(newConfig.getDatabaseName())) {
                return entry.getKey();
            }
        }

        return null;
    }

    private void removeExistingDatabase(Properties properties, String dbId) {
        String prefix = "db." + dbId + ".";
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList()) // Collect to avoid concurrent modification
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
        obdaContent.append("[PrefixDeclaration]\n")
                .append(":       http://example.org/resource#\n")
                .append("schema: http://schema.org/\n")
                .append("xsd:    http://www.w3.org/2001/XMLSchema#\n\n")
                .append("[MappingDeclaration] @collection [[\n");

        // Group mappings by table and type
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

            StringBuilder target = new StringBuilder();
            target.append(String.format(":Resource/{%s} a schema:%s", primaryKey, schemaClass));

            StringBuilder source = new StringBuilder();
            source.append("SELECT ").append(primaryKey);

            for (SchemaMappingDTO mapping : mappings) {
                target.append(String.format(" ; schema:%s {%s}",
                        mapping.getSchemaProperty(),
                        mapping.getDatabaseColumn()));
                source.append(", ").append(mapping.getDatabaseColumn());
            }
            target.append(" .\n");

            obdaContent.append(String.format("mappingId %s_mapping\n", tableName))
                    .append("target  ").append(target)
                    .append("source  ").append(source)
                    .append(" FROM ").append(tableName).append("\n\n");
        }

        // Generate relationship mappings
        for (SchemaMappingDTO mapping : relationshipMappings) {
            String mappingId = mapping.getDatabaseTable() + "_" + mapping.getTargetTable() + "_rel";

            obdaContent.append(String.format("mappingId %s\n", mappingId))
                    .append(String.format("target  :Resource/{order_id} schema:%s :Resource/{%s} .\n",
                            mapping.getSchemaProperty(),
                            mapping.getTargetKey()))
                    .append("source  ")
                    .append("SELECT o.order_id, t.")
                    .append(mapping.getTargetKey())
                    .append(" FROM ")
                    .append(mapping.getDatabaseTable())
                    .append(" o JOIN ")
                    .append(mapping.getTargetTable())
                    .append(" t ON o.")
                    .append(mapping.getDatabaseColumn())
                    .append(" = t.")
                    .append(mapping.getSourceKey())
                    .append("\n\n");
        }

        obdaContent.append("]]");

        String obdaPath = getObdaPath(controllerId, databaseName);
        Files.createDirectories(Paths.get(obdaPath).getParent());
        Files.writeString(Paths.get(obdaPath), obdaContent.toString());
    }

    public List<SchemaMappingDTO> getMappings(Long controllerId, String dbId) throws IOException {
        String obdaPath = getObdaPath(controllerId, dbId);
        if (!Files.exists(Paths.get(obdaPath))) {
            return new ArrayList<>();
        }

        List<SchemaMappingDTO> mappings = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(obdaPath));

        String currentTable = null;
        String currentColumn = null;
        String currentClass = null;
        String currentProperty = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Skip header sections and empty lines
            if (line.startsWith("[") || line.isEmpty()) {
                continue;
            }

            // Beginning of a new mapping
            if (line.startsWith("mappingId")) {
                // Clear previous values
                currentTable = null;
                currentColumn = null;
                currentClass = null;
                currentProperty = null;

                // Process target line (next line)
                String targetLine = lines.get(++i).trim();
                if (targetLine.startsWith("target")) {
                    // Extract schema class
                    if (targetLine.contains("schema:")) {
                        Pattern classPattern = Pattern.compile("a schema:([^ ]+)");
                        Matcher classMatcher = classPattern.matcher(targetLine);
                        if (classMatcher.find()) {
                            currentClass = classMatcher.group(1);
                        }

                        // Extract schema property
                        Pattern propertyPattern = Pattern.compile("schema:([^ ]+) \"");
                        Matcher propertyMatcher = propertyPattern.matcher(targetLine);
                        if (propertyMatcher.find()) {
                            currentProperty = propertyMatcher.group(1);
                        }
                    }
                }

                // Process source line (next line)
                String sourceLine = lines.get(++i).trim();
                if (sourceLine.startsWith("source")) {
                    String[] parts = sourceLine.split("FROM");
                    if (parts.length > 1) {
                        currentTable = parts[1].trim();
                        String[] selectParts = parts[0].split("SELECT")[1].trim().split(",");
                        for (String part : selectParts) {
                            if (!part.contains("user_id")) { // Skip ID column
                                currentColumn = part.trim();
                            }
                        }
                    }
                }

                // If we have all components, create a mapping
                if (currentTable != null && currentColumn != null &&
                        currentClass != null && currentProperty != null) {
                    mappings.add(new SchemaMappingDTO(
                            currentTable,
                            currentColumn,
                            currentClass,
                            currentProperty
                    ));
                }
            }
        }

        return mappings;
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

        try (Connection connection = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
        )) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Fetch tables
            ResultSet rs = metaData.getTables(
                    connection.getCatalog(),
                    null,
                    "%",
                    new String[]{"TABLE"}
            );

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                TableMetadataDTO tableMetadata = new TableMetadataDTO(tableName);

                // Fetch columns for each table
                ResultSet columns = metaData.getColumns(
                        connection.getCatalog(),
                        null,
                        tableName,
                        "%"
                );

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
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword())) {
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
            Connection connection = DriverManager.getConnection(
                    config.getJdbcUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
            connection.close();
            return true;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
        List<String> keysToRemove = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList());

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