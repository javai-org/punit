package org.javai.punit.experiment.spi;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for experiment backends, discovered via ServiceLoader.
 */
public final class ExperimentBackendRegistry {

	private static final Map<String, ExperimentBackend> backends = new ConcurrentHashMap<>();
	private static volatile boolean initialized = false;

	private ExperimentBackendRegistry() {
	}

	/**
	 * Initializes the registry by discovering backends via ServiceLoader.
	 */
	public static synchronized void initialize() {
		if (initialized) {
			return;
		}

		// Register the generic backend first
		register(new GenericExperimentBackend());

		// Discover and register backends via ServiceLoader
		ServiceLoader<ExperimentBackend> loader = ServiceLoader.load(ExperimentBackend.class);
		for (ExperimentBackend backend : loader) {
			register(backend);
		}

		initialized = true;
	}

	/**
	 * Registers a backend.
	 *
	 * @param backend the backend to register
	 */
	public static void register(ExperimentBackend backend) {
		if (backend != null) {
			backends.put(backend.getId(), backend);
		}
	}

	/**
	 * Resolves a backend by ID.
	 *
	 * @param id the backend ID
	 * @return an Optional containing the backend, or empty if not found
	 */
	public static Optional<ExperimentBackend> resolve(String id) {
		if (!initialized) {
			initialize();
		}
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(backends.get(id));
	}

	/**
	 * Returns the generic (default) backend.
	 *
	 * @return the generic backend
	 */
	public static ExperimentBackend getGenericBackend() {
		if (!initialized) {
			initialize();
		}
		return backends.get("generic");
	}

	/**
	 * Returns true if a backend with the given ID is registered.
	 *
	 * @param id the backend ID
	 * @return true if registered
	 */
	public static boolean contains(String id) {
		if (!initialized) {
			initialize();
		}
		return id != null && backends.containsKey(id);
	}

	/**
	 * Clears all registered backends.
	 *
	 * <p>Primarily for testing purposes.
	 */
	public static synchronized void clear() {
		backends.clear();
		initialized = false;
	}
}

