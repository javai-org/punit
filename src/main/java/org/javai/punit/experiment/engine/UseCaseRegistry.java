package org.javai.punit.experiment.engine;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.javai.punit.experiment.api.UseCase;

/**
 * Registry for discovering and caching use case definitions.
 *
 * <p>The registry maintains a cache of discovered use cases, keyed by their use case ID.
 * It supports:
 * <ul>
 *   <li>Explicit registration of use case methods</li>
 *   <li>Lookup by use case ID</li>
 *   <li>Classpath scanning for {@link UseCase}-annotated methods (future enhancement)</li>
 * </ul>
 *
 * <p>This class is thread-safe.
 */
public class UseCaseRegistry {
    
    private final Map<String, UseCaseDefinition> registry = new ConcurrentHashMap<>();
    
    /**
     * Creates an empty registry.
     */
    public UseCaseRegistry() {
    }
    
    /**
     * Registers a use case method.
     *
     * @param instance the object instance containing the use case method (null for static methods)
     * @param method the use case method (must be annotated with {@link UseCase})
     * @throws IllegalArgumentException if the method is not annotated with {@link UseCase}
     * @throws IllegalStateException if a use case with the same ID is already registered
     */
    public void register(Object instance, Method method) {
        Objects.requireNonNull(method, "method must not be null");
        
        UseCase annotation = method.getAnnotation(UseCase.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Method " + method.getName() + " is not annotated with @UseCase");
        }
        
        String useCaseId = annotation.value();
        UseCaseDefinition definition = new UseCaseDefinition(useCaseId, annotation.description(), instance, method);
        
        UseCaseDefinition existing = registry.putIfAbsent(useCaseId, definition);
        if (existing != null) {
            throw new IllegalStateException(
                "Use case '" + useCaseId + "' is already registered. " +
                "Existing: " + existing.method() + ", New: " + method);
        }
    }
    
    /**
     * Registers all use case methods from the given object.
     *
     * <p>Scans the object's class (and superclasses) for methods annotated with
     * {@link UseCase} and registers them.
     *
     * @param instance the object instance to scan
     */
    public void registerAll(Object instance) {
        Objects.requireNonNull(instance, "instance must not be null");
        
        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(UseCase.class)) {
                    register(instance, method);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
    
    /**
     * Resolves a use case by its ID.
     *
     * @param useCaseId the use case ID
     * @return an Optional containing the use case definition, or empty if not found
     */
    public Optional<UseCaseDefinition> resolve(String useCaseId) {
        Objects.requireNonNull(useCaseId, "useCaseId must not be null");
        return Optional.ofNullable(registry.get(useCaseId));
    }
    
    /**
     * Returns true if a use case with the given ID is registered.
     *
     * @param useCaseId the use case ID
     * @return true if registered
     */
    public boolean contains(String useCaseId) {
        return useCaseId != null && registry.containsKey(useCaseId);
    }
    
    /**
     * Removes a use case from the registry.
     *
     * @param useCaseId the use case ID to remove
     * @return the removed definition, or null if not found
     */
    public UseCaseDefinition unregister(String useCaseId) {
        if (useCaseId == null) {
            return null;
        }
        return registry.remove(useCaseId);
    }
    
    /**
     * Clears all registered use cases.
     */
    public void clear() {
        registry.clear();
    }
    
    /**
     * Returns the number of registered use cases.
     *
     * @return the count of registered use cases
     */
    public int size() {
        return registry.size();
    }
}

