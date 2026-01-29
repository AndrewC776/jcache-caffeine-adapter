package com.yms.cache.benchmark.scenarios;

import com.yms.cache.benchmark.state.CacheState;
import com.yms.cache.benchmark.state.DataState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.cache.processor.EntryProcessor;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for atomic operations: getAndPut, getAndRemove, replace, invoke.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2g", "-Xmx2g", "-XX:+UseG1GC"})
public class AtomicOperationBenchmarks {

    @State(Scope.Thread)
    public static class ThreadState {
        int index = 0;
    }

    // EntryProcessor for atomic counter increment
    private static final EntryProcessor<String, String, String> INCREMENT_PROCESSOR =
        (entry, args) -> {
            String current = entry.getValue();
            String newValue = current == null ? "1" : String.valueOf(Integer.parseInt(current) + 1);
            entry.setValue(newValue);
            return current;
        };

    // EntryProcessor for conditional update
    private static final EntryProcessor<String, String, Boolean> CONDITIONAL_UPDATE_PROCESSOR =
        (entry, args) -> {
            if (entry.exists()) {
                String newValue = (String) args[0];
                entry.setValue(newValue);
                return true;
            }
            return false;
        };

    // EntryProcessor for read-only access
    private static final EntryProcessor<String, String, String> READ_ONLY_PROCESSOR =
        (entry, args) -> entry.getValue();

    @Setup(Level.Trial)
    public void populateCache(CacheState cacheState, DataState dataState) {
        int limit = Math.min(dataState.dataSize, cacheState.cacheSize);
        for (int i = 0; i < limit; i++) {
            cacheState.stringCache.put(dataState.keys[i], dataState.values[i]);
        }
        // Initialize counter keys
        for (int i = 0; i < 1000; i++) {
            cacheState.stringCache.put("counter-" + i, "0");
        }
    }

    /**
     * Benchmark: getAndPut operation.
     */
    @Benchmark
    public String benchmarkGetAndPut(CacheState cacheState, DataState dataState,
                                     ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % dataState.dataSize;
        String oldValue = cacheState.stringCache.getAndPut(
            dataState.keys[idx],
            dataState.values[(idx + 1) % dataState.dataSize]
        );
        bh.consume(oldValue);
        return oldValue;
    }

    /**
     * Benchmark: getAndRemove operation.
     */
    @Benchmark
    public String benchmarkGetAndRemove(CacheState cacheState, DataState dataState,
                                        ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % dataState.dataSize;
        String key = dataState.keys[idx];
        String oldValue = cacheState.stringCache.getAndRemove(key);
        // Put back for next iteration
        cacheState.stringCache.put(key, dataState.values[idx]);
        bh.consume(oldValue);
        return oldValue;
    }

    /**
     * Benchmark: replace(key, value) operation.
     */
    @Benchmark
    public boolean benchmarkReplace(CacheState cacheState, DataState dataState,
                                    ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        return cacheState.stringCache.replace(
            dataState.keys[idx],
            dataState.values[(idx + 1) % dataState.dataSize]
        );
    }

    /**
     * Benchmark: replace(key, oldValue, newValue) CAS operation.
     */
    @Benchmark
    public boolean benchmarkReplaceCAS(CacheState cacheState, DataState dataState,
                                       ThreadState threadState) {
        int idx = threadState.index++ % dataState.dataSize;
        String current = cacheState.stringCache.get(dataState.keys[idx]);
        if (current != null) {
            return cacheState.stringCache.replace(
                dataState.keys[idx],
                current,
                dataState.values[(idx + 1) % dataState.dataSize]
            );
        }
        return false;
    }

    /**
     * Benchmark: getAndReplace operation.
     */
    @Benchmark
    public String benchmarkGetAndReplace(CacheState cacheState, DataState dataState,
                                         ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % dataState.dataSize;
        String oldValue = cacheState.stringCache.getAndReplace(
            dataState.keys[idx],
            dataState.values[(idx + 1) % dataState.dataSize]
        );
        bh.consume(oldValue);
        return oldValue;
    }

    /**
     * Benchmark: EntryProcessor invoke (atomic counter).
     */
    @Benchmark
    public String benchmarkInvokeCounter(CacheState cacheState,
                                         ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % 1000;
        String result = cacheState.stringCache.invoke("counter-" + idx, INCREMENT_PROCESSOR);
        bh.consume(result);
        return result;
    }

    /**
     * Benchmark: EntryProcessor invoke (conditional update).
     */
    @Benchmark
    public Boolean benchmarkInvokeConditional(CacheState cacheState, DataState dataState,
                                              ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % dataState.dataSize;
        Boolean result = cacheState.stringCache.invoke(
            dataState.keys[idx],
            CONDITIONAL_UPDATE_PROCESSOR,
            dataState.values[(idx + 1) % dataState.dataSize]
        );
        bh.consume(result);
        return result;
    }

    /**
     * Benchmark: EntryProcessor invoke (read-only).
     */
    @Benchmark
    public String benchmarkInvokeReadOnly(CacheState cacheState, DataState dataState,
                                          ThreadState threadState, Blackhole bh) {
        int idx = threadState.index++ % dataState.dataSize;
        String result = cacheState.stringCache.invoke(dataState.keys[idx], READ_ONLY_PROCESSOR);
        bh.consume(result);
        return result;
    }
}
