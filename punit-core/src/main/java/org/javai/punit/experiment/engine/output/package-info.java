/**
 * Shared output utilities for experiment writers.
 *
 * <p>This package provides common components used by all three experiment
 * output writers (MEASURE, EXPLORE, OPTIMIZE). The design favors composition
 * over inheritance since each experiment type has fundamentally different
 * output structures.
 *
 * <h2>Statistical Semantics</h2>
 *
 * <p>The package distinguishes between two types of statistics based on
 * sample size and purpose:
 *
 * <table border="1">
 *   <caption>Statistics by Experiment Mode</caption>
 *   <tr><th>Mode</th><th>Sample Size</th><th>Statistics Type</th><th>Rationale</th></tr>
 *   <tr><td>MEASURE</td><td>1000+</td><td>{@link InferentialStatistics}</td>
 *       <td>Large samples support reliable inference (SE, CI)</td></tr>
 *   <tr><td>EXPLORE</td><td>~20/config</td><td>{@link DescriptiveStatistics}</td>
 *       <td>Small samples; inferential stats would be misleading</td></tr>
 *   <tr><td>OPTIMIZE</td><td>~20/iteration</td><td>{@link DescriptiveStatistics}</td>
 *       <td>Small samples; focus is on trajectory, not inference</td></tr>
 * </table>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link OutputUtilities} - Fingerprint computation, header writing</li>
 *   <li>{@link DescriptiveStatistics} - Observed rate and counts only</li>
 *   <li>{@link InferentialStatistics} - Full stats with SE, CI, derived threshold</li>
 * </ul>
 *
 * @see org.javai.punit.experiment.measure.MeasureOutputWriter
 * @see org.javai.punit.experiment.explore.ExploreOutputWriter
 * @see org.javai.punit.experiment.optimize.OptimizeOutputWriter
 */
package org.javai.punit.experiment.engine.output;
