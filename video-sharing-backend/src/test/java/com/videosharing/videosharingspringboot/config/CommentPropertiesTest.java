package com.videosharing.videosharingspringboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommentPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "comments.featured-limit=5",
                    "comments.featured-min-likes=20",
                    "comments.page-size-default=15",
                    "comments.page-size-max=40",
                    "comments.max-length=800",
                    "comments.hot-refresh-batch-size=250"
            );

    @Test
    void bindsCommentProperties() {
        contextRunner.run(context -> {
            CommentProperties properties = context.getBean(CommentProperties.class);
            assertThat(properties.getFeaturedLimit()).isEqualTo(5);
            assertThat(properties.getFeaturedMinLikes()).isEqualTo(20);
            assertThat(properties.getPageSizeDefault()).isEqualTo(15);
            assertThat(properties.getPageSizeMax()).isEqualTo(40);
            assertThat(properties.getMaxLength()).isEqualTo(800);
            assertThat(properties.getHotRefreshBatchSize()).isEqualTo(250);
        });
    }

    @EnableConfigurationProperties(CommentProperties.class)
    static class TestConfig {
    }
}
