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

@DisplayName("Auto-injection of spec-criteria from contract posture")
class AutoInjectionFromPostureTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("auto-inject");

    private static <O> Sampling<Factors, Integer, O> sampling(
            ServiceContract<Factors, Integer, O> contract) {
        return Sampling.<Factors, Integer, O>builder()
                .serviceContractFactory(f -> contract)
                .inputs(1, 2, 3)
                .samples(20)
                .build();
    }

    @Test
    @DisplayName("contract with .meeting() posture and no test-builder criterion yields a PASS via auto-injected PassRate.meeting")
    void contractualPostureAutoInjects() {
        ServiceContract<Factors, Integer, Boolean> alwaysPasses = new ServiceContract<>() {
            @Override public String id() { return "always-passes"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.ok(true);
            }
            @Override public void criteria(CriteriaBuilder<Boolean> b) {
                b.addCriterion("contract", pb -> { })
                        .meeting(0.95, ThresholdOrigin.SLA);
            }
        };

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(alwaysPasses), FACTORS)
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionResults()).hasSize(1);
        assertThat(result.criterionResults().get(0).result().criterionName())
                .isEqualTo("bernoulli-pass-rate");
    }

    @Test
    @DisplayName("contract with .meeting() at high threshold and a failing service yields FAIL via auto-injected criterion")
    void contractualPostureAutoInjectsAndCanFail() {
        ServiceContract<Factors, Integer, Boolean> alwaysFails = new ServiceContract<>() {
            @Override public String id() { return "always-fails"; }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker t) {
                return Outcome.fail("nope", "never passes");
            }
            @Override public void criteria(CriteriaBuilder<Boolean> b) {
                b.addCriterion("contract", pb -> { })
                        .meeting(0.95, ThresholdOrigin.SLA);
            }
        };

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(alwaysFails), FACTORS)
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

}
