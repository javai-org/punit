package org.javai.punit.spec.baseline.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Strategy for matching covariate values between baseline and test.
 *
 * <p>Each covariate type has its own matching strategy:
 * <ul>
 *   <li>String values typically require exact matches</li>
 *   <li>Time windows check if test time falls within baseline window</li>
 * </ul>
 */
@FunctionalInterface
public interface CovariateMatcher {

    /**
     * Determines whether the test value matches the baseline value.
     *
     * @param baselineValue the value recorded in the baseline
     * @param testValue the value resolved at test time
     * @return the match result
     */
    MatchResult match(CovariateValue baselineValue, CovariateValue testValue);

    /**
     * Result of a covariate match.
     */
    enum MatchResult {
        /** Perfect match - conditions are identical or test falls within baseline range. */
        CONFORMS,

        /** Partial match - some overlap but not complete conformance. */
        PARTIALLY_CONFORMS,

        /** No match - conditions are incompatible. */
        DOES_NOT_CONFORM;

        /**
         * Returns true if this result indicates at least partial conformance.
         *
         * @return true if CONFORMS or PARTIALLY_CONFORMS
         */
        public boolean conformsAtLeastPartially() {
            return this == CONFORMS || this == PARTIALLY_CONFORMS;
        }
    }
}

