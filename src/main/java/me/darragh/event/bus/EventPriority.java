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
    EARLIEST((byte) -1),
    EARLY((byte) 0),
    DEFAULT((byte) 1),
    LATE((byte) 2),
    LATEST((byte) 3);

    private final byte value;
}
