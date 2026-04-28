package org.javai.punit.engine.baseline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;

/**
 * Covariate-aware best-match selection over a set of candidate
 * baselines per UC05.
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li><b>Hard CONFIGURATION gate.</b> Any candidate that disagrees
 *       with the current profile on a {@link CovariateCategory#CONFIGURATION}
 *       covariate is rejected outright. Configuration mismatches
 *       represent deliberate setup differences (model version,
 *       feature flag, A/B group) — applying a baseline measured
 *       under one configuration to a test running under a different
 *       configuration would invalidate the statistical claim.</li>
 *   <li><b>Score = number of matching covariates.</b> Among
 *       remaining candidates, count how many of the use case's
 *       declared covariates match between the current profile and
 *       the candidate's profile.</li>
 *   <li><b>Highest score wins.</b> Exact match (all covariates
 *       agree) trivially scores highest; partial matches are picked
 *       in proportion to their fit.</li>
 *   <li><b>Ties broken by category priority.</b> Per UC05's
 *       ordering, a candidate matching a higher-priority category
 *       beats one matching a lower-priority category at the same
 *       score. {@code TEMPORAL > INFRASTRUCTURE > CONFIGURATION >
 *       OPERATIONAL > EXTERNAL_DEPENDENCY > DATA_STATE}. The
 *       comparison is over the set of categories each candidate
 *       matched on, smallest-priority-value wins.</li>
 *   <li><b>Default fallback.</b> When a candidate's profile is
 *       empty (covariate-insensitive baseline) and no covariate-
 *       tagged candidate scored positively, the empty-profile
 *       candidate is selected — UC05's "default baseline" rule.</li>
 * </ol>
 *
 * <h2>Legacy compatibility</h2>
 *
 * <p>When the use case declares no covariates ({@code declarations}
 * is empty), only candidates whose own profile is empty are
 * considered — preserving the byte-identical behaviour of pre-CV-3
 * resolution for use cases that don't opt into covariates.
 *
 * <p>The category enforcement requires the categories of the
 * declared covariates; the candidate file alone (which only carries
 * key/value pairs in the {@code covariates:} block) is not enough.
 */
final class BaselineSelector {

    /**
     * UC05 priority ordering. Lower index = higher precedence.
     */
    private static final List<CovariateCategory> PRIORITY = List.of(
            CovariateCategory.TEMPORAL,
            CovariateCategory.INFRASTRUCTURE,
            CovariateCategory.CONFIGURATION,
            CovariateCategory.OPERATIONAL,
            CovariateCategory.EXTERNAL_DEPENDENCY,
            CovariateCategory.DATA_STATE);

    private BaselineSelector() { }

    /**
     * @param candidates  baselines whose {@code (useCaseId,
     *                    factorsFingerprint)} already match the lookup
     * @param currentProfile  the profile resolved for the current run
     * @param declarations    the use case's covariate declarations,
     *                        in declaration order
     * @return the best-matching baseline per UC05, or empty when no
     *         candidate is selectable (CONFIGURATION mismatch on every
     *         covariate-tagged candidate <i>and</i> no empty-profile
     *         fallback)
     */
    static Optional<BaselineRecord> select(
            List<BaselineRecord> candidates,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(currentProfile, "currentProfile");
        Objects.requireNonNull(declarations, "declarations");

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // No covariate declarations → only match unstamped baselines.
        // Preserves pre-CV-3 behaviour for use cases that don't opt in.
        if (declarations.isEmpty()) {
            return candidates.stream()
                    .filter(c -> c.covariateProfile().isEmpty())
                    .findFirst();
        }

        Map<String, CovariateCategory> categoriesByName = categoryIndex(declarations);

        List<Scored> scored = new ArrayList<>(candidates.size());
        for (BaselineRecord c : candidates) {
            Scored s = score(c, currentProfile, categoriesByName);
            if (s == null) {
                continue;
            }
            // A covariate-tagged candidate that matches zero declared
            // covariates is the "wrong" baseline by identity — it was
            // measured under environmental conditions disjoint from
            // the current run. Reject it; the empty-profile baseline
            // (UC05's default fallback) is the only candidate allowed
            // at score 0.
            if (s.matches == 0 && !c.covariateProfile().isEmpty()) {
                continue;
            }
            scored.add(s);
        }

        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(BEST_FIRST);
        return Optional.of(scored.get(0).record);
    }

    private static Map<String, CovariateCategory> categoryIndex(
            List<Covariate> declarations) {
        Map<String, CovariateCategory> out =
                new java.util.LinkedHashMap<>(declarations.size());
        for (Covariate c : declarations) {
            out.put(c.name(), c.category());
        }
        return out;
    }

    /**
     * @return the candidate's score, or {@code null} when it must be
     *         rejected (a CONFIGURATION-category covariate disagrees).
     *         The empty-profile candidate scores 0 with no matched
     *         categories — it loses any tie-breaker to a candidate
     *         that matched something, but wins as a fallback when no
     *         scoring candidate beats it.
     */
    private static Scored score(
            BaselineRecord candidate,
            CovariateProfile current,
            Map<String, CovariateCategory> categoriesByName) {
        int matches = 0;
        List<CovariateCategory> matchedCategories = new ArrayList<>();
        CovariateProfile baselineProfile = candidate.covariateProfile();

        for (Map.Entry<String, CovariateCategory> declared : categoriesByName.entrySet()) {
            String name = declared.getKey();
            CovariateCategory category = declared.getValue();
            String currentValue = current.get(name).orElse(null);
            String baselineValue = baselineProfile.get(name).orElse(null);

            boolean baselineDeclares = baselineValue != null;
            boolean valuesAgree = baselineDeclares
                    && baselineValue.equals(currentValue);

            // Hard gate: a CONFIGURATION mismatch rejects the
            // candidate entirely. A baseline that omits a declared
            // CONFIGURATION covariate is treated as a mismatch — it
            // was measured before that covariate was declared, and
            // we cannot prove it was equivalent.
            if (category.isHardGate() && !valuesAgree && baselineDeclares) {
                return null;
            }
            if (valuesAgree) {
                matches++;
                matchedCategories.add(category);
            }
        }
        return new Scored(candidate, matches, matchedCategories);
    }

    private record Scored(
            BaselineRecord record,
            int matches,
            List<CovariateCategory> matchedCategories) { }

    /**
     * Higher score first; on tie, candidate whose best matched
     * category has the lowest priority index. On further tie, the
     * candidate with the lexicographically-smaller filename wins so
     * the selection is deterministic.
     */
    private static final Comparator<Scored> BEST_FIRST =
            Comparator
                    .comparingInt((Scored s) -> -s.matches)
                    .thenComparingInt(BaselineSelector::bestCategoryPriority)
                    .thenComparing(s -> s.record.filename());

    private static int bestCategoryPriority(Scored s) {
        // Lower priority value means higher precedence. A candidate
        // matching nothing returns Integer.MAX_VALUE, sorting after
        // any candidate that matched something at the same score —
        // but at score 0 every candidate is in this state, so the
        // comparison degrades to the filename tiebreaker, which
        // happens to leave the empty-profile baseline (the shortest
        // filename for a given (ucid, ff)) at the front. That is the
        // UC05 "default baseline" fallback expressed via lexical
        // order.
        int best = Integer.MAX_VALUE;
        for (CovariateCategory cat : s.matchedCategories) {
            int idx = PRIORITY.indexOf(cat);
            if (idx < best) {
                best = idx;
            }
        }
        return best;
    }
}
