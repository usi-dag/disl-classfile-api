package ch.usi.dag.disl.classparser;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import ch.usi.dag.disl.util.ClassFileHelper;
// TODO remove all asm instances once the other are verified that works correctly
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;


final class AnnotationMapper {

    private final Map <
        Class <?>, Map <Predicate <String>, BiConsumer <String, Object>>
    > __consumers = new LinkedHashMap <> ();

    //

    public AnnotationMapper register (
        final Class <?> annotationClass,
        final String nameRegex, final BiConsumer <String, Object> consumer
    ) {
        final Predicate <String> predicate = Pattern.compile (nameRegex).asPredicate ();
        __getConsumers (annotationClass).put (predicate, consumer);
        return this;
    }


    private Map <Predicate <String>, BiConsumer <String, Object>> __getConsumers (
        final Class <?> ac
    ) {
        Map <Predicate <String>, BiConsumer <String, Object>> result = __consumers.get (ac);
        if (result == null) {
            result = new HashMap <> ();
            __consumers.put (ac, result);
        }

        return result;
    }

    private Map <Predicate <String>, BiConsumer <String, Object>> __findConsumers (
        final Class <?> ac
    ) {
        return __consumers.entrySet ().stream ()
            .filter (e -> e.getKey ().isAssignableFrom (ac))
            .findFirst ()
            .orElseThrow (() -> new ParserRuntimeException (
                "unsupported annotation type: %s", ac.getName ()
            )).getValue ();
    }

    public AnnotationMapper processDefaults () {
        __consumers.keySet ().stream ()
            .filter (ac -> ac.isAnnotation ())
            .forEach (ac -> __accept (ac));

        return this;
    }


    private void __accept (final Class <?> ac) {
        final Map <
            Predicate <String>, BiConsumer <String, Object>
        > consumers = __findConsumers (ac);

        Arrays.stream (ac.getDeclaredMethods ()).forEach (m -> {
            final String name = m.getName ();
            __getConsumer (consumers, name).accept (name, m.getDefaultValue ());
        });
    }


    private BiConsumer <String, Object> __getConsumer (
        final Map <Predicate <String>, BiConsumer <String, Object>> consumers,
        final String name
    ) {
        return consumers.entrySet ().stream ()
            .filter (e -> e.getKey ().test (name))
            .findFirst ()
            .orElseThrow (() -> new ParserRuntimeException (
                "no consumer for annotation attribute %s", name
            )).getValue ();
    }


    public AnnotationMapper accept (final MethodNode mn) {
        Arrays.asList (mn.visibleAnnotations, mn.invisibleAnnotations).stream ()
            .filter (l -> l != null)
            .flatMap (l -> l.stream ())
            .forEach (an -> accept (an));

        return this;
    }

    public AnnotationMapper accept(final MethodModel methodModel) {
        List<RuntimeInvisibleAnnotationsAttribute> runtimeInvisibleAnnotationsAttributes = ClassFileHelper.getInvisibleAnnotation(methodModel);
        List<RuntimeVisibleAnnotationsAttribute> runtimeVisibleAnnotationsAttributes = ClassFileHelper.getVisibleAnnotation(methodModel);
        List<Annotation> visibleAnnotation = runtimeVisibleAnnotationsAttributes.stream()
                .map(RuntimeVisibleAnnotationsAttribute::annotations)
                .flatMap(Collection::stream)
                .toList();
        List<Annotation> invisibleAnnotation = runtimeInvisibleAnnotationsAttributes.stream()
                .map(RuntimeInvisibleAnnotationsAttribute::annotations)
                .flatMap(Collection::stream)
                .toList();
        Arrays.asList(visibleAnnotation, invisibleAnnotation).stream()
                .flatMap(Collection::stream)
                .forEach(this::accept);

        return this;
    }

    public AnnotationMapper accept (final AnnotationNode an) {
        final Class <?> ac = __resolveClass (Type.getType (an.desc));

        final Map <
            Predicate <String>, BiConsumer <String, Object>
        > consumers = __findConsumers (ac);

        an.accept (new AnnotationVisitor (Opcodes.ASM9) {
            @Override
            public void visit (final String name, final Object value) {
                __getConsumer (consumers, name).accept (name, value);
            }

            @Override
            public void visitEnum (
                final String name, final String desc, final String value
            ) {
                final Object enumValue = __instantiateEnum (desc, value);
                __getConsumer (consumers, name).accept (name, enumValue);
            }

            @Override
            public AnnotationVisitor visitArray (final String name) {
                final BiConsumer <String, Object> consumer = __getConsumer (consumers, name);
                return new ListCollector (name, consumer);
            }
        });

        return this;
    }

