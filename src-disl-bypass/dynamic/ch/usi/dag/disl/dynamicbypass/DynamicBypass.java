package ch.usi.dag.disl.dynamicbypass;

public final class DynamicBypass {

    private static final boolean debug = Boolean.getBoolean ("debug");


    //

    public static boolean isActive () {

         return Thread.currentThread ().bypass;
    }


    public static void activate () {
        Thread current = Thread.currentThread ();
        try {

            Thread.currentThread ().bypass = true;



        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void deactivate () {
        Thread current = Thread.currentThread ();
        try {

            Thread.currentThread ().bypass = false;


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
