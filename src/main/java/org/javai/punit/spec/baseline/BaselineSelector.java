package org.javai.punit.spec.baseline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.ConformanceDetail;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.CovariateScore;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.ScoredCandidate;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher;
import org.javai.punit.spec.baseline.covariate.CovariateMatcherRegistry;

/**
 * Selects the best-matching baseline for a probabilistic test.
 *
 * <p>Two-phase selection algorithm:
 * <h3>Phase 1: Hard Gates</h3>
 * <ol>
 *   <li>Filter by CONFIGURATION covariates (exact match required)</li>
 *   <li>If no candidates remain, throw NoCompatibleBaselineException with guidance</li>
 * </ol>
 *
 * <h3>Phase 2: Soft Matching</h3>
 * <ol>
 *   <li>Score remaining candidates by TEMPORAL, INFRASTRUCTURE, etc. covariates</li>
 *   <li>Rank by match count (more matches is better)</li>
 *   <li>Break ties using category priority, declaration order, recency</li>
 *   <li>INFORMATIONAL covariates are ignored</li>
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
     * Selects the best baseline from candidates using category-aware matching.
     *
     * @param candidates baselines with matching footprint
     * @param testProfile the test's current covariate profile
     * @param declaration the covariate declaration (for category info)
     * @return selection result including the chosen baseline and conformance info
     * @throws NoCompatibleBaselineException if CONFIGURATION covariates don't match any baseline
     */
    public SelectionResult select(
            List<BaselineCandidate> candidates,
            CovariateProfile testProfile,
            CovariateDeclaration declaration) {
        
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(testProfile, "testProfile must not be null");
        Objects.requireNonNull(declaration, "declaration must not be null");

        if (candidates.isEmpty()) {
            return SelectionResult.noMatch();
        }

        // Phase 1: Hard gate - filter by CONFIGURATION covariates
        var configKeys = declaration.allKeys().stream()
            .filter(key -> declaration.getCategory(key) == CovariateCategory.CONFIGURATION)
            .toList();
        
        List<BaselineCandidate> configMatches = candidates;
        if (!configKeys.isEmpty()) {
            configMatches = candidates.stream()
                .filter(c -> matchesConfigurationCovariates(c.covariateProfile(), testProfile, configKeys))
                .toList();
            
            if (configMatches.isEmpty()) {
                // Find what CONFIGURATION values are available for error message
                throw buildConfigurationMismatchException(candidates, testProfile, configKeys);
            }
        }

        // Phase 2: Soft matching - score by remaining covariates
        var scored = configMatches.stream()
            .map(c -> new ScoredCandidate(c, score(c.covariateProfile(), testProfile, declaration)))
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

    private boolean matchesConfigurationCovariates(
            CovariateProfile baseline, 
            CovariateProfile test, 
            List<String> configKeys) {
        for (String key : configKeys) {
            var baselineValue = baseline.get(key);
            var testValue = test.get(key);
            
            if (baselineValue == null || testValue == null) {
                return false;
            }
            
            var matcher = matcherRegistry.getMatcher(key);
            var result = matcher.match(baselineValue, testValue);
            if (result != CovariateMatcher.MatchResult.CONFORMS) {
                return false;
            }
        }
        return true;
    }

    private NoCompatibleBaselineException buildConfigurationMismatchException(
            List<BaselineCandidate> candidates,
            CovariateProfile testProfile,
            List<String> configKeys) {
        
        // Build a descriptive footprint for error message
        var testConfigSignature = new StringBuilder();
        for (String key : configKeys) {
            var testValue = testProfile.get(key);
            testConfigSignature.append(key).append("=")
                   .append(testValue != null ? testValue.toCanonicalString() : "<not set>")
                   .append(" ");
        }
        
        var availableFootprints = new ArrayList<String>();
        var seen = new java.util.HashSet<String>();
        for (var candidate : candidates) {
            var configSignature = new StringBuilder();
            for (String key : configKeys) {
                var value = candidate.covariateProfile().get(key);
                configSignature.append(key).append("=")
                              .append(value != null ? value.toCanonicalString() : "<not set>")
                              .append(" ");
            }
            var sig = configSignature.toString().trim();
            if (seen.add(sig)) {
                availableFootprints.add(sig);
            }
        }
        
        return NoCompatibleBaselineException.configurationMismatch(
            "CONFIGURATION", 
            testConfigSignature.toString().trim(), 
            availableFootprints);
    }

    private CovariateScore score(CovariateProfile baseline, CovariateProfile test, CovariateDeclaration declaration) {
        var details = new ArrayList<ConformanceDetail>();
        int matchCount = 0;

        // Score based on declared covariates, not profile keys
        // This ensures we only consider covariates that were explicitly declared
        for (String key : declaration.allKeys()) {
            var category = declaration.getCategory(key);
            
            // Skip INFORMATIONAL covariates in scoring
            if (category == CovariateCategory.INFORMATIONAL) {
                continue;
            }

            var baselineValue = baseline.get(key);
            var testValue = test.get(key);
            
            // Skip CONFIGURATION covariates (already filtered in phase 1)
            if (category == CovariateCategory.CONFIGURATION) {
                // Add as CONFORMS since we already filtered
                details.add(new ConformanceDetail(key, baselineValue, testValue, 
                    CovariateMatcher.MatchResult.CONFORMS));
                matchCount++;
                continue;
            }

            CovariateMatcher.MatchResult result;
            if (baselineValue == null || testValue == null) {
                result = CovariateMatcher.MatchResult.DOES_NOT_CONFORM;
                if (testValue == null) {
                    testValue = new CovariateValue.StringValue("<missing>");
                }
                if (baselineValue == null) {
                    baselineValue = new CovariateValue.StringValue("<missing>");
                }
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

