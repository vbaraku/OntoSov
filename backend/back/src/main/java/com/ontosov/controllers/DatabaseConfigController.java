package com.ontosov.controllers;

import com.ontosov.dto.DatabaseConfigDTO;
import com.ontosov.dto.SchemaMappingDTO;
import com.ontosov.dto.TableMetadataDTO;
import com.ontosov.services.DatabaseConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/database")
public class DatabaseConfigController {

    @Autowired
    private DatabaseConfigService databaseConfigService;

    @PostMapping("/connect")
    public ResponseEntity<?> testDatabaseConnection(@RequestBody DatabaseConfigDTO configDTO) {
        boolean connectionResult = databaseConfigService.testDatabaseConnection(configDTO);
        return connectionResult
                ? ResponseEntity.ok().body("Connection successful")
                : ResponseEntity.badRequest().body("Connection failed");
    }

    @PostMapping("/save-config")
    public ResponseEntity<?> saveDatabaseConfiguration(@RequestBody DatabaseConfigDTO configDTO) throws IOException {
        databaseConfigService.saveDatabaseConfiguration(configDTO);
        return ResponseEntity.ok().body("Database configuration saved");
    }

    @PostMapping("/save-mappings")
    public ResponseEntity<?> saveSchemaMappings(
            @RequestBody List<SchemaMappingDTO> mappings
    ) throws IOException {
        databaseConfigService.saveSchemaMappings(mappings);
        return ResponseEntity.ok().body("Schema mappings saved");
    }

    @PostMapping("/tables")
    public ResponseEntity<?> getDatabaseTables(@RequestBody DatabaseConfigDTO config) {
        List<TableMetadataDTO> tables = databaseConfigService.getDatabaseTables(config);
        return ResponseEntity.ok().body(tables);
    }
}