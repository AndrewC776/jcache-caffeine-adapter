package com.yms.cache.event;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;

/**
 * JCache entry event implementation.
 *
 * <p>Represents a single cache entry event with:
 * <ul>
 *   <li>Event type (CREATED, UPDATED, REMOVED, EXPIRED)</li>
 *   <li>Key</li>
 *   <li>Old value (for UPDATED, REMOVED, EXPIRED)</li>
 *   <li>New value (for CREATED, UPDATED)</li>
 * </ul>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public final class JCacheEntryEvent<K, V> extends CacheEntryEvent<K, V> {

    private static final long serialVersionUID = 1L;

    private final K key;
    private final V oldValue;
    private final V value;

    /**
     * Creates a new cache entry event.
     *
     * @param source the source cache (may be null)
     * @param eventType the event type
     * @param key the key
     * @param oldValue the old value (may be null)
     * @param value the new value (may be null)
     */
    public JCacheEntryEvent(Cache<K, V> source, EventType eventType,
                           K key, V oldValue, V value) {
        super(source, eventType);
        this.key = key;
        this.oldValue = oldValue;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V getOldValue() {
        return oldValue;
    }

    @Override
    public boolean isOldValueAvailable() {
        return oldValue != null;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return clazz.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + clazz);
    }
}
