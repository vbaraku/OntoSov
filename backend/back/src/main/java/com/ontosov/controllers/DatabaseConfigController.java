package com.ontosov.controllers;

import com.ontosov.dto.DatabaseConfigDTO;
import com.ontosov.dto.MappingRequestDTO;
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
    public ResponseEntity<?> saveDatabaseConfiguration(
            @RequestBody DatabaseConfigDTO configDTO,
            @RequestParam Long controllerId) throws IOException {
        databaseConfigService.saveDatabaseConfiguration(configDTO, controllerId);
        return ResponseEntity.ok().body("Database configuration saved");
    }

    @PostMapping("/save-mappings")
    public ResponseEntity<Void> saveMappings(
            @RequestBody MappingRequestDTO request,
            @RequestParam Long controllerId) {
        try {
            databaseConfigService.saveSchemaMappings(request, controllerId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/mappings/{controllerId}/{databaseName}")
    public ResponseEntity<List<SchemaMappingDTO>> getMappings(
            @PathVariable Long controllerId,
            @PathVariable String databaseName) {
        try {
            List<SchemaMappingDTO> mappings = databaseConfigService.getMappings(controllerId, databaseName);
            return ResponseEntity.ok(mappings);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/controller/{controllerId}/databases")
    public ResponseEntity<List<DatabaseConfigDTO>> getDatabasesForController(
            @PathVariable Long controllerId) {
        try {
            List<DatabaseConfigDTO> databases = databaseConfigService.getDatabasesForController(controllerId);
            return ResponseEntity.ok(databases);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/tables")
    public ResponseEntity<?> getDatabaseTables(@RequestBody DatabaseConfigDTO config) {
        List<TableMetadataDTO> tables = databaseConfigService.getDatabaseTables(config);
        return ResponseEntity.ok().body(tables);
    }

    @DeleteMapping("/controller/{controllerId}/databases/{dbId}")
    public ResponseEntity<Void> deleteDatabaseConfig(
            @PathVariable Long controllerId,
            @PathVariable String dbId) {
        try {
            databaseConfigService.deleteDatabaseConfig(dbId, controllerId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}