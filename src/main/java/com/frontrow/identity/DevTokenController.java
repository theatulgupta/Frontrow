package com.frontrow.identity;

import com.frontrow.config.FrontrowProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/tokens")
@ConditionalOnProperty(name = "frontrow.identity.dev-tokens-enabled", havingValue = "true")
public class DevTokenController {

    private final TokenSigner tokenSigner;
    private final FrontrowProperties properties;

    public DevTokenController(TokenSigner tokenSigner, FrontrowProperties properties) {
        this.tokenSigner = tokenSigner;
        this.properties = properties;
    }

    @PostMapping
    public TokenResponse issue(@Valid @RequestBody IssueTokenRequest request) {
        Instant expiresAt = Instant.now().plus(properties.getIdentity().getTokenTtl());
        return new TokenResponse(tokenSigner.issue(request.userId(), expiresAt), expiresAt);
    }

    public record IssueTokenRequest(@NotBlank String userId) {
    }

    public record TokenResponse(String token, Instant expiresAt) {
    }
}
