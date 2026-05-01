package org.javai.punit.api.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.covariate.CovariateProfile;

/**
 * The richer return shape of
 * {@link BaselineProvider#baselineLookup(String, FactorBundle, String, Class, CovariateProfile, java.util.List)}.
 *
 * <p>Carries the same {@code Optional<S>} that the convenience
 * {@code baselineFor} returns, plus:
 *
 * <ul>
 *   <li>{@code baselineProfile} — the resolved baseline's covariate
 *       profile, when one was selected. Empty when no baseline
 *       matched, or when the matched baseline carried no covariate
 *       data (legacy / pre-CV-3a baselines).</li>
 *   <li>{@code notes} — human-readable selection diagnostics: one
 *       per rejected candidate, plus partial-match / fallback
 *       announcements. Surfaced through
 *       {@link ProbabilisticTestResult#warnings()} so an INCONCLUSIVE
 *       verdict explains <em>why</em> the baseline didn't match.</li>
 * </ul>
 *
 * <p>The baseline profile is what makes the verdict's "covariate
 * alignment" diagnostic possible: comparing the matched baseline's
 * profile against the current run's profile (resolved separately at
 * the engine boundary) lets the renderer produce the
 * {@code baseline=X, observed=Y} misalignment block the legacy
 * pipeline emits.
 *
 * @param <S> the requested {@link BaselineStatistics} subtype
 * @param selected         the selected statistics, when a candidate matched
 * @param baselineProfile  the matched baseline's covariate profile,
 *                         when one was selected; otherwise empty
 * @param notes            rejection / fallback reasons; possibly empty
 * @param sourceFile       filename of the matched baseline, when a
 *                         candidate matched; surfaces to RP07 verdict
 *                         XML's {@code <provenance spec-filename>} for
 *                         audit traceability. Empty otherwise.
 */
public record BaselineLookup<S extends BaselineStatistics>(
        Optional<S> selected,
        CovariateProfile baselineProfile,
        List<String> notes,
        Optional<String> sourceFile) {

    public BaselineLookup {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(baselineProfile, "baselineProfile");
        Objects.requireNonNull(notes, "notes");
        Objects.requireNonNull(sourceFile, "sourceFile");
        notes = List.copyOf(notes);
    }

    /**
     * Backward-compatible constructor that omits {@link #sourceFile()}.
     * Defaults to {@link Optional#empty()}.
     */
    public BaselineLookup(
            Optional<S> selected,
            CovariateProfile baselineProfile,
            List<String> notes) {
        this(selected, baselineProfile, notes, Optional.empty());
    }

    /**
     * @return an empty lookup ({@link Optional#empty()} selected,
     *         empty profile, no notes, no source file)
     */
    public static <S extends BaselineStatistics> BaselineLookup<S> empty() {
        return new BaselineLookup<>(
                Optional.empty(), CovariateProfile.empty(), List.of(), Optional.empty());
    }

    /**
     * @return a lookup that wraps an existing {@code Optional<S>} with
     *         no profile + no notes + no source file — convenience for
     *         default-method delegates
     */
    public static <S extends BaselineStatistics> BaselineLookup<S> of(
            Optional<S> selected) {
        return new BaselineLookup<>(
                selected, CovariateProfile.empty(), List.of(), Optional.empty());
    }
}
