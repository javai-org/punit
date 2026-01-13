package org.javai.punit.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record of covariate values captured during baseline creation
 * or resolved at test execution time.
 *
 * <p>A covariate profile captures the environmental conditions under which
 * an experiment was conducted or a test is being executed. This enables
 * matching baselines to compatible test contexts.
 *
 * <p>The profile preserves covariate ordering (declaration order), which
 * is significant for tie-breaking in baseline selection.
 */
public final class CovariateProfile {

    /** Sentinel value for unresolved custom covariates. */
    public static final String NOT_SET = "not_set";

    private final Map<String, CovariateValue> values;
    private final List<String> orderedKeys;

    private CovariateProfile(Map<String, CovariateValue> values, List<String> orderedKeys) {
        this.values = Map.copyOf(values);
        this.orderedKeys = List.copyOf(orderedKeys);
    }

    /**
     * Returns an empty covariate profile.
     *
     * @return an empty profile
     */
    public static CovariateProfile empty() {
        return new CovariateProfile(Map.of(), List.of());
    }

    /**
     * Returns covariate keys in declaration order.
     *
     * @return unmodifiable list of keys in order
     */
    public List<String> orderedKeys() {
        return orderedKeys;
    }

    /**
     * Returns the value for the given covariate key.
     *
     * @param key the covariate key
     * @return the value, or null if not present
     */
    public CovariateValue get(String key) {
        return values.get(key);
    }

    /**
     * Returns all covariate values as an unmodifiable map.
     *
     * @return unmodifiable map of keys to values
     */
    public Map<String, CovariateValue> asMap() {
        return values;
    }

    /**
     * Returns true if this profile has no covariates.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Returns the number of covariates in this profile.
     *
     * @return the count
     */
    public int size() {
        return values.size();
    }

    /**
     * Computes a stable hash of this profile for use in filenames.
     *
     * <p>The hash is computed from the canonical string representations
     * of all covariate values, in declaration order.
     *
     * @return 8-character hex hash
     */
    public String computeHash() {
        if (values.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (String key : orderedKeys) {
            var value = values.get(key);
            sb.append(key).append("=").append(value.toCanonicalString()).append("\n");
        }

        return truncateHash(sha256(sb.toString()));
    }

    /**
     * Computes individual hashes for each covariate value, in declaration order.
     *
     * <p>These hashes are used in baseline filename construction.
     *
     * @return list of 4-character hex hashes
     */
    public List<String> computeValueHashes() {
        var hashes = new ArrayList<String>();
        for (String key : orderedKeys) {
            var value = values.get(key);
            var hash = sha256(key + "=" + value.toCanonicalString());
            hashes.add(hash.substring(0, Math.min(4, hash.length())));
        }
        return hashes;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String truncateHash(String hash) {
        return hash.substring(0, Math.min(8, hash.length()));
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CovariateProfile that = (CovariateProfile) o;
        return Objects.equals(values, that.values) && Objects.equals(orderedKeys, that.orderedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, orderedKeys);
    }

    @Override
    public String toString() {
        return "CovariateProfile{" + values + "}";
    }

    /**
     * Builder for {@link CovariateProfile}.
     */
    public static class Builder {
        private final Map<String, CovariateValue> values = new LinkedHashMap<>();
        private final List<String> orderedKeys = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a covariate value.
         *
         * @param key the covariate key
         * @param value the covariate value
         * @return this builder
         */
        public Builder put(String key, CovariateValue value) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(value, "value must not be null");
            
            if (!orderedKeys.contains(key)) {
                orderedKeys.add(key);
            }
            values.put(key, value);
            return this;
        }

        /**
         * Adds a string covariate value.
         *
         * @param key the covariate key
         * @param value the string value
         * @return this builder
         */
        public Builder put(String key, String value) {
            return put(key, new CovariateValue.StringValue(value));
        }

        /**
         * Builds the covariate profile.
         *
         * @return the immutable profile
         */
        public CovariateProfile build() {
            return new CovariateProfile(values, orderedKeys);
        }
    }
}

