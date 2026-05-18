package org.javai.punit.api.criterion;

import java.util.List;

/**
 * The contract's verdict-producing strategy. Given a sample summary,
 * a {@code Criteria<O>} resolves to a contract verdict via the
 * §1.4.6 composite-verdict procedure of the Statistical Companion
 * (FAIL-dominant aggregation over per-criterion verdicts).
 *
 * <p>Two concrete shapes:
 *
 * <ul>
 *   <li>{@link CriterionDecl} — a single-criterion strategy. The
 *       K=1 case: posture, optional postconditions, optional
 *       refinements (contractRef, confidence floor, MDE/power).
 *       Returned directly from {@code Posture.meeting(...)} and
 *       friends; an author with one criterion returns the decl
 *       directly from {@code Contract.criteria()}.</li>
 *   <li>{@link CompositeCriteria} — a multi-criterion strategy
 *       constructed via {@link Composite#compose(String, CriterionDecl)
 *       compose(...)}. Holds an ordered list of named decls and
 *       resolves the contract verdict per §1.4.6.</li>
 * </ul>
 *
 * <p>A single {@code CriterionDecl} <em>is</em> a {@code Criteria}
 * directly — the contract's K=1 strategy is the criterion itself.
 * The framework treats it as a one-entry composite at lowering time,
 * with the default criterion id {@code "contract"}.
 *
 * <p>{@link #asList()} is the framework's lowering hook: it returns
 * the runtime {@link Criterion} list the engine, baseline-resolution
 * path, and verdict path consume. Authors do not call it.
 *
 * @param <O> the contract's per-sample output value type
 */
public sealed interface Criteria<O>
        permits Decl, CompositeCriteria, EmptyCriteria {

    /**
     * Lower this declaration to the runtime {@link Criterion} list
     * the framework consumes. Framework-internal; authors do not
     * call this directly.
     */
    List<Criterion<O>> asList();

    /**
     * Whether this is the empty-criteria sentinel — used by the
     * framework's effective-criteria resolution to distinguish
     * "author has not declared via the value-form" from "author has
     * declared an explicit (possibly empty) criteria list."
     *
     * <p>Default implementation: never empty. Only
     * {@link Criteria#empty()} is empty.
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * The empty criteria value — used by the default
     * {@link org.javai.punit.api.Contract#criteria()} implementation
     * when no criteria are declared via the value-form. The
     * framework's lowering treats this as "no explicit criteria;
     * fall back to the postcondition-derived K=1 default with
     * implicit zero-tolerance" — same as the empty
     * {@link CriteriaBuilder} case today.
     */
    static <O> Criteria<O> empty() {
        return EmptyCriteria.instance();
    }
}
