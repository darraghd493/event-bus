# event-bus

A simple developer-friendly event bus implementation in Java.

## Installation

This project is available via. Jitpack. View more information [here](https://jitpack.io/#Fentanyl-Client/event-bus).

## Usage

### 1. Create an event

```java
@RequiredArgsConstructor
public class MyBaseEvent extends SimpleEvent {
    private final String message; // Example field
    
    @Override
    public <T extends Event> T post() {
        // Note: this is a utility class that simplifies the event posting process
        // You do not need to use it nor implement it in your event classes
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

### 2. Create an event listener

```java
public class MyBaseEventListener extends SimpleEventListener<MyBaseEvent> {
    @Listener
    public void onEvent(MyBaseEvent event) {
        System.out.println("Received event: " + event.getMessage());
    }
    
    // or...
    
    @Listener
    private final EventListener<MyBaseEvent> listener = event -> {
        System.out.println("Received event: " + event.getMessage());
    };
}
```

### 3. Register the event listener

```java
EventDispatcher<MyBaseEvent> eventDispatcher = new SimpleEventDispatcher<>();
MyBaseEventListener eventListener = new MyBaseEventListener();

eventDispatcher.register(eventListener);
```

### 4. Post an event

```java
MyBaseEvent event = new MyBaseEvent("Hello, world!");
event.post(); // Assuming you have implemented the post() method in your event class

// or...

eventDispatcher.invoke(event);
```

### 5. Unregister the event listener

```java
eventDispatcher.unregister(eventListener);
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
