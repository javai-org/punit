package org.javai.punit.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.javai.outcome.Outcome;

/**
 * Authoring surface for a contract's postcondition clauses. Authors
 * never construct one directly — the framework supplies a fresh builder
 * to the author's {@code postconditions(ContractBuilder<O>)} method and
 * collects the result.
 *
 * <p>Two methods, both fluent: {@link #ensure ensure} adds a leaf
 * clause, {@link #deriving deriving} adds a derivation whose nested
 * clauses are populated through a sub-builder lambda.
 *
 * <pre>{@code
 * @Override
 * public void postconditions(ContractBuilder<BasketTranslation> b) {
 *     b.ensure("Has actions", t ->
 *             t.actions().isEmpty()
 *                     ? Outcome.fail("empty-actions", "actions list was empty")
 *                     : Outcome.ok())
 *      .ensure("All actions known", ShoppingBasketUseCase::allKnown)
 *      .deriving("Resolves to catalog SKUs",
 *             t -> resolveAgainstCatalog(t.actions()),
 *             sub -> sub
 *                     .ensure("Every action mapped to a SKU", ...)
 *                     .ensure("No duplicate SKUs", ...));
 * }
 * }</pre>
 *
 * @param <O> the value type the contract evaluates against (the use
 *            case's output type)
 */
public final class ContractBuilder<O> {

    private final List<Postcondition<O>> clauses = new ArrayList<>();

    /**
     * Add a leaf clause. The {@code check} returns
     * {@link Outcome.Ok} when the aspect holds, or
     * {@link Outcome.Fail} carrying a stable name and a free-text reason
     * when it does not. Both name and reason are preserved on the
     * resulting {@link PostconditionResult}.
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
     * Add a derivation: transform the value via {@code derive}, then
     * evaluate the clauses populated on the supplied sub-builder
     * against the derived value. If the derivation fails (returns
     * {@link Outcome.Fail} or throws), the nested clauses are skipped
     * and reported as failed with a "skipped: …" reason.
     *
     * @param description description of the derivation step
     * @param derive      the transform, returning an outcome over the
     *                    derived type
     * @param nested      consumer that populates the sub-builder
     * @param <D>         the derived value type
     * @return this builder
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code description} is blank
     */
    public <D> ContractBuilder<O> deriving(
            String description,
            Function<O, Outcome<D>> derive,
            Consumer<ContractBuilder<D>> nested) {
        Objects.requireNonNull(nested, "nested");
        ContractBuilder<D> sub = new ContractBuilder<>();
        nested.accept(sub);
        clauses.add(new Postcondition.Derived<>(description, derive, sub.clauses));
        return this;
    }

    /**
     * Returns the accumulated clauses as an immutable list. Called by
     * the framework after the author's {@code postconditions} method
     * has populated the builder. Authors do not call this directly.
     */
    List<Postcondition<O>> build() {
        return List.copyOf(clauses);
    }
}
