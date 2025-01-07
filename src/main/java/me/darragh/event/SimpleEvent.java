package me.darragh.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Simple implementation of a cancellable event.
 *
 * @author darraghd493
 * @since 1.0.0
 */
@Getter
@Setter
@RequiredArgsConstructor
public abstract class SimpleEvent implements CancellableEvent {
    private boolean cancelled;
}
