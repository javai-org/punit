package org.javai.punit.experiment.engine;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry for named Suppliers used in YAML-based adaptive factor definitions.
 *
 * <p>When an adaptive factor is defined in YAML with an {@code initialLevelSupplier}
 * reference (e.g., "enterprise-support-prompt-supplier"), the framework looks up
 * the corresponding Supplier in this registry.
 *
 * <h2>Example Registration</h2>
 * <pre>{@code
 * AdaptiveFactorRegistry.registerSupplier(
 *     "enterprise-support-prompt-supplier",
 *     () -> {
 *         UserContext ctx = UserContext.builder()
 *             .tier(CustomerTier.ENTERPRISE)
 *             .region(Region.EU)
 *             .build();
 *         return customerSupportPromptBuilder.build(ctx);
 *     }
 * );
 * }</pre>
 *
 * <h2>YAML Reference</h2>
 * <pre>{@code
 * adaptiveFactors:
 *   - name: systemPrompt
 *     initialLevelSupplier: "enterprise-support-prompt-supplier"
 *     refinementStrategy: llm-based
 *     maxIterations: 8
 * }</pre>
 */
public final class AdaptiveFactorRegistry {

	private static final Map<String, Supplier<?>> suppliers = new ConcurrentHashMap<>();

	private AdaptiveFactorRegistry() {
		// Static utility class
	}

	/**
	 * Registers a named Supplier for use in YAML-based adaptive factor definitions.
	 *
	 * @param <T> the type of value the supplier provides
	 * @param name the supplier name (used as reference in YAML)
	 * @param supplier the supplier
	 */
	public static <T> void registerSupplier(String name, Supplier<T> supplier) {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(supplier, "supplier must not be null");
		suppliers.put(name, supplier);
	}

	/**
	 * Resolves a named Supplier.
	 *
	 * @param <T> the expected type
	 * @param name the supplier name
	 * @return an Optional containing the supplier, or empty if not found
	 */
	@SuppressWarnings("unchecked")
	public static <T> Optional<Supplier<T>> resolve(String name) {
		if (name == null) {
			return Optional.empty();
		}
		Supplier<?> supplier = suppliers.get(name);
		return Optional.ofNullable((Supplier<T>) supplier);
	}

	/**
	 * Resolves and invokes a named Supplier, returning its value.
	 *
	 * @param <T> the expected type
	 * @param name the supplier name
	 * @return an Optional containing the supplied value, or empty if not found
	 */
	public static <T> Optional<T> resolveAndGet(String name) {
		Optional<Supplier<T>> supplier = resolve(name);
		return supplier.map(Supplier::get);
	}

	/**
	 * Removes a registered Supplier.
	 *
	 * @param name the supplier name
	 * @return true if a supplier was removed
	 */
	public static boolean unregister(String name) {
		return name != null && suppliers.remove(name) != null;
	}

	/**
	 * Clears all registered Suppliers.
	 */
	public static void clear() {
		suppliers.clear();
	}

	/**
	 * Returns true if a Supplier with the given name is registered.
	 *
	 * @param name the supplier name
	 * @return true if registered
	 */
	public static boolean contains(String name) {
		return name != null && suppliers.containsKey(name);
	}
}

