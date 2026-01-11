package org.javai.punit.experiment.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.UseCaseResult;

/**
 * Represents a discovered use case definition.
 *
 * <p>A use case definition encapsulates:
 * <ul>
 *   <li>The use case ID</li>
 *   <li>The description (if provided)</li>
 *   <li>The object instance containing the method</li>
 *   <li>The method itself</li>
 * </ul>
 *
 * <p>This class supports invoking the use case method with appropriate parameters.
 */
public record UseCaseDefinition(String useCaseId, String description, Object instance, Method method) {

	/**
	 * Creates a new use case definition.
	 *
	 * @param useCaseId the use case ID
	 * @param description the use case description
	 * @param instance the object instance (null for static methods)
	 * @param method the use case method
	 */
	public UseCaseDefinition(String useCaseId, String description, Object instance, Method method) {
		this.useCaseId = Objects.requireNonNull(useCaseId, "useCaseId must not be null");
		this.description = description != null ? description : "";
		this.instance = instance;
		this.method = Objects.requireNonNull(method, "method must not be null");

		// Make method accessible if needed
		if (!method.canAccess(instance)) {
			method.setAccessible(true);
		}
	}

	/**
	 * Returns the use case ID.
	 *
	 * @return the use case ID
	 */
	@Override
	public String useCaseId() {
		return useCaseId;
	}

	/**
	 * Returns the use case description.
	 *
	 * @return the description, or empty string if none provided
	 */
	@Override
	public String description() {
		return description;
	}

	/**
	 * Returns the object instance containing the use case method.
	 *
	 * @return the instance, or null for static methods
	 */
	@Override
	public Object instance() {
		return instance;
	}

	/**
	 * Returns the use case method.
	 *
	 * @return the method
	 */
	@Override
	public Method method() {
		return method;
	}

	/**
	 * Invokes the use case method with the given context and input arguments.
	 *
	 * <p>The method will attempt to match parameters by type:
	 * <ul>
	 *   <li>{@link UseCaseContext} - injected from the context parameter</li>
	 *   <li>Other types - taken from the inputArgs array in order</li>
	 * </ul>
	 *
	 * @param context the use case context
	 * @param inputArgs additional input arguments for the use case
	 * @return the use case result
	 * @throws UseCaseInvocationException if the invocation fails
	 */
	public UseCaseResult invoke(UseCaseContext context, Object... inputArgs) {
		Objects.requireNonNull(context, "context must not be null");

		Instant startTime = Instant.now();

		try {
			Object[] methodArgs = buildMethodArguments(context, inputArgs);
			Object result = method.invoke(instance, methodArgs);

			Duration executionTime = Duration.between(startTime, Instant.now());

			if (result instanceof UseCaseResult ucResult) {
				// If the method returned a UseCaseResult, enhance it with execution time if not set
				if (ucResult.executionTime().isZero()) {
					return UseCaseResult.builder()
							.valuesFrom(ucResult)
							.metadataFrom(ucResult)
							.executionTime(executionTime)
							.timestamp(ucResult.timestamp())
							.build();
				}
				return ucResult;
			} else if (result == null) {
				throw new UseCaseInvocationException(
						"Use case method '" + useCaseId + "' returned null. " +
								"Use case methods must return a UseCaseResult.");
			} else {
				throw new UseCaseInvocationException(
						"Use case method '" + useCaseId + "' returned " + result.getClass().getName() +
								", expected UseCaseResult.");
			}

		} catch (IllegalAccessException e) {
			throw new UseCaseInvocationException(
					"Cannot access use case method '" + useCaseId + "'", e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new UseCaseInvocationException(
					"Use case method '" + useCaseId + "' threw an exception", cause);
		}
	}

	private Object[] buildMethodArguments(UseCaseContext context, Object[] inputArgs) {
		Parameter[] parameters = method.getParameters();
		Object[] methodArgs = new Object[parameters.length];
		int inputArgIndex = 0;

		for (int i = 0; i < parameters.length; i++) {
			Class<?> paramType = parameters[i].getType();

			if (UseCaseContext.class.isAssignableFrom(paramType)) {
				methodArgs[i] = context;
			} else if (inputArgs != null && inputArgIndex < inputArgs.length) {
				methodArgs[i] = inputArgs[inputArgIndex++];
			} else {
				methodArgs[i] = null;
			}
		}

		return methodArgs;
	}

	@Override
	public String toString() {
		return "UseCaseDefinition{" +
				"useCaseId='" + useCaseId + '\'' +
				", method=" + method.getDeclaringClass().getSimpleName() + "." + method.getName() +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		UseCaseDefinition that = (UseCaseDefinition) o;
		return Objects.equals(useCaseId, that.useCaseId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(useCaseId);
	}
}

