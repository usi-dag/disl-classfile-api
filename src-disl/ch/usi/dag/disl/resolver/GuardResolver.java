package ch.usi.dag.disl.resolver;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.usi.dag.disl.exception.GuardException;


/**
 * Note that all methods accessing and working with the {@link GuardResolver}
 * singleton have to be thread-safe.
 */
public class GuardResolver {

    // TODO LB: Get rid of guard resolver singleton
    private static GuardResolver instance = new GuardResolver ();

    public static GuardResolver getInstance () {
        return instance;
    }

    //

    /** A cache of guard methods corresponding to guard classes. */
    private final Map <Class <?>, GuardMethod> __guardMethodsByClass = new HashMap <> ();


    public synchronized GuardMethod getGuardMethod (
        final Class <?> guardClass
    ) throws GuardException {
        //
        // Try to find the guard method by consulting the cache of methods
        // corresponding to guard classes. If it does not provide an answer,
        // search of a guard method within the class.
        //
        final GuardMethod cachedResult = __guardMethodsByClass.get (guardClass);
        if (cachedResult != null) {
            return cachedResult;
        }

        return __findGuardMethod (guardClass);
    }


    private GuardMethod __findGuardMethod (
        final Class <?> guardClass
    ) throws GuardException {
        final List <Method> candidateMethods = Arrays.stream (guardClass.getDeclaredMethods ())
            .filter (m -> __isGuardMethodCandidate (m))
            .collect (Collectors.toList ());

        if (candidateMethods.isEmpty ()) {
            throw new GuardException (String.format (
                "No method annotated using %s found in class %s",
                ch.usi.dag.disl.annotation.GuardMethod.class.getName(),
                guardClass.getName()
            ));
        }

        if (candidateMethods.size () > 1) {
            throw new GuardException (String.format (
                "Multiple (%d) guard methods found in class %s",
                candidateMethods.size (), guardClass.getName()
            ));
        }

        // Now just get the first method and make it accessible
        final Method method = candidateMethods.get (0);
        method.setAccessible (true);

        final GuardMethod result = new GuardMethod (method);
        __guardMethodsByClass.put (guardClass, result);
        return result;
    }


    private boolean __isGuardMethodCandidate (final Method m) {
        return m.isAnnotationPresent(
            ch.usi.dag.disl.annotation.GuardMethod.class
        );
    }

}
