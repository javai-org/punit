package org.javai.punit.experiment.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSourceType;
import org.javai.punit.api.HashableFactorSource;

/**
 * Default implementation of {@link HashableFactorSource} that materializes
 * factors and computes a SHA-256 hash.
 *
 * <p>This implementation uses lazy initialization with caching:
 * <ul>
 *   <li>Factors are materialized on first access to either {@link #factors()}
 *       or {@link #getSourceHash()}</li>
 *   <li>The materialized list is cached for subsequent {@link #factors()} calls</li>
 *   <li>The computed hash is cached for subsequent {@link #getSourceHash()} calls</li>
 * </ul>
 *
 * <h2>Memory Trade-off</h2>
 * <p>This approach holds the full factor list in memory for the entire experiment
 * duration. For typical factor sources (dozens to hundreds of arguments), this is
 * negligible. For very large sources, consider configuration-based hash computation
 * or a custom {@link HashableFactorSource} implementation.
 */
public class DefaultHashableFactorSource implements HashableFactorSource {

    private final String sourceName;
    private final Supplier<Stream<FactorArguments>> factorSupplier;

    private List<FactorArguments> cachedFactors;
    private String cachedHash;

    /**
     * Creates a new DefaultHashableFactorSource.
     *
     * @param sourceName     the name of this factor source
     * @param factorSupplier a supplier that provides a stream of factor arguments
     */
    public DefaultHashableFactorSource(String sourceName, Supplier<Stream<FactorArguments>> factorSupplier) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName must not be null");
        this.factorSupplier = Objects.requireNonNull(factorSupplier, "factorSupplier must not be null");
    }

    /**
     * Creates a DefaultHashableFactorSource from a pre-materialized list.
     *
     * @param sourceName the name of this factor source
     * @param factors    the factor arguments list
     * @return a new HashableFactorSource
     */
    public static DefaultHashableFactorSource fromList(String sourceName, List<FactorArguments> factors) {
        Objects.requireNonNull(factors, "factors must not be null");
        DefaultHashableFactorSource source = new DefaultHashableFactorSource(sourceName, factors::stream);
        // Pre-populate cache since we already have the list
        source.cachedFactors = List.copyOf(factors);
        source.cachedHash = source.computeHash(source.cachedFactors);
        return source;
    }

    @Override
    public Stream<FactorArguments> factors() {
        ensureMaterialized();
        return cachedFactors.stream();
    }

    @Override
    public String getSourceHash() {
        ensureMaterialized();
        return cachedHash;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public FactorSourceType getSourceType() {
        return FactorSourceType.LIST_CYCLING;
    }

    /**
     * Returns the number of factors in this source.
     *
     * <p>This triggers materialization if not already done.
     *
     * @return the factor count
     */
    public int getFactorCount() {
        ensureMaterialized();
        return cachedFactors.size();
    }

    private synchronized void ensureMaterialized() {
        if (cachedFactors == null) {
            cachedFactors = factorSupplier.get().toList();
            cachedHash = computeHash(cachedFactors);
        }
    }

    private String computeHash(List<FactorArguments> factors) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (FactorArguments args : factors) {
                byte[] argBytes = args.toString().getBytes(StandardCharsets.UTF_8);
                digest.update(argBytes);
            }

            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "DefaultHashableFactorSource{" +
                "sourceName='" + sourceName + '\'' +
                ", hash='" + (cachedHash != null ? cachedHash.substring(0, 8) + "..." : "not computed") + '\'' +
                ", factorCount=" + (cachedFactors != null ? cachedFactors.size() : "not materialized") +
                '}';
    }
}

