package ch.usi.dag.disl.classparser;

import java.lang.classfile.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.*;

import ch.usi.dag.disl.localvar.AbstractLocalVar;
import ch.usi.dag.disl.util.*;


import ch.usi.dag.disl.InitializationException;
import ch.usi.dag.disl.annotation.SyntheticLocal;
import ch.usi.dag.disl.annotation.SyntheticLocal.Initialize;
import ch.usi.dag.disl.annotation.ThreadLocal;
import ch.usi.dag.disl.exception.ParserException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.util.logging.Logger;

/**
 * Parses DiSL class with local variables.
 */
abstract class AbstractParser {

    private final Logger __log = Logging.getPackageInstance ();

    //

    protected LocalVars allLocalVars = new LocalVars();

    public LocalVars getAllLocalVars() {
        return allLocalVars;
    }


    // ****************************************
    // Local Variables Parsing and Processing
    // ****************************************

    protected void processLocalVars(final ClassModel dislClass) throws ParserException {
        // parse local variables
        final LocalVars localVars = parseLocalVars(dislClass.thisClass().name().stringValue(), dislClass.fields());

        // add local vars from this class to all local vars from all classes
        allLocalVars.putAll(localVars);

        MethodModel cinit = null;

        for (final MethodModel method: dislClass.methods()) {
            if (JavaNames.isInitializerName(method.methodName().stringValue())) {
                cinit = method;
                break;
            }
        }

        // parse init code for local vars and assigns them accordingly
        if (cinit != null && cinit.code().isPresent()) {
            List<CodeElement> instructions = cinit.code().get().elementList();
            parseInitCodeForSLV (instructions, localVars.getSyntheticLocals ());
            parseInitCodeForTLV (dislClass.thisClass().name().stringValue(), cinit, localVars.getThreadLocals ());
        }
    }


    private LocalVars parseLocalVars(final String className, List<FieldModel> fields) throws ParserException {
        // NOTE: if two synthetic local vars with the same name are defined
        // in different files they will be prefixed with class name as it is
        // also in byte code
        final LocalVars localVars = new LocalVars();
        for (FieldModel field: fields) {

            List<Annotation> annotations = ClassFileHelper.getFieldInvisibleAnnotations(field);
            if (annotations.isEmpty()) {
                continue;
            }
            if (annotations.size() > 1) {
                throw new ParserException("Field " + className + "."
                        + field.fieldName().stringValue() + " may have only one anotation");
            }
            final Annotation annotation = annotations.getFirst();
            final ClassDesc annotationDesc = annotation.classSymbol();
            final ClassDesc threadLocalAnnotationDesc = ClassDesc.ofDescriptor(ch.usi.dag.disl.annotation.ThreadLocal.class.descriptorString());
            if (annotationDesc.equals(threadLocalAnnotationDesc)) {
                final ThreadLocalVar tlv = parseThreadLocal(className, field, annotation);
                localVars.put(tlv);
                continue;
            }

            final ClassDesc syntheticLocalDesc = ClassDesc.ofDescriptor(SyntheticLocal.class.descriptorString());
            if (annotationDesc.equals(syntheticLocalDesc)) {
                final SyntheticLocalVar slv = parseSyntheticLocal(className, field, annotation);
                localVars.put(slv);
                continue;
            }
            throw new ParserException("Field " + className + "."
                    + field.fieldName().stringValue() + " has unsupported DiSL annotation");
        }

        return localVars;
    }


    private static class TLAnnotationData {
        /**
         * The default for the {@link ThreadLocal#inheritable} attribute.
         */
        boolean inheritable = false;
    }


    private ThreadLocalVar parseThreadLocal(final String className, final FieldModel field, final Annotation annotation) throws ParserException {
        // Ensure that the thread local field is declared static
        // and parse the annotation data.
        if (!field.flags().has(AccessFlag.STATIC)) {
            throw new ParserException("Field " + className + "." + field.fieldName().stringValue()
                    + " declared as ThreadLocal but is not static");
        }

        final TLAnnotationData tlAnnotationData = parseAnnotation(annotation, new TLAnnotationData());

        return new ThreadLocalVar(className, field.fieldName().stringValue(), field.fieldTypeSymbol(), tlAnnotationData.inheritable);
    }


