package org.javai.punit.examples;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Random;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.model.UseCaseOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Spec-driven probabilistic tests for the shopping assistant.
 * Demonstrates spec-driven probabilistic testing with statistically grounded thresholds.
 * <p>
 * If you run this test class just once, it's possible that every sample run passes, but this is
 * just luck. Run the test class multiple times to see the power of PUnit in action. You will
 * see the occasional fail, but take a look at the failure report in the log to see the conclusion based
 * on statistical significance: The test as a whole may fail, but the log may reveal that, from a statistical
 * perspective, the failure is not significant. In real-world scenarios, this is where PUnit's power shines:
 * it allows you to make data-informed decisions about whether to act on test failure.
 * <p>
 * Hence: When you get a failure from a probabilistic test, ALWAYS READ THE TEST LOG to understand
 * the statistical significance of the failure. If the failure is not statistically significant, it may
 * be a false positive, and you can safely ignore it. Whatever you do, do not spend a ton of budget investigating
 * it! On the other hand, if the failure is statistically significant, PUnit will give the verdict of FAILED.
 * Now you have data-backed justification to start an investigation.
 * <p>
 * Why does the test show up as FAILED in the IDE if PUnit gives the verdict of PASSED? Just because a FAIL is
 * statistically insignificant, it does not mean it should be ignored. It simply means you should think twice
 * before sending in a team of investigators. In other words: Take a quick look at the error messages; perhaps re-run
 * the test. Above all: stay cool.
 *
 * <p>These tests demonstrate the complete PUnit workflow:
 * <ol>
 *   <li>Experiments run with 1000 samples (MEASURE mode) to establish empirical specs</li>
 *   <li>Specs are reviewed and committed (via normal Git workflow)</li>
 *   <li>Tests reference use case class; spec is looked up automatically</li>
 * </ol>
 *
 * <h2>Architecture</h2>
 * <p>This class demonstrates the recommended pattern for PUnit tests:
 * <ul>
 *   <li>Use {@link UseCaseProvider} to configure use case construction</li>
 *   <li>Reference use case by class: {@code @ProbabilisticTest(useCase = ShoppingUseCase.class)}</li>
 *   <li>Receive use case via method parameter injection</li>
 *   <li>Spec is automatically looked up based on use case ID</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Run: ./gradlew measure --tests "ShoppingExperiment.measureRealisticSearchBaseline"
 * 2. Review: src/test/resources/punit/specs/ShoppingUseCase.yaml
 * 3. Commit: git add . && git commit -m "Add ShoppingUseCase spec"
 * 4. Test: ./gradlew test --tests "ShoppingAssistantSpecExamplesTest"
 * </pre>
 *
 * <h2>Spec Location</h2>
 * <p>The spec is automatically looked up at:
 * {@code punit/specs/ShoppingUseCase.yaml}
 *
 * @see org.javai.punit.examples.shopping.experiment.ShoppingExperiment
 * @see ShoppingUseCase
 * @see UseCaseProvider
 */
