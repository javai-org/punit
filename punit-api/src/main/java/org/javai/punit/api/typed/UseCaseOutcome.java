package org.javai.punit.api.typed;

import java.util.Objects;

/**
 * Wraps the value returned by a single invocation of
 * {@link UseCase#apply(Object) UseCase.apply}.
 *
 * <p>This is the Stage 1 shape — a plain value carrier. The
 * richer contract-evaluation outcome carried by
 * {@code org.javai.punit.contract.UseCaseOutcome} remains in place
 * under its existing package and is consumed by the legacy engine
 * paths; later refactor stages will reconcile the two.
 *
 * @param <OT> the value type produced by the use case
 * @param value the value, never {@code null}
 */
public record UseCaseOutcome<OT>(OT value) {

    public UseCaseOutcome {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Factory for fluent construction at a call site.
     *
     * @param value the value, never {@code null}
     * @param <OT> the value type
     * @return the outcome
     */
    public static <OT> UseCaseOutcome<OT> of(OT value) {
        return new UseCaseOutcome<>(value);
    }
}
