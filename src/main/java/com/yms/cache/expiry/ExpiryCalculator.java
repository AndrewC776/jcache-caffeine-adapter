package com.yms.cache.expiry;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;

/**
 * Calculates absolute expiration times from ExpiryPolicy durations.
 *
 * <p>Handles the three expiration scenarios per JCache spec:
 * <ul>
 *   <li><b>Creation</b> - when a new entry is created</li>
 *   <li><b>Update</b> - when an existing entry is updated</li>
 *   <li><b>Access</b> - when an entry is read</li>
 * </ul>
 *
 * <p>Duration semantics:
 * <ul>
 *   <li>{@code null} - don't modify the expiration time (return -1)</li>
 *   <li>{@link Duration#ZERO} - expire immediately</li>
 *   <li>{@link Duration#ETERNAL} - never expire (return Long.MAX_VALUE)</li>
 *   <li>Other - add duration to current time</li>
 * </ul>
 */
public final class ExpiryCalculator {

    private static final long NO_CHANGE = -1L;
    private final ExpiryPolicy policy;

    /**
     * Creates a new ExpiryCalculator with the given policy.
     *
     * @param policy the expiry policy
     */
    public ExpiryCalculator(ExpiryPolicy policy) {
        this.policy = policy;
    }

    /**
     * Calculates the expiration time for a newly created entry.
     *
     * @return the absolute expiration time in milliseconds
     */
    public long calculateCreationExpiry() {
        Duration duration = policy.getExpiryForCreation();
        return durationToExpireTime(duration);
    }

    /**
     * Calculates the expiration time for an updated entry.
     *
     * @param currentExpiry the current expiration time (ignored if duration is not null)
     * @return the new expiration time, or -1 if no change
     */
    public long calculateUpdateExpiry(long currentExpiry) {
        Duration duration = policy.getExpiryForUpdate();
        if (duration == null) {
            return NO_CHANGE;
        }
        return durationToExpireTime(duration);
    }

    /**
     * Calculates the expiration time for an accessed entry.
     *
     * @param currentExpiry the current expiration time (ignored if duration is not null)
     * @return the new expiration time, or -1 if no change
     */
    public long calculateAccessExpiry(long currentExpiry) {
        Duration duration = policy.getExpiryForAccess();
        if (duration == null) {
            return NO_CHANGE;
        }
        return durationToExpireTime(duration);
    }

    /**
     * Checks if the given expiry value indicates that the expiration should be updated.
     *
     * @param newExpiry the new expiry value
     * @return true if the expiry should be updated, false if no change
     */
    public boolean shouldUpdateExpiry(long newExpiry) {
        return newExpiry != NO_CHANGE;
    }

    /**
     * Converts a JCache Duration to an absolute expiration timestamp.
     *
     * @param duration the duration
     * @return the absolute expiration time in milliseconds
     */
    private long durationToExpireTime(Duration duration) {
        if (duration == null) {
            return NO_CHANGE;
        }
        if (duration.isEternal()) {
            return Long.MAX_VALUE;
        }
        if (duration.isZero()) {
            return System.currentTimeMillis();
        }
        return System.currentTimeMillis() + duration.getTimeUnit().toMillis(duration.getDurationAmount());
    }
}
