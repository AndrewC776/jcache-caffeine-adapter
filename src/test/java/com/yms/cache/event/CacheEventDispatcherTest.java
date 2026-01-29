package com.yms.cache.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for CacheEventDispatcher.
 * Tests event dispatching for CREATED/UPDATED/REMOVED/EXPIRED events.
 */
@SuppressWarnings("unchecked")
class CacheEventDispatcherTest {

    private CacheEventDispatcher<String, String> dispatcher;
    private List<CacheEntryEvent<? extends String, ? extends String>> receivedEvents;

    @BeforeEach
    void setUp() {
        dispatcher = new CacheEventDispatcher<>();
        dispatcher.setCache(mock(Cache.class));
        receivedEvents = new ArrayList<>();
    }

    // ========== CREATED Event Tests ==========

    @Test
    void shouldDispatchCreatedEvent() {
        CacheEntryCreatedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryCreatedListener.class);

        dispatcher.dispatchCreated("key", "value");

        assertEquals(1, receivedEvents.size());
        assertEquals(EventType.CREATED, receivedEvents.get(0).getEventType());
        assertEquals("key", receivedEvents.get(0).getKey());
        assertEquals("value", receivedEvents.get(0).getValue());
    }

    @Test
    void createdEventShouldNotHaveOldValue() {
        CacheEntryCreatedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryCreatedListener.class);

        dispatcher.dispatchCreated("key", "value");

        assertNull(receivedEvents.get(0).getOldValue());
        assertFalse(receivedEvents.get(0).isOldValueAvailable());
    }

    // ========== UPDATED Event Tests ==========

    @Test
    void shouldDispatchUpdatedEvent() {
        CacheEntryUpdatedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryUpdatedListener.class);

        dispatcher.dispatchUpdated("key", "oldValue", "newValue");

        assertEquals(1, receivedEvents.size());
        assertEquals(EventType.UPDATED, receivedEvents.get(0).getEventType());
        assertEquals("oldValue", receivedEvents.get(0).getOldValue());
        assertEquals("newValue", receivedEvents.get(0).getValue());
    }

    @Test
    void updatedEventShouldHaveOldValue() {
        CacheEntryUpdatedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryUpdatedListener.class);

        dispatcher.dispatchUpdated("key", "oldValue", "newValue");

        assertTrue(receivedEvents.get(0).isOldValueAvailable());
    }

    // ========== REMOVED Event Tests ==========

    @Test
    void shouldDispatchRemovedEvent() {
        CacheEntryRemovedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryRemovedListener.class);

        dispatcher.dispatchRemoved("key", "value");

        assertEquals(1, receivedEvents.size());
        assertEquals(EventType.REMOVED, receivedEvents.get(0).getEventType());
        assertEquals("key", receivedEvents.get(0).getKey());
        assertEquals("value", receivedEvents.get(0).getOldValue());
    }

    // ========== EXPIRED Event Tests ==========

    @Test
    void shouldDispatchExpiredEvent() {
        CacheEntryExpiredListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(listener, CacheEntryExpiredListener.class);

        dispatcher.dispatchExpired("key", "value");

        assertEquals(1, receivedEvents.size());
        assertEquals(EventType.EXPIRED, receivedEvents.get(0).getEventType());
        assertEquals("key", receivedEvents.get(0).getKey());
        assertEquals("value", receivedEvents.get(0).getOldValue());
    }

    // ========== Listener Management Tests ==========

    @Test
    void shouldNotDispatchToUnregisteredListener() {
        dispatcher.dispatchCreated("key", "value");

        assertTrue(receivedEvents.isEmpty());
    }

    @Test
    void shouldUnregisterListener() {
        CacheEntryCreatedListener<String, String> listener = events -> {
            events.forEach(receivedEvents::add);
        };
        CacheEntryListenerConfiguration<String, String> config =
            registerListener(listener, CacheEntryCreatedListener.class);

        dispatcher.deregisterListener(config);
        dispatcher.dispatchCreated("key", "value");

        assertTrue(receivedEvents.isEmpty());
    }

    @Test
    void shouldDispatchToMultipleListeners() {
        List<CacheEntryEvent<? extends String, ? extends String>> secondReceivedEvents = new ArrayList<>();

        CacheEntryCreatedListener<String, String> listener1 = events -> {
            events.forEach(receivedEvents::add);
        };
        CacheEntryCreatedListener<String, String> listener2 = events -> {
            events.forEach(secondReceivedEvents::add);
        };
        registerListener(listener1, CacheEntryCreatedListener.class);
        registerListener(listener2, CacheEntryCreatedListener.class);

        dispatcher.dispatchCreated("key", "value");

        assertEquals(1, receivedEvents.size());
        assertEquals(1, secondReceivedEvents.size());
    }

    @Test
    void shouldOnlyDispatchToMatchingListenerType() {
        CacheEntryCreatedListener<String, String> createdListener = events -> {
            events.forEach(receivedEvents::add);
        };
        registerListener(createdListener, CacheEntryCreatedListener.class);

        // Dispatch UPDATED event - should NOT go to CREATED listener
        dispatcher.dispatchUpdated("key", "old", "new");

        assertTrue(receivedEvents.isEmpty());
    }

    // ========== Helper Methods ==========

    private CacheEntryListenerConfiguration<String, String> registerListener(
            CacheEntryListener<String, String> listener,
            Class<? extends CacheEntryListener> listenerClass) {

        CacheEntryListenerConfiguration<String, String> config =
            mock(CacheEntryListenerConfiguration.class);
        Factory factory = mock(Factory.class);

        doReturn(factory).when(config).getCacheEntryListenerFactory();
        doReturn(listener).when(factory).create();
        when(config.isOldValueRequired()).thenReturn(true);
        when(config.isSynchronous()).thenReturn(true);

        dispatcher.registerListener(config);
        return config;
    }
}
