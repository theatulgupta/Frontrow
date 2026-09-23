package com.frontrow.risk;

public record RiskAssessment(RiskDecision decision, String reason, boolean flagged) {

    public static final String SCORER_UNAVAILABLE = "SCORER_UNAVAILABLE";

    public static RiskAssessment failOpen() {
        return new RiskAssessment(RiskDecision.ALLOW, SCORER_UNAVAILABLE, true);
    }

    public String metricTag() {
        if (flagged && decision == RiskDecision.ALLOW) {
            return "allow_flagged";
        }
        return decision.name().toLowerCase();
    }
}
