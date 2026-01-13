package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;

/**
 * Matcher for exact string match (custom covariates, REGION, TIMEZONE).
 *
 * <p>Performs case-sensitive comparison of canonical string representations.
 * Values of {@link CovariateProfile#NOT_SET} never match, even with themselves.
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

        // "not_set" never matches, even with itself
        if (CovariateProfile.NOT_SET.equals(baselineStr) ||
            CovariateProfile.NOT_SET.equals(testStr)) {
            return MatchResult.DOES_NOT_CONFORM;
        }

        boolean matches = caseSensitive
            ? baselineStr.equals(testStr)
            : baselineStr.equalsIgnoreCase(testStr);

        return matches ? MatchResult.CONFORMS : MatchResult.DOES_NOT_CONFORM;
    }
}

