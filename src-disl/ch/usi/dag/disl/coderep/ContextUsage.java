package ch.usi.dag.disl.coderep;
import java.lang.constant.ClassDesc;
import java.util.*;

import ch.usi.dag.disl.classparser.ContextKind;
import ch.usi.dag.disl.util.MethodModelCopy;


/**
 * Captures the set of context kinds and static context types referenced by
 * parameters of a particular method. This class is <b>immutable</b>.
 */
final class ContextUsage {

    /** An unmodifiable set of used contexts. */
    private final Set <ContextKind> __contextKinds;

    /** An unmodifiable set of referenced static context types. */
    private final Set <ClassDesc> __contextTypes;

    //

    private ContextUsage (
        final Set <ContextKind> usedContexts, final Set <ClassDesc> staticContexts
    ) {
        __contextKinds = Collections.unmodifiableSet (usedContexts);
        __contextTypes = Collections.unmodifiableSet (staticContexts);
    }

    //

    /**
     * @return An unmodifiable set of used context kinds.
     */
    public Set <ContextKind> usedContextKinds () {
        return __contextKinds;
    }

    /**
     * @return An unmodifiable set of used static context types.
     */
    public Set <ClassDesc> staticContextTypes () {
        return __contextTypes;
    }

    public static ContextUsage forMethod(final MethodModelCopy method) {
        // Collect the kinds of contexts appearing in the arguments as well as the types of static contexts.
        final EnumSet <ContextKind> usedContexts = EnumSet.noneOf (ContextKind.class);
        final Set<ClassDesc> staticContextTypes = new HashSet<>();
        List<ClassDesc> parameters = method.methodTypeSymbol().parameterList();
        for (final ClassDesc parameterDesc: parameters) {
            final ContextKind contextKind = ContextKind.forType(parameterDesc);
            if (contextKind != null) {
                usedContexts.add(contextKind);
                if (contextKind == ContextKind.STATIC) {
                    staticContextTypes.add(parameterDesc);
                }
            }
        }
        return new ContextUsage(usedContexts, staticContextTypes);
    }

}
