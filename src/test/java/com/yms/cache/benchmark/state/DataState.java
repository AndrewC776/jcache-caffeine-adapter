package com.yms.cache.benchmark.state;

import com.yms.cache.benchmark.data.BenchmarkData;
import com.yms.cache.benchmark.data.ComplexObject;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JMH State holding test data for benchmarking.
 */
@State(Scope.Benchmark)
public class DataState {

    @Param({"10000", "100000"})
    public int dataSize;

    @Param({"100", "1024"})  // 100B, 1KB
    public int valueSize;

    public String[] keys;
    public String[] values;
    public ComplexObject[] objects;
    public byte[][] binaryData;
    public Map<String, String> batchData;
    public Set<String> batchKeys;

    @Setup(Level.Trial)
    public void generateData() {
        keys = BenchmarkData.generateKeys(dataSize, "key-");
        values = BenchmarkData.generateValues(dataSize, valueSize);

        objects = new ComplexObject[dataSize];
        binaryData = new byte[dataSize][];
        batchData = new HashMap<>();
        batchKeys = new HashSet<>();

        for (int i = 0; i < dataSize; i++) {
            objects[i] = BenchmarkData.generateComplexObject(i);
            binaryData[i] = BenchmarkData.generateBinary(valueSize);
            batchData.put(keys[i], values[i]);
            batchKeys.add(keys[i]);
        }
    }

    /**
     * Returns a random key from the pre-generated key array.
     */
    public String randomKey() {
        return keys[ThreadLocalRandom.current().nextInt(keys.length)];
    }

    /**
     * Returns a random value from the pre-generated value array.
     */
    public String randomValue() {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * Returns a random complex object from the pre-generated array.
     */
    public ComplexObject randomObject() {
        return objects[ThreadLocalRandom.current().nextInt(objects.length)];
    }

    /**
     * Returns a random binary data array from the pre-generated array.
     */
    public byte[] randomBinary() {
        return binaryData[ThreadLocalRandom.current().nextInt(binaryData.length)];
    }

    /**
     * Returns a set of random keys of the specified size.
     */
    public Set<String> randomKeySet(int size) {
        Set<String> result = new HashSet<>();
        while (result.size() < size && result.size() < keys.length) {
            result.add(randomKey());
        }
        return result;
    }

    /**
     * Returns a map of random key-value pairs of the specified size.
     */
    public Map<String, String> randomBatchMap(int size) {
        Map<String, String> result = new HashMap<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (result.size() < size && result.size() < keys.length) {
            int idx = random.nextInt(keys.length);
            result.put(keys[idx], values[idx]);
        }
        return result;
    }
}
