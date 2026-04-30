package org.javai.punit.api.typed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.javai.outcome.Outcome;

/**
 * One named acceptance criterion on a {@link UseCase}'s output. A use
 * case declares its postconditions via {@link UseCase#postconditions()};
 * the framework evaluates each one against every successful sample and
 * surfaces the per-postcondition results in the verdict, the report,
 * and the optimize / explore feedback path.
 *
 * <h2>Authoring</h2>
 *
 * <p>The {@code ensure} factory is a nod to Eiffel's
 * design-by-contract vocabulary, not a strict replication of it. Each
 * call declares one postcondition that delivers an unopinionated
 * evaluation of an aspect of the output. The check function returns
 * an {@link Outcome.Ok} when the aspect holds, or an
 * {@link Outcome.Fail} carrying the specific reason — what was
 * observed, which element tripped the check, the diagnostic detail a
 * downstream report or optimize-feedback histogram needs.
 *
 * <p>The verdict is not formed at the postcondition site. Each
 * {@code ensure} produces a {@link PostconditionResult}; the
 * framework collects all of them per sample, and the opinion (pass /
 * fail) is taken later when the test or experiment asks for it. A
 * postcondition's {@code Outcome.Fail} only manifests as a JUnit-style
 * assertion failure when the test's {@code assertPasses()} (or
 * equivalent) is invoked.
 *
 * <pre>{@code
 * import static org.javai.punit.api.typed.Postcondition.ensure;
 * import static org.javai.punit.api.typed.Postcondition.deriving;
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
 * <h2>Derivations</h2>
 *
 * <p>Postconditions that need to inspect a transformed view of the
 * output (parsed JSON, normalised text, an extracted sub-record) use
 * the {@link #deriving(String, Function, Postcondition[]) deriving}
 * factory. The derivation runs first; its outcome contributes one
 * postcondition result of its own; its nested {@code ensure}s only
 * evaluate when the derivation succeeds.
 *
 * <pre>{@code
 * deriving("Valid JSON", ChatResponse::parseJson,
 *     ensure("Has operations array", JsonNode::hasOperations),
 *     ensure("All operations valid", JsonNode::operationsValid))
 * }</pre>
 *
 * @param <T> the type the postcondition evaluates
 */
public sealed interface Postcondition<T>
        permits Postcondition.Leaf, Postcondition.Derived {

    /** Human-readable description; non-blank. */
    String description();

    /**
     * Evaluate this postcondition and return one summary result. For a
     * derivation, this collapses the derivation's outcome to a single
     * pass / fail; use {@link #evaluateAll(Object)} when the full
     * vector (the derivation plus every nested postcondition) is
     * wanted.
     */
    PostconditionResult evaluate(T value);

    /**
     * Evaluate this postcondition and return every contributing result.
     * For a {@link Leaf} this is a singleton list; for a
     * {@link Derived} it is the derivation's own result followed by
     * the nested postconditions' results.
     */
    List<PostconditionResult> evaluateAll(T value);

    // ── Authoring entry points ──────────────────────────────────────

    /**
     * Declare a postcondition. The check function evaluates one aspect
     * of the output and returns an {@link Outcome.Ok} if the aspect
     * holds or an {@link Outcome.Fail} carrying the specific reason
     * if it does not. The framework collects every postcondition's
     * result per sample; whether a failure becomes an assertion
     * failure is decided later by the test, not here.
     */
    static <T> Postcondition<T> ensure(String description, PostconditionCheck<T> check) {
        return new Leaf<>(description, check);
    }

    /**
     * Declare a derivation: transform the output via {@code derive},
     * then evaluate the {@code nested} postconditions against the
     * derived value. On derivation failure (the function returns
     * {@link Outcome.Fail} or throws), the nested postconditions are
     * skipped and reported as failed with a "skipped: …" reason.
     */
    @SafeVarargs
    static <T, D> Postcondition<T> deriving(
            String description,
            Function<T, Outcome<D>> derive,
            Postcondition<D>... nested) {
        Objects.requireNonNull(derive, "derive");
        Objects.requireNonNull(nested, "nested");
        return new Derived<>(description, derive, List.of(nested));
    }

    // ── Variants ────────────────────────────────────────────────────

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

    /**
     * A derivation: a transform from {@code T} to {@code D} that gates
     * a list of nested postconditions over the derived value.
     */
    record Derived<T, D>(
            String description,
            Function<T, Outcome<D>> derive,
            List<Postcondition<D>> nested) implements Postcondition<T> {

        public Derived {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(derive, "derive");
            Objects.requireNonNull(nested, "nested");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
            nested = List.copyOf(nested);
        }

        @Override
        public PostconditionResult evaluate(T value) {
            Outcome<D> derived;
            try {
                derived = derive.apply(value);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                return PostconditionResult.failed(description, reason);
            }
            return switch (derived) {
                case Outcome.Ok<D> ignored -> PostconditionResult.passed(description);
                case Outcome.Fail<D> f -> PostconditionResult.failed(description, f);
            };
        }

        @Override
        public List<PostconditionResult> evaluateAll(T value) {
            List<PostconditionResult> out = new ArrayList<>(1 + nested.size());
            Outcome<D> derived;
            try {
                derived = derive.apply(value);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                out.add(PostconditionResult.failed(description, reason));
                appendSkipped(out);
                return out;
            }
            switch (derived) {
                case Outcome.Ok<D> ok -> {
                    out.add(PostconditionResult.passed(description));
                    for (Postcondition<D> child : nested) {
                        out.addAll(child.evaluateAll(ok.value()));
                    }
                }
                case Outcome.Fail<D> f -> {
                    out.add(PostconditionResult.failed(description, f));
                    appendSkipped(out);
                }
            }
            return out;
        }

        private void appendSkipped(List<PostconditionResult> out) {
            String reason = "skipped: derivation '" + description + "' failed";
            for (Postcondition<D> child : nested) {
                appendSkippedRecursive(child, reason, out);
            }
        }

        private static <D> void appendSkippedRecursive(
                Postcondition<D> p, String reason, List<PostconditionResult> out) {
            switch (p) {
                case Leaf<D> leaf ->
                        out.add(PostconditionResult.failed(leaf.description(), reason));
                case Derived<D, ?> derived -> {
                    out.add(PostconditionResult.failed(derived.description(), reason));
                    for (Postcondition<?> grandchild : derived.nested()) {
                        @SuppressWarnings("unchecked")
                        Postcondition<Object> g = (Postcondition<Object>) grandchild;
                        appendSkippedRecursive(g, reason, out);
                    }
                }
            }
        }
    }
}
