package com.ontosov.dto;

import lombok.Data;

@Data
public class SchemaMappingDTO {
    private String databaseTable;
    private String databaseColumn;
    private String schemaClass;
    private String schemaProperty;

    public SchemaMappingDTO(String databaseTable, String databaseColumn, String schemaClass, String schemaProperty) {
        this.databaseTable = databaseTable;
        this.databaseColumn = databaseColumn;
        this.schemaClass = schemaClass;
        this.schemaProperty = schemaProperty;
    }
}