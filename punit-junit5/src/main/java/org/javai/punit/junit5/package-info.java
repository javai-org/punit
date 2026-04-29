/**
 * JUnit 5 extensions for the typed-compositional authoring model.
 *
 * <p>The extensions in this package drive the new
 * {@link org.javai.punit.api.PUnitTest @PUnitTest} and
 * {@link org.javai.punit.api.PUnitExperiment @PUnitExperiment}
 * annotations through the typed
 * {@link org.javai.punit.engine.Engine}, translating the engine's
 * results back to JUnit's pass / fail / aborted semantics.
 *
 * <p>Distinct from the legacy
 * {@code org.javai.punit.ptest.engine.ProbabilisticTestExtension}
 * and {@code org.javai.punit.experiment.engine.ExperimentExtension},
 * which drive the pre-Stage-3.5 annotations through the legacy
 * engine. Both annotation paths coexist until the Stage-8 cleanup.
 */
package org.javai.punit.junit5;
