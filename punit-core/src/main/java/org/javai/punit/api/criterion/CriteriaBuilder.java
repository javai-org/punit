package org.javai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Authoring surface for a contract's criteria list. Authors never
 * construct one directly — the framework supplies a fresh builder to
 * the author's
 * {@link org.javai.punit.api.Contract#criteria(CriteriaBuilder)}
 * method and collects the result.
 *
 * <p>For now the builder is a simple list collector. A contract that
 * exercises the multi-criterion model adds one criterion per
 * {@link #add(Criterion)} call; the order of {@code add(...)} calls is
 * preserved in the resulting list. The methodology does not yet
 * require any per-criterion configuration beyond the criterion itself
 * (mode, procedure direction, denominator policy, and so on are
 * absent from this step); when those land, the builder grows to
 * carry them.
 *
 * @param <O> the value type the contract evaluates against (the
 *            contract's output type)
 */
public final class CriteriaBuilder<O> {

    private final List<Criterion<O>> criteria = new ArrayList<>();

    /**
     * Add a criterion. Returns this builder for fluent chaining.
     *
     * @param criterion the criterion to add. Must be non-null.
     * @return this builder
     * @throws NullPointerException if {@code criterion} is null
     */
    public CriteriaBuilder<O> add(Criterion<O> criterion) {
        criteria.add(Objects.requireNonNull(criterion, "criterion"));
        return this;
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
}
