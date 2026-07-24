package com.videosharing.videosharingspringboot.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyUserRoleMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public LegacyUserRoleMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        jdbc.update("update users set role = 'USER' where role is null");
        jdbc.execute(notNullStatement());
    }

    private String notNullStatement() {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("mysql") || product.contains("mariadb")) {
                return "alter table users modify column role varchar(20) not null";
            }
            return "alter table users alter column role varchar(20) not null";
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not determine database dialect for user role migration", exception);
        }
    }
}