    private static class SLAnnotationData {
        /**
         * The default for the {@link SyntheticLocal#initialize} attribute.
         */
        String initialize = Initialize.ALWAYS.name();
    }


    private SyntheticLocalVar parseSyntheticLocal(final String className, final FieldModel field, final Annotation annotation) throws ParserException {
        // Ensure that the synthetic local field is declared static, parse
        // annotation data and determine the initialization mode for the
        // variable.
        if (!field.flags().has(AccessFlag.STATIC)) {
            throw new ParserException("Field " + field.fieldName().stringValue() + className
                    + "." + " declared as SyntheticLocal but is not static");
        }

        final SLAnnotationData slAnnotationData = parseAnnotation(annotation, new SLAnnotationData());

        final Initialize initMode = Initialize.valueOf(slAnnotationData.initialize);

        return new SyntheticLocalVar(className, field.fieldName().stringValue(), field.fieldTypeSymbol(), initMode);
    }


    //
    // Parses the initialization code for synthetic local variables. Such code
    // can only contain assignment from constants of basic types, or a single
    // method call.
    private void parseInitCodeForSLV(final List<CodeElement> instructions,
                                     final Map<String, SyntheticLocalVar> slvs) {
        // Mark the first instruction of a block of initialization code and scan
        // the code. Ignore any instructions that do not access fields and stop
        // scanning when encountering any RETURN instruction.
        //
        // When encountering a field access instruction for a synthetic local
        // variable field, copy the code starting at the instruction marked as
        // first and ending with the field access instruction. Then mark the
        // instruction following the field access as the first instruction of
        // the next initialization block.
        CodeElement firstInstruction = instructions.getFirst();
        for (final CodeElement instruction: instructions) {
            if (instruction instanceof ReturnInstruction) {
                break;
            }

            // Only consider instructions access fields. This will leave us only
            // with GETFIELD, PUTFIELD, GETSTATIC, and PUTSTATIC instructions.
            //
            // RFC LB: Could we only consider PUTSTATIC instructions?
            if (instruction instanceof FieldInstruction lastInstruction) {

                // Skip accesses to fields that are not synthetic locals.
                final SyntheticLocalVar slv = slvs.get(SyntheticLocalVar.fqFieldNameFor(
                        lastInstruction.owner().name().stringValue(),
                        lastInstruction.name().stringValue()
                ));
                if (slv == null) {
                    // RFC LB: Advance firstInitInsn here as well?
                    continue;
                }

                // TODO with the classfile should I  actually clone the code or simply select it???
                // Clone the initialization code between the current first
                // initialization instruction and this field access instruction,
                // which marks the end of the initialization code.
                if (slv.hasInitCode ()) {
                    __log.warn (
                            "replacing initialization code "+
                                    "for synthetic local variable %s\n", slv.getID ()
                    );
                }
                slv.setInitCodeList(
                        simpleCopy(firstInstruction, lastInstruction, instructions)
                );

                firstInstruction = ClassFileHelper.nextInstruction(instructions, instruction);
            }
        }
    }


    private List<CodeElement> simpleCopy(final CodeElement start, final CodeElement end,
                                         final List<CodeElement> instructions) {
        final int startIndex = instructions.indexOf(start);
        final int endIndex = instructions.indexOf(end);
        if (startIndex >= 0 && endIndex >= 0 && endIndex >= startIndex) {
            return instructions.subList(startIndex, endIndex + 1);
        }
        return new ArrayList<>(); // TODO should I throw an error?
    }


