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
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;

/**
 * The set of covariates declared by a use case.
 *
 * <p>A covariate declaration captures which covariates are relevant for a use case:
 * <ul>
 *   <li>Standard covariates (from {@link StandardCovariate}) — have built-in categories</li>
 *   <li>Custom covariates — user-defined with explicit categories</li>
 * </ul>
 *
 * <p><strong>Every covariate has a category.</strong> There are no uncategorized covariates.
 *
 * <p>The declaration is used for:
 * <ul>
 *   <li>Footprint computation (covariate names contribute to the footprint)</li>
 *   <li>Covariate resolution (which covariates to capture during experiments)</li>
 *   <li>Baseline selection (matching declarations must match)</li>
 *   <li>Category-aware matching (CONFIGURATION = hard gate, others = soft match)</li>
 * </ul>
 *
 * @param standardCovariates the standard covariates in declaration order
 * @param customCovariates map of custom covariate key to category (all have explicit categories)
 */
public record CovariateDeclaration(
        List<StandardCovariate> standardCovariates,
        Map<String, CovariateCategory> customCovariates
) {

    /** An empty covariate declaration. */
    public static final CovariateDeclaration EMPTY = new CovariateDeclaration(List.of(), Map.of());

    public CovariateDeclaration {
        standardCovariates = List.copyOf(standardCovariates);
        // Use LinkedHashMap to preserve insertion order, then wrap in unmodifiable map
        customCovariates = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(customCovariates));
    }

    /**
     * Returns all covariate keys in declaration order (standard first, then custom).
     *
     * @return list of all covariate keys
     */
    public List<String> allKeys() {
        var keys = new ArrayList<String>();
        standardCovariates.forEach(sc -> keys.add(sc.key()));
        keys.addAll(customCovariates.keySet());
        return keys;
    }

    /**
     * Computes a stable hash of the covariate declaration (names only).
     *
     * <p>This hash contributes to the invocation footprint. It ensures that
     * baselines are only matched to tests with identical covariate declarations.
     *
     * @return 8-character hex hash, or empty string if no covariates declared
     */
    public String computeDeclarationHash() {
        if (isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (String key : allKeys()) {
            sb.append(key).append("\n");
        }

        return truncateHash(sha256(sb.toString()));
    }

    /**
     * Returns true if no covariates are declared.
     *
     * @return true if both standard and custom collections are empty
     */
    public boolean isEmpty() {
        return standardCovariates.isEmpty() && customCovariates.isEmpty();
    }

    /**
     * Returns the total number of declared covariates.
     *
     * @return count of all covariates
     */
    public int size() {
        return standardCovariates.size() + customCovariates.size();
    }

    /**
     * Returns the category for a covariate key.
     *
     * <p>Every covariate in this declaration has a category:
     * <ul>
     *   <li>Standard covariates get their category from the enum</li>
     *   <li>Custom covariates have explicit categories in the map</li>
     * </ul>
     *
     * @param key the covariate key
     * @return the category
     * @throws IllegalArgumentException if the key is not in this declaration
     */
    public CovariateCategory getCategory(String key) {
        // Check standard covariates
        for (StandardCovariate sc : standardCovariates) {
            if (sc.key().equals(key)) {
                return sc.category();
            }
        }
        
        // Check custom covariates
        if (customCovariates.containsKey(key)) {
            return customCovariates.get(key);
        }

        throw new IllegalArgumentException(
            "Covariate '" + key + "' is not declared. Declared covariates: " + allKeys());
    }

    /**
     * Returns true if this declaration contains the given covariate key.
     *
     * @param key the covariate key
     * @return true if declared
     */
    public boolean contains(String key) {
        for (StandardCovariate sc : standardCovariates) {
            if (sc.key().equals(key)) {
                return true;
            }
        }
        return customCovariates.containsKey(key);
    }

    /**
     * Returns all custom covariate keys.
     *
     * @return list of custom keys
     */
    public List<String> allCustomKeys() {
        return new ArrayList<>(customCovariates.keySet());
    }

    /**
     * Creates a declaration with standard covariates only.
     *
     * @param standard array of standard covariates
     * @return the covariate declaration
     */
    public static CovariateDeclaration of(StandardCovariate[] standard) {
        Objects.requireNonNull(standard, "standard must not be null");
        
        if (standard.length == 0) {
            return EMPTY;
        }
        
        return new CovariateDeclaration(List.of(standard), Map.of());
    }

    /**
     * Creates a declaration with standard and custom covariates.
     *
     * <p>Every custom covariate must have an explicit category in the map.
     *
     * @param standard array of standard covariates
     * @param custom map of custom covariate key to category
     * @return the covariate declaration
     */
    public static CovariateDeclaration of(
            StandardCovariate[] standard, 
            Map<String, CovariateCategory> custom) {
        Objects.requireNonNull(standard, "standard must not be null");
        Objects.requireNonNull(custom, "custom must not be null");
        
        if (standard.length == 0 && custom.isEmpty()) {
            return EMPTY;
        }
        
        return new CovariateDeclaration(List.of(standard), custom);
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
}

