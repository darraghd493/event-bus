package me.darragh.event.bus;

import me.darragh.event.Event;

import java.lang.reflect.ParameterizedType;

/**
 * Interface class for listening to dispatched events.
 * <p>
 * This is used as a common for handling events. The standard is methods annotated with {@link Listener}.
 *
 * @param <T> The type of event.
 *
 * @author darraghd493
 * @since 1.0.0
 */
public interface EventListener<T extends Event> {
    /**
     * Handle the event.
     *
     * @param event The event to handle.
     *
     * @since 1.0.0
     */
    void invoke(T event);

    /**
     * Retrieves the priority of the event listener.
     *
     * @return The priority of the event listener.
     *
     * @since 1.0.0
     */
    default EventPriority getPriority() {
        return EventPriority.NORMAL;
    }

    /**
     * Retrieves the type of event the listener listens to.
     *
     * @return The type of event the listener listens to.
     *
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    default Class<T> getEventType() {
        return (Class<T>) ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
    }
}
