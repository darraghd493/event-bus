package me.darragh.event.bus;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.darragh.event.Event;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A simple event dispatcher implementation.
 * <p>
 * This is intended to be widely applicable and is not optimised for any specific use case.
 *
 * @author darraghd493
 * @since 1.0.0
 */
public class SimpleEventDispatcher<T extends Event> implements EventDispatcher<T> {
    private static final List<?> EMPTY_LIST = List.of();

    private final Map<Type, List<EventListener<T>>> listeners;
    private final Map<Type, Boolean> sortedListeners;

    public SimpleEventDispatcher() {
        this.listeners = new ConcurrentHashMap<>();
        this.sortedListeners = new ConcurrentHashMap<>();
    }

    @Override
    public void register(Object instance) {
        Objects.requireNonNull(instance, "Instance cannot be null.");

        for (var method : instance.getClass().getDeclaredMethods()) {
            this.registerMethodListener(instance, method);
        }

        for (var field : instance.getClass().getDeclaredFields()) {
            this.registerFieldListener(instance, field);
        }
    }

    @Override
    public void register(EventListener<T> listener) {
        List<EventListener<T>> eventListeners = this.listeners.computeIfAbsent(
                listener.getEventType(),
                arr -> new CopyOnWriteArrayList<>()
        );
        eventListeners.add(listener);
        this.sortedListeners.put(listener.getEventType(), false);
    }

    @Override
    public void unregister(Object instance) {
        for (var method : instance.getClass().getDeclaredMethods()) {
            Listener annotation = method.getAnnotation(Listener.class);
            if (annotation == null) {
                continue;
            }

            EventListener<T> listener = new MethodEventListener<>(annotation, instance, method);
            this.removeListener(listener);
        }
    }

    @Override
    public void unregister(EventListener<T> listener) {
        this.removeListener(listener);
    }

    @Override
    public void invoke(T event) {
        List<EventListener<T>> eventListeners = this.listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }

        if (!this.sortedListeners.getOrDefault(event.getClass(), false)) {
            this.sortListeners(event.getClass());
        }

        for (EventListener<T> listener : eventListeners) {
            try {
                listener.invoke(event);
            } catch (Exception e) {
                System.err.printf("Error invoking listener: %s%n", e); // TODO: Logging
                e.printStackTrace(); // TODO: Remove
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U extends T> boolean testFor(Class<U> eventClass) {
        return !this.listeners.getOrDefault(eventClass, (List<EventListener<T>>) EMPTY_LIST).isEmpty();
    }

    /**
     * Registers a method as a listener.
     *
     * @param instance The instance of the class.
     * @param method The method to register.
     */
    private void registerMethodListener(Object instance, Method method) {
        var annotation = method.getAnnotation(Listener.class);
        if (annotation == null) {
            return;
        }

        this.validateModifiers(method.getName(), method.getModifiers(), false);

        EventListener<T> listener = new MethodEventListener<>(annotation, instance, method);
        this.listeners
                .computeIfAbsent(
                        listener.getEventType(),
                        arr -> new CopyOnWriteArrayList<>()
                )
                .add(listener);

        this.sortedListeners.put(listener.getEventType(), false);
    }

    /**
     * Registers a field as a listener.
     *
     * @param instance The instance of the class.
     * @param field The field to register.
     */
    @SuppressWarnings("unchecked")
    private void registerFieldListener(Object instance, Field field) {
        if (!EventListener.class.isAssignableFrom(field.getType())) {
            return;
        }

        var annotation = field.getAnnotation(Listener.class);
        if (annotation == null) {
            return;
        }

        this.validateModifiers(field.getName(), field.getModifiers(), true);

        try {
            EventListener<T> listener = (EventListener<T>) field.get(instance);
            if (listener != null) {
                this.listeners
                        .computeIfAbsent(
                                listener.getEventType(),
                                arr -> new CopyOnWriteArrayList<>()
                        ) // <- at .getEventType()
                        .add(listener);
            } else {
                throw new RuntimeException("Listener field %s is null.".formatted(field.getName()));
            }
            this.sortedListeners.put(listener.getEventType(), false);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to access field: " + field.getName(), e);
        }
    }

    /**
     * Validates the modifiers of a member.
     *
     * @param name The name of the member.
     * @param modifiers The modifiers of the member.
     * @param field Whether the member is a field.
     * @throws RuntimeException If the method is not public or is static.
     *
     * @since 1.0.0
     */
    private void validateModifiers(String name, int modifiers, boolean field)  {
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

    /**
     * Sorts the listeners for a given event type.
     *
     * @param eventType The event type to sort the listeners for.
     *
     * @since 1.0.0
     *
     * @see EventPriority
     */
    private void sortListeners(Type eventType) {
        List<EventListener<T>> eventListeners = this.listeners.get(eventType);
        if (eventListeners == null) {
            return;
        }

        eventListeners.sort(Comparator.comparing(eventListener -> eventListener.getPriority().value()));
        this.sortedListeners.put(eventType, true);
    }

    /**
     * Removes a listener from the dispatcher.
     *
     * @param listener The listener to remove.
     *
     * @since 1.0.0
     */
    private void removeListener(EventListener<T> listener) {
        this.listeners.computeIfPresent(listener.getEventType(), (type, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
        this.sortedListeners.remove(listener.getEventType());
    }

    /**
     * Represents a method as an event listener.
     *
     * @author darraghd493
     * @since 1.0.0
     */
    @EqualsAndHashCode
    public static class MethodEventListener<T extends Event> implements EventListener<T> {
        private final Listener annotation;
        private final Object instance;
        private final Method method;

        @Getter
        private final Class<T> eventType;

        /**
         * Create a new method event listener.
         *
         * @param instance The instance of the class.
         * @param method The method to listen to.
         *
         * @since 1.0.0
         */
        public MethodEventListener(Listener annotation, Object instance, Method method) {
            this.annotation = annotation;
            this.instance = instance;
            this.method = method;
            this.eventType = (Class<T>) method.getGenericParameterTypes()[0];
        }

        @Override
        public void invoke(Event event) {
            try {
                this.method.invoke(instance, event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public EventPriority getPriority() {
            return this.annotation.priority();
        }
    }
}
