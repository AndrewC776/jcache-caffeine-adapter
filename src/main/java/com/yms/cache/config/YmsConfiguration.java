package com.yms.cache.config;

import javax.cache.configuration.MutableConfiguration;
import java.util.function.ToLongBiFunction;

/**
 * Extended JCache configuration with Caffeine-specific options.
 *
 * <p>Extends {@link MutableConfiguration} to support:
 * <ul>
 *   <li>{@code maximumSize} - Maximum number of entries (Caffeine size-based eviction)</li>
 *   <li>{@code maximumWeight} - Maximum total weight (Caffeine weight-based eviction)</li>
 *   <li>{@code weigher} - Function to compute entry weight (required with maximumWeight)</li>
 * </ul>
 *
 * <p><b>Constraints:</b>
 * <ul>
 *   <li>{@code maximumSize} and {@code maximumWeight} are mutually exclusive</li>
 *   <li>{@code weigher} must be set when using {@code maximumWeight}</li>
 * </ul>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class YmsConfiguration<K, V> extends MutableConfiguration<K, V> {

    private static final long serialVersionUID = 1L;

    private Long maximumSize;
    private Long maximumWeight;
    private ToLongBiFunction<K, V> weigher;

    /**
     * Creates a new YmsConfiguration with default settings.
     */
    public YmsConfiguration() {
        super();
    }

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public YmsConfiguration(YmsConfiguration<K, V> other) {
        super(other);
        this.maximumSize = other.maximumSize;
        this.maximumWeight = other.maximumWeight;
        this.weigher = other.weigher;
    }

    /**
     * Returns the maximum number of entries, or null if not set.
     *
     * @return the maximum size, or null
     */
    public Long getMaximumSize() {
        return maximumSize;
    }

    /**
     * Sets the maximum number of entries.
     *
     * @param maximumSize the maximum size
     * @return this configuration for fluent chaining
     * @throws IllegalStateException if maximumWeight is already set
     */
    public YmsConfiguration<K, V> setMaximumSize(Long maximumSize) {
        if (this.maximumWeight != null) {
            throw new IllegalStateException(
                "Cannot set maximumSize when maximumWeight is already set");
        }
        this.maximumSize = maximumSize;
        return this;
    }

    /**
     * Returns the maximum total weight, or null if not set.
     *
     * @return the maximum weight, or null
     */
    public Long getMaximumWeight() {
        return maximumWeight;
    }

    /**
     * Sets the maximum total weight.
     *
     * @param maximumWeight the maximum weight
     * @return this configuration for fluent chaining
     * @throws IllegalStateException if maximumSize is already set
     */
    public YmsConfiguration<K, V> setMaximumWeight(Long maximumWeight) {
        if (this.maximumSize != null) {
            throw new IllegalStateException(
                "Cannot set maximumWeight when maximumSize is already set");
        }
        this.maximumWeight = maximumWeight;
        return this;
    }

    /**
     * Returns the weigher function, or null if not set.
     *
     * @return the weigher function, or null
     */
    public ToLongBiFunction<K, V> getWeigher() {
        return weigher;
    }

    /**
     * Sets the weigher function for weight-based eviction.
     *
     * @param weigher the weigher function
     * @return this configuration for fluent chaining
     */
    public YmsConfiguration<K, V> setWeigher(ToLongBiFunction<K, V> weigher) {
        this.weigher = weigher;
        return this;
    }

    /**
     * Validates this configuration.
     *
     * @throws IllegalStateException if maximumWeight is set without a weigher
     */
    public void validate() {
        if (maximumWeight != null && weigher == null) {
            throw new IllegalStateException(
                "Weigher must be configured when using maximumWeight");
        }
    }

    // Override parent methods to return YmsConfiguration for fluent chaining

    @Override
    public YmsConfiguration<K, V> setTypes(Class<K> keyType, Class<V> valueType) {
        super.setTypes(keyType, valueType);
        return this;
    }

    @Override
    public YmsConfiguration<K, V> setStoreByValue(boolean storeByValue) {
        super.setStoreByValue(storeByValue);
        return this;
    }
}
