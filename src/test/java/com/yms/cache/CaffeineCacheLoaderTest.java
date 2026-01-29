package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.CacheException;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.*;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache read-through (CacheLoader) functionality.
 */
class CaffeineCacheLoaderTest {

    // ========== Basic Read-Through Tests ==========

    @Test
    void getShouldTriggerLoadOnMiss() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        String value = cache.get("key1");

        assertEquals("loaded-key1", value);
        assertEquals(1, loadCount.get());
    }

    @Test
    void getShouldNotTriggerLoadOnHit() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.put("key1", "cachedValue");
        String value = cache.get("key1");

        assertEquals("cachedValue", value);
        assertEquals(0, loadCount.get());
    }

    @Test
    void getShouldTriggerLoadOnExpiredEntry() throws InterruptedException {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createExpiringCacheWithLoader(
            50,
            key -> {
                loadCount.incrementAndGet();
                return "loaded-" + key;
            }
        );

        cache.put("key1", "initialValue");
        assertEquals("initialValue", cache.get("key1"));
        assertEquals(0, loadCount.get());

        Thread.sleep(100); // Wait for expiration

        String value = cache.get("key1");
        assertEquals("loaded-key1", value);
        assertEquals(1, loadCount.get());
    }

    @Test
    void getShouldReturnNullWhenLoaderReturnsNull() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> null);

        String value = cache.get("key1");

        assertNull(value);
        assertNull(cache.get("key1")); // Should still be null (not cached)
    }

    @Test
    void getShouldCacheLoadedValue() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.get("key1"); // First access - triggers load
        cache.get("key1"); // Second access - should hit cache

        assertEquals(1, loadCount.get()); // Load should only happen once
    }

    @Test
    void getShouldThrowCacheLoaderExceptionOnLoadFailure() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            throw new RuntimeException("Simulated load failure");
        });

        assertThrows(CacheLoaderException.class, () -> cache.get("key1"));
    }

    // ========== Statistics Tests ==========

    @Test
    void loadedValueShouldRecordMissAndPut() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);

        cache.get("key1");

        assertEquals(1, cache.getStatistics().getCacheMisses());
        assertEquals(1, cache.getStatistics().getCachePuts());
    }

    @Test
    void loaderReturningNullShouldRecordMissOnly() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> null);

        cache.get("key1");

        assertEquals(1, cache.getStatistics().getCacheMisses());
        assertEquals(0, cache.getStatistics().getCachePuts());
    }

    // ========== Event Tests ==========

    @Test
    void loadedValueShouldFireCreatedEvent() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(cache, events);

        cache.get("key1");

        assertEquals(1, events.size());
        assertEquals(EventType.CREATED, events.get(0).getEventType());
        assertEquals("key1", events.get(0).getKey());
        assertEquals("loaded-key1", events.get(0).getValue());
    }

    // ========== getAll Read-Through Tests ==========

    @Test
    void getAllShouldTriggerLoadForMissingKeys() {
        Set<String> loadedKeys = new HashSet<>();
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadedKeys.add(key);
            return "loaded-" + key;
        });

        cache.put("key1", "cached1"); // Pre-cache one key

        Map<String, String> result = cache.getAll(Set.of("key1", "key2", "key3"));

        assertEquals(3, result.size());
        assertEquals("cached1", result.get("key1"));
        assertEquals("loaded-key2", result.get("key2"));
        assertEquals("loaded-key3", result.get("key3"));

        // Only missing keys should be loaded
        assertFalse(loadedKeys.contains("key1"));
        assertTrue(loadedKeys.contains("key2"));
        assertTrue(loadedKeys.contains("key3"));
    }

    // ========== loadAll Tests ==========

    @Test
    void getAllShouldUseLoadAllWhenAvailable() {
        AtomicInteger loadAllCount = new AtomicInteger(0);
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-" + key;
            }

            @Override
            public Map<String, String> loadAll(Iterable<? extends String> keys) {
                loadAllCount.incrementAndGet();
                Map<String, String> result = new HashMap<>();
                for (String key : keys) {
                    result.put(key, "batch-" + key);
                }
                return result;
            }
        };

        CaffeineCache<String, String> cache = createCacheWithLoader(loader);

        Map<String, String> result = cache.getAll(Set.of("key1", "key2"));

        assertEquals(2, result.size());
        assertEquals("batch-key1", result.get("key1"));
        assertEquals("batch-key2", result.get("key2"));
        assertEquals(1, loadAllCount.get());
    }

    // ========== invoke Read-Through Tests (I.4) ==========

    @Test
    void invokeShouldTriggerLoadOnMiss() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        // Entry doesn't exist - should trigger read-through when getValue() is called
        String result = cache.invoke("key1", (entry, args) -> {
            return entry.getValue(); // This should trigger load
        });

        assertEquals("loaded-key1", result);
        assertEquals(1, loadCount.get());
    }

    @Test
    void invokeShouldNotTriggerLoadOnHit() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.put("key1", "cachedValue");

        String result = cache.invoke("key1", (entry, args) -> entry.getValue());

        assertEquals("cachedValue", result);
        assertEquals(0, loadCount.get()); // Should NOT trigger load
    }

    @Test
    void invokeShouldTriggerLoadOnExpiredEntry() throws InterruptedException {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createExpiringCacheWithLoader(
            50,
            key -> {
                loadCount.incrementAndGet();
                return "loaded-" + key;
            }
        );

        cache.put("key1", "initialValue");
        Thread.sleep(100); // Wait for expiration

        String result = cache.invoke("key1", (entry, args) -> entry.getValue());

        assertEquals("loaded-key1", result);
        assertEquals(1, loadCount.get());
    }

    @Test
    void invokeShouldCacheLoadedValue() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        // First invoke - triggers load
        cache.invoke("key1", (entry, args) -> entry.getValue());

        // Second invoke - should use cached value
        String result = cache.invoke("key1", (entry, args) -> entry.getValue());

        assertEquals("loaded-key1", result);
        assertEquals(1, loadCount.get()); // Load should only happen once
    }

    @Test
    void invokeShouldNotLoadWhenEntryExists() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.put("key1", "existingValue"); // Pre-populate cache

        // Processor modifies existing entry - should NOT trigger load
        String result = cache.invoke("key1", (entry, args) -> {
            entry.setValue("newValue"); // Modify existing
            return "done";
        });

        assertEquals("done", result);
        assertEquals(0, loadCount.get()); // No load because entry exists
        assertEquals("newValue", cache.get("key1"));
    }

    @Test
    void invokeShouldLoadBeforeExistsCheck() {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        // If read-through is enabled and entry doesn't exist,
        // getValue() should trigger load and entry.exists() should be true after load
        Boolean existsAfterLoad = cache.invoke("key1", (entry, args) -> {
            String value = entry.getValue(); // Triggers load
            return entry.exists(); // Should be true after successful load
        });

        assertTrue(existsAfterLoad);
        assertEquals(1, loadCount.get());
    }

    @Test
    void invokeShouldReturnNullWhenLoaderReturnsNull() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> null);

        String result = cache.invoke("key1", (entry, args) -> entry.getValue());

        assertNull(result);
    }

    // ========== loadAll API Tests ==========

    @Test
    void loadAllShouldLoadMissingKeys() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] success = {false};
        final Exception[] error = {null};

        cache.loadAll(Set.of("key1", "key2"), false, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() {
                success[0] = true;
                latch.countDown();
            }
            @Override
            public void onException(Exception e) {
                error[0] = e;
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success[0]);
        assertNull(error[0]);
        assertEquals(2, loadCount.get());
        assertEquals("loaded-key1", cache.get("key1"));
        assertEquals("loaded-key2", cache.get("key2"));
    }

    @Test
    void loadAllShouldNotReplaceExistingWhenReplaceIsFalse() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.put("key1", "existingValue");

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        cache.loadAll(Set.of("key1", "key2"), false, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() { latch.countDown(); }
            @Override
            public void onException(Exception e) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, loadCount.get()); // Only key2 loaded
        assertEquals("existingValue", cache.get("key1")); // Unchanged
        assertEquals("loaded-key2", cache.get("key2")); // Newly loaded
    }

    @Test
    void loadAllShouldReplaceExistingWhenReplaceIsTrue() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        cache.put("key1", "existingValue");

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        cache.loadAll(Set.of("key1", "key2"), true, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() { latch.countDown(); }
            @Override
            public void onException(Exception e) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(2, loadCount.get()); // Both keys loaded
        assertEquals("loaded-key1", cache.get("key1")); // Replaced
        assertEquals("loaded-key2", cache.get("key2"));
    }

    @Test
    void loadAllShouldCallOnExceptionWhenLoaderFails() throws Exception {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            throw new RuntimeException("Simulated load failure");
        });

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Exception[] caughtException = {null};

        cache.loadAll(Set.of("key1"), false, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() { latch.countDown(); }
            @Override
            public void onException(Exception e) {
                caughtException[0] = e;
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(caughtException[0]);
    }

    @Test
    void loadAllShouldWorkWithNullListener() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        // Should not throw with null listener
        cache.loadAll(Set.of("key1"), false, null);

        // Give async operation time to complete
        Thread.sleep(100);

        assertEquals("loaded-key1", cache.get("key1"));
    }

    @Test
    void loadAllShouldThrowForNullKeys() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);

        assertThrows(NullPointerException.class, () ->
            cache.loadAll(null, false, null));
    }

    @Test
    void loadAllShouldThrowWhenCacheIsClosed() {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);
        cache.close();

        assertThrows(IllegalStateException.class, () ->
            cache.loadAll(Set.of("key1"), false, null));
    }

    @Test
    void loadAllShouldFireCreatedEventsForNewEntries() throws Exception {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);

        List<CacheEntryEvent<? extends String, ? extends String>> events =
            Collections.synchronizedList(new ArrayList<>());
        registerCreatedListener(cache, events);

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        cache.loadAll(Set.of("key1", "key2"), false, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() { latch.countDown(); }
            @Override
            public void onException(Exception e) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(2, events.size());
    }

    @Test
    void loadAllShouldRecordPutStatistics() throws Exception {
        CaffeineCache<String, String> cache = createCacheWithLoader(key -> "loaded-" + key);

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        cache.loadAll(Set.of("key1", "key2"), false, new javax.cache.integration.CompletionListener() {
            @Override
            public void onCompletion() { latch.countDown(); }
            @Override
            public void onException(Exception e) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(2, cache.getStatistics().getCachePuts());
    }

    // ========== No Loader Configured Tests ==========

    @Test
    void getShouldNotLoadWhenNoLoaderConfigured() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        // No loader configured

        CaffeineCache<String, String> cache = new CaffeineCache<>("noLoader", null, config);

        assertNull(cache.get("key1"));
    }

    // ========== Helper Methods ==========

    private CaffeineCache<String, String> createCacheWithLoader(
            java.util.function.Function<String, String> loadFunction) {
        return createCacheWithLoader(new SimpleCacheLoader<>(loadFunction));
    }

    private CaffeineCache<String, String> createCacheWithLoader(CacheLoader<String, String> loader) {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);
        config.setReadThrough(true);
        config.setCacheLoaderFactory(() -> loader);

        return new CaffeineCache<>("testCache", null, config);
    }

    private CaffeineCache<String, String> createExpiringCacheWithLoader(
            long expiryMs, java.util.function.Function<String, String> loadFunction) {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, expiryMs)));
        config.setStatisticsEnabled(true);
        config.setReadThrough(true);
        config.setCacheLoaderFactory(() -> new SimpleCacheLoader<>(loadFunction));

        return new CaffeineCache<>("expiringCache", null, config);
    }

    @SuppressWarnings("unchecked")
    private void registerCreatedListener(CaffeineCache<String, String> cache,
            List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryCreatedListener<String, String> listener = evts -> evts.forEach(events::add);
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        cache.registerCacheEntryListener(config);
    }

    /**
     * Simple CacheLoader implementation for testing.
     */
    private static class SimpleCacheLoader<K, V> implements CacheLoader<K, V> {
        private final java.util.function.Function<K, V> loadFunction;

        SimpleCacheLoader(java.util.function.Function<K, V> loadFunction) {
            this.loadFunction = loadFunction;
        }

        @Override
        public V load(K key) {
            return loadFunction.apply(key);
        }

        @Override
        public Map<K, V> loadAll(Iterable<? extends K> keys) {
            Map<K, V> result = new HashMap<>();
            for (K key : keys) {
                V value = load(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
            return result;
        }
    }
}
