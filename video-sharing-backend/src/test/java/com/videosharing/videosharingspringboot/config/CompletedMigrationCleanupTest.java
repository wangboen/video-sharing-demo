package com.videosharing.videosharingspringboot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CompletedMigrationCleanupTest {
    @Test
    void legacyUserRoleMigrationIsNoLongerRegisteredAfterDataMigrationCompletes() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.videosharing.videosharingspringboot.config.LegacyUserRoleMigration"));
    }
}
