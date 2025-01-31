package ch.usi.dag.disl.classparser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import ch.usi.dag.disl.annotation.After;
import ch.usi.dag.disl.annotation.AfterReturning;
import ch.usi.dag.disl.annotation.AfterThrowing;
import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.exception.GuardException;
import ch.usi.dag.disl.exception.MarkerException;
import ch.usi.dag.disl.exception.ParserException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.exception.SnippetParserException;
import ch.usi.dag.disl.guard.GuardHelper;
import ch.usi.dag.disl.marker.Marker;
import ch.usi.dag.disl.marker.Parameter;
import ch.usi.dag.disl.scope.Scope;
import ch.usi.dag.disl.scope.ScopeMatcher;
import ch.usi.dag.disl.snippet.Snippet;
import ch.usi.dag.disl.snippet.SnippetUnprocessedCode;
import ch.usi.dag.disl.util.AsmHelper;
import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.Logging;
import ch.usi.dag.disl.util.ReflectionHelper;
import ch.usi.dag.util.logging.Logger;


/**
 * Parses annotated Java class files and converts them to {@link Snippet}
 * instances which it collects.
 */
class SnippetParser extends AbstractParser {

    private final Logger __log = Logging.getPackageInstance ();

    //

    private final List <Snippet> snippets = new LinkedList <> ();


    public List <Snippet> getSnippets () {
        return snippets;
    }

    //

    // NOTE: this method can be called many times
    public void parse (final ClassNode dislClass) throws ParserException {
        processLocalVars (dislClass);

        //

        try {
            final String className = AsmHelper.typeName (dislClass);

            snippets.addAll (dislClass.methods.parallelStream ().unordered ()
                .filter (m -> __isSnippetCandidate (m))
                .map (m -> __parseSnippetWrapper (className, m))
                .collect (Collectors.toList ())
            );

        } catch (final ParserRuntimeException e) {
            throw new ParserException (e.getMessage (), e.getCause ());
        }
    }

    private boolean __isSnippetCandidate (final MethodNode m) {
        if (JavaNames.isConstructorName (m.name)) {
            __log.trace ("ignoring %s (constructor)", m.name);
            return false;
        }

        if (JavaNames.isInitializerName (m.name)) {
            __log.trace ("ignoring %s (static initializer)", m.name);
            return false;
        }

        if (m.invisibleAnnotations == null || m.invisibleAnnotations.isEmpty ()) {
            __log.trace ("ignoring %s (no annotations found)", m.name);
            return false;
        }

        return true;
    }

    private Snippet __parseSnippetWrapper (
        final String className, final MethodNode method
    ) {
        //
        // Wrap all parser exceptions into ParserRuntimeException so that
        // __parseSnippet() can be called from a stream pipeline.
        //
        try {
            return __parseSnippet (className, method);

        } catch (final Exception e) {
            throw new ParserRuntimeException (
                e, "error parsing snippet %s.%s()",
                className, method.name
            );
        }
    }


    private Snippet __parseSnippet (
        final String className, final MethodNode method
    ) throws ParserException, ReflectionException, MarkerException, GuardException  {
        __ensureSnippetIsWellDefined (method);

        //

        final SnippetAnnotationData data = __parseSnippetAnnotation (
            method.invisibleAnnotations.get (0)
        );

        //

        final Marker marker = getMarker (data.marker, data.args);
        final Scope scope = ScopeMatcher.forPattern (data.scope);
        final Method guard = GuardHelper.findAndValidateGuardMethod (
            AbstractParser.getGuard (data.guard), GuardHelper.snippetContextSet ()
        );

        final SnippetUnprocessedCode template = new SnippetUnprocessedCode (
            className, method, data.dynamicBypass
        );

        //

        return new Snippet (
            data.type, marker, scope, guard, data.order, template
        );
    }

    //

    private void __ensureSnippetIsWellDefined (
        final MethodNode method
    ) throws ParserException {
        //
        // The ordering of (some of) these checks is important -- some may
        // rely on certain assumptions to be satisfied.
        //
        __warnOnMultipleAnnotations (method);
        __ensureSnippetOnlyHasDislAnnotations (method);

        ensureMethodIsStatic (method);
        ensureMethodReturnsVoid (method);
        ensureMethodHasOnlyContextArguments (method);
        ensureMethodThrowsNoExceptions (method);

        ensureMethodIsNotEmpty (method);
        ensureMethodUsesContextProperly (method);
    }

