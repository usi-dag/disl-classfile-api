package ch.usi.dag.disl.staticcontext.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.usi.dag.disl.coderep.StaticContextMethod;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.exception.StaticContextGenException;
import ch.usi.dag.disl.resolver.SCResolver;
import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Snippet;
import ch.usi.dag.disl.staticcontext.StaticContext;
import ch.usi.dag.disl.util.JavaNames;

public class SCGenerator {

    private static final class StaticContextKey {
        private final Shadow shadow;
        private final String methodId;


        public StaticContextKey (final Shadow shadow, final String methodId) {
            this.shadow = shadow;
            this.methodId = methodId;
        }

        public StaticContextKey (
            final Shadow shadow, final String className, final String methodName
        ) {
            this (shadow, JavaNames.methodName (className, methodName));
        }

        //

        private static final int __PRIME__ = 31;

        @Override
        public int hashCode() {
            int result = __PRIME__;
            result += (shadow == null) ? 0 : shadow.hashCode ();

            result *= __PRIME__;
            result += (methodId == null) ? 0 : methodId.hashCode ();

            return result;
        }


        @Override
        public boolean equals (final Object object) {
            if (this == object) {
                return true;
            }

            if (object instanceof StaticContextKey) {
                final StaticContextKey that = (StaticContextKey) object;

                //
                // Shadows and methods must be either both null or equal.
                //
                if (__bothNullOrEqual (this.shadow, that.shadow)) {
                    return __bothNullOrEqual (this.methodId, that.methodId);
                }
            }

            return false;
        }
    }

    private static boolean __bothNullOrEqual (final Object obj1, final Object obj2) {
        if (obj1 == null) {
            return obj2 == null;
        } else {
            return obj1.equals (obj2);
        }
    }

    //

    private final Map <StaticContextKey, Object> staticInfoData;

    private SCGenerator (
        final Map <StaticContextKey, Object> staticInfoData
    ) throws ReflectionException, StaticContextGenException {
        this.staticInfoData = staticInfoData;
    }


    // Call static context for each snippet and each marked region and create
    // a static info values
    public static SCGenerator computeStaticInfo (
        final Map <Snippet, List <Shadow>> snippetMarkings
    ) throws ReflectionException, StaticContextGenException {
        //
        // For each snippet, obtain a set of invoked static context methods
        // (including those invoked in argument processors) and get static
        // context data for each static context method for each snippet
        // instance (shadow).
        //
        final Map <StaticContextKey, Object> result = new HashMap <> ();
        for (final Snippet snippet : snippetMarkings.keySet ()) {
            for (final StaticContextMethod scm : snippet.getCode ().getReferencedSCMs ()) {

                for (final Shadow shadow : snippetMarkings.get (snippet)) {
                    //
                    // Get SC instance, associate it with the shadow location, and
                    // cache it along with the method.
                    //
                    final StaticContext staticContext = SCResolver.getInstance()
                        .getStaticContextInstance (scm.getReferencedClass ());

                    staticContext.staticContextData (shadow);

                    result.put (
                        new StaticContextKey (shadow, scm.getId ()),
                        scm.invoke (staticContext)
                    );
                }
            }
        }

        return new SCGenerator (result);
    }

    //

    public boolean contains (final Shadow shadow, final String infoClass, final String infoMethod) {
        return staticInfoData.containsKey (new StaticContextKey (
            shadow, infoClass, infoMethod
        ));
    }


    public Object get (final Shadow shadow, final String infoClass, final String infoMethod) {
        return staticInfoData.get (new StaticContextKey (
            shadow, infoClass, infoMethod
        ));
    }

}
