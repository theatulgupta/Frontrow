package com.frontrow.risk;

import com.frontrow.config.FrontrowMetrics;
import com.frontrow.config.FrontrowProperties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final RiskSignalReader signals;
    private final RiskScorer scorer;
    private final ExecutorService riskExecutor;
    private final FrontrowProperties properties;
    private final FrontrowMetrics metrics;

    public RiskEvaluationService(
            RiskSignalReader signals,
            RiskScorer scorer,
            ExecutorService riskExecutor,
            FrontrowProperties properties,
            FrontrowMetrics metrics) {
        this.signals = signals;
        this.scorer = scorer;
        this.riskExecutor = riskExecutor;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Outcome evaluate(String userId, UUID showId, UUID seatId) {
        RiskSignals snapshot;
        try {
            snapshot = signals.read(userId, showId, seatId);
        } catch (RuntimeException exception) {
            log.warn("risk_signals_failed showId={} seatId={}", showId, seatId, exception);
            return finish(RiskAssessment.failOpen(), RiskSignals.empty());
        }
        Future<RiskAssessment> future;
        try {
            future = riskExecutor.submit(() -> scorer.score(snapshot));
        } catch (RejectedExecutionException exception) {
            return finish(RiskAssessment.failOpen(), snapshot);
        }
        try {
            RiskAssessment assessment = future.get(properties.getRisk().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (assessment == null || assessment.decision() == null || assessment.reason() == null) {
                return finish(RiskAssessment.failOpen(), snapshot);
            }
            return finish(assessment, snapshot);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return finish(RiskAssessment.failOpen(), snapshot);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return finish(RiskAssessment.failOpen(), snapshot);
        } catch (ExecutionException exception) {
            log.warn("risk_scorer_failed showId={} seatId={}", showId, seatId, exception.getCause());
            return finish(RiskAssessment.failOpen(), snapshot);
        }
    }

    private Outcome finish(RiskAssessment assessment, RiskSignals snapshot) {
        metrics.risk(assessment.metricTag());
        log.info(
                "risk_decision decision={} reason={} flagged={}",
                assessment.decision(),
                assessment.reason(),
                assessment.flagged());
        return new Outcome(assessment, snapshot);
    }

    public record Outcome(RiskAssessment assessment, RiskSignals signals) {
    }
}
