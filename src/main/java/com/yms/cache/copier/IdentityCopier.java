package com.yms.cache.copier;

/**
 * Identity copier that returns the same reference.
 *
 * <p>Used when {@code storeByValue=false} (store-by-reference mode).
 * This is more performant but means mutations to cached values will
 * be visible to all holders of the reference.
 *
 * @param <T> the type of value to copy
 */
public final class IdentityCopier<T> implements Copier<T> {

    @Override
    public T copy(T value) {
        return value;
    }
}
