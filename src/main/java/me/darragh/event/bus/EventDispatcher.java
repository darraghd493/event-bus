package me.darragh.event.bus;

import me.darragh.event.Event;

/**
 * Interface class for handling the dispatching of events.
 *
 * @author darraghd493
 * @since 1.0.0
 */
public interface EventDispatcher<T extends Event> {
    /**
     * Register a class to the event dispatcher.
     *
     * @param instance The instantiated class to register.
     *
     * @since 1.0.0
     */
    void register(Object instance);

    /**
     * Registers a singular event listener to the event dispatcher.
     *
     * @param listener The listener to register.
     *
     * @since 1.0.0
     */
    void register(EventListener<T> listener);

    /**
     * Unregister a class from the event dispatcher.
     *
     * @param instance The instantiated class to unregister.
     *
     * @since 1.0.0
     */
    void unregister(Object instance);

    /**
     * Unregister a singular event listener from the event dispatcher.
     *
     * @param listener The listener to unregister.
     *
     * @since 1.0.0
     */
    void unregister(EventListener<T> listener);

    /**
     * Invoke the event.
     *
     * @param event The event to invoke.
     *
     * @since 1.0.0
     */
    void invoke(T event);

    /**
     * Tests for any specified event listeners.
     *
     * @param <U> The event type to test for.
     *
     * @param eventClass The event class to test for.
     * @return Whether the event has any listeners.
     *
     * @since 1.0.0
     */
    <U extends T> boolean testFor(Class<U> eventClass);
}
