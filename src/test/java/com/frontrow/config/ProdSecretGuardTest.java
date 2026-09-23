package com.frontrow.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.frontrow.FrontrowApplication;
import com.frontrow.support.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ProdSecretGuardTest {

    static {
        Containers.start();
    }

    @Test
    void prodRefusesTheDefaultSecret() {
        assertThatThrownBy(() -> SpringApplication.run(
                FrontrowApplication.class,
                "--spring.profiles.active=prod",
                "--spring.datasource.url=" + Containers.POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + Containers.POSTGRES.getUsername(),
                "--spring.datasource.password=" + Containers.POSTGRES.getPassword(),
                "--spring.data.redis.host=" + Containers.REDIS.getHost(),
                "--spring.data.redis.port=" + Containers.REDIS.getMappedPort(6379),
                "--spring.kafka.bootstrap-servers=" + Containers.KAFKA.getBootstrapServers(),
                "--frontrow.identity.token-secret=dev-only-change-me"))
                .hasStackTraceContaining("FRONTROW_TOKEN_SECRET");
    }
}
