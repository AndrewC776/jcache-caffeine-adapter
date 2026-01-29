package com.yms.cache.benchmark.scenarios;

import com.yms.cache.benchmark.state.CacheState;
import com.yms.cache.benchmark.state.DataState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for read operations: get, getAll, containsKey.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC"})
public class ReadOperationBenchmarks {

    @State(Scope.Thread)
    public static class ThreadState {
        int index = 0;
    }

    @Setup(Level.Trial)
    public void populateCache(CacheState cacheState, DataState dataState) {
        int limit = Math.min(dataState.dataSize, cacheState.cacheSize);
        for (int i = 0; i < limit; i++) {
            cacheState.stringCache.put(dataState.keys[i], dataState.values[i]);
        }
    }

    /**
     * Benchmark: Single GET operation with sequential access (cache hit).
     */
    @Benchmark
    public String benchmarkGetSequential(CacheState cacheState, DataState dataState,
                                         ThreadState threadState, Blackhole bh) {
        String key = dataState.keys[threadState.index++ % dataState.dataSize];
        String value = cacheState.stringCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: GET with random key access pattern (cache hit).
     */
    @Benchmark
    public String benchmarkGetRandom(CacheState cacheState, DataState dataState, Blackhole bh) {
        String key = dataState.randomKey();
        String value = cacheState.stringCache.get(key);
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: GET for non-existent key (cache miss).
     */
    @Benchmark
    public String benchmarkGetMiss(CacheState cacheState, Blackhole bh) {
        String value = cacheState.stringCache.get("non-existent-key-" + System.nanoTime());
        bh.consume(value);
        return value;
    }

    /**
     * Benchmark: containsKey operation (cache hit).
     */
    @Benchmark
    public boolean benchmarkContainsKeyHit(CacheState cacheState, DataState dataState,
                                           ThreadState threadState) {
        String key = dataState.keys[threadState.index++ % dataState.dataSize];
        return cacheState.stringCache.containsKey(key);
    }

    /**
     * Benchmark: containsKey operation (cache miss).
     */
    @Benchmark
    public boolean benchmarkContainsKeyMiss(CacheState cacheState) {
        return cacheState.stringCache.containsKey("non-existent-key-" + System.nanoTime());
    }

    /**
     * Benchmark: Batch getAll with 100 keys.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    public Map<String, String> benchmarkGetAll100(CacheState cacheState,
                                                   DataState dataState, Blackhole bh) {
        Set<String> keys = dataState.randomKeySet(100);
        Map<String, String> result = cacheState.stringCache.getAll(keys);
        bh.consume(result);
        return result;
    }

    /**
     * Benchmark: Batch getAll with 1000 keys.
     */
    @Benchmark
    @OperationsPerInvocation(1000)
    public Map<String, String> benchmarkGetAll1000(CacheState cacheState,
                                                    DataState dataState, Blackhole bh) {
        Set<String> keys = dataState.randomKeySet(1000);
        Map<String, String> result = cacheState.stringCache.getAll(keys);
        bh.consume(result);
        return result;
    }
}
