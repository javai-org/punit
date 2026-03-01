package org.javai.punit.experiment.engine.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.Latency;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for @InputSource annotation with experiment strategies.
 *
 * <p>Uses JUnit TestKit to verify that @InputSource correctly injects input values
 * and distributes samples across inputs.
 */
@DisplayName("InputSource Integration")
class InputSourceIntegrationTest {

    @Nested
    @DisplayName("MeasureExperiment with @InputSource")
    class MeasureExperimentTests {

        @Test
        @DisplayName("injects input values from method source")
        void injectsInputValuesFromMethodSource() {
            MethodSourceTestSubject.capturedInputs.clear();
            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(MethodSourceTestSubject.class))
                    .execute();

            results.testEvents().assertStatistics(stats ->
                    stats.started(6).succeeded(6).failed(0));

            // Verify inputs were captured
            assertThat(MethodSourceTestSubject.capturedInputs)
                    .containsExactly("add milk", "remove bread", "clear cart",
                                     "add milk", "remove bread", "clear cart");
        }

        @Test
        @DisplayName("injects input values from JSON file source")
        void injectsInputValuesFromJsonFileSource() {
            JsonFileSourceTestSubject.capturedInstructions.clear();
            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(JsonFileSourceTestSubject.class))
                    .execute();

            results.testEvents().assertStatistics(stats ->
                    stats.started(6).succeeded(6).failed(0));

