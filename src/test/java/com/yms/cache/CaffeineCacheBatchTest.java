package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.*;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache batch operations.
 */
class CaffeineCacheBatchTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== getAll Tests ==========

    @Test
    void getAllShouldReturnEmptyMapForEmptyKeys() {
        Map<String, String> result = cache.getAll(Collections.emptySet());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllShouldReturnValuesForExistingKeys() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        Map<String, String> result = cache.getAll(Set.of("key1", "key2"));

        assertEquals(2, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals("value2", result.get("key2"));
    }

    @Test
    void getAllShouldNotIncludeMissingKeys() {
        cache.put("key1", "value1");

        Map<String, String> result = cache.getAll(Set.of("key1", "missing"));

        assertEquals(1, result.size());
        assertEquals("value1", result.get("key1"));
        assertFalse(result.containsKey("missing"));
    }

    @Test
    void getAllShouldThrowForNullKeys() {
        assertThrows(NullPointerException.class, () -> cache.getAll(null));
    }

    @Test
    void getAllShouldThrowForSetContainingNullKey() {
        Set<String> keysWithNull = new HashSet<>();
        keysWithNull.add("key1");
        keysWithNull.add(null);

        assertThrows(NullPointerException.class, () -> cache.getAll(keysWithNull));
    }

    @Test
    void getAllShouldNotIncludeExpiredEntries() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key1", "value1");
        expiringCache.put("key2", "value2");

        Thread.sleep(100);

        Map<String, String> result = expiringCache.getAll(Set.of("key1", "key2"));

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllShouldRecordHitsAndMisses() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.getAll(Set.of("key1", "key2", "missing"));

        assertEquals(2, cache.getStatistics().getCacheHits());
        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    // ========== putAll Tests ==========

    @Test
    void putAllShouldInsertAllEntries() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        cache.putAll(map);

        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
    }

    @Test
    void putAllShouldThrowForNullMap() {
        assertThrows(NullPointerException.class, () -> cache.putAll(null));
    }

    @Test
    void putAllShouldThrowForMapWithNullKey() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put(null, "value2");

        assertThrows(NullPointerException.class, () -> cache.putAll(map));
    }

    @Test
    void putAllShouldThrowForMapWithNullValue() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", null);

        assertThrows(NullPointerException.class, () -> cache.putAll(map));
    }

    @Test
    void putAllShouldUpdateExistingEntries() {
        cache.put("key1", "oldValue1");

        Map<String, String> map = new HashMap<>();
        map.put("key1", "newValue1");
        map.put("key2", "value2");

        cache.putAll(map);

        assertEquals("newValue1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
    }

    @Test
    void putAllShouldRecordPuts() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        cache.putAll(map);

        assertEquals(2, cache.getStatistics().getCachePuts());
    }

    @Test
    void putAllShouldFireCreatedEvents() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(events);

        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        cache.putAll(map);

        assertEquals(2, events.size());
    }

    @Test
    void putAllShouldFireUpdatedEventsForExisting() {
        cache.put("key1", "oldValue");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        Map<String, String> map = new HashMap<>();
        map.put("key1", "newValue");

        cache.putAll(map);

        assertEquals(1, events.size());
        assertEquals(EventType.UPDATED, events.get(0).getEventType());
    }

    // ========== removeAll(Set) Tests ==========

    @Test
    void removeAllWithKeysShouldRemoveSpecifiedKeys() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        cache.removeAll(Set.of("key1", "key2"));

        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
    }

    @Test
    void removeAllWithKeysShouldThrowForNullSet() {
        assertThrows(NullPointerException.class, () -> cache.removeAll(null));
    }

    @Test
    void removeAllWithKeysShouldThrowForSetWithNullKey() {
        Set<String> keysWithNull = new HashSet<>();
        keysWithNull.add("key1");
        keysWithNull.add(null);

        assertThrows(NullPointerException.class, () -> cache.removeAll(keysWithNull));
    }

    @Test
    void removeAllWithKeysShouldIgnoreMissingKeys() {
        cache.put("key1", "value1");

        cache.removeAll(Set.of("key1", "missing"));

        assertNull(cache.get("key1"));
        // No exception for missing key
    }

    @Test
    void removeAllWithKeysShouldRecordRemovals() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.removeAll(Set.of("key1", "key2"));

        assertEquals(2, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeAllWithKeysShouldFireRemovedEvents() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListener(events);

        cache.removeAll(Set.of("key1", "key2"));

        assertEquals(2, events.size());
    }

    // ========== removeAll() Tests ==========

    @Test
    void removeAllShouldRemoveAllEntries() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        cache.removeAll();

        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNull(cache.get("key3"));
    }

    @Test
    void removeAllShouldRecordRemovals() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.removeAll();

        assertEquals(2, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeAllShouldFireRemovedEvents() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListener(events);

        cache.removeAll();

        assertEquals(2, events.size());
    }

    @Test
    void removeAllShouldNotFireEventsForExpiredEntries() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key1", "value1");

        Thread.sleep(100);

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListenerOn(expiringCache, events);

        expiringCache.removeAll();

        // Should fire EXPIRED event, not REMOVED
        assertEquals(0, events.size());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void getAllShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.getAll(Set.of("key")));
    }

    @Test
    void putAllShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.putAll(Map.of("key", "value")));
    }

    @Test
    void removeAllWithKeysShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.removeAll(Set.of("key")));
    }

    @Test
    void removeAllShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.removeAll());
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private void registerCreatedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryCreatedListener<String, String> listener = evts -> evts.forEach(events::add);
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        cache.registerCacheEntryListener(config);
    }

    @SuppressWarnings("unchecked")
    private void registerUpdatedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryUpdatedListener<String, String> listener = evts -> evts.forEach(events::add);
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        cache.registerCacheEntryListener(config);
    }

    @SuppressWarnings("unchecked")
    private void registerRemovedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        registerRemovedListenerOn(cache, events);
    }

    @SuppressWarnings("unchecked")
    private void registerRemovedListenerOn(CaffeineCache<String, String> targetCache,
            List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryRemovedListener<String, String> listener = evts -> evts.forEach(events::add);
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        targetCache.registerCacheEntryListener(config);
    }
}