    private void __warnOnMultipleAnnotations (
        final MethodNode method
    ) throws SnippetParserException {
        final int annotationCount = method.invisibleAnnotations.size ();
        if (annotationCount > 1) {
            __log.warn (
                "%s has %d annotations, one the first will apply",
                method.name, annotationCount
            );
        }
    }


    private static void __ensureSnippetOnlyHasDislAnnotations (
        final MethodNode method
    ) throws SnippetParserException {
        for (final AnnotationNode annotation : method.invisibleAnnotations) {
            final Type annotationType = Type.getType (annotation.desc);
            if (! __isSnippetAnnotation (annotationType)) {
                throw new SnippetParserException (String.format (
                    "unsupported annotation: %s", annotationType.getClassName ()
                ));
            }
        }
    }

    //

    private SnippetAnnotationData __parseSnippetAnnotation (
        final AnnotationNode annotation
    ) throws SnippetParserException {
        //
        // At this point, the snippet has been checked for unsupported
        // annotations. The lookup is therefore safe.
        //
        final Type annotationType = Type.getType (annotation.desc);
        final Class <?> annotationClass = __getAnnotationClass (annotationType);
        return __parseSnippetAnnotationFields (annotation, annotationClass);
    }


    private static boolean __isSnippetAnnotation (final Type annotationType) {
        return __SNIPPET_ANNOTATIONS__.containsKey (annotationType);
    }

    private static Class <?> __getAnnotationClass (final Type annotationType) {
        return __SNIPPET_ANNOTATIONS__.get (annotationType);
    }

    @SuppressWarnings ("serial")
    private static final Map <Type, Class <?>> __SNIPPET_ANNOTATIONS__ =
        Collections.unmodifiableMap (new HashMap <Type, Class <?>> () {
            {
                put (After.class);
                put (AfterReturning.class);
                put (AfterThrowing.class);
                put (Before.class);
            }

            private void put (final Class <?> annotationClass) {
                put (Type.getType (annotationClass), annotationClass);
            }
        });



    // data holder for AnnotationParser
    private static class SnippetAnnotationData {
        final Class <?> type;

        Type marker;

        //
        // Default values of annotation attributes.
        //
        String args = null;
        String scope = "*";
        Type guard = null;
        int order = 100;
        boolean dynamicBypass = true;

        SnippetAnnotationData (final Class <?> type) {
            this.type = type;
        }
    }


    private SnippetAnnotationData __parseSnippetAnnotationFields (
        final AnnotationNode annotation, final Class <?> type
    ) {
        final SnippetAnnotationData result = parseAnnotation (
            annotation, new SnippetAnnotationData (type)
        );

        if (result.marker == null) {
            throw new DiSLFatalException (
                "Missing [marker] attribute in annotation "+ type.getName ()
                + ". This may happen if annotation class is changed but"
                + " data holder class is not."
            );
        }

        return result;
    }

    //

    private Marker getMarker (
        final Type markerType, final String markerParam
    ) throws ReflectionException, MarkerException {
        final Class <?> rawMarkerClass = ReflectionHelper.resolveClass (markerType);
        final Class <? extends Marker> markerClass = rawMarkerClass.asSubclass (Marker.class);

        // instantiate marker WITHOUT Parameter as an argument
        if (markerParam == null) {
            try {
                return ReflectionHelper.createInstance (markerClass);

            } catch (final ReflectionException e) {
                if (e.getCause () instanceof NoSuchMethodException) {
                    throw new MarkerException ("Marker " + markerClass.getName ()
                        + " requires \"param\" annotation attribute"
                        + " declared",
                        e);
                }

                throw e;
            }
        }

        // try to instantiate marker WITH Parameter as an argument
        try {
            return ReflectionHelper.createInstance (
                markerClass, new Parameter (markerParam)
            );

        } catch (final ReflectionException e) {
            if (e.getCause () instanceof NoSuchMethodException) {
                throw new MarkerException ("Marker " + markerClass.getName ()
                    + " does not support \"param\" attribute", e);
            }

            throw e;
        }
    }

}
