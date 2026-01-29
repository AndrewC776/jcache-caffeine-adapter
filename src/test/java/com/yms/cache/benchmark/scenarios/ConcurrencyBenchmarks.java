package com.yms.cache.benchmark.scenarios;

import com.yms.cache.CaffeineCache;
import com.yms.cache.config.YmsConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarks for concurrent access patterns.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms4g", "-Xmx4g", "-XX:+UseG1GC"})
public class ConcurrencyBenchmarks {

    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @Param({"0.8", "0.5", "0.2"})  // 80% reads, 50% reads, 20% reads
    public double readRatio;

    private CaffeineCache<String, String> sharedCache;
    private String[] keys;
    private String[] values;

    @Setup(Level.Trial)
    public void setup() {
        int id = CACHE_COUNTER.incrementAndGet();
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);
        config.setMaximumSize(100_000L);
        sharedCache = new CaffeineCache<>("concurrencyBench-" + id, null, config);

        // Pre-populate with 50,000 entries
        keys = new String[50_000];
        values = new String[50_000];
        for (int i = 0; i < 50_000; i++) {
            keys[i] = "key-" + i;
            values[i] = "value-" + i + "-" + "x".repeat(100);
            sharedCache.put(keys[i], values[i]);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (sharedCache != null) {
            sharedCache.close();
        }
    }

    /**
     * Benchmark: Mixed read/write workload with configurable ratio.
     * Group threads execute both reads and writes based on readRatio.
     */
    @Benchmark
    @Group("mixed_workload")
    @GroupThreads(4)
    public void benchmarkMixedWorkload4Threads(Blackhole bh) {
        doMixedWorkload(bh);
    }

    @Benchmark
    @Group("mixed_workload_8")
    @GroupThreads(8)
    public void benchmarkMixedWorkload8Threads(Blackhole bh) {
        doMixedWorkload(bh);
    }

    @Benchmark
    @Group("mixed_workload_16")
    @GroupThreads(16)
    public void benchmarkMixedWorkload16Threads(Blackhole bh) {
        doMixedWorkload(bh);
    }

    private void doMixedWorkload(Blackhole bh) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int idx = random.nextInt(keys.length);

        if (random.nextDouble() < readRatio) {
            // Read operation
            String value = sharedCache.get(keys[idx]);
            bh.consume(value);
        } else {
            // Write operation
            sharedCache.put(keys[idx], values[random.nextInt(values.length)]);
        }
    }

    /**
     * Benchmark: High contention on same keys (hot keys).
     * Tests performance when multiple threads compete for the same cache entries.
     */
    @Benchmark
    @Threads(8)
    public void benchmarkHotKeyContention(Blackhole bh) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Only 10 hot keys for maximum contention
        int hotKeyIdx = random.nextInt(10);
        String key = keys[hotKeyIdx];

        if (random.nextDouble() < 0.5) {
            String value = sharedCache.get(key);
            bh.consume(value);
        } else {
            sharedCache.put(key, values[random.nextInt(values.length)]);
        }
    }

    /**
     * Benchmark: Pure read stress test (no writes).
     */
    @Benchmark
    @Threads(16)
    public String benchmarkReadStress(Blackhole bh) {
        String key = keys[ThreadLocalRandom.current().nextInt(keys.length)];
        String value = sharedCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: Pure write stress test.
     */
    @Benchmark
    @Threads(16)
    public void benchmarkWriteStress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int idx = random.nextInt(keys.length);
        sharedCache.put(keys[idx], values[random.nextInt(values.length)]);
    }

    /**
     * Benchmark: Atomic operations under contention.
     */
    @Benchmark
    @Threads(8)
    public void benchmarkAtomicContention(Blackhole bh) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int idx = random.nextInt(keys.length);

        int op = random.nextInt(4);
        switch (op) {
            case 0 -> bh.consume(sharedCache.getAndPut(keys[idx], values[random.nextInt(values.length)]));
            case 1 -> bh.consume(sharedCache.putIfAbsent(keys[idx], values[random.nextInt(values.length)]));
            case 2 -> bh.consume(sharedCache.replace(keys[idx], values[random.nextInt(values.length)]));
            default -> bh.consume(sharedCache.get(keys[idx]));
        }
    }

    /**
     * Benchmark: Reader-writer contention pattern.
     * Multiple readers with occasional writers.
     */
    @Benchmark
    @Group("reader_writer")
    @GroupThreads(7)
    public String benchmarkReaders(Blackhole bh) {
        String key = keys[ThreadLocalRandom.current().nextInt(keys.length)];
        String value = sharedCache.get(key);
        bh.consume(value);
        return value;
    }

    @Benchmark
    @Group("reader_writer")
    @GroupThreads(1)
    public void benchmarkWriter() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int idx = random.nextInt(keys.length);
        sharedCache.put(keys[idx], values[random.nextInt(values.length)]);
    }
}
