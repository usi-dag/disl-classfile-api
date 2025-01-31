package ch.usi.dag.disl.coderep;

import java.lang.reflect.Method;

import ch.usi.dag.disl.exception.StaticContextGenException;
import ch.usi.dag.disl.staticcontext.StaticContext;


public class StaticContextMethod {

    /**
     * The identifier of the static context method. The identifier does not
     * include full method signature, so there can be no method overloading.
     * This is OK, because static context methods cannot have any parameters.
     * <p>
     * TODO LB: Is the id really necessary?
     */
    private final String __id;

    private final Method __method;

    /**
     * The static context class referenced when invoking the static context
     * method.
     * <p>
     * <b>Note:</b> This may be a different from
     * {@link Method#getDeclaringClass()} because the referenced static context
     * class may inherit methods from another static context class. This is not
     * so important for invocation, but for creating (and finding) instances of
     * the referenced static context class.
     */
    private final Class <?> __referencedClass;


    public StaticContextMethod (
        final String id, final Method method, final Class <?> referencedClass
    ) {
        __id = id;
        __method = method;
        __referencedClass = referencedClass;
    }


    public String getId () {
        return __id;
    }


    public Method getMethod () {
        return __method;
    }


    public Class <?> getReferencedClass () {
        return __referencedClass;
    }


    /**
     * Invokes the static context method on the given static context instance.
     *
     * @param staticContext
     *        the target of the static context method invocation
     * @return static context data
     * @throws StaticContextGenException
     *         if the static context method invocation fails for some reason
     */
    public Object invoke (
        final StaticContext staticContext
    ) throws StaticContextGenException {
        try {
            __method.setAccessible (true);
            return __method.invoke (staticContext);

        } catch (final Exception e) {
            throw new StaticContextGenException (
                e, "Invocation of static context method %s.%s failed",
                __method.getDeclaringClass ().getName (), __method.getName ()
            );
        }
    }

}
