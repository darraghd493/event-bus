package me.darragh.event.bus;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import me.darragh.event.Event;
import me.darragh.event.helper.DispatcherHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple event dispatcher implementation.
 * <p>
 * This is intended to be widely applicable and is not optimised for any specific use case.
 *
 * @author darraghd493
 * @since 1.0.0
 */
@Slf4j
public class SimpleEventDispatcher<T extends Event> implements EventDispatcher<T> {
    private final Map<Type, EventListener<T>[]> listeners = new ConcurrentHashMap<>();
    private final Map<Type, Boolean> sortedListeners = new ConcurrentHashMap<>();

    @Override
    public void registerObject(Object instance) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (var method : clazz.getDeclaredMethods()) {
                this.registerMethodListener(instance, method);
            }

            for (var field : clazz.getDeclaredFields()) {
                this.registerFieldListener(instance, field);
            }

            clazz = clazz.getSuperclass();
        }
    }

    @Override
    public void registerListener(EventListener<? extends T> listener) {
        this.addListenerToArray(listener);
    }

    @Override
    public void unregisterObject(Object instance) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (var method : clazz.getDeclaredMethods()) {
                Listener annotation = method.getAnnotation(Listener.class);
                if (annotation == null) {
                    continue;
                }

                MethodEventListener<T> listener = this.createMethodListener(annotation, instance, method);
                this.removeListener(listener);
            }

            for (var field : clazz.getDeclaredFields()) {
                if (!EventListener.class.isAssignableFrom(field.getType())) continue;
                Listener annotation = field.getAnnotation(Listener.class);
                if (annotation == null) continue;

                try {
                    if (!field.canAccess(instance)) field.setAccessible(true);
                    @SuppressWarnings("unchecked") EventListener<? extends T> listener = (EventListener<? extends T>) field.get(instance);
                    if (listener != null) this.removeListener(listener);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    @Override
    public void unregisterListener(EventListener<? extends T> listener) {
        this.removeListener(listener);
    }

    @Override
    public void invoke(T event) {
        EventListener<T>[] eventListeners = this.listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.length == 0) return;

        if (!this.sortedListeners.getOrDefault(event.getClass(), false)) {
            this.sortListeners(event.getClass());
            eventListeners = this.listeners.get(event.getClass());
        }

        for (EventListener<T> listener : eventListeners) {
            try {
                listener.invoke(event);
            } catch (Exception e) {
                log.error("Error invoking listener: {}", listener, e);
            }
        }
    }

    @Override
    public <U extends T> boolean testFor(Class<U> eventClass) {
        EventListener<T>[] eventListeners = this.listeners.get(eventClass);
        return eventListeners != null && eventListeners.length > 0;
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

        DispatcherHelper.validateModifiers(method.getName(), method.getModifiers(), false);

        MethodEventListener<T> listener = this.createMethodListener(annotation, instance, method);
        this.addListenerToArray(listener);
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

        DispatcherHelper.validateModifiers(field.getName(), field.getModifiers(), true);

        try {
            EventListener<T> listener = (EventListener<T>) field.get(instance);
            if (listener != null) {
                this.addListenerToArray(listener);
            } else {
                throw new RuntimeException("Listener field %s is null.".formatted(field.getName()));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to access field: " + field.getName(), e);
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
        EventListener<T>[] eventListeners = this.listeners.get(eventType);
        if (eventListeners == null || eventListeners.length == 0) {
            return;
        }

        Arrays.sort(eventListeners, Comparator.comparingInt(eventListener -> eventListener.getPriority().value()));
        this.sortedListeners.put(eventType, true);
    }

    /**
     * Removes a listener from the dispatcher.
     *
     * @param listener The listener to remove.
     *
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    protected void removeListener(EventListener<? extends T> listener) {
        this.listeners.computeIfPresent(listener.getEventType(), (type, arr) -> {
            int index = -1;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].equals(listener)) {
                    index = i;
                    break;
                }
            }

            if (index == -1) return arr;
            if (arr.length == 1) return null;

            EventListener<T>[] newArr = new EventListener[arr.length - 1];
            System.arraycopy(arr, 0, newArr, 0, index);
            System.arraycopy(arr, index + 1, newArr, index, arr.length - index - 1);
            return newArr;
        });
        this.sortedListeners.remove(listener.getEventType());
    }

    //region Helpers
    @SuppressWarnings("unchecked")
    private void addListenerToArray(EventListener<? extends T> listener) {
        this.listeners.compute(listener.getEventType(), (type, arr) -> {
            EventListener<T>[] newArr;
            if (arr == null) {
                newArr = new EventListener[1];
                newArr[0] = (EventListener<T>) listener;
            } else {
                newArr = Arrays.copyOf(arr, arr.length + 1);
                newArr[arr.length] = (EventListener<T>) listener;
            }
            return newArr;
        });
        this.sortedListeners.put(listener.getEventType(), false);
    }
    //endregion

    /**
     * Represents a method as an event listener.
     *
     * @author darraghd493
     * @since 1.0.0
     */
    @SuppressWarnings("ClassCanBeRecord")
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    protected static class MethodEventListener<T extends Event> implements EventListener<T> {
        private final Listener annotation;

        @EqualsAndHashCode.Include
        private final Object instance;

        private final MethodHandle methodHandle;

        @EqualsAndHashCode.Include
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
            //noinspection unchecked
            this.eventType = (Class<T>) method.getParameterTypes()[0];
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

        @Override
        public Class<T> getEventType() {
            return this.eventType;
        }
    }
}
