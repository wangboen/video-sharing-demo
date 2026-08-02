package com.videosharing.videosharingspringboot.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSqlTest {

    @Test
    void schemaDefinesCommentRelationsLikesAndCommandStatus() throws IOException {
        try (var stream = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(stream).isNotNull();
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase()
                    .replaceAll("\\s+", " ");

            assertThat(schema)
                    .contains("create table comment_relations")
                    .contains("create index idx_comment_relations_time on comment_relations (video_id, status, created_at desc, comment_id desc)")
                    .contains("create index idx_comment_relations_hot on comment_relations (video_id, status, like_count desc, created_at desc, comment_id desc)")
                    .contains("create index idx_comment_relations_user on comment_relations (user_id, anonymized, created_at desc, comment_id desc)")
                    .contains("create table comment_likes")
                    .contains("primary key (comment_id, user_id)")
                    .contains("create table comment_command_status")
                    .contains("constraint uk_comment_command_status_comment unique (comment_id)")
                    .contains("constraint chk_comment_command_status_status check (status in ('accepted', 'completed', 'failed'))");
        }
    }

    @Test
    void freshSchemaSeparatesPublicUsersFromStaffAccounts() throws IOException {
        try (var stream = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(stream).isNotNull();
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase()
                    .replaceAll("\\s+", " ");

            assertThat(schema)
                    .contains("create table users")
                    .doesNotContain("constraint chk_users_role")
                    .doesNotContain("role varchar(20) not null default 'user'")
                    .contains("create table staff_accounts")
                    .contains("role varchar(20) not null")
                    .contains("constraint chk_staff_accounts_role check (role in ('reviewer', 'admin'))")
                    .contains("status varchar(20) not null default 'pending'")
                    .contains("reviewer_id bigint null")
                    .contains("file_deleted_at timestamp with time zone null")
                    .contains("thumbnail_deleted_at timestamp with time zone null")
                    .contains("version bigint not null default 0")
                    .contains("lease_owner_id bigint null")
                    .contains("claimed_at timestamp with time zone null")
                    .contains("lease_expires_at timestamp with time zone null")
                    .contains("foreign key (user_id) references users(id) on delete cascade")
                    .contains("foreign key (reviewer_id) references staff_accounts(id) on delete set null")
                    .contains("foreign key (lease_owner_id) references staff_accounts(id) on delete set null")
                    .contains("create index idx_videos_status_upload_id on videos (status, upload_date, id)")
                    .contains("create index idx_videos_lease_owner_status_expiry on videos (lease_owner_id, status, lease_expires_at)")
                    .contains("create index idx_videos_user_upload_id on videos (user_id, upload_date, id)")
                    .contains("create index idx_staff_accounts_role_username on staff_accounts (role, username)");
        }
    }
}
