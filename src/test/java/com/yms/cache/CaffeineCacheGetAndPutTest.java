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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache getAndPut operation.
 */
class CaffeineCacheGetAndPutTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Basic getAndPut Tests ==========

    @Test
    void getAndPutShouldReturnNullWhenKeyNotPresent() {
        String oldValue = cache.getAndPut("key", "value");

        assertNull(oldValue);
        assertEquals("value", cache.get("key"));
    }

    @Test
    void getAndPutShouldReturnOldValueWhenKeyPresent() {
        cache.put("key", "oldValue");

        String returnedValue = cache.getAndPut("key", "newValue");

        assertEquals("oldValue", returnedValue);
        assertEquals("newValue", cache.get("key"));
    }

    @Test
    void getAndPutShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.getAndPut(null, "value"));
    }

    @Test
    void getAndPutShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.getAndPut("key", null));
    }

    // ========== Expiration Tests ==========

    @Test
    void getAndPutShouldReturnNullForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "oldValue");

        Thread.sleep(100); // Wait for expiration

        // Should return null because entry is expired
        String returnedValue = expiringCache.getAndPut("key", "newValue");

        assertNull(returnedValue);
        assertEquals("newValue", expiringCache.get("key"));
    }

    // ========== Statistics Tests ==========

    @Test
    void getAndPutShouldRecordPutAndMissWhenKeyNotPresent() {
        cache.getAndPut("key", "value");

        assertEquals(1, cache.getStatistics().getCachePuts());
        assertEquals(1, cache.getStatistics().getCacheMisses());
        assertEquals(0, cache.getStatistics().getCacheHits());
    }

    @Test
    void getAndPutShouldRecordPutAndHitWhenKeyPresent() {
        cache.put("key", "oldValue");

        cache.getAndPut("key", "newValue");

        assertEquals(2, cache.getStatistics().getCachePuts()); // original put + getAndPut
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    // ========== Event Tests ==========

    @Test
    void getAndPutShouldFireCreatedEventWhenKeyNotPresent() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(events);

        cache.getAndPut("key", "value");

        assertEquals(1, events.size());
        assertEquals(EventType.CREATED, events.get(0).getEventType());
        assertEquals("key", events.get(0).getKey());
        assertEquals("value", events.get(0).getValue());
    }

    @Test
    void getAndPutShouldFireUpdatedEventWhenKeyPresent() {
        cache.put("key", "oldValue");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        cache.getAndPut("key", "newValue");

        assertEquals(1, events.size());
        assertEquals(EventType.UPDATED, events.get(0).getEventType());
        assertEquals("oldValue", events.get(0).getOldValue());
        assertEquals("newValue", events.get(0).getValue());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void getAndPutShouldThrowWhenClosed() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.getAndPut("key", "value"));
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
}
