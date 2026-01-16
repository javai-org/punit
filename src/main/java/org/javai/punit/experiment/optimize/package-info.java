/**
 * OPTIMIZE mode for iterative factor optimization.
 *
 * <p>OPTIMIZE is an experiment mode that iteratively refines one treatment factor
 * of a use case while holding other factors constant. It is conceptually a
 * MEASURE experiment in a loop, where the treatment factor is mutated between
 * iterations based on evaluation of aggregated outcomes.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.javai.punit.experiment.optimize.FactorSuit} - Complete set of factor values</li>
 *   <li>{@link org.javai.punit.experiment.optimize.Scorer} - Evaluates iterations</li>
 *   <li>{@link org.javai.punit.experiment.optimize.Mutator} - Generates new factor values</li>
 *   <li>{@link org.javai.punit.experiment.optimize.TerminationPolicy} - Decides when to stop</li>
 *   <li>{@link org.javai.punit.experiment.optimize.OptimizationOrchestrator} - Executes the loop</li>
 * </ul>
 *
 * @see org.javai.punit.experiment.optimize.OptimizationOrchestrator
 */
package org.javai.punit.experiment.optimize;
