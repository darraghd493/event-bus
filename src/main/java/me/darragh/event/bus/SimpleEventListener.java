package me.darragh.event.bus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.darragh.event.Event;

/**
 * An implementation of {@link EventListener} as an abstract class with caching of the event type to prevent unnecessary reflection.
 *
 * @param <T> The type of event.
 *
 * @author darraghd493
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public abstract class SimpleEventListener<T extends Event> implements EventListener<T> {
    private final Class<T> eventType;

    /**
     * Creates a new {@link SimpleEventListener} with the given event type and listener.
     *
     * @param <U> The type of event.
     *
     * @param eventClass The class of the event.
     * @param listener The listener to handle the event.
     * @return The created event listener.
     */
    public static <U extends Event> SimpleEventListener<U> create(Class<U> eventClass, SimpleEventConsumer<U> listener) {
        return new SimpleEventListener<U>(eventClass) {
            @Override
            public void invoke(U event) {
                listener.invoke(event);
            }
        };
    }
}
