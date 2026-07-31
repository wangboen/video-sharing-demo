package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    private static final long CUSTOM_EPOCH_MILLIS = 1_704_067_200_000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    private final long workerId;
    private long sequence;
    private long lastTimestamp = -1L;

    @Autowired
    public SnowflakeIdGenerator(CommentProperties properties) {
        this(properties.getSnowflakeWorkerId());
    }

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Snowflake worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = Math.max(System.currentTimeMillis(), lastTimestamp);
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        long timestampPart = timestamp - CUSTOM_EPOCH_MILLIS;
        if (timestampPart < 0 || timestampPart >= (1L << 41)) {
            throw new IllegalStateException("Snowflake timestamp is outside the supported range");
        }
        return (timestampPart << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitUntilNextMillis(long timestamp) {
        long current = System.currentTimeMillis();
        while (current <= timestamp) {
            current = System.currentTimeMillis();
        }
        return current;
    }
}
