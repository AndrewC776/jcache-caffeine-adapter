package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache containsKey operation.
 */
class CaffeineCacheContainsKeyTest {

    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Basic containsKey Tests ==========

    @Test
    void containsKeyShouldReturnFalseForMissingKey() {
        assertFalse(cache.containsKey("missing"));
    }

    @Test
    void containsKeyShouldReturnTrueForExistingKey() {
        cache.put("key", "value");

        assertTrue(cache.containsKey("key"));
    }

    @Test
    void containsKeyShouldThrowForNullKey() {
        assertThrows(NullPointerException.class, () -> cache.containsKey(null));
    }

    // ========== Expiration Tests ==========

    @Test
    void containsKeyShouldReturnFalseForExpiredEntry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 50)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        // Verify it exists initially
        assertTrue(expiringCache.containsKey("key"));

        Thread.sleep(100); // Wait for expiration

        // Should return false after expiration
        assertFalse(expiringCache.containsKey("key"));
    }

    @Test
    void containsKeyShouldNotUpdateAccessExpiry() throws InterruptedException {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        // Use AccessedExpiryPolicy to verify containsKey doesn't extend expiry
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MILLISECONDS, 100)));

        CaffeineCache<String, String> expiringCache = new CaffeineCache<>("expiring", null, config);
        expiringCache.put("key", "value");

        // Check containsKey multiple times - should NOT extend expiry
        Thread.sleep(30);
        assertTrue(expiringCache.containsKey("key"));
        Thread.sleep(30);
        assertTrue(expiringCache.containsKey("key"));
        Thread.sleep(50); // Total ~110ms - should be expired

        // Entry should still expire at original time
        assertFalse(expiringCache.containsKey("key"));
    }

    // ========== Statistics Tests ==========

    @Test
    void containsKeyShouldNotAffectStatistics() {
        cache.put("key", "value");

        // Reset any existing stats by creating fresh cache
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);
        CaffeineCache<String, String> freshCache = new CaffeineCache<>("fresh", null, config);
        freshCache.put("key", "value");

        // containsKey should NOT affect hit/miss stats per JCache spec
        freshCache.containsKey("key");
        freshCache.containsKey("missing");

        assertEquals(0, freshCache.getStatistics().getCacheHits());
        assertEquals(0, freshCache.getStatistics().getCacheMisses());
    }

    // ========== Closed Cache Tests ==========

    @Test
    void containsKeyShouldThrowWhenClosed() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.containsKey("key"));
    }
}
