package com.yms.cache.config;

import org.junit.jupiter.api.Test;
import javax.cache.configuration.MutableConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for YmsConfiguration.
 * Tests extension of MutableConfiguration with maximumSize/Weight support.
 */
class YmsConfigurationTest {

    @Test
    void shouldExtendMutableConfiguration() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        assertTrue(config instanceof MutableConfiguration);
    }

    @Test
    void shouldSetAndGetMaximumSize() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        config.setMaximumSize(1000L);

        assertEquals(1000L, config.getMaximumSize());
    }

    @Test
    void shouldSetAndGetMaximumWeight() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        config.setMaximumWeight(5000L);
        config.setWeigher((k, v) -> v.length());

        assertEquals(5000L, config.getMaximumWeight());
        assertNotNull(config.getWeigher());
    }

    @Test
    void shouldThrowWhenSettingWeightAfterSize() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setMaximumSize(1000L);

        assertThrows(IllegalStateException.class, () -> config.setMaximumWeight(5000L));
    }

    @Test
    void shouldThrowWhenSettingSizeAfterWeight() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setMaximumWeight(5000L);

        assertThrows(IllegalStateException.class, () -> config.setMaximumSize(1000L));
    }

    @Test
    void shouldThrowWhenMaximumWeightSetWithoutWeigher() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setMaximumWeight(5000L);

        assertThrows(IllegalStateException.class, () -> config.validate());
    }

    @Test
    void shouldPassValidationWithWeigher() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setMaximumWeight(5000L);
        config.setWeigher((k, v) -> v.length());

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void shouldPassValidationWithMaximumSize() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();
        config.setMaximumSize(1000L);

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void shouldPassValidationWithNoLimits() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void shouldSupportFluentApi() {
        YmsConfiguration<String, String> config = new YmsConfiguration<String, String>()
            .setMaximumSize(1000L)
            .setTypes(String.class, String.class)
            .setStoreByValue(true);

        assertEquals(1000L, config.getMaximumSize());
        assertTrue(config.isStoreByValue());
        assertEquals(String.class, config.getKeyType());
        assertEquals(String.class, config.getValueType());
    }

    @Test
    void shouldCopyConfiguration() {
        YmsConfiguration<String, String> original = new YmsConfiguration<>();
        original.setMaximumSize(1000L);
        original.setTypes(String.class, String.class);
        original.setStoreByValue(true);

        YmsConfiguration<String, String> copy = new YmsConfiguration<>(original);

        assertEquals(original.getMaximumSize(), copy.getMaximumSize());
        assertEquals(original.isStoreByValue(), copy.isStoreByValue());
        assertEquals(original.getKeyType(), copy.getKeyType());
    }

    @Test
    void shouldCopyConfigurationWithWeigher() {
        YmsConfiguration<String, String> original = new YmsConfiguration<>();
        original.setMaximumWeight(5000L);
        original.setWeigher((k, v) -> v.length());

        YmsConfiguration<String, String> copy = new YmsConfiguration<>(original);

        assertEquals(original.getMaximumWeight(), copy.getMaximumWeight());
        assertNotNull(copy.getWeigher());
    }

    @Test
    void shouldReturnNullForUnsetMaximumSize() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        assertNull(config.getMaximumSize());
    }

    @Test
    void shouldReturnNullForUnsetMaximumWeight() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        assertNull(config.getMaximumWeight());
    }

    @Test
    void shouldReturnNullForUnsetWeigher() {
        YmsConfiguration<String, String> config = new YmsConfiguration<>();

        assertNull(config.getWeigher());
    }
}
