package com.videosharing.videosharingspringboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaCompatibilityTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void schemaAndCommentLikeInsertAreCompatibleWithPostgres() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        dropSchemaObjects(jdbc);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(dataSource);

        jdbc.update("insert into users (username, password) values (?, ?)", "postgres-user", "encoded");
        Long userId = jdbc.queryForObject("select id from users where username = ?", Long.class, "postgres-user");
        jdbc.update("insert into videos (user_id, title, file_path) values (?, ?, ?)",
                userId, "PostgreSQL video", "postgres.mp4");
        Long videoId = jdbc.queryForObject("select id from videos where file_path = ?", Long.class, "postgres.mp4");
        jdbc.update("""
                insert into comment_relations
                    (comment_id, video_id, user_id, status, like_count, anonymized, created_at, updated_at)
                values (?, ?, ?, 'VISIBLE', 0, false, ?, ?)
                """, 101L, videoId, userId, Instant.now(), Instant.now());

        int firstInsert = jdbc.update("""
                insert into comment_likes (comment_id, user_id, created_at)
                select ?, ?, ?
                where not exists (
                    select 1 from comment_likes
                    where comment_id = ?
                      and user_id = ?
                )
                """, 101L, userId, Instant.now(), 101L, userId);
        int duplicateInsert = jdbc.update("""
                insert into comment_likes (comment_id, user_id, created_at)
                select ?, ?, ?
                where not exists (
                    select 1 from comment_likes
                    where comment_id = ?
                      and user_id = ?
                )
                """, 101L, userId, Instant.now(), 101L, userId);

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comment_likes", Integer.class)).isEqualTo(1);
    }

    @Test
    void commentLikePrimaryKeyUsesExpectedPostgresConstraintName() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        dropSchemaObjects(jdbc);

        jdbc.execute("create table comment_likes (comment_id bigint not null, user_id bigint not null, primary key (comment_id, user_id))");

        String constraintName = jdbc.queryForObject("""
                select constraint_name
                from information_schema.table_constraints
                where table_name = 'comment_likes'
                  and constraint_type = 'PRIMARY KEY'
                """, String.class);

        assertThat(constraintName).isEqualTo("comment_likes_pkey");
    }

    private void dropSchemaObjects(JdbcTemplate jdbc) {
        jdbc.execute("drop table if exists comment_likes cascade");
        jdbc.execute("drop table if exists comment_command_status cascade");
        jdbc.execute("drop table if exists user_video_watch_history cascade");
        jdbc.execute("drop table if exists comment_relations cascade");
        jdbc.execute("drop table if exists videos cascade");
        jdbc.execute("drop table if exists staff_accounts cascade");
        jdbc.execute("drop table if exists users cascade");
    }
}
