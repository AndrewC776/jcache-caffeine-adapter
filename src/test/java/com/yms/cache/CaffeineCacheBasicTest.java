package com.yms.cache;

import com.yms.cache.config.YmsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.Cache;
import javax.cache.configuration.Configuration;
import javax.cache.expiry.EternalExpiryPolicy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for CaffeineCache basic structure.
 * Tests lifecycle, configuration, and core state management.
 */
class CaffeineCacheBasicTest {

    private Cache<String, String> cache;
    private YmsConfiguration<String, String> config;

    @BeforeEach
    void setUp() {
        config = new YmsConfiguration<>();
        config.setTypes(String.class, String.class);
        config.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
        config.setStatisticsEnabled(true);

        cache = new CaffeineCache<>("testCache", null, config);
    }

    // ========== Name Tests ==========

    @Test
    void shouldReturnCacheName() {
        assertEquals("testCache", cache.getName());
    }

    // ========== Configuration Tests ==========

    @Test
    void shouldReturnConfiguration() {
        Configuration<String, String> returnedConfig = cache.getConfiguration(Configuration.class);

        assertNotNull(returnedConfig);
        assertEquals(String.class, returnedConfig.getKeyType());
        assertEquals(String.class, returnedConfig.getValueType());
    }

    @Test
    void shouldReturnYmsConfiguration() {
        YmsConfiguration<String, String> returnedConfig =
            cache.getConfiguration(YmsConfiguration.class);

        assertNotNull(returnedConfig);
        assertTrue(returnedConfig.isStatisticsEnabled());
    }

    @Test
    void shouldThrowForUnsupportedConfigurationType() {
        assertThrows(IllegalArgumentException.class,
            () -> cache.getConfiguration(UnsupportedConfig.class));
    }

    // ========== Lifecycle Tests ==========

    @Test
    void shouldNotBeClosedInitially() {
        assertFalse(cache.isClosed());
    }

    @Test
    void shouldBeClosedAfterClose() {
        cache.close();

        assertTrue(cache.isClosed());
    }

    @Test
    void shouldThrowAfterCloseOnGet() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.get("key"));
    }

    @Test
    void shouldThrowAfterCloseOnPut() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.put("key", "value"));
    }

    @Test
    void shouldThrowAfterCloseOnContainsKey() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.containsKey("key"));
    }

    @Test
    void shouldThrowAfterCloseOnRemove() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.remove("key"));
    }

    @Test
    void shouldThrowAfterCloseOnClear() {
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.clear());
    }

    @Test
    void shouldAllowMultipleCloses() {
        cache.close();
        cache.close(); // Should not throw

        assertTrue(cache.isClosed());
    }

    // ========== Unwrap Tests ==========

    @Test
    void shouldUnwrapToSelf() {
        CaffeineCache<String, String> unwrapped = cache.unwrap(CaffeineCache.class);

        assertSame(cache, unwrapped);
    }

    @Test
    void shouldThrowForUnsupportedUnwrap() {
        assertThrows(IllegalArgumentException.class,
            () -> cache.unwrap(String.class));
    }

    // ========== CacheManager Tests ==========

    @Test
    void shouldReturnNullCacheManager() {
        // We passed null in setUp
        assertNull(cache.getCacheManager());
    }

    // ========== Helper Classes ==========

    private interface UnsupportedConfig extends Configuration<String, String> {}
}
