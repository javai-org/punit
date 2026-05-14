package org.javai.punit.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoring surface for a postcondition chain. Authors never
 * construct one directly — the framework supplies a fresh builder to
 * the author's {@code postconditions(ContractBuilder<O>)} method (or
 * to the body of a
 * {@link org.javai.punit.api.criterion.Criteria#direct(String,
 * java.util.function.Consumer) Criteria.direct} /
 * {@link org.javai.punit.api.criterion.Criteria#transforming(String,
 * java.util.function.Function, java.util.function.Consumer)
 * Criteria.transforming} call) and collects the result.
 *
 * <p>One fluent method: {@link #ensure ensure} adds a leaf clause.
 * Transformation of the value before a postcondition is the
 * responsibility of the surrounding criterion, not of the builder.
 *
 * <pre>{@code
 * @Override
 * public void postconditions(ContractBuilder<BasketTranslation> b) {
 *     b.ensure("Has actions", t ->
 *             t.actions().isEmpty()
 *                     ? Outcome.fail("empty-actions", "actions list was empty")
 *                     : Outcome.ok())
 *      .ensure("All actions known", ShoppingBasketServiceContract::allKnown);
 * }
 * }</pre>
 *
 * @param <O> the value type the postcondition chain evaluates
 *            against
 */
public final class ContractBuilder<O> {

    private final List<Postcondition<O>> clauses = new ArrayList<>();

    /**
     * Add a leaf clause. The {@code check} returns
     * {@link org.javai.outcome.Outcome.Ok} when the aspect holds, or
     * {@link org.javai.outcome.Outcome.Fail} carrying a stable name
     * and a free-text reason when it does not. Both name and reason
     * are preserved on the resulting {@link PostconditionResult}.
     *
     * @param description human-readable description of what is checked
     * @param check       the predicate, returning an outcome
     * @return this builder
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code description} is blank
     */
    public ContractBuilder<O> ensure(String description, PostconditionCheck<O> check) {
        clauses.add(new Postcondition.Leaf<>(description, check));
        return this;
    }

    /**
     * Returns the accumulated clauses as an immutable list. Called
     * by the framework after the author's populating method has run.
     * Authors do not call this directly; the method is part of the
     * framework's public surface only so that types in sibling
     * packages (notably {@code org.javai.punit.api.criterion}) can
     * resolve a builder they populated.
     */
    public List<Postcondition<O>> build() {
        return List.copyOf(clauses);
    }
}
