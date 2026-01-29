package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.Cache;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache Iterator implementation.
 */
class CaffeineCacheIteratorTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Basic Iterator Tests ==========

    @Test
    void iteratorShouldReturnEmptyForEmptyCache() {
        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();

        assertFalse(iterator.hasNext());
    }

    @Test
    void iteratorShouldThrowNoSuchElementForEmptyCache() {
        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();

        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void iteratorShouldIterateAllEntries() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        Map<String, String> collected = new HashMap<>();
        for (Cache.Entry<String, String> entry : cache) {
            collected.put(entry.getKey(), entry.getValue());
        }

        assertEquals(3, collected.size());
        assertEquals("value1", collected.get("key1"));
        assertEquals("value2", collected.get("key2"));
        assertEquals("value3", collected.get("key3"));
    }

    @Test
    void iteratorShouldReturnCorrectEntryValues() {
        cache.put("key", "value");

        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();
        assertTrue(iterator.hasNext());

        Cache.Entry<String, String> entry = iterator.next();
        assertEquals("key", entry.getKey());
        assertEquals("value", entry.getValue());
    }

    @Test
    void iteratorEntryShouldUnwrapToSelf() {
        cache.put("key", "value");

        Cache.Entry<String, String> entry = cache.iterator().next();

        assertNotNull(entry.unwrap(entry.getClass()));
    }

    // ========== Iterator Remove Tests ==========

    @Test
    void iteratorRemoveShouldRemoveCurrentEntry() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();
        while (iterator.hasNext()) {
            Cache.Entry<String, String> entry = iterator.next();
            if ("key1".equals(entry.getKey())) {
                iterator.remove();
            }
        }

        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
    }

    @Test
    void iteratorRemoveShouldThrowWithoutNext() {
        cache.put("key", "value");

        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();

        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    void iteratorRemoveShouldThrowOnDoubleRemove() {
        cache.put("key", "value");

        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();
        iterator.next();
        iterator.remove();

        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    void iteratorRemoveShouldRecordRemoval() {
        cache.put("key", "value");

        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();
        iterator.next();
        iterator.remove();

        assertEquals(1, cache.getStatistics().getCacheRemovals());
    }

    // ========== Expiration Tests ==========

    @Test
    void iteratorShouldSkipExpiredEntries() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key1", "value1");
        expiringCache.put("key2", "value2");

        Thread.sleep(100);

        int count = 0;
        for (Cache.Entry<String, String> entry : expiringCache) {
            count++;
        }

        assertEquals(0, count);
    }

    // ========== By-Value Semantics Tests ==========

    @Test
    void iteratorShouldReturnCopiesForByValue() {
        YmsConfiguration<String, CaffeineCacheGetPutTest.TestObject> config = new YmsConfiguration<>();
        config.setTypes(String.class, CaffeineCacheGetPutTest.TestObject.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStoreByValue(true);

        CaffeineCache<String, CaffeineCacheGetPutTest.TestObject> byValueCache =
            new CaffeineCache<>("byValue", null, config);

        CaffeineCacheGetPutTest.TestObject original = new CaffeineCacheGetPutTest.TestObject("original");
        byValueCache.put("key", original);

        // Get entry through iterator
        Cache.Entry<String, CaffeineCacheGetPutTest.TestObject> entry = byValueCache.iterator().next();

        // Modify the retrieved value
        entry.getValue().setData("modified");

        // Original cached value should be unchanged
        assertEquals("original", byValueCache.get("key").getData());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void iteratorShouldThrowWhenClosed() {
        cache.close();

        assertThrows(IllegalStateException.class, cache::iterator);
    }
}
