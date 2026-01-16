# Gradle build config simplification

The gradle build config current defines two tasks where one should suffice.

There are two types of 'experiment' in PUnit> MEASURE and EXPLORE.

A developer must express which one to use. This is done using the mode property of the Experiment annotation.

For historical reasons I introduced two different gradle tasks to invoke these two different kinds of experiment, 
but it instead if saying

