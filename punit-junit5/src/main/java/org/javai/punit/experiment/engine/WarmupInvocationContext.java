package org.javai.punit.experiment.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for a warmup invocation.
 *
 * <p>Warmup invocations are executed but their results are discarded.
 * This context provides a distinct display name (e.g., "Warmup 1/3")
 * so the JUnit test runner clearly distinguishes warmup from counted samples.
 *
 * <p>Shared by all strategies (probabilistic tests and experiments).
 * For experiment methods that require per-invocation parameter resolvers
 * (e.g., {@code OutcomeCaptor}, {@code @InputSource} values), use the
 * factory methods to create contexts with the necessary extensions.
 *
 * @param warmupIndex 1-based index of this warmup invocation
 * @param totalWarmup total number of warmup invocations
 * @param extensions parameter resolvers needed for the method signature
 */
public record WarmupInvocationContext(
        int warmupIndex,
        int totalWarmup,
        List<Extension> extensions
) implements TestTemplateInvocationContext {

    /**
     * Creates a warmup context with no additional extensions.
     *
     * <p>Suitable for {@code @ProbabilisticTest} methods where parameter
     * resolution is handled by class-level extensions.
     */
    public WarmupInvocationContext(int warmupIndex, int totalWarmup) {
        this(warmupIndex, totalWarmup, Collections.emptyList());
    }

    /**
     * Creates a warmup context for an experiment method that accepts an {@code OutcomeCaptor}.
     *
     * <p>Provides a throwaway captor — its results are discarded by the warmup handler.
     */
    public static WarmupInvocationContext forExperiment(int warmupIndex, int totalWarmup) {
        return new WarmupInvocationContext(warmupIndex, totalWarmup,
                List.of(new CaptorParameterResolver(new OutcomeCaptor(), null, 0)));
    }

    /**
     * Creates a warmup context for an experiment method that accepts an
     * {@code OutcomeCaptor} and an input parameter.
     *
     * <p>Provides throwaway values — results are discarded by the warmup handler.
     *
     * @param firstInput the first input value (used for type-compatible parameter resolution)
     * @param inputType the input parameter type
     */
    public static WarmupInvocationContext forExperimentWithInput(
            int warmupIndex, int totalWarmup, Object firstInput, Class<?> inputType) {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(new CaptorParameterResolver(new OutcomeCaptor(), null, 0));
        extensions.add(new InputParameterResolver(firstInput, inputType));
        return new WarmupInvocationContext(warmupIndex, totalWarmup, Collections.unmodifiableList(extensions));
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("Warmup %d/%d", warmupIndex, totalWarmup);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return extensions;
    }
}
