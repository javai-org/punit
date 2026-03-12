package org.javai.punit.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that logs verdict events via the PUnit logger.
 *
 * <p>This is the default sink, used when no explicit sink configuration is provided.
 * It produces a concise one-line log entry per verdict, suitable for structured
 * log aggregation.
 */
public final class LogVerdictSink implements VerdictSink {

    private static final Logger logger = LogManager.getLogger(LogVerdictSink.class);

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        String testName = verdict.identity().className() + "." + verdict.identity().methodName();
        String useCaseId = verdict.identity().useCaseId().orElse(testName);
        logger.info("[{}] {} — {} — {}",
                verdict.correlationId(),
                testName,
                useCaseId,
                verdict.punitVerdict());
    }
}
