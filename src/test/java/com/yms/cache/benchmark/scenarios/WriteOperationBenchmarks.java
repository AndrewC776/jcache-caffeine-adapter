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

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for write operations: put, putAll, putIfAbsent.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC"})
public class WriteOperationBenchmarks {

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
     * Benchmark: Single PUT operation (new entry).
     */
    @Benchmark
    public void benchmarkPutNew(CacheState cacheState, DataState dataState,
                                ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        String key = "new-" + idx + "-" + Thread.currentThread().getId() + "-" + System.nanoTime();
        cacheState.stringCache.put(key, dataState.values[idx]);
    }

    /**
     * Benchmark: Single PUT operation (update existing).
     */
    @Benchmark
    public void benchmarkPutUpdate(CacheState cacheState, DataState dataState,
                                   ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        cacheState.stringCache.put(dataState.keys[idx], dataState.values[(idx + 1) % dataState.dataSize]);
    }

    /**
     * Benchmark: putIfAbsent when key doesn't exist.
     */
    @Benchmark
    public boolean benchmarkPutIfAbsentNew(CacheState cacheState, DataState dataState,
                                           ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        String key = "absent-" + idx + "-" + Thread.currentThread().getId() + "-" + System.nanoTime();
        return cacheState.stringCache.putIfAbsent(key, dataState.values[idx]);
    }

    /**
     * Benchmark: putIfAbsent when key exists.
     */
    @Benchmark
    public boolean benchmarkPutIfAbsentExists(CacheState cacheState, DataState dataState,
                                              ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        return cacheState.stringCache.putIfAbsent(dataState.keys[idx], dataState.values[idx]);
    }

    /**
     * Benchmark: Batch putAll with 100 entries.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    public void benchmarkPutAll100(CacheState cacheState, DataState dataState) {
        Map<String, String> batch = dataState.randomBatchMap(100);
        cacheState.stringCache.putAll(batch);
    }

    /**
     * Benchmark: Batch putAll with 1000 entries.
     */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void benchmarkPutAll1000(CacheState cacheState, DataState dataState) {
        Map<String, String> batch = dataState.randomBatchMap(1000);
        cacheState.stringCache.putAll(batch);
    }

    /**
     * Benchmark: Remove operation.
     */
    @Benchmark
    public boolean benchmarkRemove(CacheState cacheState, DataState dataState,
                                   ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        String key = dataState.keys[idx];
        boolean removed = cacheState.stringCache.remove(key);
        // Put back for next iteration
        cacheState.stringCache.put(key, dataState.values[idx]);
        return removed;
    }
}
