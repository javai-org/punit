package org.javai.punit.api;

import java.util.List;
import java.util.Objects;

import org.javai.outcome.Outcome;

/**
 * A named predicate over a single sample's produced value. A
 * postcondition decides pass or fail for one observable property;
 * it performs no transformation and carries no statistical
 * configuration. The {@link Criterion}-level transform (when
 * present) does any pre-postcondition shaping; the postcondition's
 * job is strictly the predicate.
 *
 * <h2>Authoring</h2>
 *
 * <p>The {@code ensure} factory is a nod to Eiffel's
 * design-by-contract vocabulary, not a strict replication of it. Each
 * call declares one postcondition that delivers an unopinionated
 * evaluation of an aspect of the value under test. The check
 * function returns {@link Outcome.Ok} when the aspect holds, or
 * {@link Outcome.Fail} carrying the specific reason — what was
 * observed, which element tripped the check, the diagnostic detail a
 * downstream report or optimize-feedback histogram needs.
 *
 * <p>The verdict is not formed at the postcondition site. Each
 * {@code ensure} produces a {@link PostconditionResult}; the
 * framework collects all of them per sample, and the opinion (pass /
 * fail) is taken later when the test or experiment asks for it. A
 * postcondition's {@code Outcome.Fail} only manifests as a
 * JUnit-style assertion failure when the test's
 * {@code assertPasses()} (or equivalent) is invoked.
 *
 * <pre>{@code
 * import static org.javai.punit.api.Postcondition.ensure;
 *
 * ensure("Response has actions", t -> t.actions().isEmpty()
 *         ? Outcome.fail("empty", "actions list was empty")
 *         : Outcome.ok())
 *
 * ensure("All actions known", t -> {
 *     var unknown = unknownActions(t);
 *     return unknown.isEmpty()
 *             ? Outcome.ok()
 *             : Outcome.fail("unknown-action", "found: " + unknown);
 * })
 * }</pre>
 *
 * <h2>Transforming the value before the predicate</h2>
 *
 * <p>To inspect a derived view of the produced value (parsed JSON,
 * normalised text, an extracted sub-record), declare a transforming
 * criterion via
 * {@link org.javai.punit.api.criterion.Criteria#transforming(
 * String, java.util.function.Function, java.util.function.Consumer)
 * Criteria.transforming(...)}. The transform is owned by the
 * criterion; the postcondition's job remains strictly that of a
 * predicate over whatever value reaches it.
 *
 * @param <T> the type the postcondition evaluates
 */
public sealed interface Postcondition<T> permits Postcondition.Leaf {

    /** Human-readable description; non-blank. */
    String description();

    /**
     * Evaluate this postcondition and return one summary result.
     */
    PostconditionResult evaluate(T value);

    /**
     * Evaluate this postcondition and return every contributing
     * result. For a {@link Leaf} this is a singleton list.
     */
    List<PostconditionResult> evaluateAll(T value);

    // ── Authoring entry point ───────────────────────────────────────

    /**
     * Declare a postcondition. The check function evaluates one
     * aspect of the value and returns an {@link Outcome.Ok} if the
     * aspect holds or an {@link Outcome.Fail} carrying the specific
     * reason if it does not. The framework collects every
     * postcondition's result per sample; whether a failure becomes
     * an assertion failure is decided later by the test, not here.
     */
    static <T> Postcondition<T> ensure(String description, PostconditionCheck<T> check) {
        return new Leaf<>(description, check);
    }

    // ── Variant ─────────────────────────────────────────────────────

    /** A direct postcondition: one description, one check. */
    record Leaf<T>(String description, PostconditionCheck<T> check)
            implements Postcondition<T> {

        public Leaf {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(check, "check");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }

        @Override
        public PostconditionResult evaluate(T value) {
            Outcome<Void> result;
            try {
                result = check.check(value);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                return PostconditionResult.failed(description, reason);
            }
            return switch (result) {
                case Outcome.Ok<?> ignored -> PostconditionResult.passed(description);
                case Outcome.Fail<?> f -> PostconditionResult.failed(description, f);
            };
        }

        @Override
        public List<PostconditionResult> evaluateAll(T value) {
            return List.of(evaluate(value));
        }
    }
}
