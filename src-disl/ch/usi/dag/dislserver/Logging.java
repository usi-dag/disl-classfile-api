package ch.usi.dag.dislserver;

import ch.usi.dag.util.logging.Logger;


/**
 * Utility class to provide logging services tailored to the needs of the
 * framework.
 *
 * @author Lubomir Bulej
 */
final class Logging {

    /**
     * Package name of the framework entry class.
     */
    private static final String
        __BASE_PREFIX__ = DiSLServer.class.getPackage ().getName ();

    /**
     * Default prefix for top-level logs.
     */
    private static final String
        __SHORT_PREFIX__ = "dislserver";

    /**
     * Register provider property alias with the logging class.
     */
    static {
        Logger.registerProviderAlias ("dislserver.logging.provider");
        Logger.registerLevelAlias ("dislserver.logging.level");
    }

    //

    private Logging () {
        // pure static class - not to be instantiated
    }

    //

    public static Logger getPackageInstance () {
        //
        // Determine the package this method was called from and strip common
        // prefix to get tighter, more local names.
        //
        final StackTraceElement caller =
            Thread.currentThread ().getStackTrace () [2];

        return Logger.getPackageInstance (
            caller, __BASE_PREFIX__, __SHORT_PREFIX__
        );
    }

}
