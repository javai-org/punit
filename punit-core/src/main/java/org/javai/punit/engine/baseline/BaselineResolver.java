package org.javai.punit.engine.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.BaselineLookup;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.ProbabilisticTestResult;

/**
 * Exact-match baseline resolver. Looks up a baseline by
 * {@code (useCaseId, factorsFingerprint)}, then projects out the
 * matching {@link BaselineStatistics} entry by criterion name.
 *
 * <p>Stage 4 ships exact-match resolution only. Methodless lookup
 * (no {@code methodName} key in the lookup tuple) is by design —
 * Stage 4 lands ahead of the JUnit extension wiring (Stage 5) and
 * therefore has no spec-method context to drive a method-aware key.
 * The resolver matches the first file whose
 * {@code {useCaseId}.{methodName}-{factorsFingerprint}.yaml} pattern
 * agrees on the two known segments, regardless of the {@code methodName}
 * segment in between.
 *
 * <p>Future stages may layer covariate-aware closest-match resolution
 * on top of this base. The exact-match contract is forward-compatible:
 * closest-match is strictly more permissive.
 */
public final class BaselineResolver {

    /** System property used to override the baseline directory. */
    public static final String BASELINE_DIR_PROPERTY = "punit.baseline.dir";

    /** Convention-default directory under the project root. */
    public static final String CONVENTION_PATH = "src/test/resources/punit/baselines";

