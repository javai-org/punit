package org.javai.punit.spec.baseline.covariate;

/**
 * Thrown when covariate extraction encounters invalid declarations.
 *
 * <p>Examples include overlapping day groups, invalid time period formats,
 * unrecognized region codes, or duplicate covariate keys.
 */
public class CovariateValidationException extends RuntimeException {

    public CovariateValidationException(String message) {
        super(message);
    }
}
