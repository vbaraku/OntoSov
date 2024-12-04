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

    private String getObdaPath(Long controllerId) {
        return getControllerDir(controllerId) + "mappings.obda";
    }

    public void saveDatabaseConfiguration(DatabaseConfigDTO configDTO, Long controllerId) throws IOException {
        // Create controller directory if it doesn't exist
        String configPath = getConfigPath(controllerId);
        Files.createDirectories(Paths.get(configPath).getParent());

        Properties properties = new Properties();

        // Load existing properties if file exists
        if (Files.exists(Paths.get(configPath))) {
            try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
                properties.load(reader);
            }
        }

        // Generate a unique ID for this database if it's new
        String dbId = configDTO.getId() != null ?
                configDTO.getId().toString() :
                UUID.randomUUID().toString();

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
        StringBuilder obdaContent = new StringBuilder();

        // Header
        obdaContent.append("[PrefixDeclaration]\n")
                .append(":       http://example.org/resource#\n")
                .append("schema: http://schema.org/\n")
                .append("xsd:    http://www.w3.org/2001/XMLSchema#\n\n")
                .append("[MappingDeclaration] @collection [[\n");

        Map<String, String> primaryKeys = new HashMap<>();

        for (SchemaMappingDTO mapping : request.mappings()) {
            // Get or fetch primary key for this table
            String primaryKey = primaryKeys.computeIfAbsent(
                    mapping.getDatabaseTable(),
                    table -> getPrimaryKeyColumn(request.databaseConfig(), table)
            );

            String mappingId = String.format("%s_%s_%s_Mapping",
                    mapping.getDatabaseTable(),
                    mapping.getSchemaClass(),
                    mapping.getDatabaseColumn().replaceAll("\\s+", "_"));

            obdaContent.append(String.format("mappingId %s\n", mappingId))
                    .append(String.format("target :Resource/{%s} a schema:%s . ",
                            primaryKey,
                            mapping.getSchemaClass()))
                    .append(String.format(":Resource/{%s} schema:%s \"%s\"^^xsd:string .\n",
                            primaryKey,
                            mapping.getSchemaProperty(),
                            "{" + mapping.getDatabaseColumn() + "}"))
                    .append(String.format("source SELECT %s, %s FROM %s\n\n",
                            primaryKey,
                            mapping.getDatabaseColumn().equals(primaryKey) ? primaryKey + " as id" : mapping.getDatabaseColumn(),
                            mapping.getDatabaseTable()));
        }

        obdaContent.append("]]");

        // Save OBDA file
        String obdaPath = getObdaPath(controllerId);
        Files.writeString(Paths.get(obdaPath), obdaContent.toString());
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
                    dto.setId(k);  // Now just passing the String directly
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
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(configs.values());
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
        try (Connection connection = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
        )) {
            return true;
        } catch (Exception e) {
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