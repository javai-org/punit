package org.javai.punit.reporting;

import java.io.PrintStream;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;
import org.javai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that renders IDE-like verdict output to a {@link PrintStream}.
 *
 * <p>Produces the same structured output as the JUnit 5 test runner's console
 * summary, reconstructed from the structured {@link ProbabilisticTestVerdict}.
 *
 * <p>Designed for CLI use (Sentinel runtime) where the operator wants to see
 * each verdict as it happens, formatted the way they would appear in an IDE
 * test run.
 *
 * <p>Output uses the standard PUnit framing (header/footer dividers) and
 * label-value alignment provided by {@link PUnitReporter}.
 */
public final class ConsoleVerdictSink implements VerdictSink {

    private final PrintStream out;
    private final PUnitReporter reporter;

    public ConsoleVerdictSink() {
        this(System.out);
    }

    public ConsoleVerdictSink(PrintStream out) {
        this.out = out;
        this.reporter = new PUnitReporter();
    }

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        Termination term = verdict.termination();
        boolean passed = verdict.junitPassed();

        String title = "VERDICT: " + (passed ? "PASS" : "FAIL");
        String testName = verdict.identity().className() + "." + verdict.identity().methodName();

        StringBuilder sb = new StringBuilder();
        sb.append(testName).append("\n\n");

        boolean isBudgetExhausted = term.reason().isBudgetExhaustion();

        if (passed) {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) >= required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            RateFormat.format(exec.minPassRate()))));
        } else if (isBudgetExhausted) {
            sb.append(PUnitReporter.labelValueLn("Samples executed:",
                    String.format("%d of %d (budget exhausted)",
                            exec.samplesExecuted(), exec.plannedSamples())));
            sb.append(PUnitReporter.labelValueLn("Pass rate:",
                    String.format("%s (%d/%d), required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            RateFormat.format(exec.minPassRate()))));
        } else {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) < required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            RateFormat.format(exec.minPassRate()))));
        }

        // Per-dimension breakdown (when both dimensions are asserted)
        appendDimensionBreakdown(sb, verdict);

        // Termination details
        if (term.reason() != TerminationReason.COMPLETED) {
            sb.append(PUnitReporter.labelValueLn("Termination:", term.reason().getDescription()));
        }

        sb.append(PUnitReporter.labelValue("Elapsed:", exec.elapsedMs() + "ms"));

        out.println(reporter.headerDivider(title));
        out.println();
        // Indent the body (consistent with PUnitReporter.format)
        for (String line : sb.toString().split("\n")) {
            out.println("  " + line);
        }
        out.println(reporter.footerDivider());
        out.println();
    }

    private void appendDimensionBreakdown(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        if (verdict.functional().isEmpty() || verdict.latency().isEmpty()
                || verdict.latency().get().skipped()) {
            return;
        }

        FunctionalDimension func = verdict.functional().get();
        LatencyDimension lat = verdict.latency().get();

        sb.append(PUnitReporter.labelValueLn("Contract:",
                String.format("%d/%d passed",
                        func.successes(), func.successes() + func.failures())));
        sb.append(PUnitReporter.labelValueLn("Latency:",
                String.format("%d/%d within limit",
                        lat.dimensionSuccesses(), lat.dimensionSuccesses() + lat.dimensionFailures())));
    }
}
