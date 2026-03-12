package org.javai.punit.sentinel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.javai.punit.reporting.CompositeVerdictSink;
import org.javai.punit.reporting.LogVerdictSink;
import org.javai.punit.verdict.VerdictSink;
import org.javai.punit.spec.registry.LayeredSpecRepository;
import org.javai.punit.spec.registry.SpecRepository;

/**
 * Configuration for a {@code SentinelRunner} execution.
 *
 * <p>Built via a fluent builder pattern. The only required element is at least
 * one {@code @Sentinel}-annotated class.
 *
 * <pre>{@code
 * SentinelConfiguration config = SentinelConfiguration.builder()
 *     .sentinelClass(ShoppingBasketReliability.class)
 *     .verdictSink(new WebhookVerdictSink("https://alerts.example.com/punit"))
 *     .environmentMetadata(EnvironmentMetadata.fromEnvironment())
 *     .build();
 * }</pre>
 */
public final class SentinelConfiguration {

    private final List<Class<?>> sentinelClasses;
    private final SpecRepository specRepository;
    private final VerdictSink verdictSink;
    private final EnvironmentMetadata environmentMetadata;

    private SentinelConfiguration(Builder builder) {
        if (builder.sentinelClasses.isEmpty()) {
            throw new IllegalArgumentException("At least one @Sentinel class must be registered");
        }
        this.sentinelClasses = List.copyOf(builder.sentinelClasses);
        this.specRepository = builder.specRepository != null
                ? builder.specRepository
                : LayeredSpecRepository.createDefault();
        this.verdictSink = buildVerdictSink(builder.verdictSinks);
        this.environmentMetadata = builder.environmentMetadata != null
                ? builder.environmentMetadata
                : EnvironmentMetadata.fromEnvironment();
    }

    public List<Class<?>> sentinelClasses() {
        return sentinelClasses;
    }

    public SpecRepository specRepository() {
        return specRepository;
    }

    public VerdictSink verdictSink() {
        return verdictSink;
    }

    public EnvironmentMetadata environmentMetadata() {
        return environmentMetadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static VerdictSink buildVerdictSink(List<VerdictSink> sinks) {
        if (sinks.isEmpty()) {
            return new LogVerdictSink();
        }
        if (sinks.size() == 1) {
            return sinks.getFirst();
        }
        return new CompositeVerdictSink(sinks);
    }

    public static final class Builder {

        private final List<Class<?>> sentinelClasses = new ArrayList<>();
        private final List<VerdictSink> verdictSinks = new ArrayList<>();
        private SpecRepository specRepository;
        private EnvironmentMetadata environmentMetadata;

        private Builder() {}

        /**
         * Registers a {@code @Sentinel}-annotated class for execution.
         */
        public Builder sentinelClass(Class<?> sentinelClass) {
            Objects.requireNonNull(sentinelClass, "sentinelClass must not be null");
            this.sentinelClasses.add(sentinelClass);
            return this;
        }

        /**
         * Registers multiple {@code @Sentinel}-annotated classes.
         */
        public Builder sentinelClasses(List<Class<?>> classes) {
            Objects.requireNonNull(classes, "classes must not be null");
            this.sentinelClasses.addAll(classes);
            return this;
        }

        /**
         * Sets the spec repository for baseline resolution.
         * Defaults to {@link LayeredSpecRepository#createDefault()}.
         */
        public Builder specRepository(SpecRepository specRepository) {
            this.specRepository = specRepository;
            return this;
        }

        /**
         * Adds a verdict sink. Multiple sinks are composed automatically.
         * Defaults to {@link LogVerdictSink} if none are added.
         */
        public Builder verdictSink(VerdictSink sink) {
            Objects.requireNonNull(sink, "sink must not be null");
            this.verdictSinks.add(sink);
            return this;
        }

        /**
         * Sets the environment metadata attached to every verdict.
         * Defaults to {@link EnvironmentMetadata#fromEnvironment()}.
         */
        public Builder environmentMetadata(EnvironmentMetadata metadata) {
            this.environmentMetadata = metadata;
            return this;
        }

        public SentinelConfiguration build() {
            return new SentinelConfiguration(this);
        }
    }
}
