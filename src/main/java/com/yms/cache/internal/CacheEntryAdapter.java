package com.yms.cache.internal;

import javax.cache.processor.MutableEntry;

/**
 * Adapter for MutableEntry that tracks operations performed during EntryProcessor execution.
 *
 * <p>This class captures the intent of the processor (get, setValue, remove) without
 * directly modifying the cache. The cache implementation uses the tracked operations
 * to perform the actual cache modifications after the processor completes.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public final class CacheEntryAdapter<K, V> implements MutableEntry<K, V> {

    private final K key;
    private V currentValue;
    private boolean exists;

    // Operation tracking
    private boolean valueAccessed = false;
    private boolean valueSet = false;
    private boolean removed = false;
    private V newValue = null;

    /**
     * Creates a new adapter for an existing entry.
     *
     * @param key the entry key
     * @param value the current value (null if entry doesn't exist)
     * @param exists true if the entry exists in the cache
     */
    public CacheEntryAdapter(K key, V value, boolean exists) {
        this.key = key;
        this.currentValue = value;
        this.exists = exists;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        valueAccessed = true;
        if (removed) {
            return null;
        }
        if (valueSet) {
            return newValue;
        }
        return currentValue;
    }

    @Override
    public boolean exists() {
        if (removed) {
            return false;
        }
        if (valueSet) {
            return true;
        }
        return exists;
    }

    @Override
    public void remove() {
        removed = true;
        valueSet = false;
        newValue = null;
    }

    @Override
    public void setValue(V value) {
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }
        valueSet = true;
        removed = false;
        newValue = value;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return clazz.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + clazz);
    }

    // ========== Operation Query Methods ==========

    /**
     * Returns true if getValue() was called.
     */
    public boolean wasValueAccessed() {
        return valueAccessed;
    }

    /**
     * Returns true if setValue() was called.
     */
    public boolean wasValueSet() {
        return valueSet;
    }

    /**
     * Returns true if remove() was called.
     */
    public boolean wasRemoved() {
        return removed;
    }

    /**
     * Returns the new value set by setValue(), or null if not set.
     */
    public V getNewValue() {
        return newValue;
    }

    /**
     * Returns true if the original entry existed before any operations.
     */
    public boolean originalExists() {
        return exists;
    }

    /**
     * Returns the original value before any operations.
     */
    public V getOriginalValue() {
        return currentValue;
    }
}
