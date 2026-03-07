package org.javai.punit.spec.baseline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Types used in baseline selection.
 */
public final class BaselineSelectionTypes {

    private BaselineSelectionTypes() {
        // Utility class
    }

    /**
     * A candidate baseline for selection.
     *
     * @param filename the baseline filename
     * @param footprint the baseline's footprint hash
     * @param covariateProfile the baseline's covariate profile
     * @param generatedAt when the baseline was generated
     * @param spec the loaded specification
     */
    public record BaselineCandidate(
            String filename,
            String footprint,
            CovariateProfile covariateProfile,
            Instant generatedAt,
            ExecutionSpecification spec
    ) {
        public BaselineCandidate {
            Objects.requireNonNull(filename, "filename must not be null");
            Objects.requireNonNull(footprint, "footprint must not be null");
            Objects.requireNonNull(covariateProfile, "covariateProfile must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
        }
    }

    /**
     * Result of baseline selection.
     *
     * @param selected the selected baseline (null if none)
     * @param conformanceDetails conformance details for each covariate
     * @param ambiguous true if multiple equally-suitable baselines existed
     * @param candidateCount number of candidates considered
     */
    public record SelectionResult(
            BaselineCandidate selected,
            List<ConformanceDetail> conformanceDetails,
            boolean ambiguous,
            int candidateCount
    ) {
        public SelectionResult {
            conformanceDetails = conformanceDetails != null ? List.copyOf(conformanceDetails) : List.of();
        }

        /**
         * Returns a result indicating no matching baseline was found.
         */
        public static SelectionResult noMatch() {
            return new SelectionResult(null, List.of(), false, 0);
        }

        /**
         * Returns true if a baseline was selected.
         */
        public boolean hasSelection() {
            return selected != null;
        }

        /**
         * Returns true if any covariates do not conform.
         */
        public boolean hasNonConformance() {
            return conformanceDetails.stream()
                .anyMatch(d -> d.result() != CovariateMatcher.MatchResult.CONFORMS);
        }

        /**
         * Returns the non-conforming covariate details.
         */
        public List<ConformanceDetail> nonConformingDetails() {
            return conformanceDetails.stream()
                .filter(d -> d.result() != CovariateMatcher.MatchResult.CONFORMS)
                .toList();
        }
    }

    /**
     * Conformance detail for a single covariate.
     *
     * @param covariateKey the covariate key
     * @param baselineValue the value in the baseline
     * @param testValue the value at test time
     * @param result the match result
     */
    public record ConformanceDetail(
            String covariateKey,
            CovariateValue baselineValue,
            CovariateValue testValue,
            CovariateMatcher.MatchResult result
    ) {
        public ConformanceDetail {
            Objects.requireNonNull(covariateKey, "covariateKey must not be null");
            Objects.requireNonNull(baselineValue, "baselineValue must not be null");
            Objects.requireNonNull(testValue, "testValue must not be null");
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    /**
     * Internal record for scored candidates during selection.
     */
    record ScoredCandidate(
            BaselineCandidate candidate,
            CovariateScore score
    ) {}

    /**
     * Internal record for covariate matching score.
     */
    record CovariateScore(
            int matchCount,
            List<ConformanceDetail> conformanceDetails
    ) {}
}

