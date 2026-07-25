package com.videosharing.videosharingspringboot.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LegacyUserRoleMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public LegacyUserRoleMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        if (!hasColumn("users", "role")) {
            return;
        }
        jdbc.update("update users set role = 'USER' where role is null");
        if (hasTable("staff_accounts")) {
            jdbc.update("""
                    insert into staff_accounts (id, username, password, email, role, created_at, updated_at)
                    select u.id, u.username, u.password, u.email, u.role, u.created_at, u.updated_at
                    from users u
                    where u.role in ('REVIEWER', 'ADMIN')
                      and not exists (
                          select 1
                          from staff_accounts s
                          where s.username = u.username
                      )
                    """);
        }
    }

    private boolean hasTable(String table) {
        return hasMetadataMatch(table, null);
    }

    private boolean hasColumn(String table, String column) {
        return hasMetadataMatch(table, column);
    }

    private boolean hasMetadataMatch(String table, String column) {
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            if (column == null) {
                try (var tables = metadata.getTables(connection.getCatalog(), null, table, null)) {
                    if (tables.next()) {
                        return true;
                    }
                }
                try (var tables = metadata.getTables(connection.getCatalog(), null, table.toUpperCase(), null)) {
                    return tables.next();
                }
            }
            try (var columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
                if (columns.next()) {
                    return true;
                }
            }
            try (var columns = metadata.getColumns(connection.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
                return columns.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not inspect database metadata for user role migration", exception);
        }
    }
}