    private void parseInitCodeForTLV(
            final String className, final MethodModel initMethod,
            final Map<String, ThreadLocalVar> tlvs
    ) throws ParserException {
        // the method in question is the static initializer

        // get the code from the method, only the instructions
        final List<Instruction> code = initMethod.code().orElseThrow()
                .elementList().stream().filter(i -> i instanceof Instruction)
                .map(i -> (Instruction)i).toList();

        for (int index=0; index < code.size(); index++) {// loop on all the instructions
            final Instruction instruction = code.get(index);
            if (instruction instanceof FieldInstruction storeInstruction) {
                // only consider putstatic for initialization
                if (storeInstruction.opcode() != Opcode.PUTSTATIC) {
                    continue; // thread local variable must be static
                }

                String tlvName = storeInstruction.name().stringValue();
                String tlvKey = AbstractLocalVar.fqFieldNameFor(className, storeInstruction.name().stringValue());
                if (!tlvs.containsKey(tlvKey)) {
                    continue;  // check that the variable is declared as thread local
                }

                // list of instructions that will be added to the tlv
                List<CodeElement> initializationInstructions = new ArrayList<>();

                // get the instruction before the putstatic
                final Instruction previousInstruction =  code.get(index -1);

                if (previousInstruction == null) {
                    throw new ParserException(String.format (
                            "Thread local variable %s has no instruction before the putstatic", tlvName
                    ));
                }
                switch (previousInstruction.opcode()) {
                    case Opcode.INVOKESPECIAL -> {
                        // if is a constructor get the code between INVOKESPECIAL and the NEW
                        Instruction currentInstruction = previousInstruction;
                        int currentIndex = index -1;
                        int constructorTotal = 0; // if there are more constructor inside this constructor
                        // we increment this variable, this if for case like:
                        // // public static A tlv = new A(new B()));

                        while (true) { //get all the code that is part of the constructor
                            if (currentInstruction.opcode() == Opcode.NEW) {
                                constructorTotal--;
                            } else if (currentInstruction.opcode() == Opcode.INVOKESPECIAL) {
                                constructorTotal++;
                            }
                            initializationInstructions.addFirst(currentInstruction);
                            if (constructorTotal == 0) {
                                break;
                            }
                            currentIndex--;
                            if (currentIndex < 0) {
                                break; // TODO should return an error instead???
                            }
                            currentInstruction = code.get(currentIndex);
                        }
                    }
                    case Opcode.ICONST_0,
                         Opcode.ICONST_1,
                         Opcode.ICONST_2,
                         Opcode.ICONST_3,
                         Opcode.ICONST_4,
                         Opcode.ICONST_5,
                         Opcode.LCONST_0,
                         Opcode.LCONST_1,
                         Opcode.FCONST_0,
                         Opcode.FCONST_1,
                         Opcode.FCONST_2,
                         Opcode.DCONST_0,
                         Opcode.DCONST_1,
                         Opcode.BIPUSH,
                         Opcode.SIPUSH,
                         Opcode.LDC,
                         Opcode.LDC2_W,
                         Opcode.LDC_W -> // if is a constant we just need to copy 1 instruction, the ins that load the instruction on the stack
                            initializationInstructions.add(previousInstruction);
                    case Opcode.NEWARRAY,
                         Opcode.ANEWARRAY-> {
                        initializationInstructions.addFirst(previousInstruction);
                        Instruction arraySize = code.get(index -2); // get instruction that push the size of the array on the stack
                        // it needs to be an instruction that push an integer
                        if (arraySize == null || !isIntegerConstant(arraySize.opcode())) {
                            throw new ParserException(String.format (
                                    "Thread local variable %s use invalid constant to set the size" +
                                            " of the array. Eg: new int[10] is valid as 10 is a constant" +
                                            ". while: new int[myFunction()] is not! ", tlvName
                            ));
                        }
                        initializationInstructions.addFirst(arraySize);
                    }
                    case Opcode.MULTIANEWARRAY -> {
                        NewMultiArrayInstruction newMultiArrayInstruction = (NewMultiArrayInstruction) previousInstruction;
                        initializationInstructions.addFirst(newMultiArrayInstruction);
                        final int dimensions = newMultiArrayInstruction.dimensions();
                        for (int offset = 1; offset <= dimensions; offset++) {
                            Instruction arraySize = code.get(index -1 -offset);
                            // all instructions need to be integer push
                            if (arraySize == null || !isIntegerConstant(arraySize.opcode())) {
                                throw new ParserException(String.format (
                                        "Thread local variable %s use invalid constant to set the size" +
                                                " of the array. Eg: new int[10][5] is valid as 10 and 5 are constant" +
                                                ". while: new int[10][myFunction()] is not! ", tlvName
                                ));
                            }
                            initializationInstructions.addFirst(arraySize);
                        }
                    }
                    default -> throw new ParserException(String.format (
                            "Thread local variable %s use an invalid initializer. " +
                                    "Only constants, constructors or array constructors" +
                                    " are valid. E.G. static int tlv = 5; " +
                                    "static Object tlv = new Object() ; " +
                                    "static int[] tlv = new int[15]; " +
                                    "and similar.", tlvName
                    ));
                }
                // finally set the tlv initialization instructions
                tlvs.get(tlvKey).setInitializerInstructions(initializationInstructions);
            }
        }
    }


