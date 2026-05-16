package org.javai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.javai.outcome.Outcome;
import org.javai.punit.api.PostconditionBuilder;

/**
 * Authoring surface for a contract's criteria list. Authors never
 * construct one directly — the framework supplies a fresh builder to
 * the author's
 * {@link org.javai.punit.api.Contract#criteria(CriteriaBuilder)}
 * method and collects the result.
 *
 * <p>Three ways to declare a criterion:
 *
 * <ul>
 *   <li>{@link #addCriterion(String, Consumer) addCriterion(id, body)}
 *       — convenience absorbing the
 *       {@link Criteria#direct(String, Consumer) Criteria.direct}
 *       factory call. Returns a {@link CriterionPostureHandle} on
 *       which {@code .meeting / .empirical / .zeroTolerance /
 *       .atConfidence} declares the criterion's commitment.</li>
 *   <li>{@link #addTransformingCriterion(String, Function, Consumer)
 *       addTransformingCriterion(id, transform, body)} — same shape
 *       for the transform-then-evaluate kind. Absorbs
 *       {@link Criteria#transforming(String, Function, Consumer)}.</li>
 *   <li>{@link #add(Criterion) add(criterion)} — primitive entry
 *       point for the rare hand-rolled {@link Criterion}. Returns
 *       the same posture handle, so the posture chain works
 *       uniformly.</li>
 * </ul>
 *
 * <p>The criterion is committed to the builder at the {@code add*}
 * call; the returned handle modifies the already-added criterion's
 * posture. An un-postured criterion is a statement with no terminal
 * call — there is no {@code .commit()} ceremony, and no way to
 * forget a terminal that would otherwise turn a criterion into a
 * no-op.
 *
 * @param <O> the value type the contract evaluates against (the
 *            contract's output type)
 */
public final class CriteriaBuilder<O> {

    private final List<Criterion<O>> criteria = new ArrayList<>();

    /**
     * Add a criterion whose postconditions evaluate directly against
     * the contract's output. Returns a posture handle for declaring
     * the criterion's commitment ({@code .meeting / .empirical /
     * .zeroTolerance / .atConfidence}); omit the terminal call to
     * keep the implicit zero-tolerance posture.
     */
    public CriterionPostureHandle<O> addCriterion(
            String id, Consumer<PostconditionBuilder<O>> body) {
        Criterion<O> criterion = Criteria.direct(id, body);
        return commit(criterion);
    }

    /**
     * Add a criterion that first transforms the contract's output to
     * a derived form, then evaluates its postconditions against the
     * derived value. Returns a posture handle for declaring the
     * criterion's commitment.
     */
    public <D> CriterionPostureHandle<O> addTransformingCriterion(
            String id,
            Function<O, Outcome<D>> transform,
            Consumer<PostconditionBuilder<D>> body) {
        Criterion<O> criterion = Criteria.transforming(id, transform, body);
        return commit(criterion);
    }

    /**
     * Add a hand-rolled {@link Criterion}. Returns a posture handle
     * for declaring the criterion's commitment.
     *
     * <p>The {@code Criteria} factory entry points cover the
     * methodology's two shapes (direct, transforming) and structurally
     * enforce the one-transform-per-criterion cap. Prefer them. This
     * primitive is retained for the rare custom case.
     *
     * <p>If the supplied criterion already carries a non-implicit
     * posture, it is replaced when a posture method is called on the
     * returned handle. To preserve a hand-rolled criterion's existing
     * posture, do not call any posture method on the handle.
     */
    public CriterionPostureHandle<O> add(Criterion<O> criterion) {
        return commit(Objects.requireNonNull(criterion, "criterion"));
    }

    private CriterionPostureHandle<O> commit(Criterion<O> criterion) {
        int index = criteria.size();
        criteria.add(criterion);
        return new CriterionPostureHandle<>(this, index);
    }

    CriterionPosture postureAt(int index) {
        return criteria.get(index).posture();
    }

    void setPostureAt(int index, CriterionPosture posture) {
        Criterion<O> current = criteria.get(index);
        criteria.set(index, withPosture(current, posture));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <O> Criterion<O> withPosture(Criterion<O> criterion, CriterionPosture posture) {
        if (criterion instanceof DirectCriterion<O> direct) {
            return direct.withPosture(posture);
        }
        if (criterion instanceof TransformingCriterion<?, ?> transforming) {
            return (Criterion<O>) ((TransformingCriterion) transforming).withPosture(posture);
        }
        // Hand-rolled criterion that doesn't expose withPosture(...).
        // Wrap with a delegating proxy that returns the new posture
        // from posture() and forwards everything else.
        return new PosturedCriterionWrapper<>(criterion, posture);
    }

    /**
     * Whether the builder is empty. Used by the framework's default
     * {@link org.javai.punit.api.Contract#criteria()} accessor to
     * decide between the explicit-criteria list and the K=1 default
     * derived from the contract's postconditions.
     */
    public boolean isEmpty() {
        return criteria.isEmpty();
    }

    /**
     * Returns the accumulated criteria as an immutable list. Called
     * by the framework after the author's
     * {@link org.javai.punit.api.Contract#criteria(CriteriaBuilder)}
     * method has populated the builder. Authors do not call this
     * directly.
     */
    public List<Criterion<O>> build() {
        return List.copyOf(criteria);
    }

    /** Wrapper for hand-rolled {@link Criterion} instances that don't expose a withPosture(...) hook. */
    private static final class PosturedCriterionWrapper<O> implements Criterion<O> {
        private final Criterion<O> delegate;
        private final CriterionPosture posture;

        PosturedCriterionWrapper(Criterion<O> delegate, CriterionPosture posture) {
            this.delegate = delegate;
            this.posture = posture;
        }

        @Override public String id() { return delegate.id(); }
        @Override public CriterionSampleResult evaluate(O value) { return delegate.evaluate(value); }
        @Override public CriterionPosture posture() { return posture; }
    }
}
