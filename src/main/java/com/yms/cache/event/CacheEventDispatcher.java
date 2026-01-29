package com.yms.cache.event;

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches cache events to registered listeners.
 *
 * <p>Supports all JCache event types:
 * <ul>
 *   <li>{@link EventType#CREATED} - New entry created</li>
 *   <li>{@link EventType#UPDATED} - Existing entry updated</li>
 *   <li>{@link EventType#REMOVED} - Entry explicitly removed</li>
 *   <li>{@link EventType#EXPIRED} - Entry expired</li>
 * </ul>
 *
 * <p>Thread-safe listener registration using {@link CopyOnWriteArrayList}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public final class CacheEventDispatcher<K, V> {

    private final List<ListenerRegistration<K, V>> registrations = new CopyOnWriteArrayList<>();
    private Cache<K, V> cache;

    /**
     * Sets the source cache for events.
     *
     * @param cache the source cache
     */
    public void setCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    /**
     * Registers a listener configuration.
     *
     * @param config the listener configuration
     */
    @SuppressWarnings("unchecked")
    public void registerListener(CacheEntryListenerConfiguration<K, V> config) {
        CacheEntryListener<K, V> listener =
            (CacheEntryListener<K, V>) config.getCacheEntryListenerFactory().create();
        registrations.add(new ListenerRegistration<>(config, listener));
    }

    /**
     * Deregisters a listener configuration.
     *
     * @param config the listener configuration to remove
     */
    public void deregisterListener(CacheEntryListenerConfiguration<K, V> config) {
        registrations.removeIf(reg -> reg.config.equals(config));
    }

    /**
     * Dispatches a CREATED event.
     *
     * @param key the key
     * @param value the new value
     */
    public void dispatchCreated(K key, V value) {
        dispatch(EventType.CREATED, key, null, value);
    }

    /**
     * Dispatches an UPDATED event.
     *
     * @param key the key
     * @param oldValue the old value
     * @param newValue the new value
     */
    public void dispatchUpdated(K key, V oldValue, V newValue) {
        dispatch(EventType.UPDATED, key, oldValue, newValue);
    }

    /**
     * Dispatches a REMOVED event.
     *
     * @param key the key
     * @param value the removed value
     */
    public void dispatchRemoved(K key, V value) {
        dispatch(EventType.REMOVED, key, value, null);
    }

    /**
     * Dispatches an EXPIRED event.
     *
     * @param key the key
     * @param value the expired value
     */
    public void dispatchExpired(K key, V value) {
        dispatch(EventType.EXPIRED, key, value, null);
    }

    private void dispatch(EventType eventType, K key, V oldValue, V newValue) {
        if (registrations.isEmpty()) {
            return;
        }

        List<CacheEntryEvent<? extends K, ? extends V>> events = new ArrayList<>();
        events.add(new JCacheEntryEvent<>(cache, eventType, key, oldValue, newValue));

        for (ListenerRegistration<K, V> reg : registrations) {
            CacheEntryListener<K, V> listener = reg.listener;

            switch (eventType) {
                case CREATED:
                    if (listener instanceof CacheEntryCreatedListener) {
                        ((CacheEntryCreatedListener<K, V>) listener).onCreated(events);
                    }
                    break;
                case UPDATED:
                    if (listener instanceof CacheEntryUpdatedListener) {
                        ((CacheEntryUpdatedListener<K, V>) listener).onUpdated(events);
                    }
                    break;
                case REMOVED:
                    if (listener instanceof CacheEntryRemovedListener) {
                        ((CacheEntryRemovedListener<K, V>) listener).onRemoved(events);
                    }
                    break;
                case EXPIRED:
                    if (listener instanceof CacheEntryExpiredListener) {
                        ((CacheEntryExpiredListener<K, V>) listener).onExpired(events);
                    }
                    break;
            }
        }
    }

    /**
     * Internal holder for listener registration.
     */
    private static class ListenerRegistration<K, V> {
        final CacheEntryListenerConfiguration<K, V> config;
        final CacheEntryListener<K, V> listener;

        ListenerRegistration(CacheEntryListenerConfiguration<K, V> config,
                           CacheEntryListener<K, V> listener) {
            this.config = config;
            this.listener = listener;
        }
    }
}
