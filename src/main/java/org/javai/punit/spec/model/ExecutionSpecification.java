package org.javai.punit.spec.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A human-reviewed and approved contract derived from empirical baselines.
 *
 * <p>Specifications define:
 * <ul>
 *   <li>Expected minimum pass rate</li>
 *   <li>Execution context configuration</li>
 *   <li>Cost envelopes (max time, max tokens)</li>
 *   <li>Approval metadata (who, when, why)</li>
 * </ul>
 *
 * <p>Specifications are normative (what should happen), unlike baselines
 * which are descriptive (what did happen).
 */
public final class ExecutionSpecification {

	private final String specId;
	private final String useCaseId;
	private final int version;
	private final Instant approvedAt;
	private final String approvedBy;
	private final String approvalNotes;
	private final List<String> sourceBaselines;
	private final Map<String, Object> executionContext;
	private final SpecRequirements requirements;
	private final CostEnvelope costEnvelope;

	private ExecutionSpecification(Builder builder) {
		this.specId = Objects.requireNonNull(builder.specId, "specId must not be null");
		this.useCaseId = Objects.requireNonNull(builder.useCaseId, "useCaseId must not be null");
		this.version = builder.version;
		this.approvedAt = builder.approvedAt;
		this.approvedBy = builder.approvedBy;
		this.approvalNotes = builder.approvalNotes;
		this.sourceBaselines = builder.sourceBaselines != null
				? List.copyOf(builder.sourceBaselines)
				: Collections.emptyList();
		this.executionContext = builder.executionContext != null
				? Collections.unmodifiableMap(new LinkedHashMap<>(builder.executionContext))
				: Collections.emptyMap();
		this.requirements = builder.requirements != null
				? builder.requirements
				: new SpecRequirements(1.0, "");
		this.costEnvelope = builder.costEnvelope;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getSpecId() {
		return specId;
	}

	public String getUseCaseId() {
		return useCaseId;
	}

	public int getVersion() {
		return version;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public String getApprovalNotes() {
		return approvalNotes;
	}

	public List<String> getSourceBaselines() {
		return sourceBaselines;
	}

	public Map<String, Object> getExecutionContext() {
		return executionContext;
	}

	public SpecRequirements getRequirements() {
		return requirements;
	}

	public CostEnvelope getCostEnvelope() {
		return costEnvelope;
	}

	/**
	 * Returns true if this specification has valid approval metadata.
	 *
	 * @return true if approved
	 */
	public boolean isApproved() {
		return approvedAt != null && approvedBy != null && !approvedBy.isEmpty();
	}

	/**
	 * Returns the minimum pass rate from requirements.
	 *
	 * @return the minimum pass rate
	 */
	public double getMinPassRate() {
		return requirements.minPassRate();
	}

	/**
	 * Returns the success criteria from requirements.
	 *
	 * @return the success criteria
	 */
	public SuccessCriteria getSuccessCriteria() {
		String criteria = requirements.successCriteria();
		if (criteria == null || criteria.isEmpty()) {
			return SuccessCriteria.alwaysTrue();
		}
		return SuccessCriteria.parse(criteria);
	}

	/**
	 * Validates this specification.
	 *
	 * @throws SpecificationValidationException if validation fails
	 */
	public void validate() throws SpecificationValidationException {
		if (!isApproved()) {
			throw new SpecificationValidationException(
					"Specification '" + specId + "' lacks approval metadata. " +
							"Add 'approvedAt', 'approvedBy', and 'approvalNotes' to the specification file.");
		}
		if (requirements.minPassRate() < 0.0 || requirements.minPassRate() > 1.0) {
			throw new SpecificationValidationException(
					"Specification '" + specId + "' has invalid minPassRate: " + requirements.minPassRate());
		}
	}

	/**
	 * Specification requirements.
	 */
	public record SpecRequirements(double minPassRate, String successCriteria) {
	}

	/**
	 * Cost envelope for resource limits.
	 */
	public record CostEnvelope(long maxTimePerSampleMs, long maxTokensPerSample, long totalTokenBudget) {
	}

	public static final class Builder {

		private String specId;
		private String useCaseId;
		private int version = 1;
		private Instant approvedAt;
		private String approvedBy;
		private String approvalNotes;
		private List<String> sourceBaselines;
		private Map<String, Object> executionContext;
		private SpecRequirements requirements;
		private CostEnvelope costEnvelope;

		private Builder() {
		}

		public Builder specId(String specId) {
			this.specId = specId;
			return this;
		}

		public Builder useCaseId(String useCaseId) {
			this.useCaseId = useCaseId;
			return this;
		}

		public Builder version(int version) {
			this.version = version;
			return this;
		}

		public Builder approvedAt(Instant approvedAt) {
			this.approvedAt = approvedAt;
			return this;
		}

		public Builder approvedBy(String approvedBy) {
			this.approvedBy = approvedBy;
			return this;
		}

		public Builder approvalNotes(String approvalNotes) {
			this.approvalNotes = approvalNotes;
			return this;
		}

		public Builder sourceBaselines(List<String> sourceBaselines) {
			this.sourceBaselines = sourceBaselines;
			return this;
		}

		public Builder executionContext(Map<String, Object> executionContext) {
			this.executionContext = executionContext;
			return this;
		}

		public Builder requirements(SpecRequirements requirements) {
			this.requirements = requirements;
			return this;
		}

		public Builder requirements(double minPassRate, String successCriteria) {
			this.requirements = new SpecRequirements(minPassRate, successCriteria);
			return this;
		}

		public Builder costEnvelope(CostEnvelope costEnvelope) {
			this.costEnvelope = costEnvelope;
			return this;
		}

		public Builder costEnvelope(long maxTimePerSampleMs, long maxTokensPerSample, long totalTokenBudget) {
			this.costEnvelope = new CostEnvelope(maxTimePerSampleMs, maxTokensPerSample, totalTokenBudget);
			return this;
		}

		public ExecutionSpecification build() {
			return new ExecutionSpecification(this);
		}
	}
}

