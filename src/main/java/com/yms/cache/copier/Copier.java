package com.yms.cache.copier;

/**
 * Strategy interface for copying cached values.
 *
 * <p>JCache supports two modes:
 * <ul>
 *   <li><b>store-by-reference</b>: Uses {@link IdentityCopier}, returns same instance</li>
 *   <li><b>store-by-value</b>: Uses {@link SerializingCopier}, returns deep copy</li>
 * </ul>
 *
 * <p>This abstraction is internal and not exposed externally per design constraints.
 *
 * @param <T> the type of value to copy
 */
public interface Copier<T> {

    /**
     * Copies the given value according to the implementation strategy.
     *
     * @param value the value to copy (may be null)
     * @return the copied value, or null if input was null
     */
    T copy(T value);
}
