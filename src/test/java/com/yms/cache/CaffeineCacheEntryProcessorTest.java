package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.processor.EntryProcessor;
import javax.cache.CacheException;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache EntryProcessor operations.
 */
class CaffeineCacheEntryProcessorTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== invoke Tests ==========

    @Test
    void invokeShouldReturnProcessorResult() {
        cache.put("key", "value");

        String result = cache.invoke("key", (entry, args) -> {
            return entry.getValue().toUpperCase();
        });

        assertEquals("VALUE", result);
    }

    @Test
    void invokeShouldAllowModifyingEntry() {
        cache.put("key", "oldValue");

        cache.invoke("key", (entry, args) -> {
            entry.setValue("newValue");
            return null;
        });

        assertEquals("newValue", cache.get("key"));
    }

    @Test
    void invokeShouldAllowCreatingEntry() {
        String result = cache.invoke("key", (entry, args) -> {
            assertFalse(entry.exists());
            entry.setValue("createdValue");
            return "created";
        });

        assertEquals("created", result);
        assertEquals("createdValue", cache.get("key"));
    }

    @Test
    void invokeShouldAllowRemovingEntry() {
        cache.put("key", "value");

        cache.invoke("key", (entry, args) -> {
            entry.remove();
            return null;
        });

        assertNull(cache.get("key"));
    }

    @Test
    void invokeShouldPassArguments() {
        cache.put("key", "value");

        String result = cache.invoke("key", (entry, args) -> {
            return entry.getValue() + "-" + args[0] + "-" + args[1];
        }, "arg1", "arg2");

        assertEquals("value-arg1-arg2", result);
    }

    @Test
    void invokeShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () ->
            cache.invoke(null, (entry, args) -> null));
    }

    @Test
    void invokeShouldThrowForNullProcessor() {
        assertThrows(NullPointerException.class, () ->
            cache.invoke("key", null));
    }

    @Test
    void invokeShouldWrapProcessorException() {
        cache.put("key", "value");

        assertThrows(EntryProcessorException.class, () ->
            cache.invoke("key", (entry, args) -> {
                throw new RuntimeException("Test error");
            }));
    }

    @Test
    void invokeShouldNotSeeExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        Thread.sleep(100);

        Boolean exists = expiringCache.invoke("key", (entry, args) -> entry.exists());

        assertFalse(exists);
    }

    @Test
    void invokeShouldRecordStatisticsForRead() {
        cache.put("key", "value");

        cache.invoke("key", (entry, args) -> entry.getValue());

        // getValue() on existing entry should record a hit
        assertEquals(1, cache.getStatistics().getCacheHits());
    }

    // ========== invokeAll Tests ==========

    @Test
    void invokeAllShouldProcessMultipleEntries() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        Map<String, EntryProcessorResult<String>> results = cache.invokeAll(
            Set.of("key1", "key2"),
            (entry, args) -> entry.getValue().toUpperCase()
        );

        assertEquals(2, results.size());
        assertEquals("VALUE1", results.get("key1").get());
        assertEquals("VALUE2", results.get("key2").get());
    }

    @Test
    void invokeAllShouldHandleMissingKeys() {
        cache.put("key1", "value1");

        Map<String, EntryProcessorResult<String>> results = cache.invokeAll(
            Set.of("key1", "missing"),
            (entry, args) -> entry.exists() ? entry.getValue() : "default"
        );

        assertEquals(2, results.size());
        assertEquals("value1", results.get("key1").get());
        assertEquals("default", results.get("missing").get());
    }

    @Test
    void invokeAllShouldCaptureExceptionsPerKey() {
        cache.put("key1", "value1");
        cache.put("key2", "error");

        Map<String, EntryProcessorResult<String>> results = cache.invokeAll(
            Set.of("key1", "key2"),
            (entry, args) -> {
                if ("error".equals(entry.getValue())) {
                    throw new RuntimeException("Simulated error");
                }
                return entry.getValue();
            }
        );

        assertEquals(2, results.size());
        assertEquals("value1", results.get("key1").get());
        assertThrows(EntryProcessorException.class, () -> results.get("key2").get());
    }

    @Test
    void invokeAllShouldThrowForNullKeys() {
        assertThrows(NullPointerException.class, () ->
            cache.invokeAll(null, (entry, args) -> null));
    }

    @Test
    void invokeAllShouldThrowForNullProcessor() {
        assertThrows(NullPointerException.class, () ->
            cache.invokeAll(Set.of("key"), null));
    }

    @Test
    void invokeAllShouldReturnEmptyMapForEmptyKeys() {
        Map<String, EntryProcessorResult<String>> results = cache.invokeAll(
            Set.of(),
            (entry, args) -> entry.getValue()
        );

        assertTrue(results.isEmpty());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void invokeShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () ->
            cache.invoke("key", (entry, args) -> null));
    }

    @Test
    void invokeAllShouldThrowWhenClosed() {
        cache.close();
        assertThrows(IllegalStateException.class, () ->
            cache.invokeAll(Set.of("key"), (entry, args) -> null));
    }

    // ========== Reentry Detection Tests (I.5) ==========

    @Test
    void entryProcessorShouldNotAllowCacheGetReentry() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Calling cache.get() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                // This reentrant call should be detected and rejected
                return cache.get("key2");
            }));
    }

    @Test
    void entryProcessorShouldNotAllowCachePutReentry() {
        cache.put("key1", "value1");

        // Calling cache.put() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                cache.put("key2", "value2");
                return null;
            }));
    }

    @Test
    void entryProcessorShouldNotAllowCacheRemoveReentry() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Calling cache.remove() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                cache.remove("key2");
                return null;
            }));
    }

    @Test
    void entryProcessorShouldNotAllowNestedInvokeReentry() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Calling cache.invoke() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                return cache.invoke("key2", (e, a) -> e.getValue());
            }));
    }

    @Test
    void entryProcessorShouldNotAllowInvokeAllReentry() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Calling cache.invokeAll() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                cache.invokeAll(Set.of("key2"), (e, a) -> e.getValue());
                return null;
            }));
    }

    @Test
    void entryProcessorShouldNotAllowContainsKeyReentry() {
        cache.put("key1", "value1");

        // Calling cache.containsKey() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                return cache.containsKey("key2");
            }));
    }

    @Test
    void entryProcessorShouldNotAllowClearReentry() {
        cache.put("key1", "value1");

        // Calling cache.clear() inside EntryProcessor should throw CacheException
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                cache.clear();
                return null;
            }));
    }

    @Test
    void entryProcessorReentryExceptionShouldHaveClearMessage() {
        cache.put("key1", "value1");

        CacheException ex = assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> {
                return cache.get("key2");
            }));

        assertTrue(ex.getMessage().contains("reentrant") || ex.getMessage().contains("Reentrant"),
            "Exception message should mention reentrant call");
    }

    @Test
    void cacheOperationsShouldWorkNormallyAfterReentryAttempt() {
        cache.put("key1", "value1");

        // First, try a reentrant call that should fail
        assertThrows(CacheException.class, () ->
            cache.invoke("key1", (entry, args) -> cache.get("key2")));

        // After the failed reentrant call, normal operations should still work
        cache.put("key2", "value2");
        assertEquals("value2", cache.get("key2"));
        assertEquals("value1", cache.get("key1"));

        // And invoke should work normally too
        String result = cache.invoke("key1", (entry, args) -> entry.getValue().toUpperCase());
        assertEquals("VALUE1", result);
    }
}
