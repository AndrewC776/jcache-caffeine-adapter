package com.yms.cache.benchmark.scenarios;

import com.yms.cache.CaffeineCache;
import com.yms.cache.benchmark.data.BenchmarkData;
import com.yms.cache.benchmark.data.ComplexObject;
import com.yms.cache.config.YmsConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.cache.expiry.EternalExpiryPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarks for memory usage and GC pressure.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {
    "-Xms4g", "-Xmx4g",
    "-XX:+UseG1GC",
    "-Xlog:gc*:file=gc.log:time,uptime,level,tags"
})
public class MemoryBenchmarks {

    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @Param({"true", "false"})
    public boolean storeByValue;

    @Param({"1024", "10240", "102400"})  // 1KB, 10KB, 100KB per object
    public int objectSizeBytes;

    private CaffeineCache<String, byte[]> largeObjectCache;
    private CaffeineCache<String, ComplexObject> complexObjectCache;
    private byte[][] largeObjects;
    private ComplexObject[] complexObjects;
    private String[] keys;

    @Setup(Level.Trial)
    public void setup() {
        int id = CACHE_COUNTER.incrementAndGet();

        // Large binary object cache
        YmsConfiguration<String, byte[]> binaryConfig = new YmsConfiguration<>();
        binaryConfig.setTypes(String.class, byte[].class);
        binaryConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        binaryConfig.setStoreByValue(storeByValue);
        binaryConfig.setStatisticsEnabled(true);
        binaryConfig.setMaximumSize(10_000L);
        largeObjectCache = new CaffeineCache<>("largeBinaryBench-" + id, null, binaryConfig);

        // Complex object cache
        YmsConfiguration<String, ComplexObject> complexConfig = new YmsConfiguration<>();
        complexConfig.setTypes(String.class, ComplexObject.class);
        complexConfig.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        complexConfig.setStoreByValue(storeByValue);
        complexConfig.setStatisticsEnabled(true);
        complexConfig.setMaximumSize(10_000L);
        complexObjectCache = new CaffeineCache<>("complexObjectBench-" + id, null, complexConfig);

        // Pre-generate objects
        keys = new String[1000];
        largeObjects = new byte[1000][];
        complexObjects = new ComplexObject[1000];
        for (int i = 0; i < 1000; i++) {
            keys[i] = "key-" + i;
            largeObjects[i] = BenchmarkData.generateBinary(objectSizeBytes);
            complexObjects[i] = BenchmarkData.generateComplexObject(i);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (largeObjectCache != null) largeObjectCache.close();
        if (complexObjectCache != null) complexObjectCache.close();
    }

    /**
     * Benchmark: Large object allocation (GC pressure test).
     * Measures how serialization affects GC.
     */
    @Benchmark
    @Threads(4)
    public void benchmarkLargeObjectPut() {
        int idx = (int) (System.nanoTime() % 1000);
        String key = "large-" + (System.nanoTime() % 10_000);
        largeObjectCache.put(key, largeObjects[idx]);
    }

    /**
     * Benchmark: Large object retrieval.
     */
    @Benchmark
    @Threads(4)
    public byte[] benchmarkLargeObjectGet(Blackhole bh) {
        String key = "large-" + (System.nanoTime() % 10_000);
        byte[] value = largeObjectCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Complex object with nested structures.
     * Tests serialization overhead for complex graphs.
     */
    @Benchmark
    @Threads(4)
    public void benchmarkComplexObjectPut() {
        int idx = (int) (System.nanoTime() % 1000);
        String key = "complex-" + (System.nanoTime() % 10_000);
        complexObjectCache.put(key, complexObjects[idx]);
    }

    /**
     * Benchmark: Complex object retrieval.
     */
    @Benchmark
    @Threads(4)
    public ComplexObject benchmarkComplexObjectGet(Blackhole bh) {
        String key = "complex-" + (System.nanoTime() % 10_000);
        ComplexObject value = complexObjectCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Continuous churn (high allocation rate).
     * Simulates worst-case GC pressure.
     */
    @Benchmark
    @Threads(8)
    public void benchmarkHighChurn(Blackhole bh) {
        long idx = System.nanoTime();
        String key = "churn-" + (idx % 5000);  // Limited key space = high update rate

        // Alternate between put and get
        if (idx % 2 == 0) {
            largeObjectCache.put(key, BenchmarkData.generateBinary(objectSizeBytes));
        } else {
            byte[] value = largeObjectCache.get(key);
            bh.consume(value);
        }
    }

    /**
     * Benchmark: storeByValue vs storeByReference comparison.
     * Demonstrates serialization overhead.
     */
    @Benchmark
    public void benchmarkSerializationRoundTrip(Blackhole bh) {
        int idx = (int) (System.nanoTime() % 1000);
        String key = "serial-" + (idx % 10_000);

        // Put then get to measure full round-trip
        largeObjectCache.put(key, largeObjects[idx]);
        byte[] retrieved = largeObjectCache.get(key);
        bh.consume(retrieved);
    }

    /**
     * Benchmark: Small object put (baseline for comparison).
     */
    @Benchmark
    @Threads(4)
    public void benchmarkSmallObjectPut() {
        String key = "small-" + (System.nanoTime() % 10_000);
        byte[] small = new byte[100];  // 100 bytes
        largeObjectCache.put(key, small);
    }

    /**
     * Benchmark: Measure eviction overhead under memory pressure.
     * Cache size is limited, forcing evictions.
     */
    @Benchmark
    @Threads(4)
    public void benchmarkEvictionUnderPressure() {
        // Keys > maxSize will trigger eviction
        long keyId = System.nanoTime() % 50_000;  // More keys than cache capacity
        String key = "evict-" + keyId;
        int idx = (int) (keyId % 1000);
        largeObjectCache.put(key, largeObjects[idx]);
    }
}
