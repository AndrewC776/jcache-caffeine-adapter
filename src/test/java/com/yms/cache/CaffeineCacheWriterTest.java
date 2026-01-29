package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.Test;
import javax.cache.Cache;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache write-through (CacheWriter) functionality.
 */
class CaffeineCacheWriterTest {

    // ========== Basic Write-Through Tests ==========

    @Test
    void putShouldCallWriterBeforeCaching() {
        List<String> writeOrder = new ArrayList<>();
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeOrder.add("writer:" + entry.getKey());
            }
        });

        cache.put("key1", "value1");

        assertEquals(1, writeOrder.size());
        assertEquals("writer:key1", writeOrder.get(0));
        assertEquals("value1", cache.get("key1")); // Should be cached
    }

    @Test
    void putShouldNotCacheWhenWriterFails() {
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                throw new RuntimeException("Simulated write failure");
            }
        });

        assertThrows(CacheWriterException.class, () -> cache.put("key1", "value1"));
        assertNull(cache.get("key1")); // Should NOT be cached
    }

    @Test
    void removeShouldCallWriterBeforeRemoving() {
        AtomicInteger deleteCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                deleteCount.incrementAndGet();
            }
        });

        cache.put("key1", "value1");
        cache.remove("key1");

        assertEquals(1, deleteCount.get());
        assertNull(cache.get("key1"));
    }

    @Test
    void removeShouldNotRemoveWhenWriterFails() {
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                throw new RuntimeException("Simulated delete failure");
            }
        });

        cache.put("key1", "value1");

        assertThrows(CacheWriterException.class, () -> cache.remove("key1"));
        assertEquals("value1", cache.get("key1")); // Should still be cached
    }

    // ========== putAll Write-Through Tests ==========

    @Test
    void putAllShouldCallWriterForEachEntry() {
        Set<String> writtenKeys = new HashSet<>();
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void writeAll(Collection<Cache.Entry<? extends String, ? extends String>> entries) {
                for (Cache.Entry<? extends String, ? extends String> entry : entries) {
                    writtenKeys.add(entry.getKey());
                }
            }
        });

        cache.putAll(Map.of("key1", "value1", "key2", "value2"));

        assertEquals(2, writtenKeys.size());
        assertTrue(writtenKeys.contains("key1"));
        assertTrue(writtenKeys.contains("key2"));
    }

    // ========== removeAll Write-Through Tests ==========

    @Test
    void removeAllShouldCallWriterForEachKey() {
        Set<String> deletedKeys = new HashSet<>();
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void deleteAll(Collection<?> keys) {
                for (Object key : keys) {
                    deletedKeys.add((String) key);
                }
            }
        });

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.removeAll(Set.of("key1", "key2"));

        assertEquals(2, deletedKeys.size());
        assertTrue(deletedKeys.contains("key1"));
        assertTrue(deletedKeys.contains("key2"));
    }

    // ========== replace Write-Through Tests ==========

    @Test
    void replaceShouldCallWriterOnSuccess() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.put("key1", "oldValue");
        int countAfterPut = writeCount.get();

        cache.replace("key1", "newValue");

        assertEquals(countAfterPut + 1, writeCount.get());
    }

    @Test
    void replaceShouldNotCallWriterOnMiss() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.replace("missing", "value"); // Should not write

        assertEquals(0, writeCount.get());
    }

    // ========== getAndPut Write-Through Tests ==========

    @Test
    void getAndPutShouldCallWriter() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.getAndPut("key1", "value1");

        assertEquals(1, writeCount.get());
    }

    // ========== getAndRemove Write-Through Tests ==========

    @Test
    void getAndRemoveShouldCallWriter() {
        AtomicInteger deleteCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                deleteCount.incrementAndGet();
            }
        });

        cache.put("key1", "value1");
        cache.getAndRemove("key1");

        assertEquals(1, deleteCount.get());
    }

    // ========== putIfAbsent Write-Through Tests ==========

    @Test
    void putIfAbsentShouldCallWriterOnInsert() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        boolean inserted = cache.putIfAbsent("key1", "value1");

        assertTrue(inserted);
        assertEquals(1, writeCount.get());
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    void putIfAbsentShouldNotCallWriterWhenKeyExists() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.put("key1", "existingValue");
        int countAfterPut = writeCount.get();

        boolean inserted = cache.putIfAbsent("key1", "newValue");

        assertFalse(inserted);
        assertEquals(countAfterPut, writeCount.get()); // No additional write
        assertEquals("existingValue", cache.get("key1")); // Original value unchanged
    }

    @Test
    void putIfAbsentShouldNotCacheWhenWriterFails() {
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                throw new RuntimeException("Simulated write failure");
            }
        });

        assertThrows(CacheWriterException.class, () -> cache.putIfAbsent("key1", "value1"));
        assertNull(cache.get("key1")); // Should NOT be cached
    }

    // ========== replace(k,old,new) Write-Through Tests ==========

    @Test
    void replaceWithOldValueShouldCallWriterOnMatch() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.put("key1", "oldValue");
        int countAfterPut = writeCount.get();

        boolean replaced = cache.replace("key1", "oldValue", "newValue");

        assertTrue(replaced);
        assertEquals(countAfterPut + 1, writeCount.get());
        assertEquals("newValue", cache.get("key1"));
    }

    @Test
    void replaceWithOldValueShouldNotCallWriterOnMismatch() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.put("key1", "actualValue");
        int countAfterPut = writeCount.get();

        boolean replaced = cache.replace("key1", "wrongOldValue", "newValue");

        assertFalse(replaced);
        assertEquals(countAfterPut, writeCount.get()); // No additional write
        assertEquals("actualValue", cache.get("key1")); // Original value unchanged
    }

    @Test
    void replaceWithOldValueShouldNotCallWriterOnMiss() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        boolean replaced = cache.replace("missing", "oldValue", "newValue");

        assertFalse(replaced);
        assertEquals(0, writeCount.get());
    }

    // ========== remove(k,v) Write-Through Tests ==========

    @Test
    void removeWithValueShouldCallWriterOnMatch() {
        AtomicInteger deleteCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                deleteCount.incrementAndGet();
            }
        });

        cache.put("key1", "value1");
        boolean removed = cache.remove("key1", "value1");

        assertTrue(removed);
        assertEquals(1, deleteCount.get());
        assertNull(cache.get("key1"));
    }

    @Test
    void removeWithValueShouldNotCallWriterOnMismatch() {
        AtomicInteger deleteCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                deleteCount.incrementAndGet();
            }
        });

        cache.put("key1", "actualValue");
        boolean removed = cache.remove("key1", "wrongValue");

        assertFalse(removed);
        assertEquals(0, deleteCount.get()); // Writer NOT called
        assertEquals("actualValue", cache.get("key1")); // Still cached
    }

    @Test
    void removeWithValueShouldNotCallWriterOnMiss() {
        AtomicInteger deleteCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void delete(Object key) {
                deleteCount.incrementAndGet();
            }
        });

        boolean removed = cache.remove("missing", "value");

        assertFalse(removed);
        assertEquals(0, deleteCount.get());
    }

    // ========== getAndReplace Write-Through Tests ==========

    @Test
    void getAndReplaceShouldCallWriterOnSuccess() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        cache.put("key1", "oldValue");
        int countAfterPut = writeCount.get();

        String oldValue = cache.getAndReplace("key1", "newValue");

        assertEquals("oldValue", oldValue);
        assertEquals(countAfterPut + 1, writeCount.get());
        assertEquals("newValue", cache.get("key1"));
    }

    @Test
    void getAndReplaceShouldNotCallWriterOnMiss() {
        AtomicInteger writeCount = new AtomicInteger(0);
        CaffeineCache<String, String> cache = createCacheWithWriter(new TestCacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                writeCount.incrementAndGet();
            }
        });

        String oldValue = cache.getAndReplace("missing", "newValue");

        assertNull(oldValue);
        assertEquals(0, writeCount.get());
    }

    // ========== No Writer Configured Tests ==========

    @Test
    void putShouldWorkWhenNoWriterConfigured() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        // No writer configured

        CaffeineCache<String, String> cache = new CaffeineCache<>("noWriter", null, config);

        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }

    // ========== Helper Methods ==========

    private CaffeineCache<String, String> createCacheWithWriter(CacheWriter<String, String> writer) {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);
        config.setWriteThrough(true);
        config.setCacheWriterFactory(() -> writer);

        return new CaffeineCache<>("testCache", null, config);
    }

    /**
     * Base CacheWriter implementation for testing with default no-op methods.
     */
    private static abstract class TestCacheWriter<K, V> implements CacheWriter<K, V> {
        @Override
        public void write(Cache.Entry<? extends K, ? extends V> entry) {
            // Default no-op
        }

        @Override
        public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
            for (Cache.Entry<? extends K, ? extends V> entry : entries) {
                write(entry);
            }
        }

        @Override
        public void delete(Object key) {
            // Default no-op
        }

        @Override
        public void deleteAll(Collection<?> keys) {
            for (Object key : keys) {
                delete(key);
            }
        }
    }
}
