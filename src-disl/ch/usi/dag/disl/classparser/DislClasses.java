package ch.usi.dag.disl.classparser;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.usi.dag.disl.util.ClassFileAPI.ClassModelHelper;

import ch.usi.dag.disl.DiSL.CodeOption;
import ch.usi.dag.disl.InitializationException;
import ch.usi.dag.disl.annotation.ArgumentProcessor;
import ch.usi.dag.disl.exception.ParserException;
import ch.usi.dag.disl.exception.ProcessorException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.exception.StaticContextGenException;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.processor.ArgProcessor;
import ch.usi.dag.disl.snippet.Snippet;


/**
 * Parser for DiSL classes containing either snippets or method argument
 * processors. This class is not meant for public use.
 */
public final class DislClasses {

    private final SnippetParser __snippetParser;

    private DislClasses (final SnippetParser snippetParser) {
        // not to be instantiated from outside
        __snippetParser = snippetParser;
    }


    public static DislClasses load(final Set <CodeOption> options, final Stream <String> classNames
                                    ) throws ParserException, StaticContextGenException, ReflectionException, ProcessorException {
        final List<ClassModel> classModels = classNames
                .map(DislClasses::__createClassModel)
                .toList();

        if (classModels.isEmpty()) {
            throw new InitializationException("No DiSL classes found. They must be explicitly specified "+
                    "using the disl.classes system property or the DiSL-Classes "+
                    "attribute in the instrumentation JAR manifest.");
        }

        final SnippetParser sp = new SnippetParser ();
        final ArgProcessorParser app = new ArgProcessorParser ();

        for (final ClassModel classModel: classModels) {
            if (__isArgumentProcessor(classModel)) {
                app.parse(classModel);
            } else {
                sp.parse(classModel);
            }
        }

        final LocalVars localVars = LocalVars.merge (
                sp.getAllLocalVars (), app.getAllLocalVars ()
        );

        final Map<ClassDesc, ArgProcessor> processors = app.initProcessors(localVars);

        for (final Snippet snippet: sp.getSnippets()) {
            snippet.init(localVars, processors, options);
        }

        return new DislClasses(sp);
    }


    private static ClassModel __createClassModel(final String className) {
        // Parse input stream into a class node. Include debug information so that we can report line numbers in case of problems in DiSL classes.
        // Re-throw any exceptions as DiSL initialization exceptions.
        try {
            return ClassModelHelper.SNIPPET.load(className);  // TODO is my classModelHelper equivalent ans the ClassNodeHelper???
        } catch (final Exception e) {
            throw new InitializationException(e, "failed to load DiSL class %s", className);
        }
    }


    private static boolean __isArgumentProcessor(final ClassModel classModel) {
        // An argument processor must have an @ArgumentProcessor annotation associated with the class.
        // DiSL instrumentation classes may have an @Instrumentation annotation.
        // DiSL classes without annotations are by default considered to be instrumentation classes.
        List<RuntimeInvisibleAnnotationsAttribute> runtimeInvisibleAnnotationsAttributes = classModel
                .elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e)
                .toList();

        String argProcessor = ArgumentProcessor.class.descriptorString();

        for (RuntimeInvisibleAnnotationsAttribute annotationsAttribute: runtimeInvisibleAnnotationsAttributes) {
            List<Annotation> annotations = annotationsAttribute.annotations();
            for (Annotation annotation: annotations) {
                if(Objects.equals(annotation.classSymbol().descriptorString(), argProcessor)) {
                    return true;
                }
            }
        }
        return false;
    }


    public List <Snippet> getSnippets () {
        return __snippetParser.getSnippets ();
    }

    public List <Snippet> selectMatchingSnippets (
        final String className, final String methodName, final String methodDesc
    ) {
        return __snippetParser.getSnippets ().stream ().unordered ()
            .filter (s -> s.getScope ().matches (className, methodName, methodDesc))
            .collect (Collectors.toList ());
    }
}
