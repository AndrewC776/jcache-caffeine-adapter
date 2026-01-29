package com.yms.cache.stats;

import javax.cache.management.CacheStatisticsMXBean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe implementation of JCache statistics.
 *
 * <p>Uses {@link LongAdder} for high-performance concurrent counting.
 * This is optimal for high write-contention scenarios like cache statistics.
 *
 * <p><b>Statistics tracked:</b>
 * <ul>
 *   <li>{@code hits} - cache hits (value found and not expired)</li>
 *   <li>{@code misses} - cache misses (value not found or expired)</li>
 *   <li>{@code puts} - successful put operations</li>
 *   <li>{@code removals} - successful remove operations</li>
 *   <li>{@code evictions} - entries evicted (size/weight or expiration)</li>
 * </ul>
 *
 * <p><b>Per design:</b>
 * <ul>
 *   <li>Expired hit counts as miss</li>
 *   <li>invoke internal ops don't double-count</li>
 *   <li>clear() doesn't count as eviction</li>
 *   <li>Average times return 0 (not implemented per spec)</li>
 * </ul>
 */
public final class JCacheStatistics implements CacheStatisticsMXBean {

    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder puts = new LongAdder();
    private final LongAdder removals = new LongAdder();
    private final LongAdder evictions = new LongAdder();

    /**
     * Records a cache hit.
     */
    public void recordHit() {
        hits.increment();
    }

    /**
     * Records a cache miss.
     */
    public void recordMiss() {
        misses.increment();
    }

    /**
     * Records a put operation.
     */
    public void recordPut() {
        puts.increment();
    }

    /**
     * Records a removal operation.
     */
    public void recordRemoval() {
        removals.increment();
    }

    /**
     * Records an eviction (size/weight or expiration).
     */
    public void recordEviction() {
        evictions.increment();
    }

    @Override
    public void clear() {
        hits.reset();
        misses.reset();
        puts.reset();
        removals.reset();
        evictions.reset();
    }

    @Override
    public long getCacheHits() {
        return hits.sum();
    }

    @Override
    public float getCacheHitPercentage() {
        long total = getCacheGets();
        return total == 0 ? 0.0f : (float) getCacheHits() / total * 100.0f;
    }

    @Override
    public long getCacheMisses() {
        return misses.sum();
    }

    @Override
    public float getCacheMissPercentage() {
        long total = getCacheGets();
        return total == 0 ? 0.0f : (float) getCacheMisses() / total * 100.0f;
    }

    @Override
    public long getCacheGets() {
        return getCacheHits() + getCacheMisses();
    }

    @Override
    public long getCachePuts() {
        return puts.sum();
    }

    @Override
    public long getCacheRemovals() {
        return removals.sum();
    }

    @Override
    public long getCacheEvictions() {
        return evictions.sum();
    }

    @Override
    public float getAverageGetTime() {
        return 0; // Not implemented per design
    }

    @Override
    public float getAveragePutTime() {
        return 0; // Not implemented per design
    }

    @Override
    public float getAverageRemoveTime() {
        return 0; // Not implemented per design
    }
}
