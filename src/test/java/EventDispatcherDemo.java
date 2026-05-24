import lombok.RequiredArgsConstructor;
import me.darragh.event.Event;
import me.darragh.event.bus.EventDispatcher;
import me.darragh.event.bus.Listener;
import me.darragh.event.bus.SimpleEventDispatcher;
import me.darragh.event.bus.experimental.ExperimentalEventDispatcher;

import java.util.ArrayList;
import java.util.List;

public class EventDispatcherDemo {
    public static void main(String[] args) {
        new EventDispatcherDemo().run();
    }

    public void run() {
        int listenerCount = 100,
                invocationCount = 1000000;

        runTest("SimpleEventDispatcher", new SimpleEventDispatcher<>(), listenerCount, invocationCount, 1); // ts js slow :sob:
        runTest("ExperimentalEventDispatcher", new ExperimentalEventDispatcher<>(), listenerCount, invocationCount, 3);
    }

    private void runTest(String name, EventDispatcher<Event> dispatcher, int listenerCount, int invocationCount, int invocationIterations) {
        System.out.println("\n=== " + name + " ===");
        TestEvent event = new TestEvent("Hello World");

        // registration
        long startRegistrationTime = System.nanoTime();
        for (int i = 0; i < listenerCount; i++) {
            dispatcher.registerObject(new TestListener());
        }
        long registrationDuration = System.nanoTime() - startRegistrationTime;
        double registrationRate = listenerCount / (registrationDuration / 1_000_000_000.0);

        System.out.printf("Registration (%,d listeners):%n", listenerCount);
        System.out.printf("\tTime: %,d ns%n", registrationDuration);
        System.out.printf("\tRate: %,.2f ops/sec%n%n", registrationRate);

        // invocation
        System.out.printf("Invocation (%,d iterations of %,d events):%n", invocationIterations, invocationCount);
        List<Double> rates = new ArrayList<>();

        for (int iteration = 0; iteration < invocationIterations; iteration++) {
            long startInvocationTime = System.nanoTime();
            for (int i = 0; i < invocationCount; i++) {
                dispatcher.invoke(event);
            }
            long invocationDuration = System.nanoTime() - startInvocationTime;
            double invocationRate = invocationCount / (invocationDuration / 1_000_000_000.0);
            rates.add(invocationRate);

            System.out.printf("\tIter. %02d: %,16.2f ops/sec (%,d ns)%n", iteration + 1, invocationRate, invocationDuration);
        }

        // stats
        double sum = 0.0;
        for (double rate : rates) {
            sum += rate;
        }
        double mean = sum / rates.size();

        double std = 0.0;
        for (double rate : rates) {
            std += Math.pow(rate - mean, 2);
        }
        std = Math.sqrt(std / rates.size());

        // summary
        System.out.println("\n========================================");
        System.out.printf("  Mean Rate:\t\t%,.2f ops/sec%n", mean);
        System.out.printf("  Std. Deviation:\t%,.2f ops/sec%n", std);
        System.out.println("========================================");
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