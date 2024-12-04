package com.ontosov.dto;

import lombok.Data;

@Data
public class DatabaseConfigDTO {
    private String id;  // Changed from Long to String since we're using UUID
    private String databaseType;
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String password;
    private String jdbcUrl;
}