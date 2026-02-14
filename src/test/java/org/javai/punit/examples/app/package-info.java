/**
 * Simulated application code exercised by the example experiments and tests.
 *
 * <p>This package plays the role of <b>the application under test</b>. In a real
 * project, these classes would live in {@code src/main/java} — LLM clients,
 * payment gateways, domain models, validators. Here they are simulated so the
 * examples can run without external dependencies while still exhibiting the
 * non-deterministic behaviour that motivates probabilistic testing.
 *
 * <h2>Relationship to the rest of the examples</h2>
 * <pre>
 *   experiments / tests
 *         │
 *         ▼
 *     usecases          ← PUnit use case adapters
 *         │
 *         ▼
 *       app (this)      ← application code (no PUnit dependency)
 * </pre>
 *
 * <p>The key architectural constraint: <b>nothing in this package may depend on
 * PUnit</b>. Application code is independent of its test framework. This is
 * enforced by an ArchUnit rule in
 * {@link org.javai.punit.examples.ExampleArchitectureTest}.
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@code llm/} — Chat LLM clients (mock and real) with configurable
 *       success rates and realistic failure modes</li>
 *   <li>{@code payment/} — Payment gateway simulating real-world reliability
 *       patterns</li>
 *   <li>{@code shopping/} — Domain model for shopping basket operations
 *       (actions, validation, JSON mapping)</li>
 * </ul>
 */
package org.javai.punit.examples.app;
