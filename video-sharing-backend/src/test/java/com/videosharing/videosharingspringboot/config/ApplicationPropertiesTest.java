package com.videosharing.videosharingspringboot.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesTest {

    @Test
    void datasourceDefaultsToPostgreSqlAndSearchDoesNotRequireElasticsearch() throws IOException {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertThat(stream).isNotNull();
            properties.load(stream);
        }

        assertThat(properties)
                .containsEntry("spring.datasource.driver-class-name", "org.postgresql.Driver")
                .containsEntry("spring.jpa.properties.hibernate.jdbc.time_zone", "UTC")
                .doesNotContainKey("spring.jpa.properties.hibernate.dialect")
                .doesNotContainKey("spring.elasticsearch.uris")
                .doesNotContainKey("video-search.index-name");
    }

    @Test
    void sqlQueriesAreLoggedThroughHibernateLoggers() throws IOException {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertThat(stream).isNotNull();
            properties.load(stream);
        }

        assertThat(properties)
                .containsEntry("spring.jpa.show-sql", "false")
                .containsEntry("logging.level.org.hibernate.SQL", "DEBUG")
                .containsEntry("logging.level.org.hibernate.orm.jdbc.bind", "TRACE");
    }
}
