# Pacing Implementation Plan

This document tracks the implementation phases for the pacing feature.

**Design Document**: [DESIGN-PACING.md](DESIGN-PACING.md)

---

## Phase 1: Sequential Pacing (MVP) ✅ COMPLETE

- [x] Add `@Pacing` annotation
- [x] Implement `PacingCalculator`
- [x] Implement `PacingResolver` (resolves from annotation/env/props)
- [x] Implement `PacingConfiguration` record
- [x] Implement `PacingReporter` for pre-flight output
- [x] Extend `ProbabilisticTestExtension` to resolve pacing
- [x] Add inter-sample delay to execution loop
- [x] Add pre-flight report output
- [x] Add feasibility warnings
- [x] Unit tests for `PacingCalculator` (25 tests)
- [x] Unit tests for `PacingResolver` (19 tests)
- [x] Unit tests for `PacingConfiguration` (17 tests)
- [x] Unit tests for `PacingReporter` (16 tests)
- [x] Integration tests for pacing in test execution (3 tests)

---

## Phase 2: Concurrent Execution

### Execution Model: Ordered Fork-Join

Concurrent sample execution follows an **ordered fork-join** pattern:

1. **Fork**: Samples are dispatched to workers in sequential order (sample 1, 2, 3, ...)
2. **Execute**: Workers process samples concurrently (completion order is non-deterministic)
3. **Join**: Results are collected and ordered by **start order**, not completion order
4. **Final result**: A deterministic sequence `[result₁, result₂, result₃, ...]` regardless of completion timing

This ensures predictable, reproducible output even with non-deterministic completion times.

### Design Decisions

| Question | Decision | Rationale |
|----------|----------|-----------|
| **Early termination with in-flight samples** | Abandon immediately | Faster response; no waiting for potentially slow API calls that won't affect verdict |
| **IDE display order** | Show results as they arrive | Natural feedback; developers see progress in real-time |
| **Token recording** | Global concern (see note) | Token budgets span tests, experiments, and concurrent samples |
| **Budget check timing** | Ongoing via global monitor | Continuous checking, not just at sample completion |

### Prerequisite: Global Token Monitoring

Token charges are a **global concern**, not local to a sample or even a single test. The current `TokenChargeRecorder` design needs revisiting to support:

- Cross-sample aggregation within a test
- Cross-test aggregation within a class (existing `SharedBudgetMonitor`)
- Cross-class aggregation within a suite (existing `SuiteBudgetManager`)
- Concurrent sample execution (thread-safe global accumulator)

A **global monitoring singleton** should:
- Track token consumption across all concurrent samples
- Perform ongoing budget checks (not just at sample boundaries)
- Trigger early termination across all workers when budget exhausted

This is a broader design consideration that should be addressed before implementing concurrent execution.

### Implementation Tasks

- [ ] Design global monitoring singleton for time/token budgets
- [ ] Implement worker pool for concurrent samples
- [ ] Implement ordered result collection (fork-join with start-order preservation)
- [ ] Staggered worker dispatch to maintain aggregate rate limits
- [ ] Thread-safe result aggregation
- [ ] Handle early termination with in-flight sample abandonment
- [ ] Real-time result display (completion order) with final ordering (start order)

---

## Phase 3: Adaptive Pacing (Future)

- [ ] Detect 429 responses and back off
- [ ] Learn average latency during execution
- [ ] Adjust pacing dynamically within constraints

---

## Phase 4: Documentation ✅ COMPLETE

- [x] Add pacing feature description to README.md
- [x] Add comprehensive pacing chapter to docs/USER-GUIDE.md

---

## Dependencies

| Phase | Depends On |
|-------|------------|
| Phase 1 | None |
| Phase 2 | Global monitoring singleton design |
| Phase 3 | Phase 2 |
| Phase 4 | Phase 1 |

---

*Last updated: Based on implementation status as of Phase 1 completion.*

