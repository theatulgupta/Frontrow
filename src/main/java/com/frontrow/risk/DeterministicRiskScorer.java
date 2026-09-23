package com.frontrow.risk;

public class DeterministicRiskScorer implements RiskScorer {

    static final int USER_ATTEMPT_REJECT = 20;
    static final int SEAT_ATTEMPT_REJECT = 40;
    static final int DISTINCT_SEAT_CHALLENGE = 6;
    static final int IN_FLIGHT_CHALLENGE = 2;

    @Override
    public RiskAssessment score(RiskSignals signals) {
        if (signals.userAttempts() >= USER_ATTEMPT_REJECT) {
            return new RiskAssessment(RiskDecision.REJECT, "USER_ATTEMPT_RATE", false);
        }
        if (signals.seatAttempts() >= SEAT_ATTEMPT_REJECT) {
            return new RiskAssessment(RiskDecision.REJECT, "SEAT_ATTEMPT_RATE", false);
        }
        if (signals.distinctSeats() >= DISTINCT_SEAT_CHALLENGE) {
            return new RiskAssessment(RiskDecision.CHALLENGE, "SEAT_SPRAY", false);
        }
        if (signals.inFlightHolds() >= IN_FLIGHT_CHALLENGE) {
            return new RiskAssessment(RiskDecision.CHALLENGE, "TOO_MANY_HOLDS", false);
        }
        return new RiskAssessment(RiskDecision.ALLOW, "OK", false);
    }
}
