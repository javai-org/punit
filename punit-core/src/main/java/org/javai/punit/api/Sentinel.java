package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a reliability specification eligible for Sentinel deployment.
 *
 * <p>A {@code @Sentinel}-annotated class is the primary authoring artifact for
 * stochastic reliability monitoring. It is a plain Java class with no JUnit
 * dependencies, containing:
 * <ul>
 *   <li>A {@link org.javai.punit.usecase.UseCaseFactory} field with registered
 *       use case factories</li>
 *   <li>{@code @MeasureExperiment} methods for establishing baselines</li>
 *   <li>{@code @ProbabilisticTest} methods for ongoing verification</li>
 *   <li>{@code @InputSource} methods for shared input data</li>
 * </ul>
 *
 * <p>Both the Sentinel engine and the JUnit engine consume this class.
 * JUnit test classes derive from it via inheritance:
 * <pre>{@code
 * @Sentinel
 * public class PaymentReliability {
 *     UseCaseFactory factory = new UseCaseFactory();
 *     { factory.register(PaymentUseCase.class, () -> new PaymentUseCase(...)); }
 *
 *     @ProbabilisticTest(useCase = PaymentUseCase.class, samples = 100)
 *     void testPayment(PaymentUseCase useCase) {
 *         useCase.processPayment(...).assertAll();
 *     }
 * }
 *
 * // JUnit adapter — one line
 * public class PaymentReliabilityTest extends PaymentReliability {}
 * }</pre>
 *
 * <p>The annotation is an opt-in mechanism. Sentinel deployment is a significant
 * operational decision, and the developer is best positioned to decide which
 * reliability specifications are candidates.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sentinel {
}
