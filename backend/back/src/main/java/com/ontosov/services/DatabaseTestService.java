package com.ontosov.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class DatabaseTestService {

    private final JdbcTemplate postgresTemplate;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public DatabaseTestService(DataSource controller1DataSource, MongoTemplate mongoTemplate) {
        this.postgresTemplate = new JdbcTemplate(controller1DataSource);
        this.mongoTemplate = mongoTemplate;
    }

    public boolean testPostgresConnection() {
        try {
            postgresTemplate.queryForObject("SELECT 1", Integer.class);
            System.out.println("PostgreSQL connection successful.");
            return true;
        } catch (Exception e) {
            System.err.println("PostgreSQL connection failed: " + e.getMessage());
            return false;
        }
    }

    public boolean testMongoConnection() {
        try {
            mongoTemplate.getDb().listCollectionNames();
            System.out.println("MongoDB connection successful.");
            return true;
        } catch (Exception e) {
            System.err.println("MongoDB connection failed: " + e.getMessage());
            return false;
        }
    }
}
