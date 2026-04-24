/**
 * The typed-model execution engine.
 *
 * <p>One {@link org.javai.punit.engine.Engine} drives every spec
 * flavour through the strategy methods declared on
 * {@link org.javai.punit.api.typed.spec.DataGenerationSpec}. Samples are invoked
 * through a
 * {@link org.javai.punit.api.typed.spec.SampleExecutor} — today the
 * shipped implementation is
 * {@link org.javai.punit.engine.SerialSampleExecutor}.
 *
 * <p>Distinct from the legacy {@code org.javai.punit.ptest.engine} and
 * {@code org.javai.punit.experiment.engine} packages, which continue
 * to serve the annotation-driven surface until Stage 8 retires them.
 */
package org.javai.punit.engine;
