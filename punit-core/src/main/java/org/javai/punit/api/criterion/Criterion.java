package org.javai.punit.api.criterion;

import java.util.List;

import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Postcondition;

/**
 * A named, contract-level partition of the functional dimension. A
 * criterion groups a set of postconditions whose pass / fail outcomes
 * form one statistical stream; a {@link org.javai.punit.api.Contract}
 * may carry one criterion (the common case today) or several, each
 * tested independently.
 *
 * <p>This interface introduces the methodological vocabulary of a
 * criterion without yet adding the rest of the per-criterion
 * configuration the statistical companion eventually requires (mode,
 * procedure direction, denominator policy, confidence level, threshold
 * derivation rule, validation set). Those are the subject of later
 * evolution. For now a criterion is the **identity** and the
 * **postcondition chain**; everything else is inherited from the
 * surrounding contract.
 *
 * <h2>Authoring</h2>
 *
 * <p>The {@link #postconditions(ContractBuilder)} method mirrors the
 * shape today's {@link org.javai.punit.api.Contract#postconditions(ContractBuilder)}
 * carries: the framework supplies a fresh
 * {@link ContractBuilder}, the author populates it with
 * {@code ensure(...)} / {@code deriving(...)} calls, and the framework
 * collects the result. The default body is empty — a criterion with
 * no postconditions admits every observation.
 *
 * <h2>Single-criterion contracts</h2>
 *
 * <p>A contract that does not explicitly declare criteria yields a
 * single criterion derived from its existing
 * {@link org.javai.punit.api.Contract#postconditions(ContractBuilder)}.
 * The K=1 case is the same statistical object as today's single-stream
 * contract; the methodology recovers it unchanged.
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
     * Declare this criterion's postcondition chain by calling
     * {@link ContractBuilder#ensure ensure} and
     * {@link ContractBuilder#deriving deriving} on the supplied
     * builder.
     *
     * <p>The default body is empty. A criterion with no postconditions
     * admits every observation.
     *
     * @param b the builder to populate
     */
    default void postconditions(ContractBuilder<O> b) {
        // Default: no postconditions. Subclasses override to declare
        // their chain.
    }

    /**
     * Resolves this criterion's clauses to an immutable list. The
     * framework hook; do not override. The default implementation
     * builds a fresh {@link ContractBuilder}, calls
     * {@link #postconditions(ContractBuilder)} to populate it, and
     * returns the built list.
     */
    default List<Postcondition<O>> postconditions() {
        ContractBuilder<O> b = new ContractBuilder<>();
        postconditions(b);
        return b.build();
    }
}
