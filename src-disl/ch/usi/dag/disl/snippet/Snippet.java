package ch.usi.dag.disl.snippet;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import ch.usi.dag.disl.DiSL.CodeOption;
import ch.usi.dag.disl.exception.MarkerException;
import ch.usi.dag.disl.exception.ProcessorException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.guard.GuardHelper;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.marker.Marker;
import ch.usi.dag.disl.processor.ArgProcessor;
import ch.usi.dag.disl.processor.ArgProcessorMethod;
import ch.usi.dag.disl.scope.Scope;


/**
 * Holds all the information about a snippet. Is analogous to
 * {@link ArgProcessorMethod}.
 */
public class Snippet implements Comparable <Snippet> {

    private final Class <?> annotationClass;
    private final Marker marker;
    private final Scope scope;
    private final Method guard;
    private final int order;
    private final SnippetUnprocessedCode __template;

    private SnippetCode __code;


    /**
     * Creates snippet structure.
     */
    public Snippet (
        final Class <?> annotationClass, final Marker marker,
        final Scope scope, final Method guard,
        final int order, final SnippetUnprocessedCode template
    ) {
        this.annotationClass = annotationClass;
        this.marker = marker;
        this.scope = scope;
        this.guard = guard;
        this.order = order;

        __template = template;
    }

    //

    /**
     * @return The canonical name of the class in which the snippet was defined.
     */
    public String getOriginClassName() {
        return __template.className ();
    }


    /**
     * @return The name of the snippet method.
     */
    public String getOriginMethodName() {
        return __template.methodName ();
    }


    /**
     * @return A fully qualified name of the snippet method.
     */
    public String getOriginName () {
        return __template.className () +"."+ __template.methodName ();
    }


    /**
     * Checks whether this snippet's annotation class matches the given
     * annotation class.
     *
     * @param checkClass
     * @return {@code true} if this snippet's annotation class matches the given
     *         annotation class.
     */
    public boolean hasAnnotation (final Class <?> checkClass) {
        assert Objects.requireNonNull (checkClass).isAnnotation ();
        return annotationClass.equals (checkClass);

    }


    /**
     * Applies the marker associated with this snippet to the given
     * class and method, so as to provide a list of {@link Shadow}
     * instances representing individual instances of a snippet.
     *
     * @throws MarkerException
     */
    public final List <Shadow> selectApplicableShadows (
        final ClassNode classNode, final MethodNode methodNode
    ) throws MarkerException {
        return __guardedShadows (marker.mark (classNode, methodNode, this));
    }


    /**
     * Selects shadows passing the guard associated with this snippet.
     *
     * @param shadows
     *        the list of {@link Shadow} instances to filter.
     * @return A list of {@link Shadow} instances passing the guard.
     */
    private List <Shadow> __guardedShadows (
        final List <Shadow> shadows
    ) {
        if (guard == null) {
            return shadows;
        }

        return shadows.stream ()
            // potentially .parallel(), needs thread-safe static context
            .filter (shadow -> GuardHelper.guardApplicable (guard, shadow))
            .collect (Collectors.toList ());
    }


    /**
     * @returns The scope in which the snippet should be applied.
     */
    public Scope getScope () {
        return scope;
    }


    /**
     * @returns The guard which determines where the snippet should be applied.
     */
    public Method getGuard () {
        return guard;
    }


    /**
     * Returns the snippet weaving order. The lower the order, the closer a
     * snippet gets to the marked code location.
     *
     * @return The snippet weaving order.
     */
    public int getOrder () {
        return order;
    }


    /**
     * Returns the instantiated snippet code. Before calling this method, the
     * snippet must be initialized using the {@link #init(LocalVars, Map, Set)
     * init()} method.
     *
     * @return {@link SnippetCode} representing an instantiate snippet.
     */
    public SnippetCode getCode () {
        if (__code == null) {
            throw new IllegalStateException ("snippet not initialized");
        }

        return __code;
    }


    /**
     * Compares a snippet to another snippet. The natural ordering of snippets
     * is determined by their weaving order. A snippet with a lower weaving
     * order is considered greater and vice-versa.
     */
    @Override
    public int compareTo (final Snippet that) {
        return Integer.compare (that.order, this.order);
    }


    /**
     * Prepares a snippet for weaving by instantiating the snippet code template
     * with the given local variables, argument processors, and code options.
     * <p>
     * TODO LB: Consider actually returning an initialized copy of this
     * snippet and making this class immutable. Moreover, the state of a
     * snippet should not be determined by its static type.
     */
    public void init (
        final LocalVars locals, final Map <Type, ArgProcessor> processors,
        final Set <CodeOption> options
    ) throws ProcessorException, ReflectionException  {
        __code = __template.process (locals, processors, marker, options);
    }

}
