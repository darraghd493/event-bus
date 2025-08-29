package me.darragh.event.bus;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.darragh.event.Event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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

            MethodEventListener<T> listener = this.createMethodListener(annotation, instance, method);
            this.removeListener(listener);
        }

        for (var field : instance.getClass().getDeclaredFields()) { // TODO: Test
            if (!EventListener.class.isAssignableFrom(field.getType())) continue;
            Listener annotation = field.getAnnotation(Listener.class);
            if (annotation == null) continue;

            try {
                if (!field.canAccess(instance)) field.setAccessible(true);
                EventListener<?> listener = (EventListener<?>) field.get(instance);
                if (listener != null) this.removeListener((EventListener<T>) listener);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
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
    protected void registerMethodListener(Object instance, Method method) {
        var annotation = method.getAnnotation(Listener.class);
        if (annotation == null) {
            return;
        }

        this.validateModifiers(method.getName(), method.getModifiers(), false);

        MethodEventListener<T> listener = this.createMethodListener(annotation, instance, method);
        this.listeners
                .computeIfAbsent(
                        listener.getEventType(),
                        arr -> new CopyOnWriteArrayList<>()
                )
                .add(listener);

        this.sortedListeners.put(listener.getEventType(), false);
    }

    /**
     * Creates a method listener from a method, using a {@link MethodHandle} for invocation.
     *
     * @param annotation The listener annotation.
     * @param instance The instance of the class.
     * @param method The method to create the listener from.
     * @return The created method listener.
     *
     * @since 1.0.3
     */
    protected MethodEventListener<T> createMethodListener(Listener annotation, Object instance, Method method) {
        try {
            if (!method.canAccess(instance)) method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(instance);
            return new MethodEventListener<>(annotation, instance, method, handle);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Registers a field as a listener.
     *
     * @param instance The instance of the class.
     * @param field The field to register.
     *
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    protected void registerFieldListener(Object instance, Field field) {
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
    protected void validateModifiers(String name, int modifiers, boolean field)  {
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
    protected void sortListeners(Type eventType) {
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
    protected void removeListener(EventListener<T> listener) {
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
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    protected static class MethodEventListener<T extends Event> implements EventListener<T> {
        private final Listener annotation;

        @EqualsAndHashCode.Include
        private final Object instance;

        private final MethodHandle methodHandle;

        @EqualsAndHashCode.Include
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
        public MethodEventListener(Listener annotation, Object instance, Method method, MethodHandle methodHandle) {
            this.annotation = annotation;
            this.instance = instance;
            this.methodHandle = methodHandle;
            this.eventType = (Class<T>) method.getGenericParameterTypes()[0];
        }

        @Override
        public void invoke(Event event) {
            try {
                this.methodHandle.invoke(event);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public EventPriority getPriority() {
            return this.annotation.priority();
        }
    }
}