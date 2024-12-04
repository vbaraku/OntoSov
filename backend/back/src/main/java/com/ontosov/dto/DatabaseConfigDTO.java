package com.ontosov.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DatabaseConfigDTO {
    private String id;
    private String databaseType;
    private String host;
    private String port;
    private String databaseName;
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String jdbcUrl;

    @JsonProperty("password")
    public String getPasswordForSerialization() {
        return password;
    }
}