package com.frontrow.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedissonClient redisson;

    public RedisHealthIndicator(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public Health health() {
        try {
            redisson.getBucket("frontrow:health").isExists();
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
