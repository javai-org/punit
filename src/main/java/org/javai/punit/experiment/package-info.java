/**
 * Experimentation framework for gathering empirical data about non-deterministic systems.
 *
 * <p>This package provides infrastructure for running experiments that measure, explore,
 * and optimize the behavior of non-deterministic systems such as LLMs and ML models.
 * Experiments produce specifications that can later be used by probabilistic tests.
 *
 * <p>Three experiment modes are supported:
 * <ul>
 *   <li><b>Measure</b> ({@link org.javai.punit.api.MeasureExperiment}) - Runs samples to
 *       establish baseline success rates and produce execution specifications</li>
 *   <li><b>Explore</b> ({@link org.javai.punit.api.ExploreExperiment}) - Explores factor
 *       combinations to identify optimal configurations</li>
 *   <li><b>Optimize</b> ({@link org.javai.punit.api.OptimizeExperiment}) - Searches for
 *       optimal parameter values using Bayesian optimization</li>
 * </ul>
 *
 * <p>The {@link org.javai.punit.experiment.engine.ExperimentExtension} coordinates experiment
 * execution using the Strategy pattern, delegating to mode-specific strategies.
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@code engine/} - Core extension and strategy infrastructure</li>
 *   <li>{@code measure/} - Measurement strategy implementation</li>
 *   <li>{@code explore/} - Exploration strategy implementation</li>
 *   <li>{@code optimize/} - Optimization strategy implementation</li>
 * </ul>
 *
 * @see org.javai.punit.spec
 * @see org.javai.punit.ptest
 */
package org.javai.punit.experiment;
