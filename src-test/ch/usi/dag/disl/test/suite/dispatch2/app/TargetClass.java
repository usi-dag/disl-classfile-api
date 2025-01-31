package ch.usi.dag.disl.test.suite.dispatch2.app;


public class TargetClass {

    public static void main(final String[] args) throws InterruptedException {
        final int COUNT = 2000000;
        final TargetClass ta[] = new TargetClass[COUNT];

        for (int i = 0; i < COUNT; ++i) {
            ta[i] = new TargetClass();
        }

        System.out.println("Allocated " + COUNT + " objects");
    }

}
