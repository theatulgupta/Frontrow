package com.frontrow.risk;

import com.frontrow.config.FrontrowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RiskScorerConfig {

    private static final Logger log = LoggerFactory.getLogger(RiskScorerConfig.class);

    @Bean
    @Primary
    @ConditionalOnProperty(name = "frontrow.risk.provider", havingValue = "local", matchIfMissing = true)
    public RiskScorer deterministicRiskScorer() {
        return new DeterministicRiskScorer();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "frontrow.risk.provider", havingValue = "openai")
    public RiskScorer openAiRiskScorer(FrontrowProperties properties) {
        String apiKey = properties.getAi().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FRONTROW_RISK_PROVIDER=openai but FRONTROW_AI_API_KEY is blank; using the deterministic scorer");
            return new DeterministicRiskScorer();
        }
        return new OpenAiRiskScorer(properties);
    }
}
