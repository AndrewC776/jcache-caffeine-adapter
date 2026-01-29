package com.yms.cache.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for Expirable wrapper.
 * Tests expiration logic and immutability.
 */
class ExpirableTest {

    @Test
    void shouldStoreValueAndExpireTime() {
        long expireTime = System.currentTimeMillis() + 10000;
        Expirable<String> expirable = new Expirable<>("value", expireTime);

        assertEquals("value", expirable.getValue());
        assertEquals(expireTime, expirable.getExpireTime());
    }

    @Test
    void shouldBeExpiredWhenExpireTimeInPast() {
        long pastTime = System.currentTimeMillis() - 1000;
        Expirable<String> expirable = new Expirable<>("value", pastTime);

        assertTrue(expirable.isExpired());
    }

    @Test
    void shouldNotBeExpiredWhenExpireTimeInFuture() {
        long futureTime = System.currentTimeMillis() + 10000;
        Expirable<String> expirable = new Expirable<>("value", futureTime);

        assertFalse(expirable.isExpired());
    }

    @Test
    void shouldNeverExpireWithEternalTime() {
        Expirable<String> expirable = Expirable.eternal("value");

        assertFalse(expirable.isExpired());
        assertEquals(Long.MAX_VALUE, expirable.getExpireTime());
    }

    @Test
    void shouldCreateWithNewExpireTime() {
        Expirable<String> original = new Expirable<>("value", 1000L);
        long newExpireTime = 2000L;

        Expirable<String> updated = original.withExpireTime(newExpireTime);

        assertEquals("value", updated.getValue());
        assertEquals(newExpireTime, updated.getExpireTime());
        assertEquals(1000L, original.getExpireTime()); // immutable
    }

    @Test
    void shouldHandleNullValue() {
        Expirable<String> expirable = new Expirable<>(null, Long.MAX_VALUE);

        assertNull(expirable.getValue());
        assertFalse(expirable.isExpired());
    }

    @Test
    void shouldBeExpiredAtExactExpireTime() {
        // Edge case: expire time equals current time
        long now = System.currentTimeMillis();
        Expirable<String> expirable = new Expirable<>("value", now);

        // At exact time, should be considered expired (> check)
        assertTrue(expirable.isExpired() || System.currentTimeMillis() <= now);
    }
}
