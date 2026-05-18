package org.javai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionBuilder;
import org.javai.punit.api.PostconditionCheck;

/**
 * A value-form criterion decl whose postcondition chain is evaluated
 * against a transformed value, not against the contract's raw output.
 * Returned by {@link CriterionDecl#transforming(Function)}.
 *
 * <p>The criterion's posture lives on the parent {@link CriterionDecl}
 * (the type-witness {@code <O>} is the contract's output type).
 * The criterion's postconditions live here (the type-witness {@code <T>}
 * is the transformed value type the postconditions see).
 *
 * <p>Transform failure ({@link Outcome.Fail}) or a thrown exception
 * classifies the criterion's per-sample outcome as
 * {@link CriterionSampleOutcome#INCONCLUSIVE INCONCLUSIVE} —
 * <em>distinct</em> from FAIL. The postcondition chain is not
 * evaluated; the parse / projection failure flows through to the
 * per-sample record with its symbolic name and message preserved.
 *
 * @param <O> the contract's per-sample output value type
 * @param <T> the transformed value type the postconditions evaluate
 *            against
 */
public final class TransformingDecl<O, T> implements Decl<O> {

    private final CriterionPosture posture;
    private final Function<O, Outcome<T>> transform;
    private final List<NamedPostcondition<T>> postconditions;

    TransformingDecl(
            CriterionPosture posture,
            Function<O, Outcome<T>> transform,
            List<NamedPostcondition<T>> postconditions) {
        this.posture = Objects.requireNonNull(posture, "posture");
        this.transform = Objects.requireNonNull(transform, "transform");
        this.postconditions = List.copyOf(postconditions);
    }

    /** The posture inherited from the parent {@link CriterionDecl}. */
    public CriterionPosture posture() {
        return posture;
    }

    /** Named post-transform postconditions in declaration order. May be empty. */
    public List<NamedPostcondition<T>> postconditions() {
        return postconditions;
    }

    /**
     * Add a named postcondition over the transformed value. The
     * predicate returns {@code true} for pass; the framework
     * synthesises the failure message when it returns {@code false}.
     *
     * <p>For richer failure messages use
     * {@link #satisfies(String, Function)}.
     */
    public TransformingDecl<O, T> where(String name, Predicate<T> predicate) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".where(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(predicate, "predicate");
        PostconditionCheck<T> wrapped = v -> predicate.test(v)
                ? Outcome.ok()
                : Outcome.fail(name,
                        "postcondition '" + name + "' returned false for value: " + v);
        return appendPostcondition(name, wrapped);
    }

    /**
     * Add a named postcondition over the transformed value that
     * returns its own {@link Outcome}. Use this when the failure
     * message benefits from diagnostic detail.
     */
    public TransformingDecl<O, T> satisfies(String name, Function<T, Outcome<?>> check) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".satisfies(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(check, "check");
        PostconditionCheck<T> adapted = v -> {
            Outcome<?> result = check.apply(v);
            return switch (result) {
                case Outcome.Ok<?> ok -> Outcome.ok();
                case Outcome.Fail<?> fail -> Outcome.fail(fail.failure());
            };
        };
        return appendPostcondition(name, adapted);
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of(toRuntime(Composite.DEFAULT_CRITERION_ID));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Criterion<O> toRuntime(String id) {
        Criterion<O> base = Criterion.transforming(id, transform, this::populatePostconditions);
        if (base instanceof TransformingCriterion<?, ?> tc) {
            return ((TransformingCriterion<O, T>) tc).withPosture(posture);
        }
        return base;
    }

    private void populatePostconditions(PostconditionBuilder<T> pb) {
        for (NamedPostcondition<T> p : postconditions) {
            pb.ensure(p.name(), p.check());
        }
    }

    private TransformingDecl<O, T> appendPostcondition(String name, PostconditionCheck<T> check) {
        List<NamedPostcondition<T>> next = new ArrayList<>(postconditions.size() + 1);
        next.addAll(postconditions);
        next.add(new NamedPostcondition<>(name, check));
        return new TransformingDecl<>(posture, transform, next);
    }
}
