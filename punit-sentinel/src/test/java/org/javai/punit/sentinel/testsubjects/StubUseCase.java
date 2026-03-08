package org.javai.punit.sentinel.testsubjects;

import org.javai.punit.api.UseCase;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;

/**
 * A deterministic use case for testing the Sentinel runner.
 *
 * <p>Always succeeds — postcondition always passes.
 */
@UseCase("stub-use-case")
public class StubUseCase {

    private static final ServiceContract<String, String> CONTRACT =
            ServiceContract.<String, String>define()
                    .ensure("non-null result", result -> org.javai.outcome.Outcome.ok())
                    .build();

    public UseCaseOutcome<String> execute(String input) {
        return UseCaseOutcome.<String, String>withContract(CONTRACT)
                .input(input)
                .execute(i -> "OK: " + i)
                .build();
    }
}