    public static boolean isIntegerConstant(Opcode opcode) {
        switch (opcode) {
            case    Opcode.ICONST_0,
                    Opcode.ICONST_1,
                    Opcode.ICONST_2,
                    Opcode.ICONST_3,
                    Opcode.ICONST_4,
                    Opcode.ICONST_5,
                    Opcode.BIPUSH,
                    Opcode.LDC -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }


    static void ensureMethodReturnsVoid(final MethodModelCopy method) throws ParserException {
        ClassDesc returnType = method.methodTypeSymbol().returnType();
        if (!returnType.descriptorString().equals(void.class.descriptorString())) {
            throw new ParserException ("method may not return any value!");
        }
    }


    static void ensureMethodIsStatic(final MethodModelCopy method) throws ParserException {
        if (!method.flags().has(AccessFlag.STATIC)) {
            throw new ParserException ("method must be declared static!");
        }
    }


    static void ensureMethodUsesContextProperly(MethodModelCopy method) throws ParserException {
        final int firstLocalSlot = ClassFileHelper.getParameterSlotCount(method);
        if (method.hasCode()) {
            final List<CodeElement> allCode = method.instructions();
            for (CodeElement instruction: allCode) {
                if (instruction instanceof StoreInstruction && ((StoreInstruction) instruction).opcode() == Opcode.ASTORE) {
                    final int storeSlot = ((StoreInstruction) instruction).slot();
                    if (storeSlot < firstLocalSlot) {
                        throw new ParserException (String.format (
                                "context parameter stored into%s!",
                                ClassFileHelper.formatLineNo(" (at line %d)", instruction, allCode)
                        ));
                    }
                } else if (instruction instanceof LoadInstruction && ((LoadInstruction) instruction).opcode() == Opcode.ALOAD) {
                    final int loadSlot = ((LoadInstruction) instruction).slot();
                    if (loadSlot < firstLocalSlot) {
                        final CodeElement nextInstruction = ClassFileHelper.nextRealInstruction(allCode, instruction);
                        if (nextInstruction instanceof StoreInstruction && ((StoreInstruction) nextInstruction).opcode() == Opcode.ASTORE) {
                            throw new ParserException (String.format (
                                    "context parameter stored into a local variable%s!",
                                    ClassFileHelper.formatLineNo(" (at line %d)", nextInstruction, allCode)
                            ));
                        }
                    }
                }
            }
        }
    }


    static void ensureMethodHasOnlyContextArguments(final MethodModelCopy method) throws ParserException {
        // The type of each method argument must be a context of some kind.
        List<ClassDesc> argTypes = method.methodTypeSymbol().parameterList();
        for (int argIndex = 0; argIndex < argTypes.size(); argIndex++) {
            final ClassDesc argType = argTypes.get(argIndex);

            final ContextKind contextKind = ContextKind.forType(argType);
            if (contextKind == null) {
                throw new ParserException (
                        "argument #%d has invalid type, %s does not "+
                                "implement any context interface!",
                        (argIndex + 1), argType.displayName()
                );
            }
        }
    }


    /**
     * Ensures that a given method is not empty, i.e., it does not start with a
     * return instruction.
     *
     * @param method the method to check
     * @throws ParserException if the method is empty
     */
    static void ensureMethodIsNotEmpty(final MethodModelCopy method) throws ParserException {
        if (!method.hasCode() ||
                ClassFileHelper.isReturn(ClassFileHelper.selectReal(method.instructions()).getFirst())) {
            throw new ParserException ("method does not contain any code!");
        }
    }


    static void ensureMethodThrowsNoExceptions(final MethodModelCopy method) throws ParserException {
        if(method.hasCode()) {
            Optional<ExceptionsAttribute> attribute = method.getOriginal().findAttribute(Attributes.exceptions());
            if (attribute.isPresent()) {
                throw new ParserException ("method may not throw any exceptions!");
            }
        }
    }


    /**
     * Parses an annotation node and sets the fields of the given object to the
     * values found in the annotation. All fields must be declared in the class
     * of which the object is an instance.
     *
     * @param annotation
     *        the annotation to process
     * @param result
     *        the result object in which to modify field values
     * @return the modified result object
     */
    static <T> T parseAnnotation(final Annotation annotation, final T result) {
        if (annotation.elements().isEmpty()) {
            return result;
        }
        try {
            for (AnnotationElement element: annotation.elements()) {

                String name = element.name().stringValue();
                Object value = getAnnotationValue(element.value());

                __setFieldValue(name, value, result);
            }
            return result;
        } catch (final Exception e) {
            throw new InitializationException (
                    e, "failed to parse the %s annotation",
                    annotation.classSymbol().displayName()
            );
        }
    }

    private static Object getAnnotationValue(AnnotationValue annotationValue) {
        switch (annotationValue) {
            case AnnotationValue.OfBoolean ofBoolean -> {
                return ofBoolean.booleanValue();
            }
            case AnnotationValue.OfByte ofByte -> {
                return ofByte.byteValue();
            }
            case AnnotationValue.OfChar ofCharacter -> {
                return ofCharacter.charValue();
            }
            case AnnotationValue.OfShort ofShort -> {
                return ofShort.shortValue();
            }
            case AnnotationValue.OfInt ofInteger -> {
                return ofInteger.intValue();
            }
            case AnnotationValue.OfLong ofLong -> {
                return ofLong.longValue();
            }
            case AnnotationValue.OfFloat ofFloat -> {
                return ofFloat.floatValue();
            }
            case AnnotationValue.OfDouble ofDouble -> {
                return ofDouble.doubleValue();
            }
            case AnnotationValue.OfString ofString -> {
                return ofString.stringValue();
            }
            case AnnotationValue.OfClass ofClass -> {
                return ofClass.classSymbol();
            }
            case AnnotationValue.OfEnum ofEnum -> {
                return ofEnum.constantName().stringValue();
            }
            // TODO what to do with other case like OfArray, ?????
//            case AnnotationValue.OfArray ofArray -> {
//                return ofArray.values();  // this return a list of annotationValue
//            }
            default -> {
                return null;
            }
        }
    }

    private static <T> void __setFieldValue (
        final String name, final Object value, final T target
    ) {
        try {
            Field field = target.getClass ().getDeclaredField (name);
            field.set(target, value);

        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new InitializationException (
                e, "failed to store annotation attribute '%s'", name
            );
        }
    }


    static Class<?> getGuard(final ClassDesc guardType) throws ReflectionException {
        if (guardType == null) {
            return null;
        }
        return ReflectionHelper.resolveClass(guardType);
    }

}
