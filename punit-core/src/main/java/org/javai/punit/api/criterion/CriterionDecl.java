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
 * A single-criterion verdict-producing strategy — the value behind
 * one row in a contract's criteria declaration.
 *
 * <p>Carries:
 * <ul>
 *   <li>a {@link CriterionPosture} (required) — what counts as
 *       acceptable: a threshold-first {@code .meeting(...)}, the
 *       empirical Wilson-vs-baseline procedure, or zero-tolerance;</li>
 *   <li>an ordered list of named postconditions (optional) — the
 *       per-sample checks the criterion runs against the contract's
 *       output. Empty means apply-level {@code Outcome.ok / fail}
 *       drives the criterion's per-sample outcome.</li>
 * </ul>
 *
 * <p>Authors do not call {@code new CriterionDecl(...)} directly.
 * The starting point is a posture factory:
 * <pre>{@code
 * import static org.javai.punit.api.criterion.Posture.*;
 *
 * meeting(0.9999, SLA)
 *         .contractRef("Payment Provider SLA v2.3, §4.1");
 *
 * meeting(0.85, SLA)
 *         .where("parseable", v -> isJson(v));
 *
 * empirical()
 *         .detectingMde(0.02)
 *         .atPower(0.95);
 * }</pre>
 *
 * <p>Every chain method returns a new {@code CriterionDecl} —
 * declarations are values, not builders.
 *
 * <p>A bare {@code CriterionDecl<O>} <em>is</em> a {@link Criteria}.
 * For the K=1 default-id case the author returns the decl directly
 * from {@code Contract.criteria()}; the framework lowers it to a
 * one-entry runtime criteria list with the criterion id
 * {@value Composite#DEFAULT_CRITERION_ID}.
 *
 * @param <O> the contract's per-sample output value type
 */
public final class CriterionDecl<O> implements Criteria<O> {

    private final CriterionPosture posture;
    private final List<NamedPostcondition<O>> postconditions;

    CriterionDecl(CriterionPosture posture, List<NamedPostcondition<O>> postconditions) {
        this.posture = Objects.requireNonNull(posture, "posture");
        this.postconditions = List.copyOf(postconditions);
    }

    /** The criterion's posture — the verdict-producing commitment. */
    public CriterionPosture posture() {
        return posture;
    }

    /** Named postconditions in declaration order. May be empty. */
    public List<NamedPostcondition<O>> postconditions() {
        return postconditions;
    }

    /**
     * Add a named postcondition. The predicate returns {@code true}
     * for pass; the framework synthesises the failure message when
     * it returns {@code false}.
     *
     * <p>Java's lambda inference picks this overload when the lambda
     * body is a boolean expression; for richer failure messages use
     * {@link #where(String, Function)}.
     */
    public CriterionDecl<O> where(String name, Predicate<O> predicate) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".where(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(predicate, "predicate");
        PostconditionCheck<O> wrapped = v -> predicate.test(v)
                ? Outcome.ok()
                : Outcome.fail(name,
                        "postcondition '" + name + "' returned false for value: " + v);
        return appendPostcondition(name, wrapped);
    }

    /**
     * Add a named postcondition that returns its own {@link Outcome}.
     * Use this overload when the failure message benefits from
     * diagnostic detail (offending input, parse error, etc.).
     */
    public CriterionDecl<O> where(String name, Function<O, Outcome<?>> check) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".where(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(check, "check");
        PostconditionCheck<O> adapted = v -> {
            Outcome<?> result = check.apply(v);
            return switch (result) {
                case Outcome.Ok<?> ok -> Outcome.ok();
                case Outcome.Fail<?> fail -> Outcome.fail(fail.failure());
            };
        };
        return appendPostcondition(name, adapted);
    }

    /**
     * Attach a human-readable contract reference — the document and
     * clause that justify this criterion's commitment
     * (e.g. {@code "Payment Provider SLA v2.3, §4.1"}). Surfaced in
     * the verdict path so compliance reports cite the authority
     * alongside the verdict.
     */
    public CriterionDecl<O> contractRef(String ref) {
        return new CriterionDecl<>(posture.withContractRef(ref), postconditions);
    }

    /**
     * Set the per-criterion confidence floor — the run cannot
     * loosen it. Composes only with {@code empirical()}; rejected
     * by the posture machinery on a {@code .meeting(...)} or
     * zero-tolerance commitment.
     */
    public CriterionDecl<O> atConfidence(double confidence) {
        return new CriterionDecl<>(posture.withConfidenceFloor(confidence), postconditions);
    }

    /**
     * Declare the minimum detectable effect — the smallest regression
     * (in percentage points off the baseline rate) this criterion
     * commits to detecting. Composes only with {@code empirical()};
     * must be paired with {@link #atPower(double)}.
     */
    public CriterionDecl<O> detectingMde(double mde) {
        return new CriterionDecl<>(posture.withMde(mde), postconditions);
    }

    /**
     * Declare the statistical power — probability of detecting a true
     * regression of size MDE. Composes only with {@code empirical()};
     * must be paired with {@link #detectingMde(double)}.
     */
    public CriterionDecl<O> atPower(double power) {
        return new CriterionDecl<>(posture.withPower(power), postconditions);
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of(toRuntime(Composite.DEFAULT_CRITERION_ID));
    }

    /**
     * Lower this decl to a runtime {@link Criterion} with the given
     * id. Called by {@link CompositeCriteria} when assembling the
     * multi-criterion list and by {@link #asList()} for the K=1
     * default-id case.
     */
    Criterion<O> toRuntime(String id) {
        Criterion<O> base = Criterion.direct(id, this::populatePostconditions);
        if (base instanceof DirectCriterion<O> direct) {
            return direct.withPosture(posture);
        }
        // Defensive: Criterion.direct always returns DirectCriterion
        // today, but the wrapper path keeps the framework correct if
        // that ever changes.
        return base;
    }

    private void populatePostconditions(PostconditionBuilder<O> pb) {
        for (NamedPostcondition<O> p : postconditions) {
            pb.ensure(p.name(), p.check());
        }
    }

    private CriterionDecl<O> appendPostcondition(String name, PostconditionCheck<O> check) {
        List<NamedPostcondition<O>> next = new ArrayList<>(postconditions.size() + 1);
        next.addAll(postconditions);
        next.add(new NamedPostcondition<>(name, check));
        return new CriterionDecl<>(posture, next);
    }
}
