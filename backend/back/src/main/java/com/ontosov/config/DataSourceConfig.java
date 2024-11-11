package com.ontosov.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // PostgreSQL configuration
    @Value("${datasource.controller1.url}")
    private String controller1Url;

    @Value("${datasource.controller1.username}")
    private String controller1Username;

    @Value("${datasource.controller1.password}")
    private String controller1Password;

    // MongoDB configuration
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public DataSource controller1DataSource() {
        return DataSourceBuilder.create()
                .url(controller1Url)
                .username(controller1Username)
                .password(controller1Password)
                .build();
    }

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), "controller2db");
    }
}
