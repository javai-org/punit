package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.outcome.Outcome;

/**
 * Test subjects for verifying the DD-06 inheritance model.
 *
 * <p>These classes simulate the Sentinel authoring pattern where a reliability
 * specification superclass declares the {@link UseCaseFactory}, test methods,
 * and input sources — and a JUnit test subclass inherits everything.
 *
 * <p>These are NOT meant to be run directly. They are executed via TestKit
 * from {@code InheritanceIntegrationTest}.
 */
public class InheritanceTestSubjects {

    private InheritanceTestSubjects() {}

    // ═══════════════════════════════════════════════════════════════════
    // USE CASE
    // ═══════════════════════════════════════════════════════════════════

    @UseCase("InheritanceTestUseCase")
    public static class SimpleUseCase {
        public UseCaseOutcome<String> execute(String input) {
            return createOutcome("processed: " + input);
        }

        public UseCaseOutcome<String> execute() {
            return createOutcome("ok");
        }
    }

    private static final ServiceContract<Void, String> CONTRACT = ServiceContract
            .<Void, String>define()
            .ensure("non-null", s -> s != null ? Outcome.ok() : Outcome.fail("check", "was null"))
            .build();

    private static UseCaseOutcome<String> createOutcome(String value) {
        return new UseCaseOutcome<>(
                value,
                Duration.ofMillis(1),
                Instant.now(),
                Map.of(),
                CONTRACT,
                null,
                null,
                null,
                null
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROBABILISTIC TEST INHERITANCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Superclass simulating a @Sentinel reliability spec.
     * Declares UseCaseFactory, @ProbabilisticTest method, and @InputSource.
     */
    public static class ProbabilisticTestSpec {
        UseCaseFactory factory = new UseCaseFactory();
        {
            factory.register(SimpleUseCase.class, SimpleUseCase::new);
        }

        @ProbabilisticTest(useCase = SimpleUseCase.class, samples = 5,
                minPassRate = 0.95, intent = TestIntent.SMOKE)
        void testExecution(SimpleUseCase useCase) {
            assertThat(useCase.execute().result()).isEqualTo("ok");
        }
    }

    /**
     * Subclass with no declarations — mirrors the one-line JUnit test class pattern.
     */
    public static class InheritedProbabilisticTest extends ProbabilisticTestSpec {
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROBABILISTIC TEST WITH INPUT SOURCE INHERITANCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Superclass with @ProbabilisticTest + @InputSource, both inherited.
     */
    public static class ProbabilisticTestWithInputsSpec {
        UseCaseFactory factory = new UseCaseFactory();
        {
            factory.register(SimpleUseCase.class, SimpleUseCase::new);
        }

        @ProbabilisticTest(useCase = SimpleUseCase.class, samples = 6,
                minPassRate = 0.95, intent = TestIntent.SMOKE)
        @InputSource("testInputs")
        void testWithInputs(SimpleUseCase useCase, String input) {
            assertThat(useCase.execute(input).result()).startsWith("processed:");
        }

        static Stream<String> testInputs() {
            return Stream.of("alpha", "beta", "gamma");
        }
    }

    /**
     * Subclass inheriting @ProbabilisticTest + @InputSource.
     */
    public static class InheritedProbabilisticTestWithInputs
            extends ProbabilisticTestWithInputsSpec {
    }

    // ═══════════════════════════════════════════════════════════════════
    // MEASURE EXPERIMENT INHERITANCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Superclass with @MeasureExperiment.
     */
    public static class MeasureExperimentSpec {
        UseCaseFactory factory = new UseCaseFactory();
        {
            factory.register(SimpleUseCase.class, SimpleUseCase::new);
        }

        @MeasureExperiment(useCase = SimpleUseCase.class, samples = 3, expiresInDays = 0)
        void measureBaseline(OutcomeCaptor captor) {
            captor.record(createOutcome("measured"));
        }
    }

    /**
     * Subclass inheriting @MeasureExperiment.
     */
    public static class InheritedMeasureExperiment extends MeasureExperimentSpec {
    }
}
