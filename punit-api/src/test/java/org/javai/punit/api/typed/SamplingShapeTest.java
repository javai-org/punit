package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.javai.punit.api.typed.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SamplingShape")
class SamplingShapeTest {

    record Factors(String model, double temperature) {}

    static final class EchoUseCase implements UseCase<Factors, String, String> {
        private final Factors factors;

        EchoUseCase(Factors factors) {
            this.factors = factors;
        }

        @Override
        public UseCaseOutcome<String> apply(String input) {
            return UseCaseOutcome.ok(input);
        }

        @Override
        public String id() {
            return "echo";
        }
    }

    private SamplingShape.Builder<Factors, String, String> baseBuilder() {
        return SamplingShape.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs("alpha", "beta");
    }

    @Test
    @DisplayName("builder produces a shape with the declared fields")
    void buildsWithDeclaredFields() {
        SamplingShape<Factors, String, String> shape = baseBuilder()
                .samples(250)
                .timeBudget(Duration.ofSeconds(30))
                .tokenBudget(10_000)
                .tokenCharge(5)
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .maxExampleFailures(3)
                .build();

        assertThat(shape.inputs()).containsExactly("alpha", "beta");
        assertThat(shape.samples()).isEqualTo(250);
        assertThat(shape.timeBudget()).contains(Duration.ofSeconds(30));
        assertThat(shape.tokenBudget().getAsLong()).isEqualTo(10_000L);
        assertThat(shape.tokenCharge()).isEqualTo(5L);
        assertThat(shape.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.PASS_INCOMPLETE);
        assertThat(shape.exceptionPolicy()).isEqualTo(ExceptionPolicy.FAIL_SAMPLE);
        assertThat(shape.maxExampleFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("default samples is 1000")
    void defaultSamples() {
        SamplingShape<Factors, String, String> shape = baseBuilder().build();
        assertThat(shape.samples()).isEqualTo(1000);
    }

    @Test
    @DisplayName("builder has no .factors(...) method — factors bind at .factors(...) time")
    void builderHasNoFactorsMethod() {
        for (var method : SamplingShape.Builder.class.getMethods()) {
            assertThat(method.getName())
                    .as("SamplingShape.Builder method %s", method)
                    .isNotEqualTo("factors");
        }
    }

    @Test
    @DisplayName(".factors(factors) produces a DataGeneration carrying the shape and bundle")
    void atProducesDataGeneration() {
        SamplingShape<Factors, String, String> shape = baseBuilder().samples(10).build();
        Factors f = new Factors("gpt-4o", 0.3);

        DataGeneration<Factors, String, String> plan = shape.factors(f);

        assertThat(plan.shape()).isSameAs(shape);
        assertThat(plan.factors()).isEqualTo(f);
        assertThat(plan.inputs()).isEqualTo(shape.inputs());
        assertThat(plan.samples()).isEqualTo(10);
    }

    @Test
    @DisplayName(".factors(null) is rejected")
    void atNullFactors() {
        SamplingShape<Factors, String, String> shape = baseBuilder().build();
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> shape.factors(null));
    }

    @Test
    @DisplayName("build() without useCaseFactory is rejected")
    void buildWithoutFactory() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> SamplingShape.<Factors, String, String>builder()
                        .inputs("x")
                        .build())
                .withMessageContaining("useCaseFactory");
    }

    @Test
    @DisplayName("build() without inputs is rejected")
    void buildWithoutInputs() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> SamplingShape.<Factors, String, String>builder()
                        .useCaseFactory(EchoUseCase::new)
                        .build())
                .withMessageContaining("inputs");
    }

    @Test
    @DisplayName("empty inputs list is rejected")
    void emptyInputsList() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SamplingShape.<Factors, String, String>builder()
                        .useCaseFactory(EchoUseCase::new)
                        .inputs(java.util.List.of()))
                .withMessageContaining("inputs");
    }

    @Test
    @DisplayName("samples < 1 is rejected")
    void samplesBelowOne() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().samples(0));
    }

    @Test
    @DisplayName("timeBudget ≤ 0 is rejected")
    void negativeTimeBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().timeBudget(Duration.ZERO));
    }

    @Test
    @DisplayName("tokenBudget ≤ 0 is rejected")
    void negativeTokenBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().tokenBudget(0));
    }

    @Test
    @DisplayName("tokenCharge < 0 is rejected")
    void negativeTokenCharge() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().tokenCharge(-1));
    }

    @Test
    @DisplayName("maxExampleFailures < 0 is rejected")
    void negativeMaxFailures() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().maxExampleFailures(-1));
    }

    @Test
    @DisplayName("samples(int) wither returns a new shape with the updated count")
    void samplesWither() {
        SamplingShape<Factors, String, String> shape = baseBuilder().samples(100).build();

        SamplingShape<Factors, String, String> reshaped = shape.samples(500);

        assertThat(reshaped.samples()).isEqualTo(500);
        assertThat(shape.samples()).isEqualTo(100);
        assertThat(reshaped.inputs()).isEqualTo(shape.inputs());
        assertThat(reshaped.useCaseFactory()).isSameAs(shape.useCaseFactory());
    }

    @Test
    @DisplayName("samples(int) wither rejects non-positive counts")
    void samplesWitherRejectsNonPositive() {
        SamplingShape<Factors, String, String> shape = baseBuilder().build();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> shape.samples(0));
    }
}
