package com.frontrow.risk;

public record RiskSignals(int userAttempts, int seatAttempts, int distinctSeats, int inFlightHolds) {

    public static RiskSignals empty() {
        return new RiskSignals(0, 0, 0, 0);
    }
}
