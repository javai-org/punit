package org.javai.punit.ptest.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.spec.baseline.BaselineRepository;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.spec.baseline.BaselineSelector;
import org.javai.punit.spec.baseline.FootprintComputer;
import org.javai.punit.spec.baseline.NoCompatibleBaselineException;
import org.javai.punit.spec.baseline.covariate.CovariateProfileResolver;
import org.javai.punit.spec.baseline.covariate.DefaultCovariateResolutionContext;
import org.javai.punit.spec.baseline.covariate.UseCaseCovariateExtractor;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Orchestrates baseline selection for spec-driven probabilistic tests.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Preparing baseline selection by computing footprints and finding candidates</li>
 *   <li>Performing covariate-aware baseline selection</li>
 *   <li>Logging selection results with appropriate warnings for mismatches</li>
 *   <li>Finding UseCaseProvider instances from test classes</li>
 * </ul>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class BaselineSelectionOrchestrator {

    private final ConfigurationResolver configResolver;
    private final BaselineRepository baselineRepository;
    private final BaselineSelector baselineSelector;
    private final CovariateProfileResolver covariateProfileResolver;
    private final FootprintComputer footprintComputer;
    private final UseCaseCovariateExtractor covariateExtractor;
    private final PUnitReporter reporter;

    /**
     * Pending baseline selection data, stored between prepare and perform phases.
     */
    record PendingSelection(
            String specId,
            Class<?> useCaseClass,
            CovariateDeclaration declaration,
            String footprint,
            List<BaselineCandidate> candidates
    ) {
        PendingSelection {
            Objects.requireNonNull(specId, "specId must not be null");
            Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");
            Objects.requireNonNull(declaration, "declaration must not be null");
            Objects.requireNonNull(footprint, "footprint must not be null");
            Objects.requireNonNull(candidates, "candidates must not be null");
        }
    }

    /**
     * Result of preparing baseline selection.
     *
     * @param pending the pending selection data (null if no selection needed)
     * @param spec the loaded spec (null if pending selection exists, or no spec)
     */
    record PreparationResult(
            PendingSelection pending,
            ExecutionSpecification spec
    ) {
        static PreparationResult ofPending(PendingSelection pending) {
            return new PreparationResult(pending, null);
        }

        static PreparationResult ofSpec(ExecutionSpecification spec) {
            return new PreparationResult(null, spec);
        }

        static PreparationResult none() {
            return new PreparationResult(null, null);
        }

        boolean hasPending() {
            return pending != null;
        }

        boolean hasSpec() {
            return spec != null;
        }
    }

    BaselineSelectionOrchestrator(
            ConfigurationResolver configResolver,
            BaselineRepository baselineRepository,
            BaselineSelector baselineSelector,
            CovariateProfileResolver covariateProfileResolver,
            FootprintComputer footprintComputer,
            UseCaseCovariateExtractor covariateExtractor,
            PUnitReporter reporter) {
        this.configResolver = configResolver;
        this.baselineRepository = baselineRepository;
        this.baselineSelector = baselineSelector;
        this.covariateProfileResolver = covariateProfileResolver;
        this.footprintComputer = footprintComputer;
        this.covariateExtractor = covariateExtractor;
        this.reporter = reporter;
    }

    /**
     * Prepares baseline selection by computing footprints and finding candidates.
     *
     * <p>This is the "prepare" phase that happens during test initialization.
     * The actual selection is deferred to {@link #performSelection} for lazy resolution.
     *
     * @param annotation the test annotation
     * @param specId the resolved spec ID (may be null)
     * @return the preparation result
     */
    PreparationResult prepareSelection(ProbabilisticTest annotation, String specId) {
        if (specId == null) {
            return PreparationResult.none();
        }

        // If minPassRate is explicitly set, skip covariate-aware selection
        double annotationMinPassRate = annotation.minPassRate();
        if (!Double.isNaN(annotationMinPassRate)) {
            // Inline threshold mode - just load the spec for metadata
            ExecutionSpecification spec = configResolver.loadSpec(specId).orElse(null);
            return spec != null ? PreparationResult.ofSpec(spec) : PreparationResult.none();
        }

        // Get the use case class from annotation
        Class<?> useCaseClass = annotation.useCase();
        if (useCaseClass == null || useCaseClass == Void.class) {
            // No use case - fall back to simple loading
            ExecutionSpecification spec = configResolver.loadSpec(specId).orElse(null);
            return spec != null ? PreparationResult.ofSpec(spec) : PreparationResult.none();
        }

        // Extract covariate declaration
        CovariateDeclaration declaration = covariateExtractor.extractDeclaration(useCaseClass);

        if (declaration.isEmpty()) {
            // No covariates declared - fall back to simple loading
            ExecutionSpecification spec = configResolver.loadSpec(specId).orElse(null);
            return spec != null ? PreparationResult.ofSpec(spec) : PreparationResult.none();
        }

        // Compute the test's footprint
        String footprint = footprintComputer.computeFootprint(specId, declaration);

        // Find baseline candidates with matching footprint
        List<BaselineCandidate> candidates = baselineRepository.findCandidates(specId, footprint);

        // Return pending selection for lazy resolution
        return PreparationResult.ofPending(new PendingSelection(
                specId,
                useCaseClass,
                declaration,
                footprint,
                candidates
        ));
    }

    /**
     * Performs the actual baseline selection using covariate-aware matching.
     *
     * @param pending the pending selection data
     * @param useCaseInstance the use case instance for covariate resolution (may be null)
     * @return the selection result
     * @throws NoCompatibleBaselineException if no compatible baseline is found
     */
    SelectionResult performSelection(PendingSelection pending, Object useCaseInstance) {
        if (pending.candidates().isEmpty()) {
            List<String> availableFootprints = baselineRepository.findAvailableFootprints(pending.specId());
            throw new NoCompatibleBaselineException(pending.specId(), pending.footprint(), availableFootprints);
        }

        // Resolve the test's current covariate profile
        DefaultCovariateResolutionContext resolutionContext = DefaultCovariateResolutionContext.builder()
                .useCaseInstance(useCaseInstance)
                .build();
        CovariateProfile testProfile = covariateProfileResolver.resolve(pending.declaration(), resolutionContext);

        // Select the best-matching baseline (category-aware)
        SelectionResult result = baselineSelector.select(pending.candidates(), testProfile, pending.declaration());

        if (!result.hasSelection()) {
            List<String> availableFootprints = baselineRepository.findAvailableFootprints(pending.specId());
            throw new NoCompatibleBaselineException(pending.specId(), pending.footprint(), availableFootprints);
        }

        return result;
    }

    /**
     * Logs the baseline selection result with appropriate title based on match quality.
     *
     * @param result the selection result
     * @param specId the spec ID for display
     */
    void logSelectionResult(SelectionResult result, String specId) {
        String title = "BASELINE FOUND FOR USE CASE: " + specId;

        StringBuilder sb = new StringBuilder();
        sb.append(PUnitReporter.labelValue("Baseline file:", result.selected().filename()));

        // Add non-conformance details if present
        var nonConforming = result.nonConformingDetails();
        if (!nonConforming.isEmpty()) {
            sb.append("\n\nPlease note, the following covariates do not match the baseline:\n");
            for (var detail : nonConforming) {
                sb.append("  - ").append(detail.covariateKey());
                sb.append(": baseline=").append(detail.baselineValue().toCanonicalString());
                sb.append(", test=").append(detail.testValue().toCanonicalString());
                sb.append("\n");
            }
            sb.append("\nStatistical comparison may be less reliable.\n");
            sb.append("Consider running a new MEASURE experiment under current conditions.");
        }

        // Add ambiguity note if applicable
        if (result.ambiguous()) {
            if (!nonConforming.isEmpty()) {
                sb.append("\n");
            } else {
                sb.append("\n\n");
            }
            sb.append("Note: Multiple equally-suitable baselines existed. Selection may be non-deterministic.");
        }

        boolean isApproximate = result.hasNonConformance() || result.ambiguous();
        if (isApproximate) {
            reporter.reportWarn(title, sb.toString());
        } else {
            reporter.reportInfo(title, sb.toString());
        }
    }

    /**
     * Finds a UseCaseProvider from the test instance or class.
     *
     * @param testInstance the test instance (may be null)
     * @param testClass the test class (may be null)
     * @return the UseCaseProvider if found
     */
    Optional<UseCaseProvider> findUseCaseProvider(Object testInstance, Class<?> testClass) {
        // Prefer instance fields (test instance exists during lazy selection)
        if (testInstance != null) {
            for (Field field : testInstance.getClass().getDeclaredFields()) {
                if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        UseCaseProvider provider = (UseCaseProvider) field.get(testInstance);
                        if (provider != null) {
                            return Optional.of(provider);
                        }
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
        }

        // Fall back to static fields on the test class
        if (testClass != null) {
            for (Field field : testClass.getDeclaredFields()) {
                if (UseCaseProvider.class.isAssignableFrom(field.getType())
                        && Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    try {
                        UseCaseProvider provider = (UseCaseProvider) field.get(null);
                        if (provider != null) {
                            return Optional.of(provider);
                        }
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves the use case instance from a provider.
     *
     * @param provider the UseCaseProvider
     * @param useCaseClass the use case class
     * @return the use case instance, or null if not available
     */
    Object resolveUseCaseInstance(UseCaseProvider provider, Class<?> useCaseClass) {
        if (provider == null || !provider.isRegistered(useCaseClass)) {
            return null;
        }
        try {
            return provider.getInstance(useCaseClass);
        } catch (IllegalStateException e) {
            // Factory not registered - covariate resolution will use fallback
            return null;
        }
    }
}

