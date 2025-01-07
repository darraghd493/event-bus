package me.darragh.event.bus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Represents the priority of an event.
 *
 * @author darraghd493
 * @since 1.0.0
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EventPriority {
    LOWEST((byte) -1),
    LOW((byte) 0),
    NORMAL((byte) 1),
    HIGH((byte) 2),
    HIGHEST((byte) 3);

    private final byte value;
}
