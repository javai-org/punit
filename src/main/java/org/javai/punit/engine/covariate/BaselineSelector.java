package org.javai.punit.engine.covariate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.javai.punit.engine.covariate.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.ConformanceDetail;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.CovariateScore;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.ScoredCandidate;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.model.CovariateProfile;

/**
 * Selects the best-matching baseline for a probabilistic test.
 *
 * <p>Selection algorithm:
 * <ol>
 *   <li>Score each candidate by counting matching covariates</li>
 *   <li>Rank by match count (more matches is better)</li>
 *   <li>Break ties using covariate declaration order (earlier covariates prioritized)</li>
 *   <li>Break remaining ties using recency (newer baseline preferred)</li>
 *   <li>Flag as ambiguous if top candidates have identical scores</li>
 * </ol>
 */
public final class BaselineSelector {

    private final CovariateMatcherRegistry matcherRegistry;

    /**
     * Creates a selector with the standard matcher registry.
     */
    public BaselineSelector() {
        this(CovariateMatcherRegistry.withStandardMatchers());
    }

    /**
     * Creates a selector with a custom matcher registry.
     *
     * @param matcherRegistry the matcher registry to use
     */
    public BaselineSelector(CovariateMatcherRegistry matcherRegistry) {
        this.matcherRegistry = Objects.requireNonNull(matcherRegistry, "matcherRegistry must not be null");
    }

    /**
     * Selects the best baseline from candidates.
     *
     * @param candidates baselines with matching footprint
     * @param testProfile the test's current covariate profile
     * @return selection result including the chosen baseline and conformance info
     */
    public SelectionResult select(
            List<BaselineCandidate> candidates,
            CovariateProfile testProfile) {
        
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(testProfile, "testProfile must not be null");

        if (candidates.isEmpty()) {
            return SelectionResult.noMatch();
        }

        // Score each candidate
        var scored = candidates.stream()
            .map(c -> new ScoredCandidate(c, score(c.covariateProfile(), testProfile)))
            .sorted(this::compareScores)
            .toList();

        var best = scored.get(0);
        var ambiguous = scored.size() > 1 &&
            compareScores(scored.get(0), scored.get(1)) == 0;

        return new SelectionResult(
            best.candidate(),
            best.score().conformanceDetails(),
            ambiguous,
            scored.size()
        );
    }

    private CovariateScore score(CovariateProfile baseline, CovariateProfile test) {
        var details = new ArrayList<ConformanceDetail>();
        int matchCount = 0;

        for (String key : baseline.orderedKeys()) {
            var baselineValue = baseline.get(key);
            var testValue = test.get(key);

            CovariateMatcher.MatchResult result;
            if (testValue == null) {
                // Covariate not present in test profile - doesn't conform
                result = CovariateMatcher.MatchResult.DOES_NOT_CONFORM;
                testValue = new org.javai.punit.model.CovariateValue.StringValue("<missing>");
            } else {
                var matcher = matcherRegistry.getMatcher(key);
                result = matcher.match(baselineValue, testValue);
            }

            details.add(new ConformanceDetail(key, baselineValue, testValue, result));
            if (result == CovariateMatcher.MatchResult.CONFORMS) {
                matchCount++;
            }
        }

        return new CovariateScore(matchCount, details);
    }

    private int compareScores(ScoredCandidate a, ScoredCandidate b) {
        // Primary: more matches is better
        int matchDiff = Integer.compare(b.score().matchCount(), a.score().matchCount());
        if (matchDiff != 0) return matchDiff;

        // Secondary: prioritize earlier covariates (left-to-right)
        var aDetails = a.score().conformanceDetails();
        var bDetails = b.score().conformanceDetails();
        int minSize = Math.min(aDetails.size(), bDetails.size());
        
        for (int i = 0; i < minSize; i++) {
            var aDetail = aDetails.get(i);
            var bDetail = bDetails.get(i);
            int cmp = compareMatchResult(bDetail.result(), aDetail.result());
            if (cmp != 0) return cmp;
        }

        // Tertiary: recency (more recent baseline preferred)
        var aTime = a.candidate().generatedAt();
        var bTime = b.candidate().generatedAt();
        if (aTime != null && bTime != null) {
            return bTime.compareTo(aTime);
        }

        return 0;
    }

    private int compareMatchResult(CovariateMatcher.MatchResult a, CovariateMatcher.MatchResult b) {
        // CONFORMS < PARTIALLY_CONFORMS < DOES_NOT_CONFORM (lower ordinal is better)
        return Integer.compare(a.ordinal(), b.ordinal());
    }
}

