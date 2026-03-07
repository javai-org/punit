package org.javai.punit.sentinel;

import java.time.Duration;
import java.util.List;
import org.javai.punit.reporting.VerdictEvent;

/**
 * The aggregate outcome of a Sentinel execution — either a test run or an experiment run.
 *
 * <p>The caller (scheduler, HTTP handler, operator script) uses this result to
 * determine next steps (alerting, circuit-breaking, etc.). The Sentinel itself
 * takes no action beyond reporting.
 *
 * @param totalTests total number of tests or experiments executed
 * @param passed number that passed
 * @param failed number that failed
 * @param skipped number skipped (e.g., missing spec)
 * @param verdicts individual verdict details
 * @param totalDuration wall-clock time for the entire run
 */
public record SentinelResult(
        int totalTests,
        int passed,
        int failed,
        int skipped,
        List<VerdictEvent> verdicts,
        Duration totalDuration
) {

    /**
     * Returns {@code true} if all tests passed and none were skipped.
     */
    public boolean allPassed() {
        return failed == 0 && skipped == 0;
    }
}
