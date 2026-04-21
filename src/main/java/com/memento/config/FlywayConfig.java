package com.memento.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

// Explicit Flyway bean — bypasses Spring Boot's FlywayAutoConfiguration entirely.
//
// Why not rely on auto-configuration?
// Spring Boot 4.x changed how FlywayAutoConfiguration wires up, and with the
// flyway-database-postgresql module present the auto-config can silently skip
// migration without logging any errors. Declaring the bean explicitly guarantees
// Flyway always runs, regardless of auto-config ordering or classpath changes.
//
// initMethod = "migrate" tells Spring to call flyway.migrate() as soon as this
// bean is constructed — before JPA tries to validate entity mappings against the
// schema. This ensures the tables exist when Hibernate starts up.
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                // Use the same DataSource Spring Boot already configured (HikariCP pool)
                .dataSource(dataSource)
                // Must match the directory inside the JAR — src/main/resources/db/migration
                .locations("classpath:db/migration")
                .load();
    }

}
