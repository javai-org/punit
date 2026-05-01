/**
 * The execution engine.
 *
 * <p>One {@link org.javai.punit.engine.Engine} drives every spec
 * flavour through the strategy methods declared on
 * {@link org.javai.punit.api.spec.Spec}. Samples are invoked
 * through a
 * {@link org.javai.punit.api.spec.SampleExecutor} — today the
 * shipped implementation is
 * {@link org.javai.punit.engine.SerialSampleExecutor}.
 */
package org.javai.punit.engine;
