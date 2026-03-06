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
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.registry.SpecificationLoader;

/**
 * Repository for finding and loading baseline specification files.
 *
 * <p>This repository scans the specs directory for all baseline files
 * matching a use case and filters by footprint.
 */
public final class BaselineRepository {

    private static final Logger logger = LogManager.getLogger(BaselineRepository.class);

    private final Path specsRoot;

    /**
     * Creates a repository with the specified specs root directory.
     *
     * @param specsRoot the root directory for specifications
     */
    public BaselineRepository(Path specsRoot) {
        this.specsRoot = Objects.requireNonNull(specsRoot, "specsRoot must not be null");
    }

    /**
     * Creates a repository with the default specs root.
     *
     * <p>Default: {@code src/test/resources/punit/specs}
     */
    public BaselineRepository() {
        this(detectDefaultSpecsRoot());
    }

    private static Path detectDefaultSpecsRoot() {
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
     * <p>This method:
     * <ol>
     *   <li>Scans the specs directory for files starting with the use case ID</li>
     *   <li>Loads each file and extracts its footprint</li>
     *   <li>Filters to only those matching the expected footprint</li>
     *   <li>Returns a list of candidates for selection</li>
     * </ol>
     *
     * @param useCaseId the use case identifier
     * @param expectedFootprint the footprint to match (null matches any)
     * @return list of matching baseline candidates (may be empty)
     */
    public List<BaselineCandidate> findCandidates(String useCaseId, String expectedFootprint) {
        Objects.requireNonNull(useCaseId, "useCaseId must not be null");

        List<BaselineCandidate> candidates = new ArrayList<>();

        if (!Files.isDirectory(specsRoot)) {
            return candidates;
        }

        // Sanitize useCaseId for file matching
        String sanitizedUseCaseId = sanitize(useCaseId);

        try (Stream<Path> files = Files.list(specsRoot)) {
            files.filter(this::isYamlFile)
                 .filter(path -> matchesUseCaseId(path, sanitizedUseCaseId))
                 .forEach(path -> loadCandidate(path, expectedFootprint, candidates));
        } catch (IOException e) {
            // Log warning but return empty list
            logger.warn("Warning: Failed to scan specs directory: {}", e.getMessage());
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

    private boolean isYamlFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    private boolean matchesUseCaseId(Path path, String sanitizedUseCaseId) {
        String filename = path.getFileName().toString();
        // Files can be:
        // 1. Simple: {useCaseId}.yaml
        // 2. With footprint: {useCaseId}-{footprintHash}.yaml
        // 3. With footprint and covariates: {useCaseId}-{footprintHash}-{covHash1}-{covHash2}.yaml
        return filename.startsWith(sanitizedUseCaseId + ".") ||
               filename.startsWith(sanitizedUseCaseId + "-");
    }

    private void loadCandidate(Path path, String expectedFootprint, List<BaselineCandidate> candidates) {
        try {
            ExecutionSpecification spec = SpecificationLoader.load(path);
            
            String footprint = spec.getFootprint();
            CovariateProfile profile = spec.getCovariateProfile();
            
            // If no footprint in spec, skip (legacy spec)
            if (footprint == null || footprint.isEmpty()) {
                // For backward compatibility, allow loading legacy specs without footprint
                // when no footprint filter is specified
                if (expectedFootprint == null) {
                    candidates.add(new BaselineCandidate(
                            path.getFileName().toString(),
                            "",
                            profile != null ? profile : CovariateProfile.empty(),
                            spec.getGeneratedAt(),
                            spec
                    ));
                }
                return;
            }
            
            // Filter by footprint if specified
            if (expectedFootprint != null && !expectedFootprint.equals(footprint)) {
                return;
            }
            
            candidates.add(new BaselineCandidate(
                    path.getFileName().toString(),
                    footprint,
                    profile != null ? profile : CovariateProfile.empty(),
                    spec.getGeneratedAt(),
                    spec
            ));
            
        } catch (Exception e) {
            // Skip files that fail to load
            logger.warn("Warning: Failed to load baseline {}: {}", path, e.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Returns the specs root directory.
     */
    public Path getSpecsRoot() {
        return specsRoot;
    }
}

