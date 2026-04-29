package org.javai.punit.engine.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.covariate.Covariate;
import org.javai.punit.api.typed.covariate.CovariateProfile;
import org.javai.punit.api.typed.spec.BaselineLookup;
import org.javai.punit.api.typed.spec.BaselineStatistics;

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
     * baselines whose own profile is empty, preserving pre-CV-3
     * behaviour for use cases that don't declare covariates.
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
     * best-match selection per UC05.
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
     * {@link org.javai.punit.api.typed.spec.ProbabilisticTestResult#warnings()}.
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

        BaselineSelector.SelectionReport report = findAndSelectWithReport(
                useCaseId, factorsFingerprint, currentProfile, declarations);
        Optional<BaselineRecord> selected = report.selected();
        if (selected.isEmpty()) {
            return new BaselineLookup<>(Optional.empty(), report.notes());
        }
        BaselineStatistics entry = selected.get().statisticsByCriterionName().get(criterionName);
        if (entry == null) {
            return new BaselineLookup<>(Optional.empty(), report.notes());
        }
        if (!statisticsType.isInstance(entry)) {
            throw new IllegalStateException(
                    "Baseline for use case '" + useCaseId + "' carries criterion '"
                            + criterionName + "' as " + entry.getClass().getSimpleName()
                            + " but the criterion declares "
                            + statisticsType.getSimpleName()
                            + " — write-side and read-side criterion kinds disagree");
        }
        return new BaselineLookup<>(Optional.of(statisticsType.cast(entry)), report.notes());
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
        return findAndSelectWithReport(useCaseId, factorsFingerprint,
                currentProfile, declarations).selected();
    }

    private BaselineSelector.SelectionReport findAndSelectWithReport(
            String useCaseId,
            String factorsFingerprint,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        List<BaselineRecord> candidates = findRecords(useCaseId, factorsFingerprint);
        if (candidates.isEmpty()) {
            return BaselineSelector.SelectionReport.NONE;
        }
        return BaselineSelector.selectWithReport(
                candidates, currentProfile, declarations);
    }

    private List<BaselineRecord> findRecords(String useCaseId, String factorsFingerprint) {
        if (!Files.isDirectory(baselineDir)) {
            return List.of();
        }
        String prefix = useCaseId + ".";
        String factorsSegment = "-" + factorsFingerprint;
        String yamlExt = ".yaml";
        try (var stream = Files.list(baselineDir)) {
            List<BaselineRecord> out = new ArrayList<>();
            stream
                    .filter(p -> matchesFactorsFingerprint(
                            p.getFileName().toString(), prefix, factorsSegment, yamlExt))
                    .forEach(p -> out.add(readUnchecked(p)));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to enumerate baselines in " + baselineDir, e);
        }
    }

    /**
     * Match the legacy {@code -{ff}.yaml} suffix and the new
     * {@code -{ff}-{cov1}...-{covN}.yaml} extension. Both forms
     * share the {@code -{ff}} segment immediately followed by either
     * the file extension or another hash component.
     *
     * <p>Stage 4 stays exact-match on {@code (useCaseId, fingerprint)};
     * when multiple covariate-partitioned files share the same
     * fingerprint, this method returns true for each and the caller
     * picks the first one (file listing order). Covariate-aware
     * selection lands in CV-3c.
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
        // ".yaml" (legacy / empty-profile case) or "-" (the start of
        // a covariate hash). A bare longer-fingerprint match like
        // -aabbccdd matching a file with fingerprint -aabbccddee would
        // pass startsWith here but fail this terminator check.
        return afterSegment == name.length() - yamlExt.length()
                || name.charAt(afterSegment) == '-';
    }

    private BaselineRecord readUnchecked(Path file) {
        try {
            return reader.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read baseline file " + file, e);
        }
    }
}
