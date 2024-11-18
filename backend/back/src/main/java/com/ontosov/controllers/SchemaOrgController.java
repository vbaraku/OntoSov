package com.ontosov.controllers;

import com.ontosov.services.SchemaOrgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schema")
public class SchemaOrgController {

    @Autowired
    private SchemaOrgService schemaOrgService;

    @GetMapping("/classes")
    public ResponseEntity<List<String>> searchClasses(
            @RequestParam(required = false, defaultValue = "") String query
    ) {
        return ResponseEntity.ok(schemaOrgService.searchClasses(query));
    }

    @GetMapping("/properties/{className}")
    public ResponseEntity<List<String>> getProperties(@PathVariable String className) {
        return ResponseEntity.ok(schemaOrgService.getPropertiesForClass(className));
    }
}