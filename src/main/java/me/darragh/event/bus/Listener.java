package me.darragh.event.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks either a method or {@link me.darragh.event.bus.EventListener} as an active listener (identifiable).
 *
 * @author darraghd493
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface Listener {
    EventPriority priority() default EventPriority.NORMAL;
}
