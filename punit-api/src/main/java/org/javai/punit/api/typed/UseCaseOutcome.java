package org.javai.punit.api.typed;

import java.util.Objects;

import org.javai.outcome.Outcome;

/**
 * Punit's wrapper around the value returned by a single invocation of
 * {@link UseCase#apply(Object) UseCase.apply}.
 *
 * <p>The wrapped {@code value} is an {@link Outcome Outcome&lt;OT&gt;}
 * so that the use case can distinguish a successfully produced value
 * ({@link Outcome.Ok}) from an expected business-level failure
 * ({@link Outcome.Fail}) without abusing exceptions. The engine
 * counts {@code Ok} samples as successes and {@code Fail} samples as
 * failures, preserving the full {@link org.javai.outcome.Failure}
 * details for diagnostics.
 *
 * <p>This record is an extension point. Stage 3 will add per-sample
 * token cost for RC02 budget enforcement, and potentially a correlation
 * id sink. The {@code Outcome} itself already carries an optional
 * correlation id (see {@link Outcome#correlationId()}).
 *
 * @param value the wrapped outcome, never {@code null}
 * @param <OT> the success value type
 */
public record UseCaseOutcome<OT>(Outcome<OT> value) {

    public UseCaseOutcome {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps an already-constructed {@link Outcome}.
     *
     * @param value the outcome, non-null
     */
    public static <OT> UseCaseOutcome<OT> of(Outcome<OT> value) {
        return new UseCaseOutcome<>(value);
    }

    /**
     * Convenience for the common case — build a success outcome.
     *
     * @param value the successful value, non-null
     */
    public static <OT> UseCaseOutcome<OT> ok(OT value) {
        return new UseCaseOutcome<>(Outcome.ok(value));
    }

    /**
     * Convenience for the common case — build a business-failure
     * outcome from a failure code name and message.
     *
     * <p>For defects (NPE, misconfiguration, OOM) throw instead —
     * defects surface as exceptions, not as {@code Fail} outcomes.
     *
     * @param name short failure-code name (e.g. {@code "validation_failed"})
     * @param message human-readable description
     */
    public static <OT> UseCaseOutcome<OT> fail(String name, String message) {
        return new UseCaseOutcome<>(Outcome.fail(name, message));
    }
}
