package com.frontrow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicRiskScorerTest {

    private final DeterministicRiskScorer scorer = new DeterministicRiskScorer();

    @Test
    void allowsAQuietAttempt() {
        RiskAssessment assessment = scorer.score(new RiskSignals(0, 0, 1, 0));
        assertThat(assessment.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(assessment.flagged()).isFalse();
    }

    @Test
    void rejectsAUserFloodAndASeatFlood() {
        assertThat(scorer.score(new RiskSignals(20, 0, 1, 0)).reason()).isEqualTo("USER_ATTEMPT_RATE");
        assertThat(scorer.score(new RiskSignals(1, 40, 1, 0)).reason()).isEqualTo("SEAT_ATTEMPT_RATE");
    }

    @Test
    void challengesSeatSprayAndTooManyHolds() {
        assertThat(scorer.score(new RiskSignals(1, 1, 6, 0)).decision()).isEqualTo(RiskDecision.CHALLENGE);
        assertThat(scorer.score(new RiskSignals(1, 1, 1, 2)).reason()).isEqualTo("TOO_MANY_HOLDS");
    }
}
