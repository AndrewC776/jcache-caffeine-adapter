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
 * TDD tests for CaffeineCache replace operations.
 */
class CaffeineCacheReplaceTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== replace(K, V) Tests ==========

    @Test
    void replaceShouldReturnTrueWhenKeyExists() {
        cache.put("key", "oldValue");

        assertTrue(cache.replace("key", "newValue"));
        assertEquals("newValue", cache.get("key"));
    }

    @Test
    void replaceShouldReturnFalseWhenKeyNotExists() {
        assertFalse(cache.replace("missing", "value"));
        assertNull(cache.get("missing"));
    }

    @Test
    void replaceShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.replace(null, "value"));
    }

    @Test
    void replaceShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.replace("key", null));
    }

    @Test
    void replaceShouldReturnFalseForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "oldValue");

        Thread.sleep(100);

        // Should return false because entry is expired
        assertFalse(expiringCache.replace("key", "newValue"));
    }

    @Test
    void replaceShouldRecordPutAndHitOnSuccess() {
        cache.put("key", "oldValue");

        cache.replace("key", "newValue");

        assertEquals(2, cache.getStatistics().getCachePuts()); // original put + replace
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    @Test
    void replaceShouldRecordMissOnFailure() {
        cache.replace("missing", "value");

        assertEquals(0, cache.getStatistics().getCachePuts());
        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void replaceShouldFireUpdatedEvent() {
        cache.put("key", "oldValue");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        cache.replace("key", "newValue");

        assertEquals(1, events.size());
        assertEquals(EventType.UPDATED, events.get(0).getEventType());
        assertEquals("oldValue", events.get(0).getOldValue());
        assertEquals("newValue", events.get(0).getValue());
    }

    @Test
    void replaceShouldNotFireEventOnFailure() {
        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        cache.replace("missing", "value");

        assertEquals(0, events.size());
    }

    // ========== replace(K, V, V) Tests ==========

    @Test
    void replaceWithOldValueShouldReturnTrueWhenMatches() {
        cache.put("key", "oldValue");

        assertTrue(cache.replace("key", "oldValue", "newValue"));
        assertEquals("newValue", cache.get("key"));
    }

    @Test
    void replaceWithOldValueShouldReturnFalseWhenMismatch() {
        cache.put("key", "oldValue");

        assertFalse(cache.replace("key", "wrongOldValue", "newValue"));
        assertEquals("oldValue", cache.get("key"));
    }

    @Test
    void replaceWithOldValueShouldReturnFalseWhenKeyNotExists() {
        assertFalse(cache.replace("missing", "oldValue", "newValue"));
    }

    @Test
    void replaceWithOldValueShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.replace(null, "old", "new"));
    }

    @Test
    void replaceWithOldValueShouldThrowForNullOldValue() {
        assertThrows(NullPointerException.class, () -> cache.replace("key", null, "new"));
    }

    @Test
    void replaceWithOldValueShouldThrowForNullNewValue() {
        assertThrows(NullPointerException.class, () -> cache.replace("key", "old", null));
    }

    @Test
    void replaceWithOldValueShouldRecordPutAndHitOnSuccess() {
        cache.put("key", "oldValue");

        cache.replace("key", "oldValue", "newValue");

        assertEquals(2, cache.getStatistics().getCachePuts());
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    @Test
    void replaceWithOldValueShouldRecordMissOnMismatch() {
        cache.put("key", "oldValue");

        cache.replace("key", "wrongOldValue", "newValue");

        assertEquals(1, cache.getStatistics().getCachePuts()); // only original put
        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void replaceWithOldValueShouldRecordMissWhenKeyNotExists() {
        cache.replace("missing", "oldValue", "newValue");

        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    // ========== getAndReplace Tests ==========

    @Test
    void getAndReplaceShouldReturnOldValueWhenKeyExists() {
        cache.put("key", "oldValue");

        String result = cache.getAndReplace("key", "newValue");

        assertEquals("oldValue", result);
        assertEquals("newValue", cache.get("key"));
    }

    @Test
    void getAndReplaceShouldReturnNullWhenKeyNotExists() {
        String result = cache.getAndReplace("missing", "value");

        assertNull(result);
        assertNull(cache.get("missing"));
    }

    @Test
    void getAndReplaceShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.getAndReplace(null, "value"));
    }

    @Test
    void getAndReplaceShouldThrowForNullValue() {
        assertThrows(NullPointerException.class, () -> cache.getAndReplace("key", null));
    }

    @Test
    void getAndReplaceShouldReturnNullForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "oldValue");

        Thread.sleep(100);

        assertNull(expiringCache.getAndReplace("key", "newValue"));
    }

    @Test
    void getAndReplaceShouldRecordPutAndHitOnSuccess() {
        cache.put("key", "oldValue");

        cache.getAndReplace("key", "newValue");

        assertEquals(2, cache.getStatistics().getCachePuts());
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    @Test
    void getAndReplaceShouldRecordMissOnFailure() {
        cache.getAndReplace("missing", "value");

        assertEquals(0, cache.getStatistics().getCachePuts());
        assertEquals(1, cache.getStatistics().getCacheMisses());
    }

    @Test
    void getAndReplaceShouldFireUpdatedEvent() {
        cache.put("key", "oldValue");

        List<CacheEntryEvent<? extends String, ? extends String>> events = new ArrayList<>();
        registerUpdatedListener(events);

        cache.getAndReplace("key", "newValue");

        assertEquals(1, events.size());
        assertEquals(EventType.UPDATED, events.get(0).getEventType());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void replaceShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.replace("key", "value"));
    }

    @Test
    void replaceWithOldValueShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.replace("key", "old", "new"));
    }

    @Test
    void getAndReplaceShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () -> cache.getAndReplace("key", "value"));
    }

    // ========== Helper Methods ==========

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
