package me.darragh.event.bus;

import me.darragh.event.Event;

/**
 * Public functional interface for handling events within a lambda for {@link SimpleEventListener#create(Class, SimpleEventConsumer)}.
 *
 * @param <U> The type of event.
 *
 * @author darragh493
 * @since 1.0.0
 */
@FunctionalInterface
public interface SimpleEventConsumer<U extends Event> {
    /**
     * Handle the event.
     *
     * @param event The event to handle.
     *
     * @since 1.0.0
     */
    void invoke(U event);
}
