/**
 * Typed foundational value types for the punit authoring surface.
 *
 * <p>This package holds the minimum vocabulary an author needs to
 * express a probabilistic test or experiment in the typed model:
 * a {@link org.javai.punit.api.UseCase} interface, the
 * {@link org.javai.punit.api.UseCaseOutcome} wrapper that
 * carries an {@link org.javai.outcome.Outcome Outcome&lt;OT&gt;} so
 * business-level failures travel as data rather than as exceptions,
 * and the {@link org.javai.punit.api.FactorValue} /
 * {@link org.javai.punit.api.FactorBundle} pair that binds a
 * factor record ({@code FT}) to a canonical, content-addressable
 * representation used by the baseline spec filename
 * ({@link org.javai.punit.api.FactorBundle#bundleHash()}) and
 * by the YAML factor block.
 *
 * <p>These types are language-neutral data definitions — they import
 * nothing from the broader punit codebase and nothing from JUnit.
 * Spec builders, the execution engine, and framework wiring live in
 * other modules; they consume types from this package but do not
 * contribute to it.
 *
 * <p>The typed model introduced here will eventually subsume the
 * annotation-driven authoring surface in {@code org.javai.punit.api}.
 * Until that migration completes, the typed types live in this
 * {@code typed} sub-package so that simple names such as
 * {@code UseCase}, {@code Pacing}, and {@code Covariate} do not
 * collide with existing annotations of the same name.
 */
package org.javai.punit.api;
