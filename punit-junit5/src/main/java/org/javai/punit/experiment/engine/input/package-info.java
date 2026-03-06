/**
 * Input source resolution for experiments and probabilistic tests.
 *
 * <p>This package provides the infrastructure for resolving {@link org.javai.punit.api.InputSource}
 * annotations to lists of test input values. It supports:
 * <ul>
 *   <li><b>Method sources</b> — Static methods returning {@code Stream<T>}, {@code Iterable<T>}, or arrays</li>
 *   <li><b>File sources</b> — JSON and CSV files loaded from the classpath</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.javai.punit.experiment.engine.input.InputSourceResolver} — Main resolver</li>
 *   <li>{@link org.javai.punit.experiment.engine.input.InputSourceException} — Resolution errors</li>
 * </ul>
 *
 * @see org.javai.punit.api.InputSource
 */
package org.javai.punit.experiment.engine.input;
