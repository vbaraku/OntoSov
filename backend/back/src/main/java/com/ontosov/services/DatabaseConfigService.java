package com.ontosov.services;

import com.ontosov.dto.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.sql.DriverManager.getConnection;

@Service
public class DatabaseConfigService {

    private static final String ONTOP_DIR = "src/main/resources/ontop/";
    private static final String OBDA_FILE_PATH = ONTOP_DIR + "mappings.obda";
    private static final String CONFIG_FILE_PATH = ONTOP_DIR + "database_configs.properties";


    public boolean testDatabaseConnection(DatabaseConfigDTO config) {
        try (Connection connection = getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
        )) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void saveDatabaseConfiguration(DatabaseConfigDTO config) throws IOException {
        Files.createDirectories(Paths.get(ONTOP_DIR));

        String properties = String.format("""
        jdbc.url=%s
        jdbc.driver=%s
        jdbc.user=%s
        jdbc.password=%s""",
                config.getJdbcUrl(),
                getJdbcDriver(config.getDatabaseType()),
                config.getUsername(),
                config.getPassword()
        );

        Files.writeString(Paths.get(CONFIG_FILE_PATH), properties);
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

    public void saveSchemaMappings(MappingRequestDTO request) throws IOException {
        Map<String, String> primaryKeys = new HashMap<>();
        StringBuilder obdaContent = new StringBuilder();

        // Header
        obdaContent.append("[PrefixDeclaration]\n")
                .append(":       http://example.org/resource#\n")
                .append("schema: http://schema.org/\n")
                .append("xsd:    http://www.w3.org/2001/XMLSchema#\n\n")
                .append("[MappingDeclaration] @collection [[\n");

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
        Files.writeString(Paths.get(OBDA_FILE_PATH), obdaContent.toString());
    }

    private String getPrimaryKeyColumn(DatabaseConfigDTO config, String tableName) {
        try (Connection conn = getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }
            throw new RuntimeException("No primary key found for table: " + tableName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get primary key for table: " + tableName, e);
        }
    }

    public List<SchemaMappingDTO> loadExistingMappings() throws IOException {
        List<SchemaMappingDTO> mappings = new ArrayList<>();
        String content = Files.readString(Paths.get(OBDA_FILE_PATH));

        // Parse mappings section
        int mappingsStart = content.indexOf("[MappingDeclaration]");
        if (mappingsStart == -1) return mappings;

        String[] mappingBlocks = content.substring(mappingsStart)
                .split("mappingId");

        for (String block : mappingBlocks) {
            if (block.trim().isEmpty()) continue;

            // Extract mapping components using regex
            Pattern targetPattern = Pattern.compile(":Resource/\\{(.+?)\\} a schema:(.+?) \\. :Resource/\\{\\1\\} schema:(.+?) \"\\{(.+?)\\}\"");
            Pattern sourcePattern = Pattern.compile("source SELECT .+?, (.+?) FROM (.+?)\\n");

            Matcher targetMatcher = targetPattern.matcher(block);
            Matcher sourceMatcher = sourcePattern.matcher(block);

            if (targetMatcher.find() && sourceMatcher.find()) {
                mappings.add(new SchemaMappingDTO(
                        sourceMatcher.group(2).trim(),  // table
                        sourceMatcher.group(1).trim(),  // column
                        targetMatcher.group(2).trim(),  // schemaClass
                        targetMatcher.group(3).trim()   // schemaProperty
                ));
            }
        }

        return mappings;
    }


    public List<TableMetadataDTO> getDatabaseTables(DatabaseConfigDTO config) {
        List<TableMetadataDTO> tables = new ArrayList<>();

        try (Connection connection = getConnection(
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
        } catch (SQLException e) {
            // Log error or throw custom exception
            e.printStackTrace();
        }

        return tables;
    }
}