package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The richer return shape of
 * {@link BaselineProvider#baselineLookup(String, org.javai.punit.api.typed.FactorBundle, String, Class, org.javai.punit.api.typed.covariate.CovariateProfile, java.util.List)}.
 *
 * <p>Carries the same {@code Optional<S>} that the convenience
 * {@code baselineFor} returns, plus a list of human-readable notes
 * about <em>why</em> a baseline was or wasn't selected — typically
 * one note per rejected candidate ("rejected: {file} —
 * CONFIGURATION mismatch on model_version (current=v1, baseline=v2)").
 *
 * <p>Notes are surfaced through {@link ProbabilisticTestResult#warnings()}
 * so that an INCONCLUSIVE verdict explains the misalignment in the
 * report rather than leaving the author to infer it.
 *
 * @param <S> the requested {@link BaselineStatistics} subtype
 * @param selected the selected statistics, when a candidate matched
 * @param notes    rejection / fallback reasons; possibly empty
 */
public record BaselineLookup<S extends BaselineStatistics>(
        Optional<S> selected,
        List<String> notes) {

    public BaselineLookup {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(notes, "notes");
        notes = List.copyOf(notes);
    }

    /**
     * @return an empty lookup ({@link Optional#empty()} selected, no
     *         notes) — the legacy "nothing to say" outcome
     */
    public static <S extends BaselineStatistics> BaselineLookup<S> empty() {
        return new BaselineLookup<>(Optional.empty(), List.of());
    }

    /**
     * @return a lookup that wraps an existing {@code Optional<S>} with
     *         no notes — convenience for default-method delegates
     */
    public static <S extends BaselineStatistics> BaselineLookup<S> of(
            Optional<S> selected) {
        return new BaselineLookup<>(selected, List.of());
    }
}
