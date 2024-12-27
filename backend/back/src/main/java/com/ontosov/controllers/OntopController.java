package com.ontosov.controllers;

import com.ontosov.services.OntopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ontop")
public class OntopController {

    @Autowired
    private OntopService ontopService;

    @GetMapping("/person/{taxId}/controller/{controllerId}")
    public ResponseEntity<Map<String, Map<String, Object>>> getPersonData(
            @PathVariable String taxId,
            @PathVariable Long controllerId) {
        try {
            String query = ontopService.getPersonDataQuery();
            List<Map<String, String>> results = ontopService.executeQuery(controllerId, taxId, query);

            Map<String, Map<String, Object>> formattedResults = results.stream()
                    .collect(Collectors.groupingBy(
                            m -> m.get("source"),
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> {
                                        Map<String, Object> properties = new HashMap<>();
                                        list.forEach(item -> {
                                            String propertyName = item.get("property")
                                                    .replace("http://schema.org/", "");
                                            properties.put(propertyName, item.get("value"));
                                        });
                                        return properties;
                                    }
                            )
                    ));

            return ResponseEntity.ok(formattedResults);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}