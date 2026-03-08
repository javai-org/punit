package org.javai.punit.sentinel.testsubjects;

import java.util.stream.Stream;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.Sentinel;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * A sentinel spec with a measure experiment method for testing the experiment executor.
 */
@Sentinel
public class ExperimentSentinel {

    UseCaseFactory factory = new UseCaseFactory();
    {
        factory.register(StubUseCase.class, StubUseCase::new);
    }

    @MeasureExperiment(useCase = StubUseCase.class, samples = 5)
    @InputSource("inputs")
    void measureStub(StubUseCase useCase, OutcomeCaptor captor) {
        captor.record(useCase.execute("test-input"));
    }

    static Stream<String> inputs() {
        return Stream.of("a", "b", "c");
    }
}
