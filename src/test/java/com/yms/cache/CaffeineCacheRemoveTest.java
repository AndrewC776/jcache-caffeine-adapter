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
 * TDD tests for CaffeineCache remove operations.
 */
class CaffeineCacheRemoveTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== remove(K) Tests ==========

    @Test
    void removeShouldReturnTrueWhenKeyExists() {
        cache.put("key", "value");

        assertTrue(cache.remove("key"));
        assertNull(cache.get("key"));
    }

    @Test
    void removeShouldReturnFalseWhenKeyNotExists() {
        assertFalse(cache.remove("missing"));
    }

    @Test
    void removeShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.remove(null));
    }

    @Test
    void removeShouldReturnFalseForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        Thread.sleep(100);

        // Should return false because entry is expired
        assertFalse(expiringCache.remove("key"));
    }

    @Test
    void removeShouldRecordRemoval() {
        cache.put("key", "value");

        cache.remove("key");

        assertEquals(1, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeShouldNotRecordRemovalForMissingKey() {
        cache.remove("missing");

        assertEquals(0, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeShouldFireRemovedEvent() {
        cache.put("key", "value");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListener(events);

        cache.remove("key");

        assertEquals(1, events.size());
        assertEquals(EventType.REMOVED, events.get(0).getEventType());
        assertEquals("key", events.get(0).getKey());
        assertEquals("value", events.get(0).getOldValue());
    }

    @Test
    void removeShouldNotFireEventForMissingKey() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListener(events);

        cache.remove("missing");

        assertEquals(0, events.size());
    }

    // ========== remove(K, V) Tests ==========

    @Test
    void removeWithValueShouldReturnTrueWhenMatches() {
        cache.put("key", "value");

        assertTrue(cache.remove("key", "value"));
        assertNull(cache.get("key"));
    }

    @Test
    void removeWithValueShouldReturnFalseWhenValueMismatch() {
        cache.put("key", "value");

        assertFalse(cache.remove("key", "differentValue"));
        assertEquals("value", cache.get("key"));
    }

    @Test
    void removeWithValueShouldReturnFalseWhenKeyNotExists() {
        assertFalse(cache.remove("missing", "value"));
    }

    @Test
    void removeWithValueShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.remove(null, "value"));
    }

    @Test
    void removeWithValueShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.remove("key", null));
    }

    @Test
    void removeWithValueShouldRecordRemovalOnSuccess() {
        cache.put("key", "value");

        cache.remove("key", "value");

        assertEquals(1, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeWithValueShouldNotRecordRemovalOnFailure() {
        cache.put("key", "value");

        cache.remove("key", "differentValue");

        assertEquals(0, cache.getStatistics().getCacheRemovals());
    }

    @Test
    void removeWithValueShouldRecordHitOnMatch() {
        cache.put("key", "value");

        cache.remove("key", "value");

        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    @Test
    void removeWithValueShouldRecordMissOnMismatch() {
        cache.put("key", "value");

        cache.remove("key", "differentValue");

        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void removeWithValueShouldRecordMissWhenKeyNotExists() {
        cache.remove("missing", "value");

        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    // ========== getAndRemove Tests ==========

    @Test
    void getAndRemoveShouldReturnValueWhenKeyExists() {
        cache.put("key", "value");

        String result = cache.getAndRemove("key");

        assertEquals("value", result);
        assertNull(cache.get("key"));
    }

    @Test
    void getAndRemoveShouldReturnNullWhenKeyNotExists() {
        assertNull(cache.getAndRemove("missing"));
    }

    @Test
    void getAndRemoveShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.getAndRemove(null));
    }

    @Test
    void getAndRemoveShouldReturnNullForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        Thread.sleep(100);

        assertNull(expiringCache.getAndRemove("key"));
    }

    @Test
    void getAndRemoveShouldRecordRemovalAndHitOnSuccess() {
        cache.put("key", "value");

        cache.getAndRemove("key");

        assertEquals(1, cache.getStatistics().getCacheRemovals());
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    @Test
    void getAndRemoveShouldRecordMissOnFailure() {
        cache.getAndRemove("missing");

        assertEquals(0, cache.getStatistics().getCacheRemovals());
        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void getAndRemoveShouldFireRemovedEvent() {
        cache.put("key", "value");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerRemovedListener(events);

        cache.getAndRemove("key");

        assertEquals(1, events.size());
        assertEquals(EventType.REMOVED, events.get(0).getEventType());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void removeShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.remove("key"));
    }

    @Test
    void removeWithValueShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.remove("key", "value"));
    }

    @Test
    void getAndRemoveShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.getAndRemove("key"));
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private void registerRemovedListener(List<CacheEntryEvent<? extends String, ? extends String>> events) {
        CacheEntryRemovedListener<String, String> listener = evts -> evts.forEach(events::add);
        Factory<CacheEntryListener<String, String>> listenerFactory = () -> listener;
        MutableCacheEntryListenerConfiguration<String, String> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) listenerFactory, null, true, true);
        cache.registerCacheEntryListener(config);
    }
}
