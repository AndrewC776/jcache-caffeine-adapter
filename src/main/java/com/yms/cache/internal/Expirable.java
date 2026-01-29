package com.yms.cache.internal;

/**
 * Immutable wrapper that holds a cached value along with its absolute expiration time.
 *
 * <p>This class is the core data model for JCache entry-level expiration:
 * <ul>
 *   <li>{@code value} - the actual cached value (already copied if store-by-value)</li>
 *   <li>{@code expireTime} - absolute expiration timestamp in milliseconds</li>
 * </ul>
 *
 * <p>Expiration semantics:
 * <ul>
 *   <li>{@code Long.MAX_VALUE} - eternal, never expires</li>
 *   <li>Any other value - entry expires when {@code currentTimeMillis > expireTime}</li>
 * </ul>
 *
 * @param <V> the type of the cached value
 */
public final class Expirable<V> {

    private final V value;
    private final long expireTime;

    /**
     * Creates a new Expirable with the given value and expiration time.
     *
     * @param value the cached value
     * @param expireTime the absolute expiration time in milliseconds
     */
    public Expirable(V value, long expireTime) {
        this.value = value;
        this.expireTime = expireTime;
    }

    /**
     * Returns the cached value.
     *
     * @return the cached value
     */
    public V getValue() {
        return value;
    }

    /**
     * Returns the absolute expiration time.
     *
     * @return the expiration time in milliseconds
     */
    public long getExpireTime() {
        return expireTime;
    }

    /**
     * Checks if this entry has expired based on current system time.
     *
     * <p>An entry is expired when:
     * <ul>
     *   <li>expireTime is NOT {@code Long.MAX_VALUE} (eternal), AND</li>
     *   <li>current time is greater than expireTime</li>
     * </ul>
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expireTime != Long.MAX_VALUE && System.currentTimeMillis() > expireTime;
    }

    /**
     * Creates a new Expirable with the same value but different expiration time.
     * This method preserves immutability.
     *
     * @param newExpireTime the new expiration time
     * @return a new Expirable instance
     */
    public Expirable<V> withExpireTime(long newExpireTime) {
        return new Expirable<>(this.value, newExpireTime);
    }

    /**
     * Creates an eternal Expirable that never expires.
     *
     * @param value the cached value
     * @param <V> the type of the cached value
     * @return an Expirable with {@code Long.MAX_VALUE} expiration
     */
    public static <V> Expirable<V> eternal(V value) {
        return new Expirable<>(value, Long.MAX_VALUE);
    }
}
