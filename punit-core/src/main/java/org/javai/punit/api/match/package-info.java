/**
 * Built-in concrete {@link org.javai.punit.api.ValueMatcher
 * ValueMatcher} implementations.
 *
 * <p>The api root carries the matching abstractions
 * ({@link org.javai.punit.api.ValueMatcher},
 * {@link org.javai.punit.api.MatchResult},
 * {@link org.javai.punit.api.Expectation}). This subpackage carries
 * the framework's built-in concrete implementations:
 *
 * <ul>
 *   <li>{@link org.javai.punit.api.match.StringMatcher} — four
 *       string comparison modes (exact, ignore-case,
 *       trim-whitespace, normalize-whitespace).</li>
 *   <li>{@link org.javai.punit.api.match.JsonMatcher} — semantic
 *       JSON comparison via Jackson.</li>
 * </ul>
 *
 * <p>Authors who need richer behaviour implement
 * {@link org.javai.punit.api.ValueMatcher} directly.
 */
package org.javai.punit.api.match;
