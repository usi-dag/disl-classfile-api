package ch.usi.dag.disl.dynamicbypass;

public final class DynamicBypass {

    private static final boolean debug = Boolean.getBoolean ("debug");


    //

    public static boolean isActive () {
        try {
            Thread current = Thread.currentThread ();
            return (boolean) current.getClass().getDeclaredField("bypass").get(current);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // return Thread.currentThread ().bypass;
    }


    public static void activate () {
        Thread current = Thread.currentThread ();
        try {
            if (debug) {
                // bypass should be disabled in this state
                boolean b = (boolean) current.getClass().getDeclaredField("bypass").get(current);
                if (b) {
                    throw new RuntimeException (
                            "fatal error: dynamic bypass activated twice");
                }
            }

            current.getClass().getDeclaredField("bypass").set(current, true);
            //Thread.currentThread ().bypass = true;



        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public static void deactivate () {
        Thread current = Thread.currentThread ();
        try {

            if (debug) {
                // bypass should be enabled in this state
                boolean b = (boolean) current.getClass().getDeclaredField("bypass").get(current);
                if (!b) {
                    throw new RuntimeException (
                            "fatal error: dynamic bypass deactivated twice");
                }
            }

            current.getClass().getDeclaredField("bypass").set(current, true);
            //Thread.currentThread ().bypass = false;


        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
