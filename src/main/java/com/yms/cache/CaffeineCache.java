package com.yms.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.yms.cache.config.YmsConfiguration;
import com.yms.cache.copier.Copier;
import com.yms.cache.copier.IdentityCopier;
import com.yms.cache.copier.SerializingCopier;
import com.yms.cache.event.CacheEventDispatcher;
import com.yms.cache.expiry.ExpiryCalculator;
import com.yms.cache.internal.CacheEntryAdapter;
import com.yms.cache.internal.Expirable;
import com.yms.cache.stats.JCacheStatistics;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JCache (JSR-107) implementation backed by Caffeine.
 *
 * <p>Core design principles:
 * <ul>
 *   <li>No explicit locks - uses Caffeine's {@code asMap()} atomic operations</li>
 *   <li>Lazy expiration - no background cleanup threads</li>
 *   <li>By-value semantics via Copier abstraction</li>
 *   <li>Side effects (events, stats, loader, writer) outside of atomic operations</li>
 * </ul>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private final String name;
    private final CacheManager cacheManager;
    private final YmsConfiguration<K, V> configuration;
    private final com.github.benmanes.caffeine.cache.Cache<K, Expirable<V>> store;
    private final ConcurrentMap<K, Expirable<V>> storeMap;
    private final Copier<K> keyCopier;
    private final Copier<V> valueCopier;
    private final ExpiryCalculator expiryCalculator;
    private final JCacheStatistics statistics;
    private final CacheEventDispatcher<K, V> eventDispatcher;
    private final CacheLoader<K, V> cacheLoader;
    private final boolean readThrough;
    private final CacheWriter<K, V> cacheWriter;
    private final boolean writeThrough;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * ThreadLocal to detect reentrant calls from within EntryProcessor.
     * When true, we are inside an EntryProcessor and cache API calls are forbidden.
     */
    private final ThreadLocal<Boolean> inEntryProcessor = ThreadLocal.withInitial(() -> false);

    /**
     * Creates a new CaffeineCache.
     *
     * @param name the cache name
     * @param cacheManager the parent cache manager (may be null)
     * @param configuration the cache configuration
     */
    public CaffeineCache(String name, CacheManager cacheManager, YmsConfiguration<K, V> configuration) {
        this.name = name;
        this.cacheManager = cacheManager;
        this.configuration = new YmsConfiguration<>(configuration);

        // Build Caffeine cache
        this.store = buildCaffeineCache(configuration);
        this.storeMap = store.asMap();

        // Setup copiers based on store-by-value setting
        boolean storeByValue = configuration.isStoreByValue();
        this.keyCopier = storeByValue ? new SerializingCopier<>() : new IdentityCopier<>();
        this.valueCopier = storeByValue ? new SerializingCopier<>() : new IdentityCopier<>();

        // Setup expiry policy
        ExpiryPolicy expiryPolicy = configuration.getExpiryPolicyFactory().create();
        this.expiryCalculator = new ExpiryCalculator(expiryPolicy);

        // Setup statistics if enabled
        this.statistics = configuration.isStatisticsEnabled() ? new JCacheStatistics() : null;

        // Setup event dispatcher
        this.eventDispatcher = new CacheEventDispatcher<>();
        this.eventDispatcher.setCache(this);

        // Setup read-through (CacheLoader)
        this.readThrough = configuration.isReadThrough();
        if (readThrough && configuration.getCacheLoaderFactory() != null) {
            this.cacheLoader = configuration.getCacheLoaderFactory().create();
        } else {
            this.cacheLoader = null;
        }

        // Setup write-through (CacheWriter)
        this.writeThrough = configuration.isWriteThrough();
        if (writeThrough && configuration.getCacheWriterFactory() != null) {
            @SuppressWarnings("unchecked")
            CacheWriter<K, V> writer = (CacheWriter<K, V>) configuration.getCacheWriterFactory().create();
            this.cacheWriter = writer;
        } else {
            this.cacheWriter = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private com.github.benmanes.caffeine.cache.Cache<K, Expirable<V>> buildCaffeineCache(
            YmsConfiguration<K, V> config) {
        Caffeine builder = Caffeine.newBuilder();

        if (config.getMaximumSize() != null) {
            builder.maximumSize(config.getMaximumSize());
        }
        if (config.getMaximumWeight() != null && config.getWeigher() != null) {
            builder.maximumWeight(config.getMaximumWeight());
            builder.weigher((k, v) -> {
                Expirable<V> exp = (Expirable<V>) v;
                K key = (K) k;
                return (int) config.getWeigher().applyAsLong(key, exp.getValue());
            });
        }

        return (com.github.benmanes.caffeine.cache.Cache<K, Expirable<V>>) builder.build();
    }

    /**
     * Ensures the cache is open.
     *
     * @throws IllegalStateException if the cache is closed
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Cache " + name + " is closed");
        }
    }

    /**
     * Checks for reentrant calls from within EntryProcessor.
     *
     * @throws CacheException if called from within an EntryProcessor
     */
    private void checkReentrant() {
        if (inEntryProcessor.get()) {
            throw new CacheException("Reentrant cache operation detected: "
                + "cache API calls are not allowed from within an EntryProcessor");
        }
    }

    /**
     * Validates that a key is not null.
     */
    private void requireNonNullKey(K key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
    }

    /**
     * Validates that a value is not null.
     */
    private void requireNonNullValue(V value) {
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }
    }

    // ========== Core Cache Operations ==========

    @Override
    public V get(K key) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);

        // Holder for side effects to execute after atomic operation
        final V[] result = (V[]) new Object[1];
        final boolean[] isHit = {false};
        final boolean[] isExpired = {false};
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null; // Miss
            }
            if (existing.isExpired()) {
                // Entry expired - mark for removal and event dispatch
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null; // Remove expired entry
            }
            // Hit - update access expiry if configured
            isHit[0] = true;
            result[0] = existing.getValue();
            long newExpiry = expiryCalculator.calculateAccessExpiry(existing.getExpireTime());
            if (expiryCalculator.shouldUpdateExpiry(newExpiry)) {
                return existing.withExpireTime(newExpiry);
            }
            return existing;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            // Fall through to read-through if enabled
        }

        if (isHit[0]) {
            if (statistics != null) {
                statistics.recordHit();
            }
            return valueCopier.copy(result[0]);
        }

        // Miss - try read-through if enabled
        if (readThrough && cacheLoader != null) {
            return loadAndCache(key);
        }

        // Miss without read-through
        if (statistics != null) {
            statistics.recordMiss();
        }
        return null;
    }

    /**
     * Loads a value from the CacheLoader and caches it.
     */
    private V loadAndCache(K key) {
        // Load outside of compute (per design spec)
        V loadedValue;
        try {
            loadedValue = cacheLoader.load(key);
        } catch (Exception e) {
            if (statistics != null) {
                statistics.recordMiss();
            }
            throw new CacheLoaderException("Failed to load key: " + key, e);
        }

        if (loadedValue == null) {
            // Loader returned null - don't cache
            if (statistics != null) {
                statistics.recordMiss();
            }
            return null;
        }

        // Cache the loaded value
        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(loadedValue);

        storeMap.compute(keyCopy, (k, existing) -> {
            // Check if value was concurrently added
            if (existing != null && !existing.isExpired()) {
                // Concurrent update - discard loaded value, use current
                return existing;
            }
            long expiry = expiryCalculator.calculateCreationExpiry();
            return new Expirable<>(valueCopy, expiry);
        });

        // Side effects
        if (statistics != null) {
            statistics.recordMiss();
            statistics.recordPut();
        }
        eventDispatcher.dispatchCreated(keyCopy, valueCopy);

        return valueCopier.copy(loadedValue);
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        ensureOpen();
        checkReentrant();
        if (keys == null) {
            throw new NullPointerException("keys cannot be null");
        }
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }
        }

        Map<K, V> result = new HashMap<>();
        Set<K> missingKeys = new HashSet<>();

        // First pass: get cached values and collect missing keys
        for (K key : keys) {
            V value = getFromCacheOnly(key);
            if (value != null) {
                result.put(keyCopier.copy(key), value);
            } else {
                missingKeys.add(key);
            }
        }

        // Load missing keys if read-through is enabled
        if (!missingKeys.isEmpty() && readThrough && cacheLoader != null) {
            Map<K, V> loadedValues = loadAllAndCache(missingKeys);
            result.putAll(loadedValues);
        }

        return result;
    }

    /**
     * Gets a value from cache only (no read-through).
     * Returns null if not found or expired, handling expiration side effects.
     */
    private V getFromCacheOnly(K key) {
        final V[] result = (V[]) new Object[1];
        final boolean[] isHit = {false};
        final boolean[] isExpired = {false};
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.isExpired()) {
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            isHit[0] = true;
            result[0] = existing.getValue();
            long newExpiry = expiryCalculator.calculateAccessExpiry(existing.getExpireTime());
            if (expiryCalculator.shouldUpdateExpiry(newExpiry)) {
                return existing.withExpireTime(newExpiry);
            }
            return existing;
        });

        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            return null;
        }

        if (isHit[0]) {
            if (statistics != null) {
                statistics.recordHit();
            }
            return valueCopier.copy(result[0]);
        }

        if (statistics != null) {
            statistics.recordMiss();
        }
        return null;
    }

    /**
     * Loads multiple values from the CacheLoader and caches them.
     */
    private Map<K, V> loadAllAndCache(Set<K> keys) {
        Map<K, V> result = new HashMap<>();

        // Load all keys using loadAll
        Map<K, V> loadedValues;
        try {
            loadedValues = cacheLoader.loadAll(keys);
        } catch (Exception e) {
            throw new CacheLoaderException("Failed to load keys", e);
        }

        if (loadedValues == null) {
            return result;
        }

        // Cache each loaded value
        for (Map.Entry<K, V> entry : loadedValues.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();

            if (value != null) {
                K keyCopy = keyCopier.copy(key);
                V valueCopy = valueCopier.copy(value);

                storeMap.compute(keyCopy, (k, existing) -> {
                    if (existing != null && !existing.isExpired()) {
                        return existing;
                    }
                    long expiry = expiryCalculator.calculateCreationExpiry();
                    return new Expirable<>(valueCopy, expiry);
                });

                if (statistics != null) {
                    statistics.recordPut();
                }
                eventDispatcher.dispatchCreated(keyCopy, valueCopy);

                result.put(keyCopy, valueCopier.copy(value));
            }
        }

        return result;
    }

    @Override
    public boolean containsKey(K key) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);

        // Holder for side effects
        final boolean[] exists = {false};
        final boolean[] isExpired = {false};
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found
            }
            if (existing.isExpired()) {
                // Entry expired - mark for removal and event dispatch
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null; // Remove expired entry
            }
            // Key exists and is not expired
            // Note: containsKey does NOT update access expiry per JCache spec
            exists[0] = true;
            return existing;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            // Note: containsKey does NOT affect statistics per JCache spec
        }

        return exists[0];
    }

    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues,
                       CompletionListener completionListener) {
        ensureOpen();
        checkReentrant();
        if (keys == null) {
            throw new NullPointerException("keys cannot be null");
        }

        // If no loader configured, complete immediately
        if (cacheLoader == null) {
            if (completionListener != null) {
                completionListener.onCompletion();
            }
            return;
        }

        // Execute asynchronously
        Thread loadThread = new Thread(() -> {
            try {
                // Determine which keys to load
                Set<K> keysToLoad = new HashSet<>();
                for (K key : keys) {
                    if (key == null) {
                        throw new NullPointerException("key cannot be null");
                    }
                    if (replaceExistingValues || !containsKeyInternal(key)) {
                        keysToLoad.add(key);
                    }
                }

                if (keysToLoad.isEmpty()) {
                    if (completionListener != null) {
                        completionListener.onCompletion();
                    }
                    return;
                }

                // Load all keys
                Map<K, V> loadedValues;
                try {
                    loadedValues = cacheLoader.loadAll(keysToLoad);
                } catch (Exception e) {
                    if (completionListener != null) {
                        completionListener.onException(e);
                    }
                    return;
                }

                // Cache loaded values
                if (loadedValues != null) {
                    for (Map.Entry<K, V> entry : loadedValues.entrySet()) {
                        K key = entry.getKey();
                        V value = entry.getValue();
                        if (key != null && value != null) {
                            putLoadedValue(key, value);
                        }
                    }
                }

                if (completionListener != null) {
                    completionListener.onCompletion();
                }
            } catch (Exception e) {
                if (completionListener != null) {
                    completionListener.onException(e);
                }
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Internal containsKey check that doesn't trigger reentrant detection.
     * Used by loadAll to check existing keys.
     */
    private boolean containsKeyInternal(K key) {
        Expirable<V> existing = storeMap.get(key);
        return existing != null && !existing.isExpired();
    }

    /**
     * Puts a value loaded by loadAll into the cache with proper events and statistics.
     */
    private void putLoadedValue(K key, V value) {
        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Side effect holders
        final boolean[] wasCreated = {false};
        final boolean[] wasUpdated = {false};
        final V[] oldValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            long expiry = expiryCalculator.calculateCreationExpiry();

            if (existing != null && !existing.isExpired()) {
                // Update existing entry
                wasUpdated[0] = true;
                oldValue[0] = existing.getValue();
                expiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
                if (!expiryCalculator.shouldUpdateExpiry(expiry)) {
                    expiry = existing.getExpireTime();
                }
            } else {
                // Create new entry
                wasCreated[0] = true;
            }

            return new Expirable<>(valueCopy, expiry);
        });

        // Side effects outside compute
        if (wasCreated[0]) {
            eventDispatcher.dispatchCreated(keyCopy, valueCopy);
        } else if (wasUpdated[0]) {
            eventDispatcher.dispatchUpdated(keyCopy, valueCopier.copy(oldValue[0]), valueCopy);
        }

        if (statistics != null) {
            statistics.recordPut();
        }
    }

    @Override
    public void put(K key, V value) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(value);

        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Write-through: call writer FIRST (before caching)
        if (writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, valueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] isCreate = {false};
        final boolean[] isUpdate = {false};
        final boolean[] isExpiredReplace = {false};
        final V[] oldValue = (V[]) new Object[1];
        final V[] expiredOldValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                // Update existing entry
                isUpdate[0] = true;
                oldValue[0] = existing.getValue();
                long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
                long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                    ? newExpiry : existing.getExpireTime();
                return new Expirable<>(valueCopy, expiry);
            } else {
                // Create new entry (or replace expired)
                isCreate[0] = true;
                if (existing != null && existing.isExpired()) {
                    isExpiredReplace[0] = true;
                    expiredOldValue[0] = existing.getValue();
                }
                long expiry = expiryCalculator.calculateCreationExpiry();
                return new Expirable<>(valueCopy, expiry);
            }
        });

        // Side effects outside of compute
        if (isExpiredReplace[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredOldValue[0]));
        }

        if (isCreate[0]) {
            eventDispatcher.dispatchCreated(keyCopy, valueCopy);
        } else if (isUpdate[0]) {
            eventDispatcher.dispatchUpdated(keyCopy, valueCopier.copy(oldValue[0]), valueCopy);
        }

        if (statistics != null) {
            statistics.recordPut();
        }
    }

    @Override
    public V getAndPut(K key, V value) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(value);

        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Write-through: call writer FIRST (before caching)
        if (writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, valueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] isCreate = {false};
        final boolean[] isUpdate = {false};
        final boolean[] isExpiredReplace = {false};
        final V[] oldValue = (V[]) new Object[1];
        final V[] expiredOldValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                // Update existing entry
                isUpdate[0] = true;
                oldValue[0] = existing.getValue();
                long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
                long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                    ? newExpiry : existing.getExpireTime();
                return new Expirable<>(valueCopy, expiry);
            } else {
                // Create new entry (or replace expired)
                isCreate[0] = true;
                if (existing != null && existing.isExpired()) {
                    isExpiredReplace[0] = true;
                    expiredOldValue[0] = existing.getValue();
                }
                long expiry = expiryCalculator.calculateCreationExpiry();
                return new Expirable<>(valueCopy, expiry);
            }
        });

        // Side effects outside of compute
        if (isExpiredReplace[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredOldValue[0]));
        }

        if (isCreate[0]) {
            eventDispatcher.dispatchCreated(keyCopy, valueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordMiss();
            }
            return null; // No old value
        } else if (isUpdate[0]) {
            V oldValueCopy = valueCopier.copy(oldValue[0]);
            eventDispatcher.dispatchUpdated(keyCopy, oldValueCopy, valueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordHit();
            }
            return oldValueCopy;
        }

        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        ensureOpen();
        checkReentrant();
        if (map == null) {
            throw new NullPointerException("map cannot be null");
        }
        // Validate all keys and values first
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new NullPointerException("key cannot be null");
            }
            if (entry.getValue() == null) {
                throw new NullPointerException("value cannot be null");
            }
        }

        // Write-through: call writer.writeAll() FIRST (before caching)
        if (writeThrough && cacheWriter != null) {
            java.util.Collection<Entry<? extends K, ? extends V>> entries = new java.util.ArrayList<>();
            for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
                entries.add(new WriterEntry<>(keyCopier.copy(entry.getKey()), valueCopier.copy(entry.getValue())));
            }
            try {
                cacheWriter.writeAll(entries);
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write entries", e);
            }
        }

        // Put each entry (without calling writer again since we already called writeAll)
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            putWithoutWriter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Internal put without calling CacheWriter (for use after batch writer operations).
     */
    private void putWithoutWriter(K key, V value) {
        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        final boolean[] isCreate = {false};
        final boolean[] isUpdate = {false};
        final boolean[] isExpiredReplace = {false};
        final V[] oldValue = (V[]) new Object[1];
        final V[] expiredOldValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                isUpdate[0] = true;
                oldValue[0] = existing.getValue();
                long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
                long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                    ? newExpiry : existing.getExpireTime();
                return new Expirable<>(valueCopy, expiry);
            } else {
                isCreate[0] = true;
                if (existing != null && existing.isExpired()) {
                    isExpiredReplace[0] = true;
                    expiredOldValue[0] = existing.getValue();
                }
                long expiry = expiryCalculator.calculateCreationExpiry();
                return new Expirable<>(valueCopy, expiry);
            }
        });

        if (isExpiredReplace[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredOldValue[0]));
        }

        if (isCreate[0]) {
            eventDispatcher.dispatchCreated(keyCopy, valueCopy);
        } else if (isUpdate[0]) {
            eventDispatcher.dispatchUpdated(keyCopy, valueCopier.copy(oldValue[0]), valueCopy);
        }

        if (statistics != null) {
            statistics.recordPut();
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(value);

        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Check if key is absent first (for write-through decision)
        boolean keyAbsent = !containsKey(key);

        // Write-through: call writer only if key is absent
        if (keyAbsent && writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, valueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] inserted = {false};
        final boolean[] isExpiredReplace = {false};
        final V[] expiredOldValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                // Key exists and is not expired - don't insert
                return existing;
            }
            // Key absent or expired - insert new entry
            inserted[0] = true;
            if (existing != null && existing.isExpired()) {
                isExpiredReplace[0] = true;
                expiredOldValue[0] = existing.getValue();
            }
            long expiry = expiryCalculator.calculateCreationExpiry();
            return new Expirable<>(valueCopy, expiry);
        });

        // Side effects outside of compute
        if (isExpiredReplace[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredOldValue[0]));
        }

        if (inserted[0]) {
            eventDispatcher.dispatchCreated(keyCopy, valueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordMiss(); // Key was not present
            }
            return true;
        } else {
            if (statistics != null) {
                statistics.recordHit(); // Key was present
            }
            return false;
        }
    }

    @Override
    public boolean remove(K key) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);

        // Write-through: call writer.delete() FIRST (before removing from cache)
        if (writeThrough && cacheWriter != null) {
            try {
                cacheWriter.delete(key);
            } catch (Exception e) {
                throw new CacheWriterException("Failed to delete key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] removed = {false};
        final boolean[] isExpired = {false};
        final V[] removedValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Remove the entry
            removed[0] = true;
            removedValue[0] = existing.getValue();
            return null;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            return false;
        }

        if (removed[0]) {
            eventDispatcher.dispatchRemoved(key, valueCopier.copy(removedValue[0]));
            if (statistics != null) {
                statistics.recordRemoval();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(oldValue);

        // Check if value matches first (for write-through decision)
        boolean valueMatches = checkValueMatches(key, oldValue);

        // Write-through: call writer only if value matches
        if (valueMatches && writeThrough && cacheWriter != null) {
            try {
                cacheWriter.delete(key);
            } catch (Exception e) {
                throw new CacheWriterException("Failed to delete key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] removed = {false};
        final boolean[] isExpired = {false};
        final boolean[] valueMatchedFlag = {false};
        final V[] removedValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Check if value matches
            if (existing.getValue().equals(oldValue)) {
                removed[0] = true;
                valueMatchedFlag[0] = true;
                removedValue[0] = existing.getValue();
                return null; // Remove
            }
            // Value doesn't match - keep entry
            return existing;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            return false;
        }

        if (removed[0]) {
            eventDispatcher.dispatchRemoved(key, valueCopier.copy(removedValue[0]));
            if (statistics != null) {
                statistics.recordRemoval();
                statistics.recordHit();
            }
            return true;
        }

        // Key exists but value didn't match, or key not found
        if (statistics != null) {
            statistics.recordMiss();
        }
        return false;
    }

    /**
     * Checks if the current value for a key matches the expected value.
     */
    private boolean checkValueMatches(K key, V expectedValue) {
        Expirable<V> existing = storeMap.get(key);
        if (existing == null || existing.isExpired()) {
            return false;
        }
        return existing.getValue().equals(expectedValue);
    }

    @Override
    public V getAndRemove(K key) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);

        // Write-through: call writer.delete() FIRST (before removing from cache)
        if (writeThrough && cacheWriter != null) {
            try {
                cacheWriter.delete(key);
            } catch (Exception e) {
                throw new CacheWriterException("Failed to delete key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] removed = {false};
        final boolean[] isExpired = {false};
        final V[] removedValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Remove the entry
            removed[0] = true;
            removedValue[0] = existing.getValue();
            return null;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            return null;
        }

        if (removed[0]) {
            V valueCopy = valueCopier.copy(removedValue[0]);
            eventDispatcher.dispatchRemoved(key, valueCopy);
            if (statistics != null) {
                statistics.recordRemoval();
                statistics.recordHit();
            }
            return valueCopy;
        }

        // Key not found
        if (statistics != null) {
            statistics.recordMiss();
        }
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(oldValue);
        requireNonNullValue(newValue);

        K keyCopy = keyCopier.copy(key);
        V newValueCopy = valueCopier.copy(newValue);

        // Check if value matches first (for write-through decision)
        boolean valueMatches = checkValueMatches(key, oldValue);

        // Write-through: call writer only if old value matches
        if (valueMatches && writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, newValueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] replaced = {false};
        final boolean[] isExpired = {false};
        final V[] previousValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Check if old value matches
            if (existing.getValue().equals(oldValue)) {
                replaced[0] = true;
                previousValue[0] = existing.getValue();
                long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
                long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                    ? newExpiry : existing.getExpireTime();
                return new Expirable<>(newValueCopy, expiry);
            }
            // Value doesn't match - keep entry
            return existing;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredValue[0]));
            return false;
        }

        if (replaced[0]) {
            eventDispatcher.dispatchUpdated(keyCopy, valueCopier.copy(previousValue[0]), newValueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordHit();
            }
            return true;
        }

        // Key not found or value didn't match
        if (statistics != null) {
            statistics.recordMiss();
        }
        return false;
    }

    @Override
    public boolean replace(K key, V value) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(value);

        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Check if key exists first (for write-through decision)
        boolean keyExists = containsKey(key);

        // Write-through: call writer only if key exists
        if (keyExists && writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, valueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] replaced = {false};
        final boolean[] isExpired = {false};
        final V[] previousValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found - don't insert
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Replace existing entry
            replaced[0] = true;
            previousValue[0] = existing.getValue();
            long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
            long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                ? newExpiry : existing.getExpireTime();
            return new Expirable<>(valueCopy, expiry);
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredValue[0]));
            return false;
        }

        if (replaced[0]) {
            eventDispatcher.dispatchUpdated(keyCopy, valueCopier.copy(previousValue[0]), valueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordHit();
            }
            return true;
        }

        // Key not found
        if (statistics != null) {
            statistics.recordMiss();
        }
        return false;
    }

    @Override
    public V getAndReplace(K key, V value) {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        requireNonNullValue(value);

        K keyCopy = keyCopier.copy(key);
        V valueCopy = valueCopier.copy(value);

        // Check if key exists first (for write-through decision)
        boolean keyExists = containsKey(key);

        // Write-through: call writer only if key exists
        if (keyExists && writeThrough && cacheWriter != null) {
            try {
                cacheWriter.write(new WriterEntry<>(keyCopy, valueCopy));
            } catch (Exception e) {
                throw new CacheWriterException("Failed to write key: " + key, e);
            }
        }

        // Holders for side effects
        final boolean[] replaced = {false};
        final boolean[] isExpired = {false};
        final V[] previousValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(keyCopy, (k, existing) -> {
            if (existing == null) {
                return null; // Key not found - don't insert
            }
            if (existing.isExpired()) {
                // Entry expired
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            // Replace existing entry
            replaced[0] = true;
            previousValue[0] = existing.getValue();
            long newExpiry = expiryCalculator.calculateUpdateExpiry(existing.getExpireTime());
            long expiry = expiryCalculator.shouldUpdateExpiry(newExpiry)
                ? newExpiry : existing.getExpireTime();
            return new Expirable<>(valueCopy, expiry);
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
                statistics.recordMiss();
            }
            eventDispatcher.dispatchExpired(keyCopy, valueCopier.copy(expiredValue[0]));
            return null;
        }

        if (replaced[0]) {
            V previousValueCopy = valueCopier.copy(previousValue[0]);
            eventDispatcher.dispatchUpdated(keyCopy, previousValueCopy, valueCopy);
            if (statistics != null) {
                statistics.recordPut();
                statistics.recordHit();
            }
            return previousValueCopy;
        }

        // Key not found
        if (statistics != null) {
            statistics.recordMiss();
        }
        return null;
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        ensureOpen();
        checkReentrant();
        if (keys == null) {
            throw new NullPointerException("keys cannot be null");
        }
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }
        }

        // Write-through: call writer.deleteAll() FIRST (before removing from cache)
        if (writeThrough && cacheWriter != null) {
            java.util.Collection<Object> keysToDelete = new java.util.ArrayList<>(keys);
            try {
                cacheWriter.deleteAll(keysToDelete);
            } catch (Exception e) {
                throw new CacheWriterException("Failed to delete keys", e);
            }
        }

        // Remove each key (without calling writer again since we already called deleteAll)
        for (K key : keys) {
            removeWithoutWriter(key);
        }
    }

    /**
     * Internal remove without calling CacheWriter (for use after batch writer operations).
     */
    private boolean removeWithoutWriter(K key) {
        final boolean[] removed = {false};
        final boolean[] isExpired = {false};
        final V[] removedValue = (V[]) new Object[1];
        final V[] expiredValue = (V[]) new Object[1];

        storeMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.isExpired()) {
                isExpired[0] = true;
                expiredValue[0] = existing.getValue();
                return null;
            }
            removed[0] = true;
            removedValue[0] = existing.getValue();
            return null;
        });

        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
            return false;
        }

        if (removed[0]) {
            eventDispatcher.dispatchRemoved(key, valueCopier.copy(removedValue[0]));
            if (statistics != null) {
                statistics.recordRemoval();
            }
            return true;
        }

        return false;
    }

    @Override
    public void removeAll() {
        ensureOpen();
        checkReentrant();

        // Get a copy of all keys to avoid ConcurrentModificationException
        Set<K> keys = new HashSet<>(storeMap.keySet());

        // Remove each entry, firing events for non-expired entries
        for (K key : keys) {
            remove(key);
        }
    }

    @Override
    public void clear() {
        ensureOpen();
        checkReentrant();
        storeMap.clear();
        // Note: clear() does NOT trigger events or count as eviction per spec
    }

    // ========== Configuration & Metadata ==========

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        if (clazz.isInstance(configuration)) {
            return clazz.cast(configuration);
        }
        throw new IllegalArgumentException("Configuration class not supported: " + clazz);
    }

    // ========== EntryProcessor ==========

    @Override
    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments)
            throws EntryProcessorException {
        ensureOpen();
        checkReentrant();
        requireNonNullKey(key);
        if (entryProcessor == null) {
            throw new NullPointerException("entryProcessor cannot be null");
        }

        // Two-phase read-through: first check if we need to load
        V loadedValue = null;
        boolean loadedSuccessfully = false;
        boolean needsLoadPhase = false;

        if (readThrough && cacheLoader != null) {
            // Check current state (outside compute)
            Expirable<V> existing = storeMap.get(key);
            needsLoadPhase = (existing == null) || existing.isExpired();

            if (needsLoadPhase) {
                // Load outside compute
                try {
                    loadedValue = cacheLoader.load(key);
                    loadedSuccessfully = (loadedValue != null);
                } catch (Exception e) {
                    throw new CacheLoaderException("Failed to load key: " + key, e);
                }
            }
        }

        // Holders for side effects
        final CacheEntryAdapter<K, V>[] adapter = (CacheEntryAdapter<K, V>[]) new CacheEntryAdapter[1];
        final T[] result = (T[]) new Object[1];
        final Exception[] exception = new Exception[1];
        final boolean[] isExpired = {false};
        final V[] expiredValue = (V[]) new Object[1];
        final boolean[] loadWasUsed = {false};

        // Capture for lambda
        final V finalLoadedValue = loadedValue;
        final boolean finalLoadedSuccessfully = loadedSuccessfully;

        storeMap.compute(key, (k, existing) -> {
            V currentValue = null;
            boolean exists = false;

            if (existing != null) {
                if (existing.isExpired()) {
                    isExpired[0] = true;
                    expiredValue[0] = existing.getValue();
                    // Treat as non-existent - use loaded value if available
                    if (finalLoadedSuccessfully) {
                        currentValue = finalLoadedValue;
                        exists = true;
                        loadWasUsed[0] = true;
                    }
                } else {
                    // Entry exists and not expired - use current value (ignore loaded value)
                    currentValue = existing.getValue();
                    exists = true;
                }
            } else {
                // Entry doesn't exist - use loaded value if available
                if (finalLoadedSuccessfully) {
                    currentValue = finalLoadedValue;
                    exists = true;
                    loadWasUsed[0] = true;
                }
            }

            // Create adapter and execute processor with reentry detection
            adapter[0] = new CacheEntryAdapter<>(k, currentValue, exists);
            inEntryProcessor.set(true);
            try {
                result[0] = entryProcessor.process(adapter[0], arguments);
            } catch (Exception e) {
                exception[0] = e;
                return existing; // Keep original on error
            } finally {
                inEntryProcessor.set(false);
            }

            // Apply changes based on adapter state
            CacheEntryAdapter<K, V> entry = adapter[0];
            if (entry.wasRemoved()) {
                return null; // Remove entry
            }
            if (entry.wasValueSet()) {
                V newValue = entry.getNewValue();
                long expiry = entry.originalExists() && !loadWasUsed[0]
                    ? expiryCalculator.calculateUpdateExpiry(existing != null ? existing.getExpireTime() : 0)
                    : expiryCalculator.calculateCreationExpiry();
                if (!expiryCalculator.shouldUpdateExpiry(expiry) && existing != null && !existing.isExpired()) {
                    expiry = existing.getExpireTime();
                }
                return new Expirable<>(valueCopier.copy(newValue), expiry);
            }
            // If load was used but no changes made by processor, cache the loaded value
            if (loadWasUsed[0]) {
                long expiry = expiryCalculator.calculateCreationExpiry();
                return new Expirable<>(valueCopier.copy(finalLoadedValue), expiry);
            }
            // No changes - keep original (or null)
            return existing;
        });

        // Side effects outside of compute
        if (isExpired[0]) {
            if (statistics != null) {
                statistics.recordEviction();
            }
            eventDispatcher.dispatchExpired(key, valueCopier.copy(expiredValue[0]));
        }

        if (exception[0] != null) {
            throw new EntryProcessorException(exception[0]);
        }

        CacheEntryAdapter<K, V> entry = adapter[0];
        if (entry != null) {
            // Record statistics based on adapter operations
            if (entry.wasValueAccessed()) {
                // If load was used, it's a miss followed by load (which is like a put)
                if (loadWasUsed[0]) {
                    if (statistics != null) {
                        statistics.recordMiss();
                    }
                } else if (entry.originalExists()) {
                    if (statistics != null) {
                        statistics.recordHit();
                    }
                } else {
                    if (statistics != null) {
                        statistics.recordMiss();
                    }
                }
            }

            // Fire events based on changes
            if (entry.wasRemoved() && entry.originalExists()) {
                eventDispatcher.dispatchRemoved(key, valueCopier.copy(entry.getOriginalValue()));
                if (statistics != null) {
                    statistics.recordRemoval();
                }
            } else if (entry.wasValueSet()) {
                V newValueCopy = valueCopier.copy(entry.getNewValue());
                // If load was used, treat original as not existing for event purposes
                if (loadWasUsed[0]) {
                    eventDispatcher.dispatchCreated(key, newValueCopy);
                } else if (entry.originalExists()) {
                    eventDispatcher.dispatchUpdated(key, valueCopier.copy(entry.getOriginalValue()), newValueCopy);
                } else {
                    eventDispatcher.dispatchCreated(key, newValueCopy);
                }
                if (statistics != null) {
                    statistics.recordPut();
                }
            } else if (loadWasUsed[0]) {
                // Load was used but processor didn't modify - we still cached the loaded value
                eventDispatcher.dispatchCreated(key, valueCopier.copy(loadedValue));
                if (statistics != null) {
                    statistics.recordPut();
                }
            }
        }

        return result[0];
    }

    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys,
            EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
        ensureOpen();
        checkReentrant();
        if (keys == null) {
            throw new NullPointerException("keys cannot be null");
        }
        if (entryProcessor == null) {
            throw new NullPointerException("entryProcessor cannot be null");
        }

        Map<K, EntryProcessorResult<T>> results = new HashMap<>();

        for (K key : keys) {
            try {
                T result = invoke(key, entryProcessor, arguments);
                results.put(keyCopier.copy(key), () -> result);
            } catch (EntryProcessorException e) {
                results.put(keyCopier.copy(key), () -> {
                    throw e;
                });
            }
        }

        return results;
    }

    // ========== Lifecycle ==========

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            storeMap.clear();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return clazz.cast(this);
        }
        if (clazz.isAssignableFrom(store.getClass())) {
            return clazz.cast(store);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + clazz);
    }

    // ========== Listener Management ==========

    @Override
    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> config) {
        ensureOpen();
        eventDispatcher.registerListener(config);
    }

    @Override
    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> config) {
        ensureOpen();
        eventDispatcher.deregisterListener(config);
    }

    // ========== Iterator ==========

    @Override
    public Iterator<Entry<K, V>> iterator() {
        ensureOpen();
        return new CacheIterator();
    }

    /**
     * Iterator implementation that skips expired entries and supports remove.
     */
    private class CacheIterator implements Iterator<Entry<K, V>> {
        private final Iterator<Map.Entry<K, Expirable<V>>> delegate;
        private Entry<K, V> nextEntry;
        private Entry<K, V> lastReturned;
        private boolean canRemove = false;

        CacheIterator() {
            this.delegate = storeMap.entrySet().iterator();
            advance();
        }

        private void advance() {
            nextEntry = null;
            while (delegate.hasNext()) {
                Map.Entry<K, Expirable<V>> mapEntry = delegate.next();
                Expirable<V> expirable = mapEntry.getValue();

                if (expirable != null && !expirable.isExpired()) {
                    // Found a non-expired entry
                    K key = keyCopier.copy(mapEntry.getKey());
                    V value = valueCopier.copy(expirable.getValue());
                    nextEntry = new CacheEntry(key, value);
                    return;
                } else if (expirable != null && expirable.isExpired()) {
                    // Remove expired entry and dispatch event
                    K expiredKey = mapEntry.getKey();
                    V expiredValue = expirable.getValue();
                    delegate.remove();
                    if (statistics != null) {
                        statistics.recordEviction();
                    }
                    eventDispatcher.dispatchExpired(expiredKey, valueCopier.copy(expiredValue));
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextEntry != null;
        }

        @Override
        public Entry<K, V> next() {
            if (nextEntry == null) {
                throw new java.util.NoSuchElementException();
            }
            lastReturned = nextEntry;
            canRemove = true;
            advance();
            return lastReturned;
        }

        @Override
        public void remove() {
            if (!canRemove) {
                throw new IllegalStateException("next() must be called before remove()");
            }
            canRemove = false;

            // Remove from cache
            K key = lastReturned.getKey();
            CaffeineCache.this.remove(key);
        }
    }

    /**
     * Simple Entry implementation for iterator results.
     */
    private class CacheEntry implements Entry<K, V> {
        private final K key;
        private final V value;

        CacheEntry(K key, V value) {
            this.key = key;
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
        public <T> T unwrap(Class<T> clazz) {
            if (clazz.isAssignableFrom(getClass())) {
                return clazz.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + clazz);
        }
    }

    /**
     * Simple Entry implementation for CacheWriter operations.
     */
    private static class WriterEntry<K, V> implements Entry<K, V> {
        private final K key;
        private final V value;

        WriterEntry(K key, V value) {
            this.key = key;
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
        public <T> T unwrap(Class<T> clazz) {
            if (clazz.isAssignableFrom(getClass())) {
                return clazz.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + clazz);
        }
    }

    // ========== Internal Accessors (for testing) ==========

    /**
     * Returns the statistics collector, or null if disabled.
     *
     * @return the statistics collector, or null if statistics are not enabled
     */
    public JCacheStatistics getStatistics() {
        return statistics;
    }

    /**
     * Returns the internal store map for testing.
     */
    protected ConcurrentMap<K, Expirable<V>> getStoreMap() {
        return storeMap;
    }

    /**
     * Returns the event dispatcher for testing.
     */
    protected CacheEventDispatcher<K, V> getEventDispatcher() {
        return eventDispatcher;
    }

    /**
     * Returns the expiry calculator for testing.
     */
    protected ExpiryCalculator getExpiryCalculator() {
        return expiryCalculator;
    }

    /**
     * Returns the value copier for testing.
     */
    protected Copier<V> getValueCopier() {
        return valueCopier;
    }
}
