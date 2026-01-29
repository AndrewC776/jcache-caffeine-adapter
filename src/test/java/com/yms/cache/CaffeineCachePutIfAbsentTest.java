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
 * TDD tests for CaffeineCache putIfAbsent operation.
 */
class CaffeineCachePutIfAbsentTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Basic putIfAbsent Tests ==========

    @Test
    void putIfAbsentShouldReturnTrueWhenKeyNotPresent() {
        boolean result = cache.putIfAbsent("key", "value");

        assertTrue(result);
        assertEquals("value", cache.get("key"));
    }

    @Test
    void putIfAbsentShouldReturnFalseWhenKeyPresent() {
        cache.put("key", "existingValue");

        boolean result = cache.putIfAbsent("key", "newValue");

        assertFalse(result);
        assertEquals("existingValue", cache.get("key"));
    }

    @Test
    void putIfAbsentShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.putIfAbsent(null, "value"));
    }

    @Test
    void putIfAbsentShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.putIfAbsent("key", null));
    }

    // ========== Expiration Tests ==========

    @Test
    void putIfAbsentShouldSucceedForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "oldValue");

        Thread.sleep(100); // Wait for expiration

        // Should succeed because entry is expired
        boolean result = expiringCache.putIfAbsent("key", "newValue");

        assertTrue(result);
        assertEquals("newValue", expiringCache.get("key"));
    }

    // ========== Statistics Tests ==========

    @Test
    void putIfAbsentShouldRecordPutOnSuccess() {
        cache.putIfAbsent("key", "value");

        assertEquals(1, cache.getStatistics().getCachePuts());
    }

    @Test
    void putIfAbsentShouldNotRecordPutOnFailure() {
        cache.put("key", "existingValue");
        long putsBeforeAttempt = cache.getStatistics().getCachePuts();

        cache.putIfAbsent("key", "newValue");

        assertEquals(putsBeforeAttempt, cache.getStatistics().getCachePuts());
    }

    @Test
    void putIfAbsentShouldRecordMissOnSuccess() {
        cache.putIfAbsent("key", "value");

        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void putIfAbsentShouldRecordHitOnFailure() {
        cache.put("key", "existingValue");

        cache.putIfAbsent("key", "newValue");

        // The put recorded a miss, the putIfAbsent should record a hit
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    // ========== Event Tests ==========

    @Test
    void putIfAbsentShouldFireCreatedEventOnSuccess() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(events);

        cache.putIfAbsent("key", "value");

        assertEquals(1, events.size());
        assertEquals(EventType.CREATED, events.get(0).getEventType());
        assertEquals("key", events.get(0).getKey());
        assertEquals("value", events.get(0).getValue());
    }

    @Test
    void putIfAbsentShouldNotFireEventOnFailure() {
        cache.put("key", "existingValue");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerCreatedListener(events);

        cache.putIfAbsent("key", "newValue");

        assertEquals(0, events.size());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void putIfAbsentShouldThrowWhenClosed() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.putIfAbsent("key", "value"));
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
}
