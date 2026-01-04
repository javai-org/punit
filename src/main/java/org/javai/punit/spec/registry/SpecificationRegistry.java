package org.javai.punit.spec.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Registry for loading and resolving execution specifications.
 *
 * <p>Specifications are loaded from the filesystem and cached.
 *
 * <h2>Spec ID Format</h2>
 * <p>Spec IDs use the format: {@code "useCaseId:vN"} (e.g., "usecase.json.gen:v3")
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
	 * @param specId the specification ID (format: "useCaseId:vN")
	 * @return the loaded specification
	 * @throws SpecificationNotFoundException if not found
	 * @throws org.javai.punit.spec.model.SpecificationValidationException if invalid
	 */
	public ExecutionSpecification resolve(String specId) {
		Objects.requireNonNull(specId, "specId must not be null");
		return cache.computeIfAbsent(specId, this::loadSpec);
	}

	private ExecutionSpecification loadSpec(String specId) {
		ParsedSpecId parsed = ParsedSpecId.parse(specId);
		Path specDir = specsRoot.resolve(parsed.useCaseId());

		Path specPath = resolveSpecPath(specDir, parsed.version());

		if (specPath == null) {
			throw new SpecificationNotFoundException(
					"Specification not found: " + specId +
							" (tried .yaml and .json in " + specDir + ")");
		}

		try {
			ExecutionSpecification spec = SpecificationLoader.load(specPath);
			spec.validate();
			return spec;
		} catch (IOException e) {
			throw new SpecificationLoadException(
					"Failed to load specification: " + specId, e);
		}
	}

	private Path resolveSpecPath(Path specDir, int version) {
		// YAML is preferred (default format)
		Path yamlPath = specDir.resolve("v" + version + ".yaml");
		if (Files.exists(yamlPath)) return yamlPath;

		Path ymlPath = specDir.resolve("v" + version + ".yml");
		if (Files.exists(ymlPath)) return ymlPath;

		// JSON as fallback
		Path jsonPath = specDir.resolve("v" + version + ".json");
		if (Files.exists(jsonPath)) return jsonPath;

		return null;
	}

	/**
	 * Returns true if a specification with the given ID exists.
	 *
	 * @param specId the specification ID
	 * @return true if the spec exists
	 */
	public boolean exists(String specId) {
		if (specId == null) return false;

		if (cache.containsKey(specId)) return true;

		try {
			ParsedSpecId parsed = ParsedSpecId.parse(specId);
			Path specDir = specsRoot.resolve(parsed.useCaseId());
			return resolveSpecPath(specDir, parsed.version()) != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Clears the specification cache.
	 */
	public void clearCache() {
		cache.clear();
	}

	/**
	 * Parsed specification ID.
	 */
	private record ParsedSpecId(String useCaseId, int version) {

		static ParsedSpecId parse(String specId) {
			int colonIndex = specId.lastIndexOf(':');
			if (colonIndex < 0 || !specId.substring(colonIndex + 1).startsWith("v")) {
				throw new IllegalArgumentException(
						"Invalid spec ID format: " + specId +
								". Expected: useCaseId:vN (e.g., usecase.json.gen:v3)");
			}
			String useCaseId = specId.substring(0, colonIndex);
			int version = Integer.parseInt(specId.substring(colonIndex + 2));
			return new ParsedSpecId(useCaseId, version);
		}
	}
}

