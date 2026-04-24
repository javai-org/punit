package org.javai.punit.api.typed.spec;

/**
 * The value produced by {@link DataGenerationSpec#conclude()}. Sealed with two
 * variants: a probabilistic test concludes with a
 * {@link ProbabilisticTestResult}; an experiment concludes with an
 * {@link ExperimentResult}.
 */
public sealed interface EngineResult
        permits ProbabilisticTestResult, ExperimentResult {
}
