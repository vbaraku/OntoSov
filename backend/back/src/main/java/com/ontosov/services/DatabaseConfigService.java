package com.ontosov.services;

import com.ontosov.dto.ColumnMetadataDTO;
import com.ontosov.dto.DatabaseConfigDTO;
import com.ontosov.dto.SchemaMappingDTO;
import com.ontosov.dto.TableMetadataDTO;
import org.springframework.stereotype.Service;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;

@Service
public class DatabaseConfigService {
    private static final String CONFIG_FILE_PATH = "database_configs.properties";
    private static final String OBDA_FILE_PATH = "ontop_mappings.obda";

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
        Properties properties = new Properties();
        File configFile = new File(CONFIG_FILE_PATH);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            }
        }

        String configKey = config.getDatabaseName() + "_" + config.getHost();
        properties.setProperty(configKey + ".url", config.getJdbcUrl());
        properties.setProperty(configKey + ".username", config.getUsername());
        properties.setProperty(configKey + ".password", config.getPassword());

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Database Configurations");
        }
    }

    public void saveSchemaMappings(List<SchemaMappingDTO> mappings) throws IOException {
        StringBuilder obdaContent = new StringBuilder();

        // OBDA File Header
        obdaContent.append("[PrefixDeclaration]\n");
        obdaContent.append(":       http://example.org/resource#\n");
        obdaContent.append("schema: http://schema.org/\n");
        obdaContent.append("xsd:    http://www.w3.org/2001/XMLSchema#\n\n");

        obdaContent.append("[MappingDeclaration] @collection [[\n");

        // Generate mappings
        for (SchemaMappingDTO mapping : mappings) {
            obdaContent.append(String.format("mappingId %s_%s_Mapping\n",
                    mapping.getDatabaseTable(),
                    mapping.getSchemaClass()));

            obdaContent.append(String.format("target :Resource/{id} a schema:%s .\n",
                    mapping.getSchemaClass()));
            obdaContent.append(String.format(":Resource/{id} schema:%s {column}^^xsd:string .\n",
                    mapping.getSchemaProperty()));

            obdaContent.append(String.format("source SELECT id, %s FROM %s\n\n",
                    mapping.getDatabaseColumn(),
                    mapping.getDatabaseTable()));
        }

        obdaContent.append("]]");

        try (FileWriter writer = new FileWriter(OBDA_FILE_PATH)) {
            writer.write(obdaContent.toString());
        }
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