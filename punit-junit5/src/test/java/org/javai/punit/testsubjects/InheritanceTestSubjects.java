package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.legacy.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.legacy.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.outcome.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test subjects for verifying the Sentinel inheritance model.
 *
 * <p>These classes simulate the pattern where a {@code @Sentinel} reliability
 * specification superclass declares test methods and input sources, and a JUnit
 * test subclass provides its own {@link UseCaseProvider} with
 * {@code @RegisterExtension} and {@code @BeforeEach} setup.
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
     * Declares UseCaseFactory, @ProbabilisticTest method — pure PUnit, no JUnit.
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
     * JUnit subclass — adds UseCaseProvider with @RegisterExtension.
     */
    public static class InheritedProbabilisticTest extends ProbabilisticTestSpec {
        @RegisterExtension
        UseCaseProvider provider = new UseCaseProvider();

        @BeforeEach
        void setUp() {
            provider.register(SimpleUseCase.class, SimpleUseCase::new);
        }
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
     * JUnit subclass inheriting @ProbabilisticTest + @InputSource.
     */
    public static class InheritedProbabilisticTestWithInputs
            extends ProbabilisticTestWithInputsSpec {
        @RegisterExtension
        UseCaseProvider provider = new UseCaseProvider();

        @BeforeEach
        void setUp() {
            provider.register(SimpleUseCase.class, SimpleUseCase::new);
        }
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
     * JUnit subclass inheriting @MeasureExperiment.
     */
    public static class InheritedMeasureExperiment extends MeasureExperimentSpec {
        @RegisterExtension
        UseCaseProvider provider = new UseCaseProvider();

        @BeforeEach
        void setUp() {
            provider.register(SimpleUseCase.class, SimpleUseCase::new);
        }
    }
}
