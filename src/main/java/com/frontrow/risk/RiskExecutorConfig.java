package com.frontrow.risk;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService riskExecutor() {
        return new ThreadPoolExecutor(
                8,
                8,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
