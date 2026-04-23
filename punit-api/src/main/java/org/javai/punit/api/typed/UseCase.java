package org.javai.punit.api.typed;

/**
 * A stochastic service invocation expressed as a typed, three-parameter
 * function.
 *
 * <p>{@code FT} is the factor record — the configuration the author has
 * chosen to vary. {@code IT} is the per-sample input. {@code OT} is the
 * per-sample output value wrapped in a {@link UseCaseOutcome}.
 *
 * <p>A {@code UseCase} is constructed by the framework once per factor
 * configuration (via the factory declared on the spec). Once
 * constructed the instance must behave as if immutable for the duration
 * of sampling — the framework caches and reuses the same instance
 * across samples for a given configuration.
 *
 * <p>Implementations are free to keep internal caches, connection
 * handles, or other per-configuration state, but must not mutate such
 * state in a way that changes the behaviour observed by
 * {@link #apply(Object) apply}. Pre-existing caches and pools are fine;
 * live reconfiguration in response to sample outcomes is not.
 *
 * @param <FT> the factor record type — typically a {@code record}
 * @param <IT> the per-sample input type
 * @param <OT> the per-sample output value type
 */
public interface UseCase<FT, IT, OT> {

    /**
     * Invokes the service for one sample.
     *
     * <p>Implementations may throw; the framework captures exceptions
     * as failed samples according to the configured exception policy.
     *
     * @param input the per-sample input
     * @return the outcome wrapping the produced value
     */
    UseCaseOutcome<OT> apply(IT input);

    /**
     * The stable identifier used in baseline filenames, logs, and
     * diagnostics.
     *
     * <p>Defaults to a kebab-cased form of the simple class name
     * (e.g. {@code ShoppingBasketUseCase} becomes
     * {@code shopping-basket-use-case}). Implementations that outgrow
     * the default should override with a fixed, filename-safe string.
     *
     * <p>The value must be stable across runs, non-empty, and must not
     * contain characters that are invalid in baseline filenames
     * ({@code / \\ : * ? " < > |} or ASCII control characters).
     *
     * @return the use case id
     */
    default String id() {
        return defaultIdFor(getClass());
    }

    /**
     * A human-readable description of the use case. Empty by default.
     * Surfaced in reports and logs; has no semantic effect.
     *
     * @return the description
     */
    default String description() {
        return "";
    }

    /**
     * The number of warmup invocations to run before counted samples
     * begin. Warmup outcomes are discarded — they do not contribute to
     * pass rate or any statistic — but they do consume budget.
     *
     * <p>A value of {@code 0} (the default) disables warmup.
     *
     * @return the warmup invocation count; never negative
     */
    default int warmup() {
        return 0;
    }

    /**
     * Computes the default id for a class: the simple class name with
     * any {@code UseCase} suffix stripped, camel-case boundaries
     * converted to kebab-case, and the whole string lower-cased.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ShoppingBasketUseCase}   → {@code shopping-basket}</li>
     *   <li>{@code PaymentGateway}          → {@code payment-gateway}</li>
     *   <li>{@code HTTPClientUseCase}       → {@code http-client}</li>
     *   <li>{@code SimpleLLMUseCase}        → {@code simple-llm}</li>
     * </ul>
     *
     * @param type the class to derive an id from
     * @return the kebab-cased id
     */
    static String defaultIdFor(Class<?> type) {
        String name = type.getSimpleName();
        if (name.endsWith("UseCase") && name.length() > "UseCase".length()) {
            name = name.substring(0, name.length() - "UseCase".length());
        }
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && isWordBoundary(name, i)) {
                out.append('-');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static boolean isWordBoundary(String s, int i) {
        char prev = s.charAt(i - 1);
        char curr = s.charAt(i);
        if (Character.isLowerCase(prev) && Character.isUpperCase(curr)) {
            return true;
        }
        if (Character.isUpperCase(prev) && Character.isUpperCase(curr)
                && i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1))) {
            return true;
        }
        return false;
    }
}
