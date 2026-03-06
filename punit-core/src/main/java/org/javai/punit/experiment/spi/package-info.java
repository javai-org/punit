/**
 * Service Provider Interfaces (SPI) for punit experiment extensibility.
 *
 * <p>This package defines extension points for:
 * <ul>
 *   <li>{@link org.javai.punit.experiment.spi.ExperimentBackend} - Domain-specific backends</li>
 *   <li>{@link org.javai.punit.experiment.spi.RefinementStrategy} - Adaptive factor refinement</li>
 * </ul>
 *
 * <h2>Backend Discovery</h2>
 * <p>Backends are discovered via {@link java.util.ServiceLoader}. To register a backend:
 * <ol>
 *   <li>Implement {@link org.javai.punit.experiment.spi.ExperimentBackend}</li>
 *   <li>Create {@code META-INF/services/org.javai.punit.experiment.spi.ExperimentBackend}</li>
 *   <li>Add the fully-qualified class name of your implementation</li>
 * </ol>
 *
 * @see org.javai.punit.experiment.spi.ExperimentBackendRegistry
 */
package org.javai.punit.experiment.spi;

