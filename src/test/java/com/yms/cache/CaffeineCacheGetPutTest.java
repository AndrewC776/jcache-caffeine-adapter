package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import com.yms.cache.stats.JCacheStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.Cache;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.*;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache get/put operations.
 * Tests core cache operations with expiration, events, and statistics.
 */
class CaffeineCacheGetPutTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Basic Get Tests ==========

    @Test
    void getShouldReturnNullForMissingKey() {
        assertNull(cache.get("missing"));
    }

    @Test
    void getShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.get(null));
    }

    // ========== Basic Put Tests ==========

    @Test
    void putAndGetShouldWork() {
        cache.put("key", "value");

        assertEquals("value", cache.get("key"));
    }

    @Test
    void putShouldOverwriteExistingValue() {
        cache.put("key", "value1");
        cache.put("key", "value2");

        assertEquals("value2", cache.get("key"));
    }

    @Test
    void putShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
    }

    @Test
    void putShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.put("key", null));
    }

    // ========== Expiration Tests ==========

    @Test
    void getShouldReturnNullForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));
        config.setStatisticsEnabled(true);

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        // Verify it's there initially
        assertEquals("value", expiringCache.get("key"));

        Thread.sleep(100); // Wait for expiration

        // Should return null after expiration
        assertNull(expiringCache.get("key"));
    }

    @Test
    void expiredEntryShouldBeTreatedAsMiss() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));
        config.setStatisticsEnabled(true);

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        Thread.sleep(100);

        // Access expired entry
        expiringCache.get("key");

        JCacheStatistics stats = expiringCache.getStatistics();
        assertTrue(stats.getCacheMisses() > 0, "Expired entry access should count as miss");
    }

    // ========== Statistics Tests ==========

    @Test
    void getShouldRecordHit() {
        cache.put("key", "value");
        cache.get("key");

        JCacheStatistics stats = cache.getStatistics();
        assertEquals(1, stats.getCacheHits());
    }

    @Test
    void getShouldRecordMiss() {
        cache.get("nonexistent");

        JCacheStatistics stats = cache.getStatistics();
        assertEquals(1, stats.getCacheMisses());
    }

    @Test
    void putShouldRecordPut() {
        cache.put("key", "value");

        JCacheStatistics stats = cache.getStatistics();
        assertEquals(1, stats.getCachePuts());
    }

    @Test
    void putShouldRecordPutOnUpdate() {
        cache.put("key", "value1");
        cache.put("key", "value2");

        JCacheStatistics stats = cache.getStatistics();
        assertEquals(2, stats.getCachePuts());
    }

    // ========== Event Tests ==========

    @Test
    void putShouldFireCreatedEvent() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(events);

        cache.put("key", "value");

        assertEquals(1, events.size());
        assertEquals(EventType.CREATED, events.get(0).getEventType());
        assertEquals("key", events.get(0).getKey());
        assertEquals("value", events.get(0).getValue());
    }

    @Test
    void putShouldFireUpdatedEventOnOverwrite() {
        cache.put("key", "value1");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        cache.put("key", "value2");

        assertEquals(1, events.size());
        assertEquals(EventType.UPDATED, events.get(0).getEventType());
        assertEquals("value1", events.get(0).getOldValue());
        assertEquals("value2", events.get(0).getValue());
    }

    @Test
    void getShouldFireExpiredEventOnExpiredAccess() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerExpiredListener(expiringCache, events);

        Thread.sleep(100);
        expiringCache.get("key");

        assertEquals(1, events.size());
        assertEquals(EventType.EXPIRED, events.get(0).getEventType());
    }

    // ========== By-Value Semantics Tests ==========

    @Test
    void storeByValueShouldCopyOnPut() {
        YmsConfiguration<String, TestObject> config = new YmsConfiguration<>();
        config.setTypes(String.class, TestObject.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStoreByValue(true);

        CaffeineCache<String, TestObject> byValueCache = new CaffeineCache<>("byValue", null, config);

        TestObject original = new TestObject("original");
        byValueCache.put("key", original);

        // Modify the original
        original.setData("modified");

        // Cached value should be unchanged (it was copied)
        TestObject retrieved = byValueCache.get("key");
        assertEquals("original", retrieved.getData());
    }

    @Test
    void storeByValueShouldCopyOnGet() {
        YmsConfiguration<String, TestObject> config = new YmsConfiguration<>();
        config.setTypes(String.class, TestObject.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStoreByValue(true);

        CaffeineCache<String, TestObject> byValueCache = new CaffeineCache<>("byValue", null, config);
        byValueCache.put("key", new TestObject("original"));

        // Get and modify
        TestObject retrieved1 = byValueCache.get("key");
        retrieved1.setData("modified");

        // Next get should return original value
        TestObject retrieved2 = byValueCache.get("key");
        assertEquals("original", retrieved2.getData());
    }

    @Test
    void storeByReferenceShouldShareReference() {
        YmsConfiguration<String, TestObject> config = new YmsConfiguration<>();
        config.setTypes(String.class, TestObject.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStoreByValue(false);

        CaffeineCache<String, TestObject> byRefCache = new CaffeineCache<>("byRef", null, config);

        TestObject original = new TestObject("original");
        byRefCache.put("key", original);

        // Modify the original
        original.setData("modified");

        // Cached value should reflect the change (same reference)
        TestObject retrieved = byRefCache.get("key");
        assertEquals("modified", retrieved.getData());
    }

    // ========== Helper Methods ==========

    private void registerCreatedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryCreatedListener<String, String> listener = evts -> evts.forEach(events::add);
        registerListener(cache, listener);
    }

    private void registerUpdatedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryUpdatedListener<String, String> listener = evts -> evts.forEach(events::add);
        registerListener(cache, listener);
    }

    private void registerExpiredListener(CaffeineCache<String, String> targetCache,
            List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryExpiredListener<String, String> listener = evts -> evts.forEach(events::add);
        registerListener(targetCache, listener);
    }

    @SuppressWarnings("unchecked")
    private void registerListener(Cache<String, String> targetCache,
            CacheEntryListener<String, String> listener) {
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        targetCache.registerCacheEntryListener(config);
    }

    // ========== Test Helper Class ==========

    static class TestObject implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String data;

        TestObject(String data) {
            this.data = data;
        }

        String getData() {
            return data;
        }

        void setData(String data) {
            this.data = data;
        }
    }
}
