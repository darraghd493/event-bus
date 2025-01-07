package me.darragh.event;

/**
 * Interface class for cancellable events.
 *
 * @author darraghd493
 * @since 1.0.0
 */
public interface CancellableEvent extends Event {
    /**
     * Set the event as cancelled.
     *
     * @param cancelled The state of the event.
     *
     * @since 1.0.0
     */
    void setCancelled(boolean cancelled);

    /**
     * Check if the event is cancelled.
     *
     * @return If the event is cancelled.
     *
     * @since 1.0.0
     */
    boolean isCancelled();
}
