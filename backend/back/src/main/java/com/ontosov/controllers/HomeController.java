package com.ontosov.controllers;

import com.ontosov.services.DatabaseTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class HomeController {

    private final DatabaseTestService databaseTestService;

    @Autowired
    public HomeController(DatabaseTestService databaseTestService) {
        this.databaseTestService = databaseTestService;
    }

    @GetMapping("/postgres")
    public String testPostgres() {
        return databaseTestService.testPostgresConnection() ? "Postgres connected successfully" : "Postgres connection failed";
    }

    @GetMapping("/mongo")
    public String testMongo() {
        return databaseTestService.testMongoConnection() ? "MongoDB connected successfully" : "MongoDB connection failed";
    }
}
