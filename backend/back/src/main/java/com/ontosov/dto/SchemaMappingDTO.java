package com.ontosov.dto;

import lombok.Data;

@Data
public class SchemaMappingDTO {
    private String databaseTable;
    private String databaseColumn;
    private String schemaClass;
    private String schemaProperty;
}