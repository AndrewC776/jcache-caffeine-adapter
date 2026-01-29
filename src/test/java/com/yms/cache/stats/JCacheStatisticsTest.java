package com.yms.cache.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for JCacheStatistics.
 * Tests thread-safe statistics collection per JCache specification.
 */
class JCacheStatisticsTest {

    private JCacheStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new JCacheStatistics();
    }

    // ========== Initial State Tests ==========

    @Test
    void shouldStartWithZeroCounts() {
        assertEquals(0, stats.getCacheHits());
        assertEquals(0, stats.getCacheMisses());
        assertEquals(0, stats.getCachePuts());
        assertEquals(0, stats.getCacheRemovals());
        assertEquals(0, stats.getCacheEvictions());
    }

    @Test
    void shouldStartWithZeroGets() {
        assertEquals(0, stats.getCacheGets());
    }

    // ========== Hit/Miss Tests ==========

    @Test
    void shouldIncrementHits() {
        stats.recordHit();
        stats.recordHit();

        assertEquals(2, stats.getCacheHits());
    }

    @Test
    void shouldIncrementMisses() {
        stats.recordMiss();

        assertEquals(1, stats.getCacheMisses());
    }

    @Test
    void shouldCalculateGetsAsHitsPlusMisses() {
        stats.recordHit();
        stats.recordHit();
        stats.recordMiss();

        assertEquals(3, stats.getCacheGets());
    }

    // ========== Put/Removal/Eviction Tests ==========

    @Test
    void shouldIncrementPuts() {
        stats.recordPut();
        stats.recordPut();
        stats.recordPut();

        assertEquals(3, stats.getCachePuts());
    }

    @Test
    void shouldIncrementRemovals() {
        stats.recordRemoval();

        assertEquals(1, stats.getCacheRemovals());
    }

    @Test
    void shouldIncrementEvictions() {
        stats.recordEviction();
        stats.recordEviction();

        assertEquals(2, stats.getCacheEvictions());
    }

    // ========== Percentage Tests ==========

    @Test
    void shouldCalculateHitPercentage() {
        stats.recordHit();
        stats.recordHit();
        stats.recordHit();
        stats.recordMiss();

        assertEquals(75.0f, stats.getCacheHitPercentage(), 0.01f);
    }

    @Test
    void shouldCalculateMissPercentage() {
        stats.recordHit();
        stats.recordMiss();
        stats.recordMiss();
        stats.recordMiss();

        assertEquals(75.0f, stats.getCacheMissPercentage(), 0.01f);
    }

    @Test
    void shouldReturnZeroPercentageWhenNoAccesses() {
        assertEquals(0.0f, stats.getCacheHitPercentage(), 0.01f);
        assertEquals(0.0f, stats.getCacheMissPercentage(), 0.01f);
    }

    @Test
    void shouldReturn100PercentHitWhenAllHits() {
        stats.recordHit();
        stats.recordHit();
        stats.recordHit();

        assertEquals(100.0f, stats.getCacheHitPercentage(), 0.01f);
        assertEquals(0.0f, stats.getCacheMissPercentage(), 0.01f);
    }

    @Test
    void shouldReturn100PercentMissWhenAllMisses() {
        stats.recordMiss();
        stats.recordMiss();

        assertEquals(0.0f, stats.getCacheHitPercentage(), 0.01f);
        assertEquals(100.0f, stats.getCacheMissPercentage(), 0.01f);
    }

    // ========== Clear Tests ==========

    @Test
    void shouldClearAllStatistics() {
        stats.recordHit();
        stats.recordMiss();
        stats.recordPut();
        stats.recordRemoval();
        stats.recordEviction();

        stats.clear();

        assertEquals(0, stats.getCacheHits());
        assertEquals(0, stats.getCacheMisses());
        assertEquals(0, stats.getCachePuts());
        assertEquals(0, stats.getCacheRemovals());
        assertEquals(0, stats.getCacheEvictions());
    }

    // ========== Average Time Tests (Per spec: return 0) ==========

    @Test
    void shouldReturnZeroForAverageGetTime() {
        assertEquals(0.0f, stats.getAverageGetTime(), 0.01f);
    }

    @Test
    void shouldReturnZeroForAveragePutTime() {
        assertEquals(0.0f, stats.getAveragePutTime(), 0.01f);
    }

    @Test
    void shouldReturnZeroForAverageRemoveTime() {
        assertEquals(0.0f, stats.getAverageRemoveTime(), 0.01f);
    }

    // ========== Thread Safety Tests ==========

    @Test
    void shouldBeThreadSafeForHits() throws InterruptedException {
        int threads = 10;
        int incrementsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    stats.recordHit();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threads * incrementsPerThread, stats.getCacheHits());
    }

    @Test
    void shouldBeThreadSafeForMixedOperations() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    switch (threadId % 4) {
                        case 0 -> stats.recordHit();
                        case 1 -> stats.recordMiss();
                        case 2 -> stats.recordPut();
                        case 3 -> stats.recordEviction();
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 2 threads each for hit, miss, put, eviction
        assertEquals(2 * operationsPerThread, stats.getCacheHits());
        assertEquals(2 * operationsPerThread, stats.getCacheMisses());
        assertEquals(2 * operationsPerThread, stats.getCachePuts());
        assertEquals(2 * operationsPerThread, stats.getCacheEvictions());
    }
}
