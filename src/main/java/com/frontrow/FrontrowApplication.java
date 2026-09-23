package com.frontrow;

import com.frontrow.config.FrontrowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FrontrowProperties.class)
public class FrontrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontrowApplication.class, args);
    }
}
