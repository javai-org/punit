package org.javai.punit.api.criterion;

import java.util.Locale;
import java.util.Objects;

import org.javai.punit.api.Contract;

/**
 * The single-criterion default a {@link Contract} resolves to when
 * the author has not explicitly declared criteria. Wraps the
 * contract's existing {@link Contract#postconditions()} as a direct
 * (no-transform) criterion; its identifier is derived from the
 * contract's class name.
 *
 * <p>This type makes the K=1 isomorphism concrete: a contract
 * written today, with no awareness of the criterion vocabulary, is
 * still a single-criterion contract under the model. The same
 * postconditions evaluated in the same order against the same value
 * produce the same per-postcondition results as before.
 *
 * <p>The class is final and constructible only via its single public
 * constructor. It is the framework's K=1 shim, not an extension
 * point. Authors wanting the multi-criterion authoring surface use
 * the {@link Criteria} factory and supply their criteria via
 * {@link Contract#criteria()}; they do not extend or
 * instantiate {@code DefaultCriterion}.
 *
 * @param <O> the contract's per-sample output value type
 */
public final class DefaultCriterion<O> implements Criterion<O> {

    private final Contract<?, O> contract;
    private final String id;

    /**
     * Construct a default criterion for the supplied contract.
     *
     * @param contract the contract whose postconditions this
     *                 criterion delegates to. Must be non-null.
     * @throws NullPointerException if {@code contract} is null
     */
    public DefaultCriterion(Contract<?, O> contract) {
        this.contract = Objects.requireNonNull(contract, "contract");
        this.id = deriveId(contract.getClass());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CriterionSampleResult evaluate(O value) {
        return DirectCriterion.evaluateChain(id, contract.postconditions(), value);
    }

    /**
     * Lowercase-hyphenated form of the contract class's simple name.
     * Stable for a given concrete contract class; unique within the
     * K=1 criteria list (trivially).
     */
    private static String deriveId(Class<?> type) {
        String simple = type.getSimpleName();
        if (simple.isEmpty()) {
            // Anonymous inner class. Fall back to a stable derived
            // token from the enclosing-class hierarchy.
            String full = type.getName();
            int lastDollar = full.lastIndexOf('$');
            simple = lastDollar >= 0 ? full.substring(0, lastDollar) : full;
            int lastDot = simple.lastIndexOf('.');
            if (lastDot >= 0) simple = simple.substring(lastDot + 1);
            simple = simple + "-anon";
        }
        StringBuilder sb = new StringBuilder(simple.length() + 8);
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (i > 0 && Character.isUpperCase(c)
                    && !Character.isUpperCase(simple.charAt(i - 1))) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
