package com.ontosov.dto;

import lombok.Data;

@Data
public class DatabaseConfigDTO {
    private String databaseType;
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String password;
    private String jdbcUrl;
}