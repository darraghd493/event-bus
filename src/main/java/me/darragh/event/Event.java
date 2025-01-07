package me.darragh.event;

/**
 * Interface class for events.
 *
 * @author darraghd493
 * @since 1.0.0
 */
public interface Event {
    /**
     * Post the event to the default event dispatcher
     * @return The current event.
     * @param <T> The type of event.
     */
    <T extends Event> T post();
}
