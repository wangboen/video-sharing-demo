package com.videosharing.videosharingspringboot.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesTenThousandUniqueStrictlyIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);

        List<Long> ids = IntStream.range(0, 10_000)
                .mapToObj(ignored -> generator.nextId())
                .toList();

        Set<Long> uniqueIds = new HashSet<>(ids);
        assertThat(uniqueIds).hasSize(10_000);
        assertThat(ids).isSorted();
        assertThat(ids).doesNotHaveDuplicates();
    }
}
