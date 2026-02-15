package org.javai.punit.gradle

import org.gradle.api.provider.Property

/**
 * DSL extension for configuring PUnit experiment tasks.
 *
 * ```kotlin
 * punit {
 *     specsDir.set("src/test/resources/punit/specs")
 *     explorationsDir.set("src/test/resources/punit/explorations")
 *     optimizationsDir.set("src/test/resources/punit/optimizations")
 *     configureTestTask.set(true)
 *     excludeTestSubjects.set(true)
 * }
 * ```
 */
abstract class PunitExperimentExtension {

    /** Output directory for MEASURE specs. Default: `src/test/resources/punit/specs` */
    abstract val specsDir: Property<String>

    /** Output directory for EXPLORE results. Default: `src/test/resources/punit/explorations` */
    abstract val explorationsDir: Property<String>

    /** Output directory for OPTIMIZE results. Default: `src/test/resources/punit/optimizations` */
    abstract val optimizationsDir: Property<String>

    /** Whether to configure the `test` task (exclude experiment tags, forward properties, etc.). Default: `true` */
    abstract val configureTestTask: Property<Boolean>

    /** Whether to exclude testsubjects directories from test and experiment tasks. Default: `true` */
    abstract val excludeTestSubjects: Property<Boolean>
}
