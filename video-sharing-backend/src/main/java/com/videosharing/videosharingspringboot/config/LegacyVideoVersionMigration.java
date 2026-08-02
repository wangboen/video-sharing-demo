package com.videosharing.videosharingspringboot.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyVideoVersionMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public LegacyVideoVersionMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        jdbc.update("update videos set version = 0 where version is null");
        for (String statement : statementsFor(databaseProductName())) {
            jdbc.execute(statement);
        }
    }

    static List<String> statementsFor(String databaseProductName) {
        String product = databaseProductName.toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return List.of(
                    "alter table videos alter column version set default 0",
                    "alter table videos modify column version bigint not null default 0");
        }
        if (product.contains("postgresql")) {
            return List.of(
                    "alter table videos alter column version set default 0",
                    "alter table videos alter column version set not null");
        }
        if (product.contains("h2")) {
            return List.of(
                    "alter table videos alter column version set default 0",
                    "alter table videos alter column version set not null");
        }
        throw new IllegalStateException(
                "Unsupported database dialect for legacy video version migration: " + databaseProductName);
    }

    private String databaseProductName() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not determine database dialect for legacy video version migration", exception);
        }
    }
}
