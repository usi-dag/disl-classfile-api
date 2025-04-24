package ch.usi.dag.disl.classparser;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;


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

        return __consumers.computeIfAbsent(ac, k -> new HashMap<>());
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
            .filter (Class::isAnnotation)
            .forEach (this::__accept);

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


    public AnnotationMapper accept(final MethodModelCopy methodModel) {
        List<Annotation> visibleAnnotation = ClassFileHelper.getVisibleAnnotation(methodModel);
        List<Annotation> invisibleAnnotation = ClassFileHelper.getInvisibleAnnotation(methodModel);
        Stream.of(visibleAnnotation, invisibleAnnotation)
                .flatMap(Collection::stream)
                .forEach(this::accept);

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


    private static Class<?> __resolveClass(final ClassDesc desc) {
        try {
            return Class.forName (ClassFileHelper.getClassName(desc));

        } catch (final ClassNotFoundException e) {
            throw new ParserRuntimeException (e);
        }
    }


    private static Object __instantiateEnum (
        final String desc, final String value
    ) {
        final ClassDesc classDesc = ClassDesc.ofDescriptor(desc);

        try {
            final Class <?> enumClass = Class.forName (ClassFileHelper.getClassName(classDesc));
            final Method valueMethod = enumClass.getMethod ("valueOf", String.class );
            final Object result = valueMethod.invoke (null, value);
            if (result != null) {
                return result;
            }

            // Throw the exception outside this try-catch block.

        } catch (final Exception e) {
            throw new ParserRuntimeException (
                e, "failed to instantiate enum value %s.%s", classDesc.displayName(), value
            );
        }

        throw new ParserRuntimeException (
            "failed to instantiate enum value %s.%s", classDesc.displayName(), value
        );
    }

}

