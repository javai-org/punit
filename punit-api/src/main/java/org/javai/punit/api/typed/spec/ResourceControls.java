package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.javai.punit.api.typed.LatencySpec;

/**
 * Bundled resource-control / latency values shared by every
 * Stage-3 spec builder.
 *
 * <p>All four spec flavours (Measure, Explore, Optimize,
 * ProbabilisticTest) expose the same eight Stage-3 knobs. Rather than
 * duplicate the builder-side field declarations and validation, each
 * spec keeps a single {@code ResourceControls} instance and delegates
 * the {@link Spec} interface's default accessors to its fields.
 *
 * <p>This record is internal plumbing; authors see only the fluent
 * builder methods. Exposed as package-public so concrete specs can
 * store and consult it without a separate getter per field.
 *
 * @param timeBudget wall-clock budget; {@link Optional#empty()} = no limit
 * @param tokenBudget token budget; {@link OptionalLong#empty()} = no limit
 * @param tokenCharge static per-sample charge; default 0
 * @param budgetPolicy what to do on budget exhaustion; default FAIL
 * @param exceptionPolicy how to treat a thrown exception from
 *                       {@code UseCase.apply}; default ABORT_TEST
 * @param maxExampleFailures cap on retained failure detail; default 10
 * @param latency optional latency-threshold declaration; default
 *                {@link LatencySpec#disabled()}
 */
public record ResourceControls(
        Optional<Duration> timeBudget,
        OptionalLong tokenBudget,
        long tokenCharge,
        BudgetExhaustionPolicy budgetPolicy,
        ExceptionPolicy exceptionPolicy,
        int maxExampleFailures,
        LatencySpec latency) {

    private static final ResourceControls DEFAULTS = new ResourceControls(
            Optional.empty(),
            OptionalLong.empty(),
            0L,
            BudgetExhaustionPolicy.FAIL,
            ExceptionPolicy.ABORT_TEST,
            10,
            LatencySpec.disabled());

    public ResourceControls {
        Objects.requireNonNull(timeBudget, "timeBudget");
        Objects.requireNonNull(tokenBudget, "tokenBudget");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy");
        Objects.requireNonNull(exceptionPolicy, "exceptionPolicy");
        Objects.requireNonNull(latency, "latency");
        if (timeBudget.isPresent() && (timeBudget.get().isZero() || timeBudget.get().isNegative())) {
            throw new IllegalArgumentException(
                    "timeBudget must be > 0, got " + timeBudget.get());
        }
        if (tokenBudget.isPresent() && tokenBudget.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    "tokenBudget must be > 0, got " + tokenBudget.getAsLong());
        }
        if (tokenCharge < 0) {
            throw new IllegalArgumentException(
                    "tokenCharge must be non-negative, got " + tokenCharge);
        }
        if (maxExampleFailures < 0) {
            throw new IllegalArgumentException(
                    "maxExampleFailures must be non-negative, got " + maxExampleFailures);
        }
    }

    /** The default-valued bundle — empty budgets, FAIL, ABORT_TEST, 10, disabled latency. */
    public static ResourceControls defaults() {
        return DEFAULTS;
    }
}
