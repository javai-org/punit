package org.javai.punit.api.criterion;

/**
 * A single-criterion declaration — direct or transforming — that can
 * be lowered to one runtime {@link Criterion} given an id.
 *
 * <p>The sealed supertype shared by {@link CriterionDecl} (direct,
 * postconditions over the contract's output type {@code O}) and
 * {@link TransformingDecl} (postconditions over a derived type
 * {@code T} after a transform). It is the parameter type accepted by
 * {@link Composite#compose(String, Decl) compose(...)} and
 * {@link Composite#entry(String, Decl) entry(...)} so a composite may
 * mix direct and transforming criteria.
 *
 * <p>Authors rarely reference this type by name — call-site idiom is
 * {@code compose("a", meeting(0.99, SLA), "b", empirical().transforming(parse).where(...))}
 * and inference picks {@code Decl<O>} as the unified type.
 *
 * @param <O> the contract's per-sample output value type
 */
public sealed interface Decl<O> extends Criteria<O>
        permits CriterionDecl, TransformingDecl {

    /**
     * Lower this declaration to a runtime {@link Criterion} with the
     * given id. Framework-internal; authors do not call this directly.
     */
    Criterion<O> toRuntime(String id);
}
