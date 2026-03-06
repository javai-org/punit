package org.javai.punit.spec.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Registry for loading and resolving execution specifications.
 *
 * <p>Specifications are loaded from the filesystem and cached.
 *
 * <h2>Spec ID Format</h2>
 * <p>Spec IDs are simply the use case ID (e.g., "ShoppingUseCase").
 * Specs are stored as flat files: {@code punit/specs/{useCaseId}.yaml}
 */
public class SpecificationRegistry {

	private final Path specsRoot;
	private final Map<String, ExecutionSpecification> cache = new ConcurrentHashMap<>();

	/**
	 * Creates a registry with the specified specs root directory.
	 *
	 * @param specsRoot the root directory for specifications
	 */
	public SpecificationRegistry(Path specsRoot) {
		this.specsRoot = Objects.requireNonNull(specsRoot, "specsRoot must not be null");
	}

	/**
	 * Creates a registry with the default specs root.
	 *
	 * <p>Default: {@code src/test/resources/punit/specs}
	 */
	public SpecificationRegistry() {
		this(detectDefaultSpecsRoot());
	}

	private static Path detectDefaultSpecsRoot() {
		// Try common locations
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

		// Return default even if it doesn't exist yet
		return candidates[0];
	}

	/**
	 * Resolves a specification by its ID.
	 *
	 * @param specId the specification ID (the use case ID, e.g., "ShoppingUseCase")
	 * @return the loaded specification
	 * @throws SpecificationNotFoundException if not found
	 * @throws org.javai.punit.spec.model.SpecificationValidationException if invalid
	 */
	public ExecutionSpecification resolve(String specId) {
		Objects.requireNonNull(specId, "specId must not be null");
		// Strip any legacy version suffix (e.g., ":v1") for backwards compatibility
		String useCaseId = stripVersionSuffix(specId);
		return cache.computeIfAbsent(useCaseId, this::loadSpec);
	}

	private String stripVersionSuffix(String specId) {
		int colonIndex = specId.lastIndexOf(':');
		if (colonIndex > 0 && specId.substring(colonIndex + 1).startsWith("v")) {
			return specId.substring(0, colonIndex);
		}
		return specId;
	}

	private ExecutionSpecification loadSpec(String useCaseId) {
		Optional<Path> specPathOpt = resolveSpecPath(useCaseId);

		if (specPathOpt.isEmpty()) {
			throw new SpecificationNotFoundException(
					"Specification not found: " + useCaseId +
							" (tried " + useCaseId + ".yaml/.yml in " + specsRoot + ")");
		}

		try {
			ExecutionSpecification spec = SpecificationLoader.load(specPathOpt.get());
			spec.validate();
			return spec;
		} catch (IOException e) {
			throw new SpecificationLoadException(
					"Failed to load specification: " + useCaseId, e);
		}
	}

	private Optional<Path> resolveSpecPath(String useCaseId) {
		// Flat structure: specs/{useCaseId}.yaml
		Path yamlPath = specsRoot.resolve(useCaseId + ".yaml");
		if (Files.exists(yamlPath)) return Optional.of(yamlPath);

		Path ymlPath = specsRoot.resolve(useCaseId + ".yml");
		if (Files.exists(ymlPath)) return Optional.of(ymlPath);

		return Optional.empty();
	}

	/**
	 * Returns true if a specification with the given ID exists.
	 *
	 * @param specId the specification ID
	 * @return true if the spec exists
	 */
	public boolean exists(String specId) {
		if (specId == null) return false;

		String useCaseId = stripVersionSuffix(specId);
		if (cache.containsKey(useCaseId)) return true;

		return resolveSpecPath(useCaseId).isPresent();
	}

	/**
	 * Clears the specification cache.
	 */
	public void clearCache() {
		cache.clear();
	}

}

