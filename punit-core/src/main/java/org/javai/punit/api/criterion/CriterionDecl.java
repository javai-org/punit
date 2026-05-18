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
 * import static org.javai.punit.api.criterion.Acceptance.*;
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
public final class CriterionDecl<O> implements Decl<O> {

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
     * <p>For richer failure messages — author-supplied symbolic name
     * and message — use {@link #satisfies(String, Function)}.
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
     * diagnostic detail (offending input, parse error, etc.) —
     * {@code Outcome.fail("symbolic-name", "message")} flows through to
     * the verdict's failure histogram unchanged.
     */
    public CriterionDecl<O> satisfies(String name, Function<O, Outcome<?>> check) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".satisfies(name, ...) requires a non-blank name");
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

    /**
     * Chain a transform — parse, project, derive — that produces a
     * value of a different type {@code T} the criterion's
     * postconditions will check. Returns a {@link TransformingDecl}
     * whose {@code .where(...)} and {@code .satisfies(...)} operate
     * on {@code T}, not on the contract's output {@code O}.
     *
     * <p>Transform semantics:
     * <ul>
     *   <li>Transform returns {@link Outcome.Ok Ok(t)} — the
     *       postcondition chain runs against {@code t}; criterion
     *       sample is PASS / FAIL based on the chain.</li>
     *   <li>Transform returns {@link Outcome.Fail Fail(...)} or throws
     *       — criterion sample is
     *       {@link CriterionSampleOutcome#INCONCLUSIVE INCONCLUSIVE};
     *       the postcondition chain is skipped. The failure's
     *       symbolic name and message flow through to the per-sample
     *       record for diagnostics.</li>
     * </ul>
     *
     * <p>Acceptance stays attached to the outer (this) decl; postconditions
     * already attached here are <em>not</em> carried forward to the
     * returned {@code TransformingDecl} — postconditions must be
     * stated either pre-transform (on this decl, via
     * {@code .where} / {@code .satisfies}) or post-transform
     * (on the returned decl), not both. Mixing pre- and
     * post-transform postconditions is a misuse and is rejected.
     *
     * @param <T> the derived value type the postcondition chain
     *            evaluates against on successful transform
     */
    public <T> TransformingDecl<O, T> transforming(Function<O, Outcome<T>> transform) {
        Objects.requireNonNull(transform, "transform");
        if (!postconditions.isEmpty()) {
            throw new IllegalStateException(
                    ".transforming(...) cannot follow .where/.satisfies on the same"
                            + " criterion decl: postconditions are evaluated either"
                            + " against the contract's output or against the"
                            + " transformed value, not both. Either drop the"
                            + " pre-transform postconditions, or split into two"
                            + " criteria via Composite.compose(...).");
        }
        return new TransformingDecl<>(posture, transform, List.of());
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of(toRuntime(Composite.DEFAULT_CRITERION_ID));
    }

    @Override
    public Criterion<O> toRuntime(String id) {
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