@Disabled("Example - run MEASURE experiment first to generate spec")
@DisplayName("Shopping Assistant Spec-Driven Tests")
class ShoppingAssistantSpecExamplesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // USE CASE PROVIDER CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The use case provider handles construction and injection of use cases.
     */
    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    /**
     * Configures the use case provider before each test sample.
     *
     * <p>Uses the same mock configuration as the experiment to ensure
     * consistency between experiment and test behavior.
     */
    @BeforeEach
    void setUp() {
        // Configure how ShoppingUseCase instances are created.
        // By registering the same use case as was used in the long-running MEASURE experiment,
        // we ensure that probability tests are statistically grounded in the data captured in the spec.
        // This is what PUnit is all about: Probability tests for non-deterministic use cases, where
        // we can make data-informed decisions about whether to act on test failure. Without PUnit,
        // we would be flying blind, taking guesses, chasing false flags, or simply labeling certain FAILS
        // as "flaky" - and then ignoring them.
        // For those interested in where the probabilistic test gets its data from: It comes from a spec file which
        // was generated by the MEASURE experiment. It is located in a subfolder of the resources directory, and
        // in this case it is named "ShoppingUseCase.yaml".
        provider.register(ShoppingUseCase.class, () ->
            new ShoppingUseCase(
                new MockShoppingAssistant(
                    new Random(),
                    MockShoppingAssistant.MockConfiguration.experimentRealistic()
                )
            )
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPEC-DRIVEN TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests that responses are valid JSON, using spec-derived threshold.
     *
     * <p>This test demonstrates the recommended pattern:
     * <ul>
     *   <li>{@code useCase = ShoppingUseCase.class} - type-safe reference</li>
     *   <li>Spec is automatically looked up at: {@code specs/ShoppingUseCase/v1.yaml}</li>
     *   <li>Threshold ({@code minPassRate}) comes from the spec</li>
     *   <li>Use case is injected by the provider</li>
     *   <li>Configuration is set explicitly (determined during EXPLORE phase)</li>
     * </ul>
     *
     * <h3>Post-EXPLORE Configuration</h3>
     * <p>After the EXPLORE phase determined optimal settings (model, temperature),
     * and the MEASURE phase established the baseline with those settings,
     * this probabilistic test uses the same fixed configuration.
     *
     * <h3>Statistical Basis</h3>
     * <p>The threshold in the spec comes from the 95% confidence interval
     * lower bound of the 1000-sample baseline experiment.
     *
     * @param useCase the shopping use case (injected by {@link UseCaseProvider})
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        maxExampleFailures = 5
        // minPassRate is derived from spec
    )
    @DisplayName("Should return valid JSON (spec-driven)")
    void shouldReturnValidJson(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        // Configuration fixed post-EXPLORE (same as MEASURE experiment)
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        // Query can vary - using a representative query from the standard set
        UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");

        tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

        assertThat(outcome.result().getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    /**
     * Tests JSON validity with explicit inline threshold (no spec lookup).
     *
     * <p>This demonstrates the fallback mode when you want to specify
     * the threshold explicitly rather than deriving it from a spec.
     *
     * <p>Configuration is set explicitly, as determined during EXPLORE phase.
     *
     * @param useCase the shopping use case
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        minPassRate = 0.95,  // Explicit threshold (no spec lookup)
        maxExampleFailures = 3
    )
    @DisplayName("Should return valid JSON (explicit threshold)")
    void shouldReturnValidJsonExplicit(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        // Configuration fixed post-EXPLORE (same as MEASURE experiment)
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        // Query can vary - using a representative query
        UseCaseOutcome outcome = useCase.searchProducts("laptop stand");

        tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

        assertThat(outcome.result().getBoolean("isValidJson", false))
            .as("Response should be valid JSON")
            .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUCCESS CRITERIA-DRIVEN TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tests using the use case's success criteria bundled with the outcome.
     *
     * <p>This demonstrates the best practice: the use case method returns a
     * {@link UseCaseOutcome} containing both the result and its success criteria.
     * This ensures:
     * <ul>
     *   <li><b>Type safety</b>: Criteria is bound to its result, no mismatches</li>
     *   <li><b>Evaluator consistency</b>: Same criteria used in MEASURE experiments</li>
     *   <li><b>Single source of truth</b>: Success definition lives with the use case</li>
     *   <li><b>Cleaner tests</b>: Just call outcome.assertAll()</li>
     * </ul>
     *
     * <p>The spec file will contain per-criterion pass rates from the MEASURE
     * experiment, providing visibility into which criteria are most reliable.
     *
     * @param useCase the shopping use case (injected by {@link UseCaseProvider})
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 30,
        maxExampleFailures = 5
        // minPassRate is derived from spec
    )
    @DisplayName("Should pass all success criteria (spec-driven)")
    void shouldPassAllSuccessCriteria(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        // Configuration fixed post-EXPLORE (same as MEASURE experiment)
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");

        tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

        // The success criteria is bundled with the outcome - same as used in experiments!
        // This ensures evaluator consistency between MEASURE and test.
        outcome.assertAll();
    }

    /**
     * Tests using success criteria with explicit threshold.
     *
     * <p>Combines the clarity of success criteria with explicit threshold control.
     *
     * @param useCase the shopping use case
     * @param tokenRecorder for tracking token consumption
     */
    @ProbabilisticTest(
        useCase = ShoppingUseCase.class,
        samples = 25,
        minPassRate = 0.90,  // Explicit threshold
        maxExampleFailures = 3
    )
    @DisplayName("Should pass success criteria (explicit threshold)")
    void shouldPassSuccessCriteriaExplicit(
            ShoppingUseCase useCase,
            TokenChargeRecorder tokenRecorder) {
        // Configuration fixed post-EXPLORE
        useCase.setModel("gpt-4");
        useCase.setTemperature(0.7);
        
        UseCaseOutcome outcome = useCase.searchProducts("USB-C hub");

        tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

        // Success criteria bundled with the outcome
        outcome.assertAll();
    }
}
