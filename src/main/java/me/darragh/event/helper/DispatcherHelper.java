package me.darragh.event.helper;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Modifier;

/**
 * Provides commonly-used methods for {@link me.darragh.event.bus.EventDispatcher}s.
 *
 * @author darraghd493
 * @since 1.0.5
 */
@UtilityClass
public final class DispatcherHelper {
    /**
     * Validates the modifiers of a member.
     *
     * @param name The name of the member.
     * @param modifiers The modifiers of the member.
     * @param field Whether the member is a field.
     * @throws RuntimeException If the method is not public or is static.
     *
     * @since 1.0.5
     */
    public static void validateModifiers(String name, int modifiers, boolean field)  {
        if (!Modifier.isPublic(modifiers)) {
            throw new RuntimeException("Member %s is not public: %x".formatted(name, modifiers));
        }

        if (Modifier.isStatic(modifiers)) {
            throw new RuntimeException("Member %s is static: %x".formatted(name, modifiers));
        }

        if (!Modifier.isFinal(modifiers) && field) {
            throw new RuntimeException("Member %s is not final: %x".formatted(name, modifiers));
        }
    }
}
