package com.ontosov.controllers;

import com.ontosov.services.OntopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ontop")
public class OntopController {

    @Autowired
    private OntopService ontopService;

    @GetMapping("/person/{taxId}/controller/{controllerId}")
    public ResponseEntity<List<Map<String, String>>> getPersonData(
            @PathVariable String taxId,
            @PathVariable Long controllerId) {
        try {
            String query = ontopService.getPersonDataQuery();
            List<Map<String, String>> results = ontopService.executeQuery(controllerId, taxId, query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace(); // for debugging
            return ResponseEntity.internalServerError().build();
        }
    }
}