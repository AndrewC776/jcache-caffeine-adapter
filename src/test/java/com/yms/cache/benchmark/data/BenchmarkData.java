package com.yms.cache.benchmark.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating benchmark test data.
 */
public final class BenchmarkData {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private BenchmarkData() {
    }

    /**
     * Generate a random string of specified length.
     */
    public static String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generate random binary data of specified size.
     */
    public static byte[] generateBinary(int size) {
        byte[] data = new byte[size];
        ThreadLocalRandom.current().nextBytes(data);
        return data;
    }

    /**
     * Generate a complex object with nested structures.
     */
    public static ComplexObject generateComplexObject(int seed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        ComplexObject obj = new ComplexObject();
        obj.setId("obj-" + seed);
        obj.setName("Object " + seed);
        obj.setDescription(generateString(500));
        obj.setTimestamp(System.currentTimeMillis());
        obj.setScore(random.nextDouble() * 1000);

        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tags.add("tag-" + random.nextInt(100));
        }
        obj.setTags(tags);

        Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            metadata.put("meta-" + i, generateString(50));
        }
        obj.setMetadata(metadata);

        List<ComplexObject.NestedObject> nestedList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ComplexObject.NestedObject nested = new ComplexObject.NestedObject();
            nested.setKey("nested-" + i);
            nested.setValue(generateString(100));
            int[] numbers = new int[10];
            for (int j = 0; j < 10; j++) {
                numbers[j] = random.nextInt(10000);
            }
            nested.setNumbers(numbers);
            nestedList.add(nested);
        }
        obj.setNestedList(nestedList);

        return obj;
    }

    /**
     * Pre-generate an array of keys.
     */
    public static String[] generateKeys(int count, String prefix) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = prefix + i;
        }
        return keys;
    }

    /**
     * Pre-generate an array of string values.
     */
    public static String[] generateValues(int count, int valueSize) {
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = generateString(valueSize);
        }
        return values;
    }
}
