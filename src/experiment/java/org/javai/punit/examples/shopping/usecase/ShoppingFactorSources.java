package org.javai.punit.examples.shopping.usecase;

import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.experiment.api.FactorArguments;

/**
 * Standalone factor source provider for shopping-related experiments.
 *
 * <p>This class demonstrates that factor sources can be defined separately from
 * both use cases and experiments. This pattern is useful when:
 * <ul>
 *   <li>Factor sources are shared across multiple experiments/tests</li>
 *   <li>Factor sources are complex and benefit from dedicated documentation</li>
 *   <li>Teams prefer separation of test data from test logic</li>
 * </ul>
 *
 * <h2>Three Patterns for Factor Source Location</h2>
 * <table>
 *   <tr>
 *     <th>Location</th>
 *     <th>Example</th>
 *     <th>When to Use</th>
 *   </tr>
 *   <tr>
 *     <td>Co-located with UseCase</td>
 *     <td>{@link ShoppingUseCase#standardProductQueries()}</td>
 *     <td>Recommended default: factors naturally belong with the use case</td>
 *   </tr>
 *   <tr>
 *     <td>Local to Experiment</td>
 *     <td>ShoppingExperiment.modelConfigurations()</td>
 *     <td>Exploration-specific factors not used elsewhere</td>
 *   </tr>
 *   <tr>
 *     <td>Standalone class</td>
 *     <td>{@link #specializedQueries()}</td>
 *     <td>Complex factors or cross-cutting concerns</td>
 *   </tr>
 * </table>
 *
 * <h2>Referencing Standalone Factor Sources</h2>
 * <pre>{@code
 * // From an experiment or test
 * @FactorSource("ShoppingFactorSources#specializedQueries")
 * void measureSpecialCases(...) { ... }
 *
 * // Or with full package path if class is in a different package
 * @FactorSource("org.javai.punit.examples.shopping.usecase.ShoppingFactorSources#specializedQueries")
 * void measureSpecialCases(...) { ... }
 * }</pre>
 *
 * @see ShoppingUseCase#standardProductQueries()
 */
public final class ShoppingFactorSources {

    private ShoppingFactorSources() {
        // Utility class - prevent instantiation
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPECIALIZED FACTOR SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Factor source for testing edge cases and specialized queries.
     *
     * <p>These queries test edge cases that might not appear in production
     * but are important for comprehensive coverage:
     * <ul>
     *   <li>Very short queries (single word)</li>
     *   <li>Long queries with multiple constraints</li>
     *   <li>Queries with special characters</li>
     *   <li>Queries with price constraints</li>
     * </ul>
     *
     * @return factor arguments for specialized query testing
     */
    public static List<FactorArguments> specializedQueries() {
        return FactorArguments.configurations()
            .names("query")
            // Single word queries
            .values("headphones")
            .values("laptop")
            // Complex queries with multiple terms
            .values("wireless noise cancelling over-ear headphones")
            .values("ergonomic split mechanical keyboard")
            // Queries with price context
            .values("budget laptop under $500")
            .values("premium headphones over $300")
            // Queries with brand mentions
            .values("Sony headphones wireless")
            .values("Apple accessories for iPhone")
            // Queries with feature requirements
            .values("waterproof bluetooth speaker")
            .values("USB-C hub with HDMI and ethernet")
            .stream().toList();
    }

    /**
     * Factor source with model configuration for cross-model comparison.
     *
     * <p>Unlike the explore configurations in ShoppingExperiment, these are
     * designed for targeted A/B comparisons between specific model configurations.
     *
     * @return factor arguments for model comparison testing
     */
    public static Stream<FactorArguments> modelComparisonFactors() {
        return FactorArguments.configurations()
            .names("model", "temp", "query")
            // GPT-4 baseline configurations
            .values("gpt-4", 0.0, "wireless headphones")
            .values("gpt-4", 0.0, "laptop stand")
            // GPT-4 Turbo comparison
            .values("gpt-4-turbo", 0.0, "wireless headphones")
            .values("gpt-4-turbo", 0.0, "laptop stand")
            // Temperature variation
            .values("gpt-4", 0.3, "wireless headphones")
            .values("gpt-4", 0.7, "wireless headphones")
            .stream();
    }

    /**
     * Factor source for stress testing with high-volume queries.
     *
     * <p>A large set of queries for load testing or when you need
     * high sample variety without repetition.
     *
     * @return factor arguments for stress testing
     */
    public static List<FactorArguments> highVolumeQueries() {
        return FactorArguments.configurations()
            .names("query")
            // Electronics - Computing
            .values("laptop 15 inch")
            .values("desktop computer gaming")
            .values("tablet android")
            .values("chromebook for students")
            .values("mac mini m2")
            // Electronics - Mobile
            .values("smartphone 5G")
            .values("phone case protective")
            .values("screen protector tempered glass")
            .values("wireless earbuds")
            .values("power bank 20000mah")
            // Electronics - Audio
            .values("soundbar for TV")
            .values("home theater system")
            .values("record player vintage")
            .values("smart speaker alexa")
            .values("podcast microphone USB")
            // Electronics - Wearables
            .values("smartwatch fitness")
            .values("fitness band heart rate")
            .values("VR headset")
            .values("smart glasses")
            .values("GPS running watch")
            // Accessories - Desk
            .values("monitor 4K 27 inch")
            .values("keyboard wireless compact")
            .values("mouse ergonomic vertical")
            .values("webcam 1080p")
            .values("desk mat leather")
            // Accessories - Storage
            .values("external SSD 1TB")
            .values("USB flash drive 256GB")
            .values("memory card 128GB")
            .values("NAS home server")
            .values("hard drive enclosure")
            .stream().toList();
    }
}

