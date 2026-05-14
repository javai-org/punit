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
 * Tests for the criterion vocabulary as reshaped in step 2 of the
 * multi-criterion rollout: {@link Criterion} now exposes only
 * {@code id()} and {@code evaluate(value)}. The K=1 isomorphism is
 * preserved — a contract written today, with no awareness of the
 * criterion vocabulary, produces the same per-sample postcondition
 * results through the criterion-mediated path as through a direct
 * read of {@link Contract#postconditions()}.
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

    // A contract with explicit criteria, declared via the Criteria
    // factory (the idiomatic authoring path).
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
            b.add(Criteria.direct("well-formed",
                    cb -> cb.ensure("non-empty", v ->
                            v.isEmpty()
                                    ? Outcome.fail("empty", "")
                                    : Outcome.ok())));
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
            b.add(Criteria.direct("other",
                    cb -> cb.ensure("starts-with-h", v ->
                            v.startsWith("h")
                                    ? Outcome.ok()
                                    : Outcome.fail("no-h", ""))));
        }
    }

    @Test
    void singleCriterionDefaultEvaluatesSameAsLegacyPostconditionsWalk() {
        // The K=1 default's per-sample evaluation must produce the
        // same postcondition results as a direct walk of the
        // contract's postconditions(). Step 2's behaviour-preserving
        // invariant.
        var contract = new SingleStreamContract();
        String value = "hello world";

        List<PostconditionResult> legacy = new java.util.ArrayList<>();
        for (Postcondition<String> p : contract.postconditions()) {
            legacy.addAll(p.evaluateAll(value));
        }

        Criterion<String> sole = contract.criteria().get(0);
        CriterionSampleResult result = sole.evaluate(value);

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.PASS);
        assertThat(result.transformFailure()).isEmpty();
        assertThat(result.postconditionResults()).hasSameSizeAs(legacy);
        for (int i = 0; i < legacy.size(); i++) {
            PostconditionResult a = legacy.get(i);
            PostconditionResult b = result.postconditionResults().get(i);
            assertThat(b.description()).isEqualTo(a.description());
            assertThat(b.passed()).isEqualTo(a.passed());
        }
    }

    @Test
    void singleCriterionDefaultClassifiesFailWhenAnyPostconditionFails() {
        var contract = new SingleStreamContract();
        // "world" passes "non-empty" but fails "starts with greeting".
        CriterionSampleResult result = contract.criteria().get(0).evaluate("world");

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
        assertThat(result.postconditionResults()).hasSize(2);
        assertThat(result.postconditionResults().get(0).passed()).isTrue();
        assertThat(result.postconditionResults().get(1).failed()).isTrue();
    }

    @Test
    void singleCriterionIdIsStableAcrossCalls() {
        var contract = new SingleStreamContract();
        String first = contract.criteria().get(0).id();
        String second = contract.criteria().get(0).id();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
        assertThat(first).matches("[a-z0-9-]+");
    }

    @Test
    void defaultCriterionIdDerivedFromContractClass() {
        var contract = new SingleStreamContract();
        String id = contract.criteria().get(0).id();
        // SingleStreamContract → single-stream-contract.
        assertThat(id).isEqualTo("single-stream-contract");
    }

    @Test
    void explicitCriteriaListIsReturnedAsIs() {
        var contract = new ExplicitCriteriaContract();
        List<Criterion<String>> criteria = contract.criteria();

        assertThat(criteria).hasSize(1);
        assertThat(criteria.get(0).id()).isEqualTo("well-formed");
        CriterionSampleResult result = criteria.get(0).evaluate("hello");
        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.PASS);
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
}
