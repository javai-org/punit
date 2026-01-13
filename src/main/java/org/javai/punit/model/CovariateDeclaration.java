package org.javai.punit.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.StandardCovariate;

/**
 * The set of covariates declared by a use case.
 *
 * <p>A covariate declaration captures which covariates are relevant for a use case,
 * both standard (from {@link StandardCovariate}) and custom (user-defined strings).
 *
 * <p>The declaration is used for:
 * <ul>
 *   <li>Footprint computation (covariate names contribute to the footprint)</li>
 *   <li>Covariate resolution (which covariates to capture during experiments)</li>
 *   <li>Baseline selection (matching declarations must match)</li>
 * </ul>
 *
 * @param standardCovariates the standard covariates in declaration order
 * @param customCovariates the custom covariate keys in declaration order
 */
public record CovariateDeclaration(
        List<StandardCovariate> standardCovariates,
        List<String> customCovariates
) {

    /** An empty covariate declaration. */
    public static final CovariateDeclaration EMPTY = new CovariateDeclaration(List.of(), List.of());

    public CovariateDeclaration {
        standardCovariates = List.copyOf(standardCovariates);
        customCovariates = List.copyOf(customCovariates);
    }

    /**
     * Returns all covariate keys in declaration order (standard first, then custom).
     *
     * @return list of all covariate keys
     */
    public List<String> allKeys() {
        var keys = new ArrayList<String>();
        standardCovariates.forEach(sc -> keys.add(sc.key()));
        keys.addAll(customCovariates);
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
     * @return true if both standard and custom lists are empty
     */
    public boolean isEmpty() {
        return standardCovariates.isEmpty() && customCovariates.isEmpty();
    }

    /**
     * Returns the total number of declared covariates.
     *
     * @return count of standard plus custom covariates
     */
    public int size() {
        return standardCovariates.size() + customCovariates.size();
    }

    /**
     * Creates a declaration from arrays (convenience for annotation processing).
     *
     * @param standard array of standard covariates
     * @param custom array of custom covariate keys
     * @return the covariate declaration
     */
    public static CovariateDeclaration of(StandardCovariate[] standard, String[] custom) {
        Objects.requireNonNull(standard, "standard must not be null");
        Objects.requireNonNull(custom, "custom must not be null");
        
        if (standard.length == 0 && custom.length == 0) {
            return EMPTY;
        }
        
        return new CovariateDeclaration(List.of(standard), List.of(custom));
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

