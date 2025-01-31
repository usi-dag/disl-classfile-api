package ch.usi.dag.disl.test.suite.dispatchmp.app;

import java.util.concurrent.TimeUnit;


public class TargetClass {

    private static final int THREAD_COUNT = 3;
    private static final int OBJECT_COUNT = 2_000_000;

    public static class TCTask implements Runnable {
        @Override
        public void run() {
            final TargetClass ta[] = new TargetClass[OBJECT_COUNT];

            for (int i = 0; i < OBJECT_COUNT; i++) {
                ta[i] = new TargetClass();
            }

            System.out.println("Allocated " + OBJECT_COUNT + " objects");
        }
    }

    public static void main(final String[] args) throws InterruptedException {
        final TCTask task = new TCTask();

        final Thread[] threads = new Thread [THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads [i] = new Thread(task, "TCTask "+ i);
        }

        for (Thread thread : threads) { 
            thread.start();
        }

        for (Thread thread : threads) { 
            thread.join();
        }

        // Force GC of allocated objects.
        for (int i = 0; i < 5; i++) {
            Runtime.getRuntime().gc();
            TimeUnit.SECONDS.sleep(1);
        }
    }

}
