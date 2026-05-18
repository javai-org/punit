package org.javai.punit.api.criterion;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionBuilder;

/**
 * A named, contract-level partition of the functional dimension. A
 * criterion is a unit that judges a single sample's produced value
 * and yields one of {PASS, FAIL, INCONCLUSIVE}; a
 * {@link org.javai.punit.api.Contract} may carry one criterion (the
 * common case today) or several, each evaluated independently.
 *
 * <h2>What lives at this interface</h2>
 *
 * <p>The public surface is deliberately minimal: an identifier and a
 * per-sample evaluation method. Anything else a downstream consumer
 * (verdict path, report, sentinel) needs about the criterion's
 * behaviour on a sample rides on the returned
 * {@link CriterionSampleResult} — per-postcondition pass/fail
 * details, transform-failure detail when the outcome is
 * {@link CriterionSampleOutcome#INCONCLUSIVE}, and any other
 * diagnostic content the implementation chooses to carry.
 *
 * <h2>Authoring</h2>
 *
 * <p>The idiomatic authoring path is the {@link Criteria} factory:
 * {@link Criteria#direct(String, java.util.function.Consumer)} for a
 * criterion whose postconditions evaluate directly against the
 * contract's output, or
 * {@link Criteria#transforming(String, java.util.function.Function,
 * java.util.function.Consumer)} for a criterion that first transforms
 * the contract's output to a derived form, then evaluates
 * postconditions against the derived value. The factory produces
 * implementations that own the per-sample machinery — authors do not
 * reimplement evaluate-then-classify by hand.
 *
 * <p>Direct implementation of this interface is supported but is
 * not the expected path; the factory entry points cover the
 * methodology's two shapes (transform / no-transform) and enforce
 * the one-transform-per-criterion cap structurally.
 *
 * <h2>The single-criterion default</h2>
 *
 * <p>A contract that does not explicitly declare criteria yields a
 * single-criterion list (the K=1 default) whose postconditions are
 * the contract's existing
 * {@link org.javai.punit.api.Contract#postconditions()}. That
 * default criterion is a {@code direct} criterion under the hood; it
 * has no transform, and its per-sample outcome is restricted to PASS
 * or FAIL (INCONCLUSIVE cannot arise without a transform).
 *
 * @param <O> the contract's per-sample output value type
 */
public interface Criterion<O> {

    /**
     * A stable identifier for this criterion, used in reports, in the
     * verdict tuple, and wherever the criterion needs to be referenced
     * by name. Must be unique within the criteria of one contract and
     * must remain stable across runs of the same contract.
     *
     * <p>Conventionally a lowercase, hyphen-separated token. The
     * framework does not enforce a specific format.
     */
    String id();

    /**
     * Evaluate this criterion against one sample's produced value.
     *
     * <p>The returned {@link CriterionSampleResult} carries the
     * three-valued per-sample outcome, the per-postcondition results
     * for the chain that ran (empty on INCONCLUSIVE), and the
     * transform failure that caused INCONCLUSIVE (empty otherwise).
     *
     * <p>Whether this criterion carries an internal transform, and
     * over what derived type its postcondition chain evaluates, is
     * an implementation detail not visible at this interface. The
     * factory-produced implementations supply the per-sample
     * machinery: transform-fails-into-INCONCLUSIVE,
     * postcondition-fails-into-FAIL, all-passes-into-PASS.
     *
     * @param value the contract's produced output for this sample
     * @return the criterion's per-sample evaluation record
     */
    CriterionSampleResult evaluate(O value);

    /**
     * The criterion's run-time commitment — what counts as acceptable,
     * and optionally how confidently to evaluate it. Authored via the
     * {@link Acceptance} factories ({@code meeting(...)}, {@code empirical()},
     * {@code zeroTolerance(...)}); read by the framework's evaluation
     * path when it computes the per-criterion verdict from the run's
     * sample counts.
     *
     * <p>The default is {@link CriterionPosture#implicit()} — used by
     * criteria constructed without an explicit posture (hand-rolled
     * {@link Criterion} implementations and the auto-derived K=1
     * default).
     */
    default CriterionPosture posture() {
        return CriterionPosture.implicit();
    }

    /**
     * Construct a criterion whose postcondition chain evaluates
     * directly against the contract's output. No transform.
     *
     * <p>Framework-internal entry point: the value-returning
     * authoring surface (see {@code Acceptance.meeting/.empirical/.zeroTolerance}
     * and {@code Composite.compose}) is the path authors use.
     */
    static <O> Criterion<O> direct(
            String id, Consumer<PostconditionBuilder<O>> body) {
        validateId(id);
        Objects.requireNonNull(body, "body");
        PostconditionBuilder<O> b = new PostconditionBuilder<>();
        body.accept(b);
        return new DirectCriterion<>(id, b.build());
    }

    /**
     * Construct a criterion that first transforms the contract's
     * output {@code O} to a derived form {@code D}, then evaluates
     * its postcondition chain against the derived value.
     */
    static <O, D> Criterion<O> transforming(
            String id,
            Function<O, Outcome<D>> transform,
            Consumer<PostconditionBuilder<D>> body) {
        validateId(id);
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(body, "body");
        PostconditionBuilder<D> b = new PostconditionBuilder<>();
        body.accept(b);
        return new TransformingCriterion<>(id, transform, b.build());
    }

    private static void validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
