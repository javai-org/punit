PROVIDER DISCUSSION
===================

Please respond under each point. Feel free to edit or delete the wording.


P1: Duplication + Drift
----------------------
If the same use case is tested across multiple classes, each class must register the provider
consistently. Drift is easy and can silently fork baseline selection.

Your response: The baseline form would not be silent because, by design, it would lead to a new and independent baseline file or set of files.


P2: Shared DI Contexts
---------------------
In Spring/Guice apps, tests often share a common application context. Re-registering the same
provider in many test classes is repetitive and risks mismatched wiring.

Your response: Let's think about this more in the context of a classically configured test in a SpringBoot application.
We can declare a use case provider to be a @Component and subsequently inject this into the test. We might consider
providing an optional extension for PUnit offering such Spring (or Guice) support. So while we cannot prevent developers
from repeating code, we can reduce boilerplate, and offer a convenient solution out of the box.


P3: Multi-Use-Case Test Suites
------------------------------
Some teams organize tests by feature or SLA rather than use case. A single test class may cover
multiple use cases; provider setup can become noisy and harder to reason about.

Your response: Use Case in the context of PUnit is a very specific technical term. I don't believe developers will confuse it
with a feature or a use case as one might define one in a project planning context. And the use case can be organised any
way the developer pleases. But it raises an interesting question. Wih PUnit in play, we can envisage a project comprising the following:
- Main application code
- Use case code
- Traditional unit and integration test code
- Experiment code
- Probabilistic test code
- A second deployable artifact used to perform experiments in the same environment as the main application, but which is nevertheless deployed separately.
- A third deployable artifact used to perform probabilistic tests in the same environment as the main application... effectively integration tests which operate in a monitoring capacity.

I do not yet have an answer for how one should organise the code base and the build config for this scenario. I think it is worth considering. It certainly goes beyond what the standard gradle/maven build config does.
I would like to explore this further, and perhaps come up with a formula, which we can demonstrate within the confines of the PUnit project itself and its 'examples' of the shopping application.

P4: Baseline Selection Timing
-----------------------------
Covariate-aware selection requires the provider to be static and registered in @BeforeAll.
If we leave it to the test, we must be explicit about this constraint or selection may fail.

Your response: Selection MUST be covariate-aware. This is the whole point of the use case declaring covariates and the solution must be solid as a rock as well as architecturally sound.
I need to understand in detail what it is about the internal flow of the probabilistic test engine, that forces us to currently
register the provider in @BeforeAll. Help me understand this better. My gut feel is that their may be a way to tackle this
without calling upon the baseline file upfront, but lazily.


P5: Test Parallelism and Coupling
---------------------------------
Per-class providers are safe in parallel runs, but a shared static provider across classes
without a registry abstraction can introduce accidental cross-test coupling.

Your response: In documentation I intend to make a case for the stateless use case class; just as I would for a Spring/Guice component.
Nothing can prevent a developer from breaking this rule, but they will have been warned.

P6: Multi-Module Repos
---------------------
In multi-module repos, a use case may be tested from a different module. A global registry
helps reduce boilerplate and keeps config in one place.

Your response: I am happy to explore the global registry concept further, and adopt whatever best-practice approaches others have come up with.
But, again, the solution must be solid as a rock as well as architecturally sound. A singleton static instance is an outmoded pattern that I want to avoid.
 

