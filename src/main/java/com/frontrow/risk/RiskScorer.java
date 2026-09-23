package com.frontrow.risk;

public interface RiskScorer {

    RiskAssessment score(RiskSignals signals);
}
