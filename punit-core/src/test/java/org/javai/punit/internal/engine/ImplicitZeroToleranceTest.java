package org.javai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.criterion.CriteriaBuilder;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Implicit zero-tolerance — un-postured contract criteria classify zero-tolerance")
class ImplicitZeroToleranceTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("zt");

    private static <O> Sampling<Factors, Integer, O> sampling(
            ServiceContract<Factors, Integer, O> contract) {
        return Sampling.<Factors, Integer, O>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(20)
                .build();
    }

    @Test
    @DisplayName("contract declares a criterion with no posture and always passes → PASS")
    void implicitZeroTolerancePasses() {
        ServiceContract<Factors, Integer, Boolean> alwaysPasses = new ServiceContract<>() {
            @Override public String id() { return "always-passes"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.ok(true);
            }
            @Override public void criteria(CriteriaBuilder<Boolean> b) {
                b.addCriterion("contract", pb -> { }); // no posture method → implicit zero-tolerance
            }
        };

        var result = (ProbabilisticTestResult) new Engine().run(
                ProbabilisticTest.testing(sampling(alwaysPasses), FACTORS).build());

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults()).hasSize(1);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("zero-tolerance");
    }

    @Test
    @DisplayName("implicit zero-tolerance + a single criterion-level failure → FAIL")
    void implicitZeroToleranceFailsOnAnyFailure() {
        // The criterion checks the produced value; one sample returns
        // false, which fails the postcondition and lights up the
        // zero-tolerance FAIL.
        ServiceContract<Factors, Integer, Boolean> failsOnce = new ServiceContract<>() {
            int call = 0;
            @Override public String id() { return "fails-once"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                call++;
                return Outcome.ok(call != 5);
            }
            @Override public void criteria(CriteriaBuilder<Boolean> b) {
                b.addCriterion("contract",
                        pb -> pb.ensure("must-be-true",
                                v -> v ? Outcome.ok() : Outcome.fail("notTrue", "expected true")));
            }
        };

        var result = (ProbabilisticTestResult) new Engine().run(
                ProbabilisticTest.testing(sampling(failsOnce), FACTORS).build());

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("explicit .zeroTolerance(SLA) on the contract → PASS when all samples pass")
    void explicitZeroTolerancePasses() {
        ServiceContract<Factors, Integer, Boolean> alwaysPasses = new ServiceContract<>() {
            @Override public String id() { return "always-passes"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.ok(true);
            }
            @Override public void criteria(CriteriaBuilder<Boolean> b) {
                b.addCriterion("contract", pb -> { })
                        .zeroTolerance(ThresholdOrigin.SLA);
            }
        };

        var result = (ProbabilisticTestResult) new Engine().run(
                ProbabilisticTest.testing(sampling(alwaysPasses), FACTORS).build());

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("zero-tolerance")
                .contains("SLA");
    }
}
