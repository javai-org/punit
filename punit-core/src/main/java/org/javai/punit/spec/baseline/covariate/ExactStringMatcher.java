package org.javai.punit.spec.baseline.covariate;

import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;

/**
 * Matcher for exact string match (custom covariates, REGION, TIMEZONE).
 *
 * <p>Performs case-sensitive comparison of canonical string representations.
 * Values of {@link CovariateProfile#UNDEFINED} never match, even with themselves.
 */
public final class ExactStringMatcher implements CovariateMatcher {

    private final boolean caseSensitive;

    /**
     * Creates a case-sensitive matcher.
     */
    public ExactStringMatcher() {
        this(true);
    }

    /**
     * Creates a matcher with configurable case sensitivity.
     *
     * @param caseSensitive true for case-sensitive matching
     */
    public ExactStringMatcher(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        String baselineStr = baselineValue.toCanonicalString();
        String testStr = testValue.toCanonicalString();

        // "undefined" never matches, even with itself
        if (CovariateProfile.UNDEFINED.equals(baselineStr) ||
            CovariateProfile.UNDEFINED.equals(testStr)) {
            return MatchResult.DOES_NOT_CONFORM;
        }

        boolean matches = caseSensitive
            ? baselineStr.equals(testStr)
            : baselineStr.equalsIgnoreCase(testStr);

        return matches ? MatchResult.CONFORMS : MatchResult.DOES_NOT_CONFORM;
    }
}

