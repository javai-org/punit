package org.javai.punit.api.criterion;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.javai.punit.api.Contract;
import org.javai.punit.api.Postcondition;

/**
 * The single-criterion default a {@link Contract} resolves to when
 * the author has not explicitly declared criteria. The default
 * criterion's postconditions are exactly the contract's existing
 * {@link Contract#postconditions()}; its identifier is derived from
 * the contract's class name.
 *
 * <p>This type exists to make the K=1 isomorphism concrete: a
 * contract written today, with no awareness of the criterion
 * vocabulary, is still a single-criterion contract under the new
 * model. The same postconditions evaluated in the same order produce
 * the same per-trial pass / fail outcomes as before.
 *
 * <p>The class is final and constructible only via its single public
 * constructor. It is the framework's shim, not an extension point.
 * Authors wanting the multi-criterion authoring surface implement
 * {@link Criterion} directly and supply their criteria via
 * {@link Contract#criteria(CriteriaBuilder)}; they do not extend or
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

    /**
     * Returns the contract's postconditions directly. Bypasses the
     * default {@link Criterion#postconditions()} materialiser because
     * the chain already lives on the contract.
     */
    @Override
    public List<Postcondition<O>> postconditions() {
        return contract.postconditions();
    }

    /**
     * Lowercase-hyphenated form of the contract class's simple name.
     * Stable for a given concrete contract class; unique within the
     * K=1 criteria list (trivially).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ShoppingBasketContract} → {@code "shopping-basket-contract"}</li>
     *   <li>{@code PaymentGatewaySLA} → {@code "payment-gateway-sla"}</li>
     *   <li>An anonymous subclass yields a derived token incorporating
     *       the enclosing class's simple name; the exact token is an
     *       implementation detail and is only required to be stable
     *       and report-readable.</li>
     * </ul>
     */
    private static String deriveId(Class<?> type) {
        String simple = type.getSimpleName();
        if (simple.isEmpty()) {
            // Anonymous inner class. Fall back to a stable derived
            // token from the enclosing-class hierarchy. getName() is
            // unique per concrete class and stable for the life of
            // the JVM; we trim it to keep the id readable.
            String full = type.getName();
            int lastDollar = full.lastIndexOf('$');
            simple = lastDollar >= 0 ? full.substring(0, lastDollar) : full;
            int lastDot = simple.lastIndexOf('.');
            if (lastDot >= 0) simple = simple.substring(lastDot + 1);
            simple = simple + "-anon";
        }
        // Camel-case to hyphen-separated lowercase.
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
