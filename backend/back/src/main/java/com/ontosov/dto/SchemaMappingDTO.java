package com.ontosov.dto;

import lombok.Data;

@Data
public class SchemaMappingDTO {
    private String databaseTable;
    private String databaseColumn;
    private String joinTable;     // For many-to-many
    private String targetTable;   // The table being joined to
    private String sourceKey;     // Foreign key from source
    private String targetKey;     // Foreign key to target
    private String schemaClass;
    private String schemaProperty;

    public SchemaMappingDTO() {}

    public SchemaMappingDTO(String databaseTable, String databaseColumn, String schemaClass, String schemaProperty) {
        this.databaseTable = databaseTable;
        this.databaseColumn = databaseColumn;
        this.schemaClass = schemaClass;
        this.schemaProperty = schemaProperty;
    }

    public SchemaMappingDTO(String databaseTable, String databaseColumn, String joinTable, String targetTable, String sourceKey, String targetKey, String schemaClass, String schemaProperty) {
        this.databaseTable = databaseTable;
        this.databaseColumn = databaseColumn;
        this.joinTable = joinTable;
        this.targetTable = targetTable;
        this.sourceKey = sourceKey;
        this.targetKey = targetKey;
        this.schemaClass = schemaClass;
        this.schemaProperty = schemaProperty;
    }
}