package org.javai.punit.experiment.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.javai.punit.api.AdaptiveFactor;
import org.javai.punit.experiment.spi.RefinementStrategy;

/**
 * Builder for adaptive factor levels with flexible initial level sourcing.
 *
 * <h2>Example: Static Initial Level</h2>
 * <pre>{@code
 * AdaptiveLevels.<String>builder()
 *     .startingFrom("You are a helpful assistant...")
 *     .refinedBy(refinementStrategy)
 *     .maxIterations(10)
 *     .build()
 * }</pre>
 *
 * <h2>Example: Supplier-Based Initial Level</h2>
 * <pre>{@code
 * AdaptiveLevels.<String>builder()
 *     .startingFrom(() -> promptFactory.buildPrompt(config))
 *     .refinedBy(refinementStrategy)
 *     .maxIterations(10)
 *     .build()
 * }</pre>
 *
 * @param <T> the type of the level
 */
public final class AdaptiveLevels<T> {

	private final String name;
	private final Supplier<T> initialLevelSupplier;
	private final RefinementStrategy<T> strategy;
	private final int maxIterations;

	private AdaptiveLevels(Builder<T> builder) {
		this.name = Objects.requireNonNull(builder.name, "name must not be null");
		this.initialLevelSupplier = Objects.requireNonNull(builder.initialLevelSupplier,
				"initialLevelSupplier must not be null");
		this.strategy = Objects.requireNonNull(builder.strategy, "strategy must not be null");
		this.maxIterations = builder.maxIterations;

		if (maxIterations <= 0) {
			throw new IllegalArgumentException("maxIterations must be > 0");
		}
	}

	/**
	 * Creates a new builder.
	 *
	 * @param <T> the level type
	 * @return a new builder
	 */
	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	/**
	 * Creates an AdaptiveFactor from this configuration.
	 *
	 * @return the adaptive factor
	 */
	public AdaptiveFactor<T> toFactor() {
		return new DefaultAdaptiveFactor<>(name, initialLevelSupplier, strategy, maxIterations);
	}

	public String getName() {
		return name;
	}

	public RefinementStrategy<T> getStrategy() {
		return strategy;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	/**
	 * Builder for AdaptiveLevels.
	 *
	 * @param <T> the level type
	 */
	public static final class Builder<T> {

		private String name;
		private Supplier<T> initialLevelSupplier;
		private RefinementStrategy<T> strategy;
		private int maxIterations = 10;

		private Builder() {
		}

		/**
		 * Sets the factor name.
		 *
		 * @param name the factor name
		 * @return this builder
		 */
		public Builder<T> name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Provides a static initial level value.
		 *
		 * @param initialValue the initial level
		 * @return this builder
		 */
		public Builder<T> startingFrom(T initialValue) {
			Objects.requireNonNull(initialValue, "initialValue must not be null");
			this.initialLevelSupplier = () -> initialValue;
			return this;
		}

		/**
		 * Provides a Supplier that produces the initial level.
		 *
		 * <p>The Supplier is invoked once at experiment start. Use this when:
		 * <ul>
		 *   <li>The initial level is constructed by production code</li>
		 *   <li>The initial level depends on application context</li>
		 *   <li>You want to test the actual prompt construction logic</li>
		 * </ul>
		 *
		 * @param initialValueSupplier the supplier for the initial level
		 * @return this builder
		 */
		public Builder<T> startingFrom(Supplier<T> initialValueSupplier) {
			this.initialLevelSupplier = Objects.requireNonNull(initialValueSupplier,
					"initialValueSupplier must not be null");
			return this;
		}

		/**
		 * Sets the refinement strategy.
		 *
		 * @param strategy the refinement strategy
		 * @return this builder
		 */
		public Builder<T> refinedBy(RefinementStrategy<T> strategy) {
			this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
			return this;
		}

		/**
		 * Sets the maximum number of iterations.
		 *
		 * <p>This is a mandatory safety bound.
		 *
		 * @param maxIterations the maximum iterations
		 * @return this builder
		 */
		public Builder<T> maxIterations(int maxIterations) {
			if (maxIterations <= 0) {
				throw new IllegalArgumentException("maxIterations must be > 0");
			}
			this.maxIterations = maxIterations;
			return this;
		}

		/**
		 * Builds the AdaptiveLevels configuration.
		 *
		 * @return the configuration
		 */
		public AdaptiveLevels<T> build() {
			return new AdaptiveLevels<>(this);
		}
	}

	/**
	 * Default implementation of AdaptiveFactor.
	 */
	private static final class DefaultAdaptiveFactor<T> implements AdaptiveFactor<T> {

		private final String name;
		private final Supplier<T> initialLevelSupplier;
		private final RefinementStrategy<T> strategy;
		private final int maxIterations;
		private T currentLevel;
		private boolean initialized = false;

		DefaultAdaptiveFactor(String name, Supplier<T> initialLevelSupplier,
							  RefinementStrategy<T> strategy, int maxIterations) {
			this.name = name;
			this.initialLevelSupplier = initialLevelSupplier;
			this.strategy = strategy;
			this.maxIterations = maxIterations;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public T initialLevel() {
			if (!initialized) {
				currentLevel = initialLevelSupplier.get();
				initialized = true;
			}
			return currentLevel;
		}

		@Override
		public Optional<T> refine(IterationFeedback feedback) {
			return strategy.refine(currentLevel, feedback);
		}

		@Override
		public int maxIterations() {
			return maxIterations;
		}

		@Override
		public T currentLevel() {
			if (!initialized) {
				return initialLevel();
			}
			return currentLevel;
		}

		@Override
		public void setCurrentLevel(T level) {
			this.currentLevel = level;
			this.initialized = true;
		}
	}
}

