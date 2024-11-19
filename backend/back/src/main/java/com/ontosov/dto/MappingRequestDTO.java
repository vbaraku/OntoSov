package com.ontosov.dto;

import java.util.List;

public record MappingRequestDTO(DatabaseConfigDTO databaseConfig, List<SchemaMappingDTO> mappings) {}
