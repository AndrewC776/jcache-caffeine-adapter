package com.yms.cache.expiry;

import org.junit.jupiter.api.Test;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for ExpiryCalculator.
 * Tests expiration time calculation based on ExpiryPolicy.
 */
class ExpiryCalculatorTest {

    // ========== Creation Expiry Tests ==========

    @Test
    void shouldCalculateCreationExpiry() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForCreation()).thenReturn(Duration.ONE_MINUTE);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateCreationExpiry();
        long now = System.currentTimeMillis();

        // Should be approximately 1 minute (60000ms) in the future
        assertTrue(expiry >= now + 59900, "Expiry should be at least 59.9 seconds in future");
        assertTrue(expiry <= now + 60100, "Expiry should be at most 60.1 seconds in future");
    }

    @Test
    void shouldReturnEternalForEternalDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForCreation()).thenReturn(Duration.ETERNAL);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateCreationExpiry();

        assertEquals(Long.MAX_VALUE, expiry);
    }

    @Test
    void shouldReturnImmediateExpiryForZeroDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForCreation()).thenReturn(Duration.ZERO);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateCreationExpiry();
        long now = System.currentTimeMillis();

        // Zero duration means immediate expiry (current time)
        assertTrue(expiry <= now + 100, "Zero duration should mean immediate expiry");
    }

    // ========== Update Expiry Tests ==========

    @Test
    void shouldCalculateUpdateExpiry() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForUpdate()).thenReturn(Duration.FIVE_MINUTES);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateUpdateExpiry(1000L);
        long now = System.currentTimeMillis();

        // Should be approximately 5 minutes (300000ms) in the future
        assertTrue(expiry >= now + 299900);
        assertTrue(expiry <= now + 300100);
    }

    @Test
    void shouldReturnNoChangeForNullUpdateDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForUpdate()).thenReturn(null);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateUpdateExpiry(1000L);

        assertEquals(-1L, expiry); // -1 means "no change"
    }

    @Test
    void shouldReturnNoChangeIndicatorValue() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForUpdate()).thenReturn(null);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateUpdateExpiry(5000L);

        assertFalse(calculator.shouldUpdateExpiry(expiry));
    }

    // ========== Access Expiry Tests ==========

    @Test
    void shouldCalculateAccessExpiry() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForAccess()).thenReturn(Duration.TEN_MINUTES);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateAccessExpiry(1000L);
        long now = System.currentTimeMillis();

        // Should be approximately 10 minutes (600000ms) in the future
        assertTrue(expiry >= now + 599900);
        assertTrue(expiry <= now + 600100);
    }

    @Test
    void shouldReturnNoChangeForNullAccessDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForAccess()).thenReturn(null);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateAccessExpiry(1000L);

        assertEquals(-1L, expiry);
    }

    @Test
    void shouldIndicateUpdateNeededForValidExpiry() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForAccess()).thenReturn(Duration.ONE_MINUTE);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateAccessExpiry(1000L);

        assertTrue(calculator.shouldUpdateExpiry(expiry));
    }

    // ========== Edge Cases ==========

    @Test
    void shouldHandleOneHourDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForCreation()).thenReturn(Duration.ONE_HOUR);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateCreationExpiry();
        long now = System.currentTimeMillis();

        // Should be approximately 1 hour (3600000ms) in the future
        assertTrue(expiry >= now + 3599900);
        assertTrue(expiry <= now + 3600100);
    }

    @Test
    void shouldHandleOneDayDuration() {
        ExpiryPolicy policy = mock(ExpiryPolicy.class);
        when(policy.getExpiryForCreation()).thenReturn(Duration.ONE_DAY);
        ExpiryCalculator calculator = new ExpiryCalculator(policy);

        long expiry = calculator.calculateCreationExpiry();
        long now = System.currentTimeMillis();

        long oneDayMs = 24 * 60 * 60 * 1000L;
        assertTrue(expiry >= now + oneDayMs - 100);
        assertTrue(expiry <= now + oneDayMs + 100);
    }
}
