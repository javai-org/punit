package org.javai.punit.engine.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

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
     * @return the {@link BaselineStatistics} of the requested kind for
     *         the given criterion, or {@link Optional#empty()} when no
     *         matching baseline file exists or the named criterion has
     *         no entry in it
     * @throws IllegalStateException when a baseline file matches but its
     *         entry for {@code criterionName} is of a different
     *         {@link BaselineStatistics} flavour than {@code statisticsType}
     */
    public <S extends BaselineStatistics> Optional<S> resolve(
            String useCaseId,
            String factorsFingerprint,
            String criterionName,
            Class<S> statisticsType) {
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(statisticsType, "statisticsType");

        Optional<BaselineRecord> record = findRecord(useCaseId, factorsFingerprint);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        BaselineStatistics entry = record.get().statisticsByCriterionName().get(criterionName);
        if (entry == null) {
            return Optional.empty();
        }
        if (!statisticsType.isInstance(entry)) {
            throw new IllegalStateException(
                    "Baseline for use case '" + useCaseId + "' carries criterion '"
                            + criterionName + "' as " + entry.getClass().getSimpleName()
                            + " but the criterion declares "
                            + statisticsType.getSimpleName()
                            + " — write-side and read-side criterion kinds disagree");
        }
        return Optional.of(statisticsType.cast(entry));
    }

    /**
     * @return the recorded inputs identity from the matching baseline,
     *         or {@link Optional#empty()} when no match exists
     */
    public Optional<String> resolveInputsIdentity(
            String useCaseId, String factorsFingerprint) {
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        return findRecord(useCaseId, factorsFingerprint).map(BaselineRecord::inputsIdentity);
    }

    private Optional<BaselineRecord> findRecord(String useCaseId, String factorsFingerprint) {
        if (!Files.isDirectory(baselineDir)) {
            return Optional.empty();
        }
        String prefix = useCaseId + ".";
        String suffix = "-" + factorsFingerprint + ".yaml";
        try (var stream = Files.list(baselineDir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .findFirst()
                    .map(this::readUnchecked);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to enumerate baselines in " + baselineDir, e);
        }
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
