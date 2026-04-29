package org.javai.punit.api.typed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

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
 * <p>The {@code ensure} factory mirrors Eiffel's design-by-contract
 * vocabulary: each call declares one postcondition that the use case
 * promises will hold for every successful invocation. The check is a
 * predicate — the description label carries the failure semantics.
 *
 * <pre>{@code
 * import static org.javai.punit.api.typed.Postcondition.ensure;
 * import static org.javai.punit.api.typed.Postcondition.deriving;
 *
 * ensure("Response has actions", t -> !t.actions().isEmpty())
 * ensure("All actions known",     ShoppingBasketUseCase::allKnown)
 * }</pre>
 *
 * <p>Authors who need a richer failure reason — naming <em>which</em>
 * input element tripped the check, embedding diagnostic data in the
 * failure — construct a {@link Leaf} directly with a
 * {@link PostconditionCheck} that returns
 * {@code Outcome.fail("name", reason)}.
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
     * Declare a postcondition from a predicate. On failure the
     * description label is used as the reason — suitable when no
     * further explanation is meaningful at the failure site. For
     * richer failure reasons, construct {@link Leaf} directly with a
     * {@link PostconditionCheck}.
     */
    static <T> Postcondition<T> ensure(String description, Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new Leaf<>(description, value -> predicate.test(value)
                ? Outcome.ok()
                : Outcome.fail(description, "postcondition not satisfied"));
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
                case Outcome.Fail<?> f -> PostconditionResult.failed(description, f.failure().message());
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
                case Outcome.Fail<D> f -> PostconditionResult.failed(description, f.failure().message());
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
                    out.add(PostconditionResult.failed(description, f.failure().message()));
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