    public AnnotationMapper accept(final Annotation annotation) {
        final Class<?> ac = __resolveClass(annotation.classSymbol());
        final Map<Predicate<String>, BiConsumer<String, Object>> consumers = __findConsumers(ac);
        List<AnnotationElement> elements = annotation.elements();
        for (AnnotationElement element: elements) {
            Utf8Entry elementName = element.name();
            AnnotationValue value = element.value();
            acceptElement(elementName, value, consumers);
        }
        return this;
    }

    public void acceptElement(final Utf8Entry elementName,
                              final AnnotationValue value ,
                              final Map<Predicate<String>, BiConsumer<String, Object>> consumers
    ) {
        switch (value) {
            case AnnotationValue.OfArray ofArray -> {
                List<AnnotationValue> annotationValues = ofArray.values();
                for (AnnotationValue innerAnnotationValue: annotationValues) {
                    acceptElement(elementName,innerAnnotationValue, consumers);
                }
            }
            case AnnotationValue.OfEnum ofEnum -> {
                // TODO is ofEnum.constantName() equivalent to "Object value" of method "Visit" from asm visitor????
                final Object enumValue = __instantiateEnum(ofEnum.classSymbol().descriptorString(), ofEnum.constantName().stringValue());
                // TODO is elementName.stringValue() the correct name to pass??
                __getConsumer(consumers, elementName.stringValue()).accept(elementName.stringValue(), enumValue);
            }
            case AnnotationValue.OfClass ofClass -> {
                // TODO what to pass here??? maybe the class itself???
                __getConsumer(consumers, elementName.stringValue()).accept(elementName.stringValue(), ofClass.getClass());
            }
            case AnnotationValue.OfString ofString -> {
                __getConsumer(consumers, elementName.stringValue()).accept(elementName.stringValue(), ofString.stringValue());
            }
            case AnnotationValue.OfConstant ofConstant -> {
                // TODO do we ned this??? for example Int, Float, ... ???
                throw new ParserRuntimeException("Failed to parse annotation: "
                        + elementName.stringValue() + " with value " + value);
            }
            default -> throw new ParserRuntimeException("Failed to parse annotation: "
                    + elementName.stringValue() + " with value " + value);
        }
    }


    /**
     * Collects individual values into a list and submits the result to the
     * given consumer when the {@link #visitEnd()} method is called.
     * <p>
     * <b>Note:</b>This collector does not currently support nested arrays or
     * annotation values.
     */
    private static class ListCollector extends AnnotationVisitor {
        final List <Object> __values = new ArrayList <> ();

        final String __name;
        final BiConsumer <String, Object> __consumer;

        ListCollector (final String name, final BiConsumer <String, Object> consumer) {
            super (Opcodes.ASM9);

            __name = name;
            __consumer = consumer;
        }

        @Override
        public void visit (final String name, final Object value) {
            __values.add (value);
        }

        @Override
        public void visitEnum (final String name, final String desc, final String value) {
            __values.add (__instantiateEnum (desc, value));
        }

        @Override
        public void visitEnd () {
            __consumer.accept (__name, __values);
        }
    };


    private static Class <?> __resolveClass (final Type type) {
        try {
            return Class.forName (type.getClassName ());

        } catch (final ClassNotFoundException e) {
            throw new ParserRuntimeException (e);
        }
    }

    private static Class<?> __resolveClass(final ClassDesc desc) {
        try {
            return Class.forName (desc.displayName());

        } catch (final ClassNotFoundException e) {
            throw new ParserRuntimeException (e);
        }
    }


    private static Object __instantiateEnum (
        final String desc, final String value
    ) {
        //Type.getType (desc).getClassName ();
        final String className = ClassDesc.ofDescriptor(desc).displayName();

        try {
            final Class <?> enumClass = Class.forName (className);
            final Method valueMethod = enumClass.getMethod ("valueOf", String.class );
            final Object result = valueMethod.invoke (null, value);
            if (result != null) {
                return result;
            }

            // Throw the exception outside this try-catch block.

        } catch (final Exception e) {
            throw new ParserRuntimeException (
                e, "failed to instantiate enum value %s.%s", className, value
            );
        }

        throw new ParserRuntimeException (
            "failed to instantiate enum value %s.%s", className, value
        );
    }

}

