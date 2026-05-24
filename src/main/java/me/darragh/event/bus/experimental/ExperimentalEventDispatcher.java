package me.darragh.event.bus.experimental;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.darragh.event.Event;
import me.darragh.event.bus.EventDispatcher;
import me.darragh.event.bus.EventListener;
import me.darragh.event.bus.EventPriority;
import me.darragh.event.bus.Listener;
import me.darragh.event.helper.DispatcherHelper;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @apiNote This is an experimental implementation of an event dispatcher that may encounter issues in certain edge cases.
 *          It is not recommended for general use and may be removed in future versions.
 *          <p>
 *          You must also have <b>fastutils</b> as a dependency in order to use this.
 *          Since this is not intended for production, it is not included by default.
 * @author darraghd493
 * @since 1.0.5
 * @param <T>
 */
@Slf4j
public class ExperimentalEventDispatcher<T extends Event> implements EventDispatcher<T> {
    private final Map<Class<?>, EventListener<T>[]> listeners = new ConcurrentHashMap<>();

    private static final ClassValue<CachedClassData> CLASS_CACHE = new ClassValue<>() {
        @Override
        protected CachedClassData computeValue(@NonNull Class<?> type) {
            return new CachedClassData(type);
        }
    };

    @Override
    public void registerObject(Object instance) {
        CachedClassData cachedData = CLASS_CACHE.get(instance.getClass());
        Reference2ObjectOpenHashMap<Class<?>, ObjectArrayList<EventListener<T>>> newListeners = new Reference2ObjectOpenHashMap<>();

        for (var methodData : cachedData.methods) {
            EventListener<T> listener = methodData.createListener(instance);
            newListeners.computeIfAbsent(listener.getEventType(), k -> new ObjectArrayList<>()).add(listener);
        }

        for (var field : cachedData.fields) {
            try {
                @SuppressWarnings("unchecked")
                EventListener<T> listener = (EventListener<T>) field.get(instance);
                if (listener != null) {
                    newListeners.computeIfAbsent(listener.getEventType(), k -> new ObjectArrayList<>()).add(listener);
                } else {
                    throw new RuntimeException("Listener field %s is null.".formatted(field.getName()));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Unable to access field: " + field.getName(), e);
            }
        }

        for (var entry : newListeners.reference2ObjectEntrySet()) {
            this.listeners.compute(entry.getKey(), (type, currentArray) -> {
                ObjectArrayList<EventListener<T>> listenerList = new ObjectArrayList<>();
                if (currentArray != null) {
                    listenerList.addAll(Arrays.asList(currentArray));
                }
                listenerList.addAll(entry.getValue());
                listenerList.sort(Comparator.comparingInt(l -> l.getPriority().value()));
                //noinspection unchecked
                return listenerList.toArray(new EventListener[0]);
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void registerListener(EventListener<? extends T> listener) {
        Class<?> eventType = listener.getEventType();
        this.listeners.compute(eventType, (type, currentArray) -> {
            final EventListener<T>[] newArray;
            if (currentArray == null) {
                newArray = (EventListener<T>[]) new EventListener[1];
                newArray[0] = (EventListener<T>) listener;
            } else {
                newArray = Arrays.copyOf(currentArray, currentArray.length + 1);
                newArray[currentArray.length] = (EventListener<T>) listener;
            }
            Arrays.sort(newArray, Comparator.comparingInt(l -> l.getPriority().value()));
            return newArray;
        });
    }

    @Override
    public void unregisterObject(Object instance) {
        ObjectOpenHashSet<EventListener<T>> fieldListenersToRemove = new ObjectOpenHashSet<>();

        CachedClassData cachedData = CLASS_CACHE.get(instance.getClass());
        for (var field : cachedData.fields) {
            try {
                @SuppressWarnings("unchecked")
                EventListener<? extends T> listener = (EventListener<? extends T>) field.get(instance);
                if (listener != null) {
                    //noinspection unchecked
                    fieldListenersToRemove.add((EventListener<T>) listener);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        this.removeListeners(instance, fieldListenersToRemove);
    }

    @Override
    public void unregisterListener(EventListener<? extends T> listener) {
        this.removeListener(listener);
    }

    @Override
    public void invoke(T event) {
        EventListener<T>[] eventListeners = this.listeners.get(event.getClass());
        if (eventListeners == null) return;

        for (EventListener<T> eventListener : eventListeners) {
            try {
                eventListener.invoke(event);
            } catch (Exception e) {
                log.error("Error invoking listener", e);
            }
        }
    }

    @Override
    public <U extends T> boolean testFor(Class<U> eventClass) {
        return this.listeners.containsKey(eventClass);
    }

    protected void removeListener(Object instance) {
        this.removeListeners(instance, new ObjectOpenHashSet<>());
    }

    /**
     * Removes multiple listeners in a single pass through the map.
     * This is more efficient than calling removeListener multiple times.
     */
    @SuppressWarnings("unchecked")
    private void removeListeners(Object instance, ObjectOpenHashSet<EventListener<T>> fieldListeners) {
        for (var entry : this.listeners.entrySet()) {
            this.listeners.computeIfPresent(entry.getKey(), (type, arr) -> {
                ObjectArrayList<EventListener<T>> remainingListeners = new ObjectArrayList<>(arr.length);
                for (var listener : arr) {
                    boolean instanceMethod = listener instanceof LambdaEventListener<?> lambda && lambda.getInstance() == instance,
                            fieldListener = fieldListeners.contains(listener);

                    if (!instanceMethod && !fieldListener) {
                        remainingListeners.add(listener);
                    }
                }
                return remainingListeners.isEmpty() ? null : remainingListeners.toArray(new EventListener[0]);
            });
        }
    }

    /**
     * @author darraghd493
     * @since 1.0.5
     */
    private static class CachedClassData {
        final CachedMethodData[] methods;
        final Field[] fields;

        CachedClassData(Class<?> clazz) {
            var methodList = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(Listener.class))
                    .peek(m -> DispatcherHelper.validateModifiers(m.getName(), m.getModifiers(), false))
                    .map(CachedMethodData::new)
                    .toArray(CachedMethodData[]::new);

            var fieldList = Arrays.stream(clazz.getDeclaredFields())
                    .filter(f -> EventListener.class.isAssignableFrom(f.getType()))
                    .filter(f -> f.isAnnotationPresent(Listener.class))
                    .peek(f -> {
                        DispatcherHelper.validateModifiers(f.getName(), f.getModifiers(), true);
                        if (!f.canAccess(null)) f.setAccessible(true);
                    })
                    .toArray(Field[]::new);

            this.methods = methodList;
            this.fields = fieldList;
        }
    }

    /**
     * Pre-compiled metadata block for an individual listener method.
     *
     * @author darraghd493
     * @since 1.0.5
     */
    private static class CachedMethodData {
        final Listener annotation;
        final Class<?> eventType;
        final BiConsumer<Object, Event> lambdaInvoker;

        @SuppressWarnings("unchecked")
        CachedMethodData(Method method) {
            this.annotation = method.getAnnotation(Listener.class);
            this.eventType = method.getParameterTypes()[0];

            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle handle = lookup.unreflect(method);

                // Instead of calling the method handle, we are (effectively)
                // generating a lambda that calls the method handle, which allows for direct
                // invocation without any of the overhead of method handle invocation or conversions
                CallSite callSite = LambdaMetafactory.metafactory(
                        lookup,
                        "accept",
                        MethodType.methodType(BiConsumer.class),
                        MethodType.methodType(void.class, Object.class, Object.class),
                        handle,
                        MethodType.methodType(void.class, method.getDeclaringClass(), this.eventType)
                );

                this.lambdaInvoker = (BiConsumer<Object, Event>) callSite.getTarget().invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException("Failed to generate lambda invoker for " + method, t);
            }
        }

        <E extends Event> EventListener<E> createListener(Object instance) {
            //noinspection unchecked
            return new LambdaEventListener<>(annotation, instance, (Class<E>) eventType, lambdaInvoker);
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    private static class LambdaEventListener<E extends Event> implements EventListener<E> {
        private final Listener annotation;

        @Getter
        @EqualsAndHashCode.Include
        private final Object instance;

        @EqualsAndHashCode.Include
        private final Class<E> eventType;

        private final BiConsumer<Object, Event> lambdaInvoker;

        @Override
        public void invoke(E event) {
            this.lambdaInvoker.accept(this.instance, event);
        }

        @Override
        public EventPriority getPriority() {
            return this.annotation.priority();
        }

        @Override
        public Class<E> getEventType() {
            return this.eventType;
        }
    }
}
