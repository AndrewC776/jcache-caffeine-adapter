package com.yms.cache.benchmark.state;

import com.yms.cache.CaffeineCache;
import com.yms.cache.benchmark.data.ComplexObject;
import com.yms.cache.config.YmsConfiguration;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH State holding cache instances for benchmarking.
 */
@State(Scope.Benchmark)
public class CacheState {

    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @Param({"10000", "100000"})
    public int cacheSize;

    @Param({"true", "false"})
    public boolean storeByValue;

    @Param({"true", "false"})
    public boolean statisticsEnabled;

    public CaffeineCache<String, String> stringCache;
    public CaffeineCache<String, ComplexObject> objectCache;
    public CaffeineCache<String, byte[]> binaryCache;

    @Setup(Level.Trial)
    public void setupCache() {
        int id = CACHE_COUNTER.incrementAndGet();

        // String cache
        YmsConfiguration<String, String> stringConfig = new YmsConfiguration<>();
        stringConfig.setTypes(String.class, String.class);
        stringConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        stringConfig.setStoreByValue(storeByValue);
        stringConfig.setStatisticsEnabled(statisticsEnabled);
        stringConfig.setMaximumSize((long) cacheSize);
        stringCache = new CaffeineCache<>("stringBench-" + id, null, stringConfig);

        // Complex object cache
        YmsConfiguration<String, ComplexObject> objectConfig = new YmsConfiguration<>();
        objectConfig.setTypes(String.class, ComplexObject.class);
        objectConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        objectConfig.setStoreByValue(storeByValue);
        objectConfig.setStatisticsEnabled(statisticsEnabled);
        objectConfig.setMaximumSize((long) cacheSize);
        objectCache = new CaffeineCache<>("objectBench-" + id, null, objectConfig);

        // Binary cache for large objects
        YmsConfiguration<String, byte[]> binaryConfig = new YmsConfiguration<>();
        binaryConfig.setTypes(String.class, byte[].class);
        binaryConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        binaryConfig.setStoreByValue(storeByValue);
        binaryConfig.setStatisticsEnabled(statisticsEnabled);
        binaryConfig.setMaximumSize((long) cacheSize);
        binaryCache = new CaffeineCache<>("binaryBench-" + id, null, binaryConfig);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (stringCache != null) {
            stringCache.close();
        }
        if (objectCache != null) {
            objectCache.close();
        }
        if (binaryCache != null) {
            binaryCache.close();
        }
    }

    /**
     * Creates a read-through cache with the provided loader.
     */
    public CaffeineCache<String, String> createReadThroughCache(
            CacheLoader<String, String> loader) {
        int id = CACHE_COUNTER.incrementAndGet();
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setReadThrough(true);
        config.setCacheLoaderFactory(() -> loader);
        config.setStatisticsEnabled(true);
        config.setMaximumSize((long) cacheSize);
        return new CaffeineCache<>("readThroughBench-" + id, null, config);
    }

    /**
     * Creates a write-through cache with the provided writer.
     */
    public CaffeineCache<String, String> createWriteThroughCache(
            CacheWriter<? super String, ? super String> writer) {
        int id = CACHE_COUNTER.incrementAndGet();
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setWriteThrough(true);
        config.setCacheWriterFactory(() -> writer);
        config.setStatisticsEnabled(true);
        config.setMaximumSize((long) cacheSize);
        return new CaffeineCache<>("writeThroughBench-" + id, null, config);
    }

    /**
     * Creates a cache with expiry policy.
     */
    public CaffeineCache<String, String> createExpiringCache(long ttlMs, String policyType) {
        int id = CACHE_COUNTER.incrementAndGet();
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setStatisticsEnabled(true);
        config.setMaximumSize((long) cacheSize);

        Duration duration = new Duration(TimeUnit.MILLISECONDS, ttlMs);
        switch (policyType) {
            case "CREATED" -> config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(duration));
            case "ACCESSED" -> config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(duration));
            case "MODIFIED" -> config.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(duration));
            default -> config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        }

        return new CaffeineCache<>("expiringBench-" + id, null, config);
    }
}
