package org.javai.punit.api.spec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.Contract;
import org.javai.punit.api.MatchResult;
import org.javai.punit.api.PostconditionResult;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseOutcome;
import org.javai.outcome.Outcome;

/**
 * The engine's per-sample classification — what was observed about
 * one sample, in the form the aggregator and downstream reporters
 * read.
 *
 * <p>Built from a {@link UseCaseOutcome} plus the
 * {@link UseCase#maxLatency() use case's max-latency bound}. The
 * classifier (a static helper) reads everything it needs from those
 * two sources; it does not consult the {@link
 * Contract Contract} for evaluation
 * because postcondition results are already pre-evaluated and
 * carried on the outcome.
 *
 * <p>Two shapes:
 *
 * <ul>
 *   <li>**Apply-level failure**: when the use case's
 *       {@link Contract#invoke invoke}
 *       returned {@link Outcome.Fail}, postcondition evaluation is
 *       skipped — there is no value to evaluate. The classification
 *       carries the failure name and message, plus the duration and
 *       tokens.
 *   <li>**Apply-level success**: postcondition results are present
 *       (possibly empty when the contract has no clauses), the
 *       optional match result reflects an instance-conformance
 *       check if matching was configured, and the optional duration
 *       violation reflects the per-sample max-latency bound.
 * </ul>
 *
 * @param applyFailureName    when the apply call itself failed,
 *                            the {@link org.javai.outcome.Failure}
 *                            id name; empty otherwise
 * @param applyFailureMessage when the apply call itself failed,
 *                            the failure message; empty otherwise
 * @param postconditionResults pre-evaluated per-clause results;
 *                            empty when {@link #applyFailed()}
 * @param durationViolation   present when the sample exceeded the
 *                            use case's {@link
 *                            UseCase#maxLatency() declared max}
 * @param match               the instance-conformance match result,
 *                            if matching was configured for this run
 * @param duration            the wall-clock time the sample took
 * @param tokens              the cost the sample consumed
 */
public record SampleClassification(
        Optional<String> applyFailureName,
        Optional<String> applyFailureMessage,
        List<PostconditionResult> postconditionResults,
        Optional<DurationViolation> durationViolation,
        Optional<MatchResult> match,
        Duration duration,
        long tokens) {

    public SampleClassification {
        Objects.requireNonNull(applyFailureName, "applyFailureName");
        Objects.requireNonNull(applyFailureMessage, "applyFailureMessage");
        Objects.requireNonNull(postconditionResults, "postconditionResults");
        Objects.requireNonNull(durationViolation, "durationViolation");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(duration, "duration");
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be non-negative, got " + tokens);
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must be non-negative, got " + duration);
        }
        postconditionResults = List.copyOf(postconditionResults);
    }

    /** Whether the sample's apply call returned an Outcome.Fail (no value produced). */
    public boolean applyFailed() {
        return applyFailureName.isPresent();
    }

    /**
     * Build a classification for an apply-level failure — postcondition
     * results, match, and duration-violation are all empty.
     */
    public static SampleClassification failedAtApply(
            String name, String message, Duration duration, long tokens) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(message, "message");
        return new SampleClassification(
                Optional.of(name),
                Optional.of(message),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                duration,
                tokens);
    }

    /**
     * Build a classification for a successful apply call — the
     * postcondition results, optional match, optional duration
     * violation, duration, and tokens together describe what was
     * observed.
     */
    public static SampleClassification from(
            List<PostconditionResult> postconditionResults,
            Optional<DurationViolation> durationViolation,
            Optional<MatchResult> match,
            Duration duration,
            long tokens) {
        return new SampleClassification(
                Optional.empty(),
                Optional.empty(),
                postconditionResults,
                durationViolation,
                match,
                duration,
                tokens);
    }

    /**
     * Classify one sample. Reads the outcome's pre-evaluated
     * postcondition results and match; reads the use case's
     * max-latency bound to detect a duration violation.
     */
    public static <I, O> SampleClassification classify(
            UseCase<?, I, O> useCase, UseCaseOutcome<I, O> outcome) {
        if (outcome.result() instanceof Outcome.Fail<O> f) {
            return failedAtApply(
                    f.failure().id().name(),
                    f.failure().message(),
                    outcome.duration(),
                    outcome.tokens());
        }
        Optional<DurationViolation> violation = useCase.maxLatency()
                .filter(max -> outcome.duration().compareTo(max) > 0)
                .map(max -> new DurationViolation(outcome.duration(), max));
        return from(
                outcome.postconditionResults(),
                violation,
                outcome.match(),
                outcome.duration(),
                outcome.tokens());
    }
}
