/**
 * Covariate resolution and matching infrastructure.
 *
 * <p>This package provides the core components for:
 * <ul>
 *   <li>Resolving covariate values from the environment during experiments</li>
 *   <li>Matching covariate values between baselines and tests</li>
 *   <li>Computing footprints for baseline selection</li>
 *   <li>Selecting the best baseline for a probabilistic test</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.javai.punit.engine.covariate.CovariateResolver} - Strategy for resolving covariate values</li>
 *   <li>{@link org.javai.punit.engine.covariate.CovariateResolverRegistry} - Maps keys to resolvers</li>
 *   <li>{@link org.javai.punit.engine.covariate.CovariateMatcher} - Strategy for matching values</li>
 *   <li>{@link org.javai.punit.engine.covariate.CovariateMatcherRegistry} - Maps keys to matchers</li>
 *   <li>{@link org.javai.punit.engine.covariate.FootprintComputer} - Computes invocation footprints</li>
 *   <li>{@link org.javai.punit.engine.covariate.BaselineSelector} - Selects best baseline</li>
 * </ul>
 *
 * @see org.javai.punit.api.StandardCovariate
 * @see org.javai.punit.model.CovariateProfile
 */
package org.javai.punit.engine.covariate;

