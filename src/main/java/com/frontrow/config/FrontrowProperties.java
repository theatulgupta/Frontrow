package com.frontrow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "frontrow")
public class FrontrowProperties {

    private Duration holdTtl = Duration.ofMinutes(2);
    private final Lock lock = new Lock();
    private final Payment payment = new Payment();
    private final Risk risk = new Risk();
    private final Ai ai = new Ai();
    private final Outbox outbox = new Outbox();
    private final Expiry expiry = new Expiry();
    private final Identity identity = new Identity();

    public Duration getHoldTtl() {
        return holdTtl;
    }

    public void setHoldTtl(Duration holdTtl) {
        this.holdTtl = holdTtl;
    }

    public Lock getLock() {
        return lock;
    }

    public Payment getPayment() {
        return payment;
    }

    public Risk getRisk() {
        return risk;
    }

    public Ai getAi() {
        return ai;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Expiry getExpiry() {
        return expiry;
    }

    public Identity getIdentity() {
        return identity;
    }

    public static class Lock {
        private Duration wait = Duration.ofMillis(200);
        private Duration lease = Duration.ofSeconds(10);

        public Duration getWait() {
            return wait;
        }

        public void setWait(Duration wait) {
            this.wait = wait;
        }

        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }
    }

    public static class Payment {
        public enum Mode {
            SUCCESS, DECLINE, TIMEOUT
        }

        private Mode mode = Mode.SUCCESS;
        private int latencyMs = 50;
        private int timeoutMs = 200;
        private int maxAttempts = 3;
        private boolean consumerEnabled = true;

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public int getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(int latencyMs) {
            this.latencyMs = latencyMs;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public boolean isConsumerEnabled() {
            return consumerEnabled;
        }

        public void setConsumerEnabled(boolean consumerEnabled) {
            this.consumerEnabled = consumerEnabled;
        }
    }

    public static class Risk {
        private String provider = "local";
        private int timeoutMs = 150;
        private Duration window = Duration.ofSeconds(60);

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Ai {
        private String apiKey = "";
        private String model = "gpt-4o-mini";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Outbox {
        private Duration pollInterval = Duration.ofMillis(200);
        private boolean relayEnabled = true;

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }
    }

    public static class Expiry {
        private Duration scanInterval = Duration.ofSeconds(1);

        public Duration getScanInterval() {
            return scanInterval;
        }

        public void setScanInterval(Duration scanInterval) {
            this.scanInterval = scanInterval;
        }
    }

    public static class Identity {
        private String tokenSecret = "dev-only-change-me";
        private boolean devTokensEnabled = false;
        private Duration tokenTtl = Duration.ofHours(12);

        public String getTokenSecret() {
            return tokenSecret;
        }

        public void setTokenSecret(String tokenSecret) {
            this.tokenSecret = tokenSecret;
        }

        public boolean isDevTokensEnabled() {
            return devTokensEnabled;
        }

        public void setDevTokensEnabled(boolean devTokensEnabled) {
            this.devTokensEnabled = devTokensEnabled;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }
    }
}
