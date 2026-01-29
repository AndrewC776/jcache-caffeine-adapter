package com.yms.cache.benchmark.scenarios;

import com.yms.cache.CaffeineCache;
import com.yms.cache.config.YmsConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.cache.Cache;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmarks for CacheLoader (read-through) and CacheWriter (write-through) integration.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC"})
public class IntegrationBenchmarks {

    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @Param({"0", "100", "1000"})  // Simulated backend latency in microseconds
    public int backendLatencyUs;

    private CaffeineCache<String, String> readThroughCache;
    private CaffeineCache<String, String> writeThroughCache;
    private CaffeineCache<String, String> readWriteThroughCache;
    private Map<String, String> backendStore;
    private AtomicLong loadCount;
    private AtomicLong writeCount;
    private String[] keys;
    private String[] values;

    private CacheLoader<String, String> createLoader() {
        return new CacheLoader<>() {
            @Override
            public String load(String key) throws CacheLoaderException {
                simulateLatency();
                loadCount.incrementAndGet();
                return backendStore.get(key);
            }

            @Override
            public Map<String, String> loadAll(Iterable<? extends String> keys) throws CacheLoaderException {
                simulateLatency();
                Map<String, String> result = new HashMap<>();
                for (String key : keys) {
                    loadCount.incrementAndGet();
                    String value = backendStore.get(key);
                    if (value != null) {
                        result.put(key, value);
                    }
                }
                return result;
            }
        };
    }

    private CacheWriter<String, String> createWriter() {
        return new CacheWriter<>() {
            @Override
            public void write(Cache.Entry<? extends String, ? extends String> entry) throws CacheWriterException {
                simulateLatency();
                writeCount.incrementAndGet();
                backendStore.put(entry.getKey(), entry.getValue());
            }

            @Override
            public void writeAll(Collection<Cache.Entry<? extends String, ? extends String>> entries)
                    throws CacheWriterException {
                simulateLatency();
                for (Cache.Entry<? extends String, ? extends String> entry : entries) {
                    writeCount.incrementAndGet();
                    backendStore.put(entry.getKey(), entry.getValue());
                }
            }

            @Override
            public void delete(Object key) throws CacheWriterException {
                simulateLatency();
                writeCount.incrementAndGet();
                backendStore.remove(key);
            }

            @Override
            public void deleteAll(Collection<?> keys) throws CacheWriterException {
                simulateLatency();
                for (Object key : keys) {
                    writeCount.incrementAndGet();
                    backendStore.remove(key);
                }
            }
        };
    }

    private void simulateLatency() {
        if (backendLatencyUs > 0) {
            long start = System.nanoTime();
            while (System.nanoTime() - start < backendLatencyUs * 1000L) {
                Thread.onSpinWait();
            }
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        int id = CACHE_COUNTER.incrementAndGet();
        backendStore = new ConcurrentHashMap<>();
        loadCount = new AtomicLong(0);
        writeCount = new AtomicLong(0);

        keys = new String[10_000];
        values = new String[10_000];

        // Pre-populate backend
        for (int i = 0; i < 10_000; i++) {
            keys[i] = "key-" + i;
            values[i] = "value-" + i + "-" + "x".repeat(100);
            backendStore.put(keys[i], values[i]);
        }

        // Read-through cache
        YmsConfiguration<String, String> readConfig = new YmsConfiguration<>();
        readConfig.setTypes(String.class, String.class);
        readConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        readConfig.setReadThrough(true);
        readConfig.setCacheLoaderFactory(this::createLoader);
        readConfig.setStatisticsEnabled(true);
        readConfig.setMaximumSize(10_000L);
        readThroughCache = new CaffeineCache<>("readThroughBench-" + id, null, readConfig);

        // Write-through cache
        YmsConfiguration<String, String> writeConfig = new YmsConfiguration<>();
        writeConfig.setTypes(String.class, String.class);
        writeConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        writeConfig.setWriteThrough(true);
        writeConfig.setCacheWriterFactory(this::createWriter);
        writeConfig.setStatisticsEnabled(true);
        writeConfig.setMaximumSize(10_000L);
        writeThroughCache = new CaffeineCache<>("writeThroughBench-" + id, null, writeConfig);

        // Pre-populate write-through cache
        for (int i = 0; i < 10_000; i++) {
            writeThroughCache.put(keys[i], values[i]);
        }

        // Read-write-through cache
        YmsConfiguration<String, String> rwConfig = new YmsConfiguration<>();
        rwConfig.setTypes(String.class, String.class);
        rwConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        rwConfig.setReadThrough(true);
        rwConfig.setCacheLoaderFactory(this::createLoader);
        rwConfig.setWriteThrough(true);
        rwConfig.setCacheWriterFactory(this::createWriter);
        rwConfig.setStatisticsEnabled(true);
        rwConfig.setMaximumSize(10_000L);
        readWriteThroughCache = new CaffeineCache<>("readWriteThroughBench-" + id, null, rwConfig);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (readThroughCache != null) readThroughCache.close();
        if (writeThroughCache != null) writeThroughCache.close();
        if (readWriteThroughCache != null) readWriteThroughCache.close();
    }

    /**
     * Benchmark: Read-through cache miss (loads from backend).
     */
    @Benchmark
    public String benchmarkReadThroughMiss(Blackhole bh) {
        // Always miss - key with unique suffix
        String key = "miss-key-" + System.nanoTime();
        backendStore.put(key, "loaded-value");
        String value = readThroughCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Read-through cache hit (no backend call).
     */
    @Benchmark
    public String benchmarkReadThroughHit(Blackhole bh) {
        // Hit - use pre-loaded key
        int idx = (int) (System.nanoTime() % 10_000);
        String key = keys[idx];
        // Ensure it's in cache
        if (!readThroughCache.containsKey(key)) {
            readThroughCache.get(key);  // Trigger load
        }
        String value = readThroughCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Write-through put operation.
     */
    @Benchmark
    public void benchmarkWriteThroughPut() {
        int idx = (int) (System.nanoTime() % 10_000);
        writeThroughCache.put(keys[idx], "wt-value-" + System.nanoTime());
    }

    /**
     * Benchmark: Batch loadAll through read-through.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    public Map<String, String> benchmarkReadThroughLoadAll(Blackhole bh) {
        Set<String> keySet = new HashSet<>();
        int base = (int) (System.nanoTime() % 10_000);
        for (int i = 0; i < 100; i++) {
            keySet.add(keys[(base + i) % 10_000]);
        }
        Map<String, String> result = readThroughCache.getAll(keySet);
        bh.consume(result);
        return result;
    }

    /**
     * Benchmark: Mixed read-write-through operations.
     */
    @Benchmark
    public void benchmarkReadWriteThroughMixed(Blackhole bh) {
        long idx = System.nanoTime() % 10_000;
        String key = keys[(int) idx];

        // 80% reads, 20% writes
        if (idx % 5 != 0) {
            String value = readWriteThroughCache.get(key);
            bh.consume(value);
        } else {
            readWriteThroughCache.put(key, "updated-" + System.nanoTime());
        }
    }

    /**
     * Benchmark: Write-through remove operation.
     */
    @Benchmark
    public boolean benchmarkWriteThroughRemove() {
        int idx = (int) (System.nanoTime() % 10_000);
        String key = keys[idx];
        boolean removed = writeThroughCache.remove(key);
        // Restore for next iteration
        writeThroughCache.put(key, values[idx]);
        return removed;
    }
}
