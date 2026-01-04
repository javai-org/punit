/**
 * Execution specification support for punit.
 *
 * <p>This package provides:
 * <ul>
 *   <li>Specification model ({@link org.javai.punit.spec.model.ExecutionSpecification})</li>
 *   <li>Specification registry ({@link org.javai.punit.spec.registry.SpecificationRegistry})</li>
 *   <li>Success criteria evaluation ({@link org.javai.punit.spec.model.SuccessCriteria})</li>
 * </ul>
 *
 * <h2>Specification Flow</h2>
 * <pre>
 * Experiment → Empirical Baseline → (human review) → Execution Specification → Probabilistic Test
 * </pre>
 *
 * <p>Specifications are normative (what should happen), unlike baselines which are
 * descriptive (what did happen).
 *
 * @see org.javai.punit.spec.model.ExecutionSpecification
 * @see org.javai.punit.spec.registry.SpecificationRegistry
 */
package org.javai.punit.spec;

