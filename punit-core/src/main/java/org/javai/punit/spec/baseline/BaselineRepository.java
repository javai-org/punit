package org.javai.punit.spec.baseline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineSource;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.registry.SpecificationLoader;

/**
 * Repository for finding and loading baseline specification files.
 *
 * <p>This repository scans one or more spec directories for baseline files
 * matching a use case and filters by footprint. When multiple roots are
 * configured, candidates from all roots are pooled and each is tagged with
 * its {@link BaselineSource} so that downstream selection can apply source
 * priority as a tiebreaker.
 *
 * <p>Default construction mirrors {@code LayeredSpecRepository.createDefault()}:
 * if {@code punit.spec.dir} (or {@code PUNIT_SPEC_DIR}) is set and points to
 * an existing directory, that directory is scanned first with
 * {@link BaselineSource#ENVIRONMENT_LOCAL}. The classpath default is always
 * added with {@link BaselineSource#BUNDLED}.
 */
public final class BaselineRepository {

    static final String PROP_SPEC_DIR = "punit.spec.dir";
    static final String ENV_SPEC_DIR = "PUNIT_SPEC_DIR";

    private static final Logger logger = LogManager.getLogger(BaselineRepository.class);

    private final List<TaggedRoot> roots;

    private record TaggedRoot(Path path, BaselineSource source) {}

    /**
     * Creates a repository with the specified specs root directory.
     * The root is tagged as {@link BaselineSource#BUNDLED}.
     *
     * @param specsRoot the root directory for specifications
     */
    public BaselineRepository(Path specsRoot) {
        Objects.requireNonNull(specsRoot, "specsRoot must not be null");
        this.roots = List.of(new TaggedRoot(specsRoot, BaselineSource.BUNDLED));
    }

    /**
     * Creates a repository with explicit roots and their sources.
     *
     * @param envLocalRoot the environment-local root (may be null)
     * @param bundledRoot the classpath-bundled root
     */
    public BaselineRepository(Path envLocalRoot, Path bundledRoot) {
        Objects.requireNonNull(bundledRoot, "bundledRoot must not be null");
        List<TaggedRoot> list = new ArrayList<>();
        if (envLocalRoot != null) {
            list.add(new TaggedRoot(envLocalRoot, BaselineSource.ENVIRONMENT_LOCAL));
        }
        list.add(new TaggedRoot(bundledRoot, BaselineSource.BUNDLED));
        this.roots = List.copyOf(list);
    }

    /**
     * Creates a repository with the default spec roots.
     *
     * <p>Checks {@code punit.spec.dir} / {@code PUNIT_SPEC_DIR} for an
     * environment-local directory, then adds the classpath default.
     */
    public BaselineRepository() {
        this.roots = detectDefaultRoots();
    }

    private static List<TaggedRoot> detectDefaultRoots() {
        List<TaggedRoot> result = new ArrayList<>();

        // Environment-local layer
        String specDir = System.getProperty(PROP_SPEC_DIR);
        if (specDir == null || specDir.isBlank()) {
            specDir = System.getenv(ENV_SPEC_DIR);
        }
        if (specDir != null && !specDir.isBlank()) {
            Path envPath = Paths.get(specDir);
            if (Files.isDirectory(envPath)) {
                result.add(new TaggedRoot(envPath, BaselineSource.ENVIRONMENT_LOCAL));
            }
        }

        // Bundled classpath layer
        result.add(new TaggedRoot(detectDefaultBundledRoot(), BaselineSource.BUNDLED));
        return List.copyOf(result);
    }

    private static Path detectDefaultBundledRoot() {
        Path[] candidates = {
                Paths.get("src", "test", "resources", "punit", "specs"),
                Paths.get("punit", "specs"),
                Paths.get("specs")
        };

        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        return candidates[0];
    }

    /**
     * Finds all baseline candidates for a use case with matching footprint.
     *
     * <p>Scans all configured roots and pools the results. Each candidate
     * is tagged with the {@link BaselineSource} of the root it was loaded from.
     *
     * @param useCaseId the use case identifier
     * @param expectedFootprint the footprint to match (null matches any)
     * @return list of matching baseline candidates (may be empty)
     */
    public List<BaselineCandidate> findCandidates(String useCaseId, String expectedFootprint) {
        Objects.requireNonNull(useCaseId, "useCaseId must not be null");

        List<BaselineCandidate> candidates = new ArrayList<>();
        String sanitizedUseCaseId = sanitize(useCaseId);

        for (TaggedRoot root : roots) {
            scanRoot(root, sanitizedUseCaseId, expectedFootprint, candidates);
        }

        return candidates;
    }

    /**
     * Finds all baseline candidates for a use case (all footprints).
     *
     * @param useCaseId the use case identifier
     * @return list of all baseline candidates for the use case
     */
    public List<BaselineCandidate> findAllCandidates(String useCaseId) {
        return findCandidates(useCaseId, null);
    }

    /**
     * Returns all distinct footprints available for a use case.
     *
     * @param useCaseId the use case identifier
     * @return list of available footprints
     */
    public List<String> findAvailableFootprints(String useCaseId) {
        return findAllCandidates(useCaseId).stream()
                .map(BaselineCandidate::footprint)
                .distinct()
                .toList();
    }

    private void scanRoot(TaggedRoot root, String sanitizedUseCaseId,
                           String expectedFootprint, List<BaselineCandidate> candidates) {
        if (!Files.isDirectory(root.path())) {
            return;
        }

        try (Stream<Path> files = Files.list(root.path())) {
            files.filter(this::isYamlFile)
                 .filter(path -> matchesUseCaseId(path, sanitizedUseCaseId))
                 .forEach(path -> loadCandidate(path, expectedFootprint, root.source(), candidates));
        } catch (IOException e) {
            logger.warn("Warning: Failed to scan specs directory: {}", e.getMessage());
        }
    }

    private boolean isYamlFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    private boolean matchesUseCaseId(Path path, String sanitizedUseCaseId) {
        String filename = path.getFileName().toString();
        return filename.startsWith(sanitizedUseCaseId + ".") ||
               filename.startsWith(sanitizedUseCaseId + "-");
    }

    private void loadCandidate(Path path, String expectedFootprint,
                                BaselineSource source, List<BaselineCandidate> candidates) {
        try {
            ExecutionSpecification spec = SpecificationLoader.load(path);

            String footprint = spec.getFootprint();
            CovariateProfile profile = spec.getCovariateProfile();

            if (footprint == null || footprint.isEmpty()) {
                if (expectedFootprint == null) {
                    candidates.add(new BaselineCandidate(
                            path.getFileName().toString(),
                            "",
                            profile != null ? profile : CovariateProfile.empty(),
                            spec.getGeneratedAt(),
                            spec,
                            source
                    ));
                }
                return;
            }

            if (expectedFootprint != null && !expectedFootprint.equals(footprint)) {
                return;
            }

            candidates.add(new BaselineCandidate(
                    path.getFileName().toString(),
                    footprint,
                    profile != null ? profile : CovariateProfile.empty(),
                    spec.getGeneratedAt(),
                    spec,
                    source
            ));

        } catch (Exception e) {
            logger.warn("Warning: Failed to load baseline {}: {}", path, e.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Returns the first specs root directory (primary root).
     */
    public Path getSpecsRoot() {
        return roots.getFirst().path();
    }

    /**
     * Returns all configured roots with their sources (for testing).
     */
    List<Path> getRoots() {
        return roots.stream().map(TaggedRoot::path).toList();
    }
}
