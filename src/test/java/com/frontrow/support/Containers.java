package com.frontrow.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public final class Containers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    private static volatile boolean started;

    private Containers() {
    }

    public static void start() {
        if (started) {
            return;
        }
        synchronized (Containers.class) {
            if (started) {
                return;
            }
            POSTGRES.start();
            REDIS.start();
            KAFKA.start();
            started = true;
        }
    }
}
