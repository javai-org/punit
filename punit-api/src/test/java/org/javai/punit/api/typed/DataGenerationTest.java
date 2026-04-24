package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.javai.punit.api.typed.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DataGeneration")
class DataGenerationTest {

    record Factors(String model, double temperature) {}

    static final class EchoUseCase implements UseCase<Factors, String, String> {
        EchoUseCase(Factors factors) {}
        @Override public UseCaseOutcome<String> apply(String input) { return UseCaseOutcome.ok(input); }
        @Override public String id() { return "echo"; }
    }

    private SamplingShape<Factors, String, String> shape() {
        return SamplingShape.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs("alpha", "beta", "gamma")
                .samples(333)
                .timeBudget(Duration.ofSeconds(60))
                .tokenBudget(20_000)
                .tokenCharge(2)
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .maxExampleFailures(7)
                .build();
    }

    @Test
    @DisplayName("pass-through accessors expose every shape field")
    void passThroughAccessors() {
        SamplingShape<Factors, String, String> s = shape();
        Factors f = new Factors("claude-3-sonnet", 0.7);

        DataGeneration<Factors, String, String> plan = s.at(f);

        assertThat(plan.shape()).isSameAs(s);
        assertThat(plan.factors()).isEqualTo(f);
        assertThat(plan.useCaseFactory()).isSameAs(s.useCaseFactory());
        assertThat(plan.inputs()).containsExactly("alpha", "beta", "gamma");
        assertThat(plan.samples()).isEqualTo(333);
        assertThat(plan.timeBudget()).contains(Duration.ofSeconds(60));
        assertThat(plan.tokenBudget().getAsLong()).isEqualTo(20_000L);
        assertThat(plan.tokenCharge()).isEqualTo(2L);
        assertThat(plan.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.PASS_INCOMPLETE);
        assertThat(plan.exceptionPolicy()).isEqualTo(ExceptionPolicy.FAIL_SAMPLE);
        assertThat(plan.maxExampleFailures()).isEqualTo(7);
    }

    @Test
    @DisplayName("binding two different factor bundles produces two DataGenerations sharing the shape")
    void twoBundlesOneShape() {
        SamplingShape<Factors, String, String> s = shape();
        Factors a = new Factors("gpt-4o", 0.0);
        Factors b = new Factors("gpt-4o", 0.5);

        DataGeneration<Factors, String, String> planA = s.at(a);
        DataGeneration<Factors, String, String> planB = s.at(b);

        assertThat(planA.shape()).isSameAs(planB.shape());
        assertThat(planA.factors()).isNotEqualTo(planB.factors());
    }

    @Test
    @DisplayName("DataGeneration has no public constructor or builder")
    void noPublicConstructorOrBuilder() {
        for (var ctor : DataGeneration.class.getConstructors()) {
            throw new AssertionError("unexpected public constructor: " + ctor);
        }
        for (var method : DataGeneration.class.getDeclaredMethods()) {
            if (method.getName().equals("builder")) {
                throw new AssertionError("unexpected builder(): " + method);
            }
        }
    }

    @Test
    @DisplayName(".at(null) throws NullPointerException")
    void atRejectsNull() {
        SamplingShape<Factors, String, String> s = shape();
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> s.at(null));
    }
}