    /**
     * Resolves the baseline directory from {@value #BASELINE_DIR_PROPERTY}
     * if set, otherwise from the convention path
     * {@value #CONVENTION_PATH}. The returned path is not required to
     * exist — callers that read or list it must handle a missing
     * directory.
     *
     * <p>Exposed for utilities that consume the same baseline directory
     * the test pipeline does (e.g.
     * {@link org.javai.punit.power.PowerAnalysis}). The JUnit
     * extension's {@code BaselineProviderResolver} delegates here too
     * so framework and user code resolve the same path through one
     * implementation.
     */
    public static Path defaultDir() {
        String prop = System.getProperty(BASELINE_DIR_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop.trim());
        }
        return Paths.get(CONVENTION_PATH);
    }

    private final Path baselineDir;
    private final BaselineReader reader;

    public BaselineResolver(Path baselineDir) {
        this(baselineDir, new BaselineReader());
    }

    BaselineResolver(Path baselineDir, BaselineReader reader) {
        this.baselineDir = Objects.requireNonNull(baselineDir, "baselineDir");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * Resolve a baseline statistics entry without covariate context.
     *
     * <p>Equivalent to the covariate-aware overload with an empty
     * current profile and empty declarations. Selection limits to
     * baselines whose own profile is empty.
     */
    public <S extends BaselineStatistics> Optional<S> resolve(
            String useCaseId,
            String factorsFingerprint,
            String criterionName,
            Class<S> statisticsType) {
        return resolve(useCaseId, factorsFingerprint, criterionName,
                statisticsType, CovariateProfile.empty(), List.of());
    }

    /**
     * Resolve a baseline statistics entry with covariate-aware
     * best-match selection.
     *
     * @param currentProfile the resolved covariate profile for the
     *                       current run; empty when the use case
     *                       declared no covariates
     * @param declarations   the use case's covariate declarations,
     *                       in declaration order; empty when the use
     *                       case declared no covariates
     * @return the {@link BaselineStatistics} of the requested kind for
     *         the selected baseline, or {@link Optional#empty()} when
     *         no candidate is selectable (CONFIGURATION mismatch on
     *         every covariate-tagged candidate <i>and</i> no
     *         empty-profile fallback)
     * @throws IllegalStateException when a baseline file matches but
     *         its entry for {@code criterionName} is of a different
     *         {@link BaselineStatistics} flavour than {@code statisticsType}
     */
    public <S extends BaselineStatistics> Optional<S> resolve(
            String useCaseId,
            String factorsFingerprint,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return lookup(useCaseId, factorsFingerprint, criterionName,
                statisticsType, currentProfile, declarations).selected();
    }

    /**
     * Like {@link #resolve} but additionally returns selection notes
     * (one per rejected candidate, plus partial-match / fallback
     * announcements) for surfacing through
     * {@link ProbabilisticTestResult#warnings()}.
     */
    public <S extends BaselineStatistics> BaselineLookup<S> lookup(
            String useCaseId,
            String factorsFingerprint,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(statisticsType, "statisticsType");
        Objects.requireNonNull(currentProfile, "currentProfile");
        Objects.requireNonNull(declarations, "declarations");

        EnumeratedBaselines enumerated = enumerateCandidates(useCaseId, factorsFingerprint);
        BaselineSelector.SelectionReport report = enumerated.records().isEmpty()
                ? BaselineSelector.SelectionReport.NONE
                : BaselineSelector.selectWithReport(
                        enumerated.records(), currentProfile, declarations);
        Optional<BaselineRecord> selected = report.selected();
        if (selected.isEmpty()) {
            return new BaselineLookup<>(
                    Optional.empty(), CovariateProfile.empty(), report.notes(),
                    Optional.empty());
        }
        Optional<String> sourceFile = Optional.of(selected.get().filename());
        // EX10: surface the integrity warning (if any) for the
        // *selected* candidate only — warnings about candidates that
        // didn't influence the verdict would be noise.
        List<String> notes = withIntegrityWarning(
                report.notes(), enumerated, selected.get());
        CovariateProfile baselineProfile = selected.get().covariateProfile();
        BaselineStatistics entry = selected.get().statisticsByCriterionName().get(criterionName);
        if (entry == null) {
            return new BaselineLookup<>(
                    Optional.empty(), baselineProfile, notes, sourceFile);
        }
        if (!statisticsType.isInstance(entry)) {
            throw new IllegalStateException(
                    "Baseline for use case '" + useCaseId + "' carries criterion '"
                            + criterionName + "' as " + entry.getClass().getSimpleName()
                            + " but the criterion declares "
                            + statisticsType.getSimpleName()
                            + " — write-side and read-side criterion kinds disagree");
        }
        return new BaselineLookup<>(
                Optional.of(statisticsType.cast(entry)), baselineProfile, notes, sourceFile);
    }

    private static List<String> withIntegrityWarning(
            List<String> selectionNotes,
            EnumeratedBaselines enumerated,
            BaselineRecord selected) {
        Optional<String> integrity = enumerated.integrityWarningFor(selected.filename());
        if (integrity.isEmpty()) {
            return selectionNotes;
        }
        List<String> combined = new ArrayList<>(selectionNotes.size() + 1);
        combined.addAll(selectionNotes);
        combined.add(integrity.get());
        return combined;
    }

    /**
     * @return the recorded inputs identity from the matching baseline,
     *         or {@link Optional#empty()} when no match exists.
     *         Selects without covariate context — for callers
     *         (typically the empirical-checks identity-match path)
     *         that have not yet threaded covariate state through.
     */
    public Optional<String> resolveInputsIdentity(
            String useCaseId, String factorsFingerprint) {
        return resolveInputsIdentity(useCaseId, factorsFingerprint,
                CovariateProfile.empty(), List.of());
    }

    /**
     * Covariate-aware variant of {@link #resolveInputsIdentity(String, String)}.
     */
    public Optional<String> resolveInputsIdentity(
            String useCaseId, String factorsFingerprint,
            CovariateProfile currentProfile, List<Covariate> declarations) {
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        Objects.requireNonNull(currentProfile, "currentProfile");
        Objects.requireNonNull(declarations, "declarations");
        return findAndSelect(useCaseId, factorsFingerprint,
                currentProfile, declarations)
                .map(BaselineRecord::inputsIdentity);
    }

    private Optional<BaselineRecord> findAndSelect(
            String useCaseId,
            String factorsFingerprint,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        EnumeratedBaselines enumerated = enumerateCandidates(useCaseId, factorsFingerprint);
        if (enumerated.records().isEmpty()) {
            return Optional.empty();
        }
        return BaselineSelector.selectWithReport(
                enumerated.records(), currentProfile, declarations).selected();
    }

    private EnumeratedBaselines enumerateCandidates(
            String useCaseId, String factorsFingerprint) {
        if (!Files.isDirectory(baselineDir)) {
            return EnumeratedBaselines.empty();
        }
        String prefix = useCaseId + ".";
        String factorsSegment = "-" + factorsFingerprint;
        String yamlExt = ".yaml";
        try (var stream = Files.list(baselineDir)) {
            List<BaselineRecord> records = new ArrayList<>();
            Map<String, Optional<String>> warnings = new LinkedHashMap<>();
            stream
                    .filter(p -> matchesFactorsFingerprint(
                            p.getFileName().toString(), prefix, factorsSegment, yamlExt))
                    .forEach(p -> {
                        BaselineReader.LoadedBaseline loaded = loadUnchecked(p);
                        records.add(loaded.record());
                        warnings.put(loaded.record().filename(), loaded.integrityWarning());
                    });
            return new EnumeratedBaselines(records, warnings);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to enumerate baselines in " + baselineDir, e);
        }
    }

    /**
     * Match the {@code -{ff}.yaml} suffix (covariate-insensitive form)
     * and the {@code -{ff}-{cov1}...-{covN}.yaml} extension
     * (covariate-stamped form). Both share the {@code -{ff}} segment
     * immediately followed by either the file extension or another
     * hash component.
     *
     * <p>This method stays exact-match on {@code (useCaseId,
     * fingerprint)}; when multiple covariate-partitioned files share
     * the same fingerprint, it returns true for each and the caller
     * runs covariate-aware selection over the matching set.
     */
    private static boolean matchesFactorsFingerprint(
            String name, String prefix, String factorsSegment, String yamlExt) {
        if (!name.startsWith(prefix) || !name.endsWith(yamlExt)) {
            return false;
        }
        int idx = name.indexOf(factorsSegment);
        if (idx < 0) {
            return false;
        }
        int afterSegment = idx + factorsSegment.length();
        // The factors-fingerprint segment must be terminated by either
        // ".yaml" (covariate-insensitive case) or "-" (the start of a
        // covariate hash). A bare longer-fingerprint match like
        // -aabbccdd matching a file with fingerprint -aabbccddee would
        // pass startsWith here but fail this terminator check.
        return afterSegment == name.length() - yamlExt.length()
                || name.charAt(afterSegment) == '-';
    }

    private BaselineReader.LoadedBaseline loadUnchecked(Path file) {
        try {
            return reader.load(file);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read baseline file " + file, e);
        }
    }

    /**
     * Per-enumeration scratch carrying the candidate records the
     * selector chooses among, plus a side-table of integrity
     * warnings keyed by canonical filename. After selection, only
     * the warning matching the chosen record's filename is folded
     * into the lookup notes; non-selected candidates' warnings are
     * dropped (they would be noise — the verdict only depends on
     * the selected baseline).
     */
    private record EnumeratedBaselines(
            List<BaselineRecord> records,
            Map<String, Optional<String>> integrityWarningsByFilename) {

        Optional<String> integrityWarningFor(String filename) {
            return integrityWarningsByFilename.getOrDefault(filename, Optional.empty());
        }

        static EnumeratedBaselines empty() {
            return new EnumeratedBaselines(List.of(), Map.of());
        }
    }
}
