package org.javai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.Postcondition;
import org.javai.punit.api.PostconditionResult;

/**
 * A criterion whose postconditions evaluate directly against the
 * contract's output. No transform — the per-sample outcome is
 * restricted to {@link CriterionSampleOutcome#PASS PASS} or
 * {@link CriterionSampleOutcome#FAIL FAIL}; INCONCLUSIVE cannot
 * arise.
 *
 * <p>Package-private; constructed by
 * {@link Criteria#direct(String, java.util.function.Consumer)} and
 * by the K=1 default {@link DefaultCriterion}. Authors do not
 * reference this type directly.
 */
final class DirectCriterion<O> implements Criterion<O> {

    private final String id;
    private final List<Postcondition<O>> postconditions;
    private final CriterionPosture posture;

    DirectCriterion(String id, List<Postcondition<O>> postconditions) {
        this(id, postconditions, CriterionPosture.implicit());
    }

    DirectCriterion(String id, List<Postcondition<O>> postconditions, CriterionPosture posture) {
        this.id = Objects.requireNonNull(id, "id");
        this.postconditions = List.copyOf(
                Objects.requireNonNull(postconditions, "postconditions"));
        this.posture = Objects.requireNonNull(posture, "posture");
    }

    DirectCriterion<O> withPosture(CriterionPosture replacement) {
        return new DirectCriterion<>(id, postconditions, replacement);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CriterionPosture posture() {
        return posture;
    }

    @Override
    public CriterionSampleResult evaluate(O value) {
        return evaluateChain(id, postconditions, value);
    }

    /**
     * Evaluate a postcondition chain over a value. Used by both
     * {@link DirectCriterion} and {@link TransformingCriterion} once
     * the transform has produced a derived value.
     *
     * <p>The walk does not short-circuit on first failure: every
     * postcondition is evaluated and its result preserved on the
     * record, so a downstream consumer can see the full diagnostic
     * picture for the sample.
     */
    static <T> CriterionSampleResult evaluateChain(
            String id, List<Postcondition<T>> chain, T value) {
        List<PostconditionResult> results = new ArrayList<>();
        boolean anyFailed = false;
        for (Postcondition<T> p : chain) {
            List<PostconditionResult> pResults = p.evaluateAll(value);
            results.addAll(pResults);
            for (PostconditionResult r : pResults) {
                if (r.failed()) {
                    anyFailed = true;
                }
            }
        }
        return anyFailed
                ? CriterionSampleResult.fail(id, results)
                : CriterionSampleResult.pass(id, results);
    }
}
