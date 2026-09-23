package com.frontrow.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdSecretGuard {

    public ProdSecretGuard(FrontrowProperties properties) {
        String secret = properties.getIdentity().getTokenSecret();
        if (secret == null || secret.isBlank() || "dev-only-change-me".equals(secret) || secret.length() < 32) {
            throw new IllegalStateException(
                    "FRONTROW_TOKEN_SECRET must be set to a value of at least 32 characters in prod");
        }
    }
}
