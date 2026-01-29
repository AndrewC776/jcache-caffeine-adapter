package com.yms.cache.benchmark.scenarios;

import com.yms.cache.CaffeineCache;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarks for expiry policy behavior and lazy eviction.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC"})
public class ExpiryBenchmarks {

    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @Param({"100", "1000", "10000"})  // TTL in milliseconds
    public int ttlMs;

    @Param({"CREATED", "ACCESSED", "MODIFIED"})
    public String expiryPolicy;

    private CaffeineCache<String, String> expiringCache;
    private String[] keys;
    private String[] values;

    @Setup(Level.Trial)
    public void setup() {
        int id = CACHE_COUNTER.incrementAndGet();
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setStatisticsEnabled(true);
        config.setMaximumSize(100_000L);

        Duration duration = new Duration(TimeUnit.MILLISECONDS, ttlMs);
        switch (expiryPolicy) {
            case "CREATED" -> config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(duration));
            case "ACCESSED" -> config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(duration));
            case "MODIFIED" -> config.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(duration));
        }

        expiringCache = new CaffeineCache<>("expiryBench-" + id, null, config);

        keys = new String[10_000];
        values = new String[10_000];
        for (int i = 0; i < 10_000; i++) {
            keys[i] = "key-" + i;
            values[i] = "value-" + i + "-" + "x".repeat(100);
        }

        // Pre-populate
        for (int i = 0; i < 10_000; i++) {
            expiringCache.put(keys[i], values[i]);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (expiringCache != null) {
            expiringCache.close();
        }
    }

    /**
     * Benchmark: Get with expiration check (lazy eviction).
     */
    @Benchmark
    public String benchmarkGetWithExpiry(Blackhole bh) {
        int idx = (int) (System.nanoTime() % keys.length);
        String value = expiringCache.get(keys[idx]);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Put with expiry calculation.
     */
    @Benchmark
    public void benchmarkPutWithExpiry() {
        int idx = (int) (System.nanoTime() % keys.length);
        expiringCache.put(keys[idx], values[(idx + 1) % values.length]);
    }

    /**
     * Benchmark: containsKey with expiration check.
     */
    @Benchmark
    public boolean benchmarkContainsKeyWithExpiry() {
        int idx = (int) (System.nanoTime() % keys.length);
        return expiringCache.containsKey(keys[idx]);
    }

    /**
     * Benchmark: Mixed operations with expiration.
     * Measures overhead of expiry checking and eviction.
     */
    @Benchmark
    public void benchmarkMixedWithExpiry(Blackhole bh) {
        long idx = System.nanoTime() % keys.length;

        if (idx % 3 == 0) {
            // Write
            expiringCache.put(keys[(int) idx], values[(int) ((idx + 1) % values.length)]);
        } else {
            // Read (may trigger expiration)
            String value = expiringCache.get(keys[(int) idx]);
            bh.consume(value);
        }
    }

    /**
     * Benchmark: High-frequency access pattern (for ACCESSED policy).
     * Tests expiry time update overhead on every access.
     */
    @Benchmark
    public String benchmarkHighFrequencyAccess(Blackhole bh) {
        // Access the same small set of keys repeatedly
        int idx = (int) (System.nanoTime() % 100);  // Only 100 hot keys
        String value = expiringCache.get(keys[idx]);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Rapid update pattern (for MODIFIED policy).
     * Tests expiry time update overhead on every modification.
     */
    @Benchmark
    public void benchmarkRapidUpdate() {
        // Update the same small set of keys repeatedly
        int idx = (int) (System.nanoTime() % 100);  // Only 100 hot keys
        expiringCache.put(keys[idx], values[(idx + 1) % values.length]);
    }

    /**
     * Benchmark: Replace operation with expiry update.
     */
    @Benchmark
    public boolean benchmarkReplaceWithExpiry() {
        int idx = (int) (System.nanoTime() % keys.length);
        return expiringCache.replace(keys[idx], values[(idx + 1) % values.length]);
    }
}
