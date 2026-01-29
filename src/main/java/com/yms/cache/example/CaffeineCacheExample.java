package com.yms.cache.example;

import com.yms.cache.CaffeineCache;
import com.yms.cache.config.YmsConfiguration;

import javax.cache.Cache;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.*;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive example demonstrating all CaffeineCache features.
 *
 * This example covers:
 * - Basic CRUD operations (get, put, remove, containsKey, clear)
 * - Batch operations (getAll, putAll, removeAll)
 * - Conditional operations (putIfAbsent, replace, getAndPut, getAndRemove, getAndReplace)
 * - EntryProcessor (invoke, invokeAll)
 * - Read-through with CacheLoader
 * - Write-through with CacheWriter
 * - Asynchronous loadAll
 * - Event listeners
 * - Statistics
 * - Expiry policies
 * - Iterator
 */
public class CaffeineCacheExample {

    // Mock database for read-through/write-through demos
    private static final Map<String, String> mockDatabase = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("CaffeineCache (JCache/JSR-107) Complete Example");
        System.out.println("=".repeat(60));

        // Initialize mock database
        initMockDatabase();

        // Run all examples
        example1_BasicCRUD();
        example2_BatchOperations();
        example3_ConditionalOperations();
        example4_EntryProcessor();
        example5_ReadThrough();
        example6_WriteThrough();
        example7_LoadAll();
        example8_EventListeners();
        example9_Statistics();
        example10_ExpiryPolicy();
        example11_Iterator();
        example12_Lifecycle();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("All examples completed successfully!");
        System.out.println("=".repeat(60));
    }

    private static void initMockDatabase() {
        mockDatabase.put("user:1", "Alice");
        mockDatabase.put("user:2", "Bob");
        mockDatabase.put("user:3", "Charlie");
        mockDatabase.put("product:1", "Laptop");
        mockDatabase.put("product:2", "Phone");
    }

    // ========== Example 1: Basic CRUD Operations ==========
    private static void example1_BasicCRUD() {
        printHeader("Example 1: Basic CRUD Operations");

        CaffeineCache<String, String> cache = createSimpleCache("basicCache");

        // PUT - Store values
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        System.out.println("PUT: Added 3 entries");

        // GET - Retrieve values
        String value1 = cache.get("key1");
        String missing = cache.get("nonexistent");
        System.out.println("GET key1: " + value1);
        System.out.println("GET nonexistent: " + missing);

        // CONTAINS KEY - Check existence
        boolean exists = cache.containsKey("key1");
        boolean notExists = cache.containsKey("nonexistent");
        System.out.println("CONTAINS key1: " + exists);
        System.out.println("CONTAINS nonexistent: " + notExists);

        // REMOVE - Delete entry
        boolean removed = cache.remove("key2");
        System.out.println("REMOVE key2: " + removed);
        System.out.println("GET key2 after remove: " + cache.get("key2"));

        // CLEAR - Remove all entries
        cache.clear();
        System.out.println("CLEAR: All entries removed");
        System.out.println("GET key1 after clear: " + cache.get("key1"));

        cache.close();
    }

    // ========== Example 2: Batch Operations ==========
    private static void example2_BatchOperations() {
        printHeader("Example 2: Batch Operations");

        CaffeineCache<String, String> cache = createSimpleCache("batchCache");

        // PUT ALL - Store multiple values at once
        Map<String, String> entries = new HashMap<>();
        entries.put("a", "Apple");
        entries.put("b", "Banana");
        entries.put("c", "Cherry");
        cache.putAll(entries);
        System.out.println("PUT ALL: Added " + entries.size() + " entries");

        // GET ALL - Retrieve multiple values
        Set<String> keys = Set.of("a", "b", "c", "d"); // "d" doesn't exist
        Map<String, String> results = cache.getAll(keys);
        System.out.println("GET ALL results:");
        results.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // REMOVE ALL (specific keys)
        cache.removeAll(Set.of("a", "b"));
        System.out.println("REMOVE ALL [a, b]: Removed 2 entries");
        System.out.println("Remaining entries: " + cache.getAll(Set.of("a", "b", "c")));

        // REMOVE ALL (all entries)
        cache.put("x", "X-ray");
        cache.put("y", "Yellow");
        cache.removeAll();
        System.out.println("REMOVE ALL: All entries removed");

        cache.close();
    }

    // ========== Example 3: Conditional Operations ==========
    private static void example3_ConditionalOperations() {
        printHeader("Example 3: Conditional Operations");

        CaffeineCache<String, String> cache = createSimpleCache("conditionalCache");

        // PUT IF ABSENT - Only add if key doesn't exist
        boolean added1 = cache.putIfAbsent("key1", "first");
        System.out.println("PUT IF ABSENT key1=first: " + added1);

        boolean added2 = cache.putIfAbsent("key1", "second");
        System.out.println("PUT IF ABSENT key1=second (already exists): " + added2);
        System.out.println("Current value of key1: " + cache.get("key1"));

        // REPLACE - Only update if key exists
        cache.put("key2", "original");
        boolean replaced1 = cache.replace("key2", "updated");
        System.out.println("REPLACE key2: " + replaced1 + ", value: " + cache.get("key2"));

        boolean replaced2 = cache.replace("nonexistent", "value");
        System.out.println("REPLACE nonexistent: " + replaced2);

        // REPLACE with old value check - CAS operation
        boolean cas1 = cache.replace("key2", "updated", "newest");
        System.out.println("REPLACE key2 (updated->newest): " + cas1);

        boolean cas2 = cache.replace("key2", "wrong", "failed");
        System.out.println("REPLACE key2 (wrong->failed): " + cas2);
        System.out.println("Current value of key2: " + cache.get("key2"));

        // GET AND PUT - Get old value while setting new
        String oldValue = cache.getAndPut("key2", "getAndPut-value");
        System.out.println("GET AND PUT key2: old=" + oldValue + ", new=" + cache.get("key2"));

        // GET AND REMOVE - Get value while removing
        String removedValue = cache.getAndRemove("key2");
        System.out.println("GET AND REMOVE key2: " + removedValue);
        System.out.println("key2 exists after removal: " + cache.containsKey("key2"));

        // GET AND REPLACE - Get old value while replacing
        cache.put("key3", "original3");
        String replaced = cache.getAndReplace("key3", "replaced3");
        System.out.println("GET AND REPLACE key3: old=" + replaced + ", new=" + cache.get("key3"));

        cache.close();
    }

    // ========== Example 4: EntryProcessor ==========
    private static void example4_EntryProcessor() {
        printHeader("Example 4: EntryProcessor (Atomic Operations)");

        CaffeineCache<String, Integer> cache = createCache("processorCache", Integer.class);

        // Initialize counters
        cache.put("counter1", 0);
        cache.put("counter2", 100);

        // INVOKE - Atomic increment
        Integer result = cache.invoke("counter1", (entry, args) -> {
            int current = entry.getValue();
            int increment = (int) args[0];
            entry.setValue(current + increment);
            return current; // Return old value
        }, 5);
        System.out.println("INVOKE increment counter1 by 5: old=" + result + ", new=" + cache.get("counter1"));

        // INVOKE - Create entry if not exists
        String created = cache.invoke("newKey", (entry, args) -> {
            if (!entry.exists()) {
                entry.setValue(42);
                return "created";
            }
            return "existed";
        });
        System.out.println("INVOKE create newKey: " + created + ", value=" + cache.get("newKey"));

        // INVOKE - Conditional remove
        Boolean removed = cache.invoke("newKey", (entry, args) -> {
            if (entry.exists() && entry.getValue() < 50) {
                entry.remove();
                return true;
            }
            return false;
        });
        System.out.println("INVOKE conditional remove: " + removed);

        // INVOKE ALL - Process multiple entries
        cache.put("item1", 10);
        cache.put("item2", 20);
        cache.put("item3", 30);

        EntryProcessor<String, Integer, Integer> doubler = (entry, args) -> {
            int old = entry.getValue();
            entry.setValue(old * 2);
            return old;
        };

        var results = cache.invokeAll(Set.of("item1", "item2", "item3"), doubler);
        System.out.println("INVOKE ALL (double values):");
        results.forEach((k, r) -> {
            System.out.println("  " + k + ": old=" + r.get() + ", new=" + cache.get(k));
        });

        cache.close();
    }

    // ========== Example 5: Read-Through with CacheLoader ==========
    private static void example5_ReadThrough() {
        printHeader("Example 5: Read-Through (CacheLoader)");

        // Create cache with CacheLoader that reads from mock database
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                System.out.println("  [Loader] Loading from database: " + key);
                return mockDatabase.get(key);
            }

            @Override
            public Map<String, String> loadAll(Iterable<? extends String> keys) {
                System.out.println("  [Loader] Batch loading from database");
                Map<String, String> result = new HashMap<>();
                for (String key : keys) {
                    String value = mockDatabase.get(key);
                    if (value != null) {
                        result.put(key, value);
                    }
                }
                return result;
            }
        };

        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setReadThrough(true);
        config.setCacheLoaderFactory(() -> loader);

        CaffeineCache<String, String> cache = new CaffeineCache<>("readThroughCache", null, config);

        // First access - triggers load from database
        System.out.println("First GET user:1 (cache miss, triggers load):");
        String user1 = cache.get("user:1");
        System.out.println("Result: " + user1);

        // Second access - served from cache
        System.out.println("\nSecond GET user:1 (cache hit):");
        String user1Again = cache.get("user:1");
        System.out.println("Result: " + user1Again);

        // GET ALL - triggers batch load for missing keys
        System.out.println("\nGET ALL [user:1, user:2, user:3] (partial cache miss):");
        Map<String, String> users = cache.getAll(Set.of("user:1", "user:2", "user:3"));
        users.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // GET non-existent key - loader returns null
        System.out.println("\nGET nonexistent:");
        String nonexistent = cache.get("nonexistent");
        System.out.println("Result: " + nonexistent);

        cache.close();
    }

    // ========== Example 6: Write-Through with CacheWriter ==========
    private static void example6_WriteThrough() {
        printHeader("Example 6: Write-Through (CacheWriter)");

        // Separate storage to show write-through effect
        Map<String, String> writeStorage = new ConcurrentHashMap<>();

        CacheWriter<String, String> writer = new CacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) {
                System.out.println("  [Writer] Writing to storage: " + entry.getKey() + "=" + entry.getValue());
                writeStorage.put(entry.getKey(), entry.getValue());
            }

            @Override
            public void writeAll(Collection<Cache.Entry<? extends String, ? extends String>> entries) {
                System.out.println("  [Writer] Batch writing " + entries.size() + " entries");
                for (Cache.Entry<? extends String, ? extends String> entry : entries) {
                    writeStorage.put(entry.getKey(), entry.getValue());
                }
            }

            @Override
            public void delete(Object key) {
                System.out.println("  [Writer] Deleting from storage: " + key);
                writeStorage.remove(key);
            }

            @Override
            public void deleteAll(Collection<?> keys) {
                System.out.println("  [Writer] Batch deleting " + keys.size() + " entries");
                for (Object key : keys) {
                    writeStorage.remove(key);
                }
            }
        };

        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setWriteThrough(true);
        config.setCacheWriterFactory(() -> writer);

        CaffeineCache<String, String> cache = new CaffeineCache<>("writeThroughCache", null, config);

        // PUT - triggers write
        System.out.println("PUT data:1=Hello:");
        cache.put("data:1", "Hello");
        System.out.println("Storage after PUT: " + writeStorage);

        // PUT ALL - triggers batch write
        System.out.println("\nPUT ALL [data:2, data:3]:");
        cache.putAll(Map.of("data:2", "World", "data:3", "!"));
        System.out.println("Storage after PUT ALL: " + writeStorage);

        // REMOVE - triggers delete
        System.out.println("\nREMOVE data:1:");
        cache.remove("data:1");
        System.out.println("Storage after REMOVE: " + writeStorage);

        cache.close();
    }

    // ========== Example 7: Asynchronous loadAll ==========
    private static void example7_LoadAll() throws Exception {
        printHeader("Example 7: Asynchronous loadAll");

        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return mockDatabase.get(key);
            }

            @Override
            public Map<String, String> loadAll(Iterable<? extends String> keys) {
                System.out.println("  [Loader] Async batch loading...");
                // Simulate slow database
                try { Thread.sleep(100); } catch (InterruptedException e) { }

                Map<String, String> result = new HashMap<>();
                for (String key : keys) {
                    String value = mockDatabase.get(key);
                    if (value != null) {
                        result.put(key, value);
                    }
                }
                return result;
            }
        };

        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setReadThrough(true);
        config.setCacheLoaderFactory(() -> loader);

        CaffeineCache<String, String> cache = new CaffeineCache<>("loadAllCache", null, config);

        // Pre-populate one entry
        cache.put("product:1", "Already Cached");

        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("Calling loadAll with replaceExistingValues=false...");
        cache.loadAll(Set.of("product:1", "product:2"), false, new CompletionListener() {
            @Override
            public void onCompletion() {
                System.out.println("  [Callback] loadAll completed successfully!");
                latch.countDown();
            }

            @Override
            public void onException(Exception e) {
                System.out.println("  [Callback] loadAll failed: " + e.getMessage());
                latch.countDown();
            }
        });

        System.out.println("loadAll is asynchronous - main thread continues...");
        latch.await(5, TimeUnit.SECONDS);

        System.out.println("\nCache contents after loadAll:");
        System.out.println("  product:1 = " + cache.get("product:1") + " (should be 'Already Cached')");
        System.out.println("  product:2 = " + cache.get("product:2") + " (loaded from database)");

        cache.close();
    }

    // ========== Example 8: Event Listeners ==========
    private static void example8_EventListeners() {
        printHeader("Example 8: Event Listeners");

        CaffeineCache<String, String> cache = createSimpleCache("eventCache");

        // Register event listeners
        CacheEntryCreatedListener<String, String> createdListener = events -> {
            for (CacheEntryEvent<? extends String, ? extends String> event : events) {
                System.out.println("  [EVENT] CREATED: " + event.getKey() + "=" + event.getValue());
            }
        };

        CacheEntryUpdatedListener<String, String> updatedListener = events -> {
            for (CacheEntryEvent<? extends String, ? extends String> event : events) {
                System.out.println("  [EVENT] UPDATED: " + event.getKey() +
                    " old=" + event.getOldValue() + " new=" + event.getValue());
            }
        };

        CacheEntryRemovedListener<String, String> removedListener = events -> {
            for (CacheEntryEvent<? extends String, ? extends String> event : events) {
                System.out.println("  [EVENT] REMOVED: " + event.getKey() + " (was " + event.getOldValue() + ")");
            }
        };

        // Register listeners
        registerListener(cache, createdListener, CacheEntryCreatedListener.class);
        registerListener(cache, updatedListener, CacheEntryUpdatedListener.class);
        registerListener(cache, removedListener, CacheEntryRemovedListener.class);

        System.out.println("Performing operations:");

        // CREATE
        System.out.println("\n1. PUT key1=value1");
        cache.put("key1", "value1");

        // UPDATE
        System.out.println("\n2. PUT key1=value1-updated");
        cache.put("key1", "value1-updated");

        // REMOVE
        System.out.println("\n3. REMOVE key1");
        cache.remove("key1");

        cache.close();
    }

    // ========== Example 9: Statistics ==========
    private static void example9_Statistics() {
        printHeader("Example 9: Statistics");

        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        CaffeineCache<String, String> cache = new CaffeineCache<>("statsCache", null, config);

        // Perform various operations
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.get("a");  // hit
        cache.get("a");  // hit
        cache.get("b");  // hit
        cache.get("missing1");  // miss
        cache.get("missing2");  // miss

        cache.remove("c");

        // Print statistics
        var stats = cache.getStatistics();
        System.out.println("Cache Statistics:");
        System.out.println("  Hits: " + stats.getCacheHits());
        System.out.println("  Misses: " + stats.getCacheMisses());
        System.out.println("  Hit Rate: " + String.format("%.2f%%", stats.getCacheHitPercentage()));
        System.out.println("  Puts: " + stats.getCachePuts());
        System.out.println("  Removals: " + stats.getCacheRemovals());
        System.out.println("  Evictions: " + stats.getCacheEvictions());

        cache.close();
    }

    // ========== Example 10: Expiry Policy ==========
    private static void example10_ExpiryPolicy() throws Exception {
        printHeader("Example 10: Expiry Policy");

        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        // Entries expire 200ms after creation
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 200)));

        CaffeineCache<String, String> cache = new CaffeineCache<>("expiryCache", null, config);

        System.out.println("Setting entry with 200ms TTL...");
        cache.put("temp", "temporary-value");
        System.out.println("Immediately after PUT: " + cache.get("temp"));

        System.out.println("Waiting 100ms...");
        Thread.sleep(100);
        System.out.println("After 100ms: " + cache.get("temp"));

        System.out.println("Waiting another 150ms...");
        Thread.sleep(150);
        System.out.println("After 250ms (expired): " + cache.get("temp"));

        cache.close();
    }

    // ========== Example 11: Iterator ==========
    private static void example11_Iterator() {
        printHeader("Example 11: Iterator");

        CaffeineCache<String, String> cache = createSimpleCache("iteratorCache");

        // Add entries
        cache.put("fruit:apple", "Red");
        cache.put("fruit:banana", "Yellow");
        cache.put("fruit:grape", "Purple");
        cache.put("vegetable:carrot", "Orange");
        cache.put("vegetable:broccoli", "Green");

        System.out.println("Iterating over all cache entries:");
        for (Cache.Entry<String, String> entry : cache) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }

        // Remove during iteration
        System.out.println("\nRemoving vegetables during iteration:");
        Iterator<Cache.Entry<String, String>> iterator = cache.iterator();
        while (iterator.hasNext()) {
            Cache.Entry<String, String> entry = iterator.next();
            if (entry.getKey().startsWith("vegetable:")) {
                System.out.println("  Removing: " + entry.getKey());
                iterator.remove();
            }
        }

        System.out.println("\nRemaining entries:");
        for (Cache.Entry<String, String> entry : cache) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }

        cache.close();
    }

    // ========== Example 12: Lifecycle ==========
    private static void example12_Lifecycle() {
        printHeader("Example 12: Lifecycle (close/isClosed)");

        CaffeineCache<String, String> cache = createSimpleCache("lifecycleCache");

        cache.put("key", "value");
        System.out.println("Before close:");
        System.out.println("  isClosed: " + cache.isClosed());
        System.out.println("  get(key): " + cache.get("key"));
        System.out.println("  getName(): " + cache.getName());

        cache.close();
        System.out.println("\nAfter close:");
        System.out.println("  isClosed: " + cache.isClosed());

        try {
            cache.get("key");
        } catch (IllegalStateException e) {
            System.out.println("  get(key) throws: " + e.getClass().getSimpleName());
        }

        try {
            cache.put("key2", "value2");
        } catch (IllegalStateException e) {
            System.out.println("  put() throws: " + e.getClass().getSimpleName());
        }
    }

    // ========== Helper Methods ==========

    private static CaffeineCache<String, String> createSimpleCache(String name) {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        return new CaffeineCache<>(name, null, config);
    }

    private static <V> CaffeineCache<String, V> createCache(String name, Class<V> valueType) {
        YmsConfiguration<String, V> config = new YmsConfiguration<>();
        config.setTypes(String.class, valueType);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        return new CaffeineCache<>(name, null, config);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> void registerListener(
            CaffeineCache<K, V> cache,
            CacheEntryListener<K, V> listener,
            Class<? extends CacheEntryListener> listenerType) {
        Factory<CacheEntryListener<K, V>> factory = () -> listener;
        MutableCacheEntryListenerConfiguration<K, V> config =
            new MutableCacheEntryListenerConfiguration<>(
                (Factory) factory, null, true, true);
        cache.registerCacheEntryListener(config);
    }

    private static void printHeader(String title) {
        System.out.println("\n" + "-".repeat(60));
        System.out.println(title);
        System.out.println("-".repeat(60));
    }
}
