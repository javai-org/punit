package org.javai.punit.api.typed;

import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;

/**
 * The result of evaluating one postcondition against a use case's raw
 * result.
 *
 * <p>Mirrors {@code org.javai.punit.contract.PostconditionResult} —
 * the two will converge when Stage 8 retires the legacy contract
 * surface. Until then they coexist: the legacy one is consumed by the
 * annotation-driven engine, this one by the typed engine.
 *
 * @param description human-readable description of what was checked
 * @param outcome the check's outcome — {@link Outcome.Ok} if the
 *                postcondition held, {@link Outcome.Fail} otherwise
 *                (the failure carries the reason)
 */
public record PostconditionResult(String description, Outcome<?> outcome) {

    public PostconditionResult {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(outcome, "outcome");
    }

    public boolean passed() {
        return outcome.isOk();
    }

    public boolean failed() {
        return outcome.isFail();
    }

    /**
     * @return the failure reason, or empty if the postcondition passed
     */
    public Optional<String> failureReason() {
        return switch (outcome) {
            case Outcome.Fail<?> f -> Optional.of(f.failure().message());
            case Outcome.Ok<?> ignored -> Optional.empty();
        };
    }

    /**
     * @return {@code "{description}: {reason}"} when failed,
     *         just {@code description} when passed
     */
    public String failureMessage() {
        return failureReason()
                .map(reason -> description + ": " + reason)
                .orElse(description);
    }

    public static PostconditionResult passed(String description) {
        return new PostconditionResult(description, Outcome.ok());
    }

    public static PostconditionResult failed(String description, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new PostconditionResult(description, Outcome.fail(description, reason));
    }
}
