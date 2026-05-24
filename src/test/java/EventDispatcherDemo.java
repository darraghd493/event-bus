import lombok.RequiredArgsConstructor;
import me.darragh.event.Event;
import me.darragh.event.bus.EventDispatcher;
import me.darragh.event.bus.Listener;
import me.darragh.event.bus.SimpleEventDispatcher;
import me.darragh.event.bus.experimental.ExperimentalEventDispatcher;

public class EventDispatcherDemo {
    public static void main(String[] args) {
        new EventDispatcherDemo().run();
    }

    public void run() {
        int listenerCount = 100,
                invocationCount = 100000;

        runTest("SimpleEventDispatcher", new SimpleEventDispatcher<>(), listenerCount, invocationCount);
        runTest("ExperimentalEventDispatcher", new ExperimentalEventDispatcher<>(), listenerCount, invocationCount);
    }

    private void runTest(String name, EventDispatcher<Event> dispatcher, int listenerCount, int invocationCount) {
        System.out.println("\n" + name + " Performance:");
        TestEvent event = new TestEvent("Hello World");

        // Measure registration speeds
        long startRegistrationTime = System.nanoTime();
        for (int i = 0; i < listenerCount; i++) {
            dispatcher.registerObject(new TestListener());
        }
        long registrationDuration = System.nanoTime() - startRegistrationTime;
        double registrationRate = listenerCount / (registrationDuration / 1_000_000_000.0);

        System.out.printf("  Registration (%,d listeners): %,d ns (%,.2f ops/sec)%n",
                listenerCount, registrationDuration, registrationRate);

        // Measure invocation speeds
        long startInvocationTime = System.nanoTime();
        for (int i = 0; i < invocationCount; i++) {
            dispatcher.invoke(event);
        }
        long invocationDuration = System.nanoTime() - startInvocationTime;
        double invocationRate = invocationCount / (invocationDuration / 1_000_000_000.0);

        System.out.printf("  Invocation   (%,d times):     %,d ns (%,.2f ops/sec)%n",
                invocationCount, invocationDuration, invocationRate);
    }

    @RequiredArgsConstructor
    public static class TestEvent implements Event {
        @SuppressWarnings("unused")
        private final String message;

        @Override
        public <T extends Event> T post() {
            //noinspection unchecked
            return (T) this;
        }
    }

    @SuppressWarnings("unused")
    public static class TestListener {
        @Listener
        public void onTestEvent(TestEvent event) {
            // no-op
        }

        @Listener
        public void onTestEvent2(TestEvent event) {
            // no-op
        }

        @Listener
        public void onTestEvent3(TestEvent event) {
            // no-op
        }
    }
}