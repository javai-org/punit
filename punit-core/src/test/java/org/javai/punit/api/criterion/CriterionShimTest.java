package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.Postcondition;
import org.javai.punit.api.PostconditionResult;
import org.javai.punit.api.TokenTracker;
import org.junit.jupiter.api.Test;

/**
 * Tests for the vocabulary shim introduced by the first step of the
 * multi-criterion rollout. Asserts that:
 *
 * <ul>
 *   <li>A contract with no explicit criteria yields a single-criterion
 *       list derived from its postconditions, with the same chain in
 *       the same order.</li>
 *   <li>A contract that declares both a non-empty postconditions chain
 *       and a non-empty explicit criteria list throws at first
 *       criteria() access — the framework refuses the structural
 *       ambiguity.</li>
 *   <li>The engine's read-through criteria() produces the same
 *       per-sample postcondition results as the legacy direct read.</li>
 *   <li>The default-criterion identifier is stable across calls and
 *       readable as a report token.</li>
 * </ul>
 */
class CriterionShimTest {

    // A simple contract carrying two postconditions and no explicit
    // criteria declaration. Represents the common case today.
    static final class SingleStreamContract implements Contract<String, String> {
        @Override
        public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }

        @Override
        public void postconditions(ContractBuilder<String> b) {
            b.ensure("non-empty", v ->
                    v == null || v.isEmpty()
                            ? Outcome.fail("empty", "value was empty")
                            : Outcome.ok());
            b.ensure("starts with greeting", v ->
                    v.startsWith("hello")
                            ? Outcome.ok()
                            : Outcome.fail("no-greeting", "missing 'hello'"));
        }
    }

    // A contract with explicit criteria (one criterion in this case).
    static final class ExplicitCriteriaContract implements Contract<String, String> {
        @Override
        public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }

        @Override
        public void postconditions(ContractBuilder<String> b) {
            // Empty: the explicit criteria carry the postconditions.
        }

        @Override
        public void criteria(CriteriaBuilder<String> b) {
            b.add(new Criterion<String>() {
                @Override
                public String id() {
                    return "well-formed";
                }

                @Override
                public void postconditions(ContractBuilder<String> cb) {
                    cb.ensure("non-empty", v ->
                            v.isEmpty()
                                    ? Outcome.fail("empty", "")
                                    : Outcome.ok());
                }
            });
        }
    }

    // A contract that declares both — the structurally ambiguous case
    // the framework rejects.
    static final class BothOverriddenContract implements Contract<String, String> {
        @Override
        public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }

        @Override
        public void postconditions(ContractBuilder<String> b) {
            b.ensure("non-empty", v ->
                    v.isEmpty() ? Outcome.fail("empty", "") : Outcome.ok());
        }

        @Override
        public void criteria(CriteriaBuilder<String> b) {
            b.add(new Criterion<String>() {
                @Override
                public String id() {
                    return "other";
                }

                @Override
                public void postconditions(ContractBuilder<String> cb) {
                    cb.ensure("starts-with-h", v ->
                            v.startsWith("h")
                                    ? Outcome.ok()
                                    : Outcome.fail("no-h", ""));
                }
            });
        }
    }

    @Test
    void singleCriterionDefaultDerivesFromPostconditions() {
        var contract = new SingleStreamContract();
        List<Criterion<String>> criteria = contract.criteria();

        assertThat(criteria).hasSize(1);
        Criterion<String> sole = criteria.get(0);

        List<Postcondition<String>> fromCriterion = sole.postconditions();
        List<Postcondition<String>> fromContract = contract.postconditions();

        assertThat(fromCriterion).hasSameSizeAs(fromContract);
        // Same postconditions in the same order — the synthesised
        // criterion is a thin pass-through.
        assertThat(fromCriterion).containsExactlyElementsOf(fromContract);
    }

    @Test
    void singleCriterionIdIsStableAcrossCalls() {
        var contract = new SingleStreamContract();
        String first = contract.criteria().get(0).id();
        String second = contract.criteria().get(0).id();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
        // Should be a reasonable report-friendly token.
        assertThat(first).matches("[a-z0-9-]+");
    }

    @Test
    void explicitCriteriaListIsReturnedAsIs() {
        var contract = new ExplicitCriteriaContract();
        List<Criterion<String>> criteria = contract.criteria();

        assertThat(criteria).hasSize(1);
        assertThat(criteria.get(0).id()).isEqualTo("well-formed");
        assertThat(criteria.get(0).postconditions()).hasSize(1);
    }

    @Test
    void bothOverriddenThrowsAtCriteriaAccess() {
        var contract = new BothOverriddenContract();
        assertThatThrownBy(contract::criteria)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postconditions(ContractBuilder)")
                .hasMessageContaining("criteria(CriteriaBuilder)")
                .hasMessageContaining(BothOverriddenContract.class.getName());
    }

    @Test
    void engineReadThroughCriteriaMatchesLegacyDirectRead() {
        // The K=1 path's evaluation must produce the same per-sample
        // postcondition results as a direct evaluation of the legacy
        // postconditions() list. This is the behaviour-preserving
        // invariant of step 1.
        var contract = new SingleStreamContract();
        String value = "hello world";

        // Legacy path — direct read.
        var legacyResults = new java.util.ArrayList<PostconditionResult>();
        for (Postcondition<String> p : contract.postconditions()) {
            legacyResults.addAll(p.evaluateAll(value));
        }

        // New path — read through criteria().
        var newResults = new java.util.ArrayList<PostconditionResult>();
        for (Criterion<String> c : contract.criteria()) {
            for (Postcondition<String> p : c.postconditions()) {
                newResults.addAll(p.evaluateAll(value));
            }
        }

        assertThat(newResults).hasSameSizeAs(legacyResults);
        for (int i = 0; i < legacyResults.size(); i++) {
            PostconditionResult a = legacyResults.get(i);
            PostconditionResult b = newResults.get(i);
            assertThat(b.description()).isEqualTo(a.description());
            assertThat(b.passed()).isEqualTo(a.passed());
        }
    }

    @Test
    void defaultCriterionIdDerivedFromContractClass() {
        var contract = new SingleStreamContract();
        String id = contract.criteria().get(0).id();
        // SingleStreamContract → single-stream-contract.
        assertThat(id).isEqualTo("single-stream-contract");
    }
}