            // Verify inputs were captured - 3 inputs × 2 cycles = 6 samples
            assertThat(JsonFileSourceTestSubject.capturedInstructions).hasSize(6);
            assertThat(JsonFileSourceTestSubject.capturedInstructions)
                    .containsOnly("add milk", "remove bread", "clear cart");
        }

        @Test
        @DisplayName("injects record inputs from method source")
        void injectsRecordInputsFromMethodSource() {
            RecordInputTestSubject.capturedInputs.clear();
            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(RecordInputTestSubject.class))
                    .execute();

            results.testEvents().assertStatistics(stats ->
                    stats.started(4).succeeded(4).failed(0));

            // Verify record inputs were captured
            assertThat(RecordInputTestSubject.capturedInputs).hasSize(4);
            assertThat(RecordInputTestSubject.capturedInputs.stream()
                    .map(TestInput::instruction)
                    .distinct()
                    .toList())
                    .containsExactlyInAnyOrder("add milk", "remove bread");
        }
    }

    @Nested
    @DisplayName("ExploreExperiment with @InputSource")
    class ExploreExperimentTests {

        @Test
        @DisplayName("runs samples per input configuration")
        void runsSamplesPerInputConfiguration() {
            ExploreTestSubject.capturedInputs.clear();
            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(ExploreTestSubject.class))
                    .execute();

            // 3 inputs × 2 samples per config = 6 total samples
            results.testEvents().assertStatistics(stats ->
                    stats.started(6).succeeded(6).failed(0));

            // Verify inputs were captured
            assertThat(ExploreTestSubject.capturedInputs).hasSize(6);
            // Each input should be run 2 times (samplesPerConfig)
            long addMilkCount = ExploreTestSubject.capturedInputs.stream()
                    .filter("add milk"::equals).count();
            long removeBreadCount = ExploreTestSubject.capturedInputs.stream()
                    .filter("remove bread"::equals).count();
            long clearCartCount = ExploreTestSubject.capturedInputs.stream()
                    .filter("clear cart"::equals).count();
            assertThat(addMilkCount).isEqualTo(2);
            assertThat(removeBreadCount).isEqualTo(2);
            assertThat(clearCartCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("ProbabilisticTest with @InputSource")
    class ProbabilisticTestTests {

        @Test
        @DisplayName("cycles through inputs during samples")
        void cyclesThroughInputs() {
            ProbabilisticTestSubject.capturedInputs.clear();
            EngineExecutionResults results = EngineTestKit
                    .engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(ProbabilisticTestSubject.class))
                    .execute();

            // Test may terminate early when success is guaranteed, so don't check exact counts
            // Just verify it completed without failures
            results.testEvents().assertStatistics(stats -> stats.failed(0));

            // Verify inputs were captured (at least 3 to see the cycle start)
            assertThat(ProbabilisticTestSubject.capturedInputs).hasSizeGreaterThanOrEqualTo(3);
            // First three should be the cycling inputs
            assertThat(ProbabilisticTestSubject.capturedInputs.subList(0, 3))
                    .containsExactly("add milk", "remove bread", "clear cart");
        }
    }

    // ========== Test Subjects ==========

    private static final ServiceContract<String, String> CONTRACT = ServiceContract
            .<String, String>define()
            .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "was empty") : Outcome.ok())
            .build();

    @UseCase("test-use-case")
    static class TestUseCase {
        public UseCaseOutcome<String> process(String input) {
            return UseCaseOutcome
                    .withContract(CONTRACT)
                    .input(input)
                    .execute(String::toUpperCase)
                    .build();
        }
    }

    public record TestInput(String instruction, String expected) {}

    /**
     * Test subject for method source with String inputs.
     */
    public static class MethodSourceTestSubject {
        static List<String> capturedInputs = new ArrayList<>();

        static Stream<String> testInputs() {
            return Stream.of("add milk", "remove bread", "clear cart");
        }

        @MeasureExperiment(useCase = TestUseCase.class, samples = 6)
        @InputSource("testInputs")
        void measureWithInputs(OutcomeCaptor captor, String input) {
            capturedInputs.add(input);
            TestUseCase useCase = new TestUseCase();
            captor.record(useCase.process(input));
        }
    }

    /**
     * Test subject for JSON file source with record inputs.
     */
    public static class JsonFileSourceTestSubject {
        static List<String> capturedInstructions = new ArrayList<>();

        @MeasureExperiment(useCase = TestUseCase.class, samples = 6)
        @InputSource(file = "input/test-inputs.json")
        void measureWithJsonInputs(OutcomeCaptor captor, TestInput input) {
            capturedInstructions.add(input.instruction());
            TestUseCase useCase = new TestUseCase();
            captor.record(useCase.process(input.instruction()));
        }
    }

    /**
     * Test subject for record input from method source.
     */
    public static class RecordInputTestSubject {
        static List<TestInput> capturedInputs = new ArrayList<>();

        static Stream<TestInput> goldenInputs() {
            return Stream.of(
                    new TestInput("add milk", "{\"action\":\"add\"}"),
                    new TestInput("remove bread", "{\"action\":\"remove\"}")
            );
        }

        @MeasureExperiment(useCase = TestUseCase.class, samples = 4)
        @InputSource("goldenInputs")
        void measureWithRecordInputs(OutcomeCaptor captor, TestInput input) {
            capturedInputs.add(input);
            TestUseCase useCase = new TestUseCase();
            captor.record(useCase.process(input.instruction()));
        }
    }

    /**
     * Test subject for ExploreExperiment with @InputSource.
     */
    public static class ExploreTestSubject {
        static List<String> capturedInputs = new ArrayList<>();

        static Stream<String> testInputs() {
            return Stream.of("add milk", "remove bread", "clear cart");
        }

        @ExploreExperiment(useCase = TestUseCase.class, samplesPerConfig = 2)
        @InputSource("testInputs")
        void exploreWithInputs(OutcomeCaptor captor, String input) {
            capturedInputs.add(input);
            TestUseCase useCase = new TestUseCase();
            captor.record(useCase.process(input));
        }
    }

    /**
     * Test subject for ProbabilisticTest with @InputSource.
     */
    public static class ProbabilisticTestSubject {
        static List<String> capturedInputs = new ArrayList<>();

        static Stream<String> testInputs() {
            return Stream.of("add milk", "remove bread", "clear cart");
        }

        @ProbabilisticTest(useCase = TestUseCase.class, samples = 6, minPassRate = 0.5,
                latency = @Latency(disabled = true))
        @InputSource("testInputs")
        void testWithInputs(String input) {
            capturedInputs.add(input);
            // Simple assertion that always passes
            if (input == null) {
                throw new AssertionError("Input should not be null");
            }
        }
    }
}
