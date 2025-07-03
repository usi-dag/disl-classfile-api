package ch.usi.dag.disl.weaver;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessFlag;
import java.util.*;

import ch.usi.dag.disl.util.*;
import ch.usi.dag.disl.util.ClassFileAnalyzer.AnalyzerException;
import ch.usi.dag.disl.util.ClassFileAnalyzer.BasicValue;
import ch.usi.dag.disl.util.ClassFileAnalyzer.Frame;
import ch.usi.dag.disl.util.ClassFileAnalyzer.SourceValue;
import ch.usi.dag.disl.weaver.peClassFile.PartialEvaluator;
import ch.usi.dag.disl.classcontext.ClassContext;
import ch.usi.dag.disl.coderep.Code;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.exception.InvalidContextUsageException;
import ch.usi.dag.disl.processor.generator.PIResolver;
import ch.usi.dag.disl.processor.generator.ProcInstance;
import ch.usi.dag.disl.processor.generator.ProcMethodInstance;
import ch.usi.dag.disl.processorcontext.ArgumentContext;
import ch.usi.dag.disl.processorcontext.ArgumentProcessorContext;
import ch.usi.dag.disl.processorcontext.ArgumentProcessorMode;
import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Snippet;
import ch.usi.dag.disl.snippet.SnippetCode;
import ch.usi.dag.disl.staticcontext.StaticContext;
import ch.usi.dag.disl.staticcontext.generator.SCGenerator;

public class WeavingCode {

    final static String PROP_PE = "disl.parteval";

    private final WeavingInfo info;
    private final SnippetCode code;
    private final Snippet snippet;
    private final Shadow shadow;

    private final MethodModelCopy methodModel;
    private final CodeElement weavingLocation;
    //private final List<CodeElement> instructionsToInstrument;

    private final List<CodeElement> snippetInstructions;
    private final List<CodeElement> methodInstructions;

    private final CodeElement[] instructionsArray;
    //

    private int snippetMaxLocals;
    private int methodMaxLocals;
    private final List<ExceptionCatch> exceptionCatches; // this will be updated and used later in the PartialEvaluator, in the end when we will build the new class everything will be re-computed by the
    // ClassFile, such as max-locals and max-stacks

    //


    public WeavingCode(
            final WeavingInfo weavingInfo, final MethodModelCopy methodModel, final SnippetCode src,
            final Snippet snippet, final Shadow shadow, final CodeElement location, final List<CodeElement> methodInstructions,
            List<ExceptionCatch> exceptionCatches, int methodMaxLocals
    ) {
        this.info = weavingInfo;
        this.methodModel = methodModel;
        this.code = src.clone();
        this.snippet = snippet;
        this.shadow = shadow;
        this.weavingLocation = location;

        this.methodInstructions = methodInstructions;
        this.snippetInstructions = code.getInstructions();
        this.instructionsArray = snippetInstructions.toArray(new CodeElement[0]);
        this.exceptionCatches = new ArrayList<>(exceptionCatches);

        this.methodMaxLocals = methodMaxLocals;
        this.snippetMaxLocals = ClassFileHelper.getMaxLocals(snippetInstructions, methodModel.methodTypeSymbol(), methodModel.flags());

    }


    /**
     * Replaces instruction sequences representing invocations of methods in
     * {@link StaticContext} classes with precomputed constants.
     */
    private void rewriteStaticContextCalls(
            final SCGenerator staticInfoHolder, final List<CodeElement> instructions
    ) {
        // Iterate over a copy -- we will be modifying the underlying list.
        for (final CodeElement element: instructions.toArray(new CodeElement[0])) {
            // Look for virtual method invocations on static-context classes.
            if (!(element instanceof InvokeInstruction invokeInstruction)
                    || invokeInstruction.opcode() != Opcode.INVOKEVIRTUAL) {
                continue;
            }

            // TODO LB: If owner implements StaticContext, should not missing info be an error?
            if (!staticInfoHolder.contains(shadow,
                    invokeInstruction.owner().name().stringValue(),
                    invokeInstruction.name().stringValue())) {
                continue;
            }

            // Lookup the results for the given method.
            // If none is found, return null to the client code.
            final Object staticInfo = staticInfoHolder.get(
                    shadow,
                    invokeInstruction.owner().name().stringValue(),
                    invokeInstruction.name().stringValue());

            // TODO LB: Why insert into code.getInstructions() instead of insns?
            ClassFileHelper.insert(
                    invokeInstruction,
                    (staticInfo != null) ?
                            ClassFileHelper.loadConst(staticInfo) :
                            ClassFileHelper.loadNull(),
                    code.getInstructions()
            );

            // Remove the invocation sequence.
            __removeInstruction(Opcode.ALOAD,
                    ClassFileHelper.previousRealInstruction(
                            instructions,
                            invokeInstruction
                    ),
                    instructions,
                    "rewriteStaticContextCalls"
            );
            instructions.remove(invokeInstruction);
        }
    }


    private static final String __CLASS_CONTEXT_INTERNAL_NAME__ =
        ClassContext.class.getName().replace('.', '/');


    /**
     * Replaces instruction sequences representing invocations of methods on the
     * {@link ClassContext} interface with constants.
     */
    private void rewriteClassContextCalls(final List<CodeElement> instructions) throws InvalidContextUsageException {
        // Iterate over a copy -- we will be modifying the underlying list.
        for (CodeElement element: instructions.toArray(new CodeElement[0])) {
            // Look for invocations on the ClassContext interface.
            final InvokeInstruction invokeInstruction = __getInvokeInterfaceInstruction(
                    element, __CLASS_CONTEXT_INTERNAL_NAME__
            );

            if (invokeInstruction == null) {
                continue;
            }

            // Handle individual methods.
            final String methodName = invokeInstruction.name().stringValue();
            if ("asClass".equals(methodName)) {
                //                      ALOAD (ClassContext interface reference)
                //                      LDC (String class name)
                // invokeInstruction => INVOKEINTERFACE
                final Instruction classNameInstruction = ClassFileHelper.previousRealInstruction(
                        instructions, invokeInstruction
                );
                final String internalName = __expectStringConstLoad(
                        classNameInstruction, "ClassContext", methodName, "internalName"
                );

                // TODO LB Check that the literal is actually an internal name.

                // Convert the literal to a type and replace the LDC of the
                // String literal with LDC of a class literal. Then remove the
                // invocation sequence instructions.

                // TODO does this work? is the internal name treated equally by asm and the classfile
                ClassDesc classDesc = ClassDesc.ofInternalName(internalName);
                LoadableConstantEntry entry = ConstantPoolBuilder.of().loadableConstantEntry(classDesc);
                ConstantInstruction constantInstruction = ConstantInstruction.ofLoad(Opcode.LDC, entry);
                ClassFileHelper.insert(invokeInstruction, constantInstruction, instructions);

                __removeInstruction(
                        Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, classNameInstruction),
                        instructions,
                        "rewriteClassContextCalls"
                        );
                instructions.remove(classNameInstruction);
                instructions.remove(invokeInstruction);

            } else {
                throw new DiSLFatalException (
                        "%s: unsupported ClassContext method %s()",
                        __location (snippet, invokeInstruction), methodName
                );
            }
        }
    }

    //

    private static final int INVALID_SLOT = -1;


    private static final String __DYNAMIC_CONTEXT_INTERNAL_NAME__ =
        DynamicContext.class.getName().replace('.', '/');


    /**
     * Search for an instruction sequence that stands for a request for dynamic
     * information, and replace them with a load instruction.
     * NOTE that if the user requests for the stack value, some store
     * instructions will be inserted to the target method, and new local slot
     * will be used for storing this.
     * @param throwing (not sure)
     * @param instructions the instruction of the snippet (it is basically the same as this.snippetInstructions,
     *                     I am not sure why it is passed as a parameter instead of using this.snippetInstructions, it was like this
     *                     already in the original DiSL)
     * @throws InvalidContextUsageException throws if the context is used wrongly
     */
    private void rewriteDynamicContextCalls(
            final boolean throwing, final List<CodeElement> instructions
    ) throws InvalidContextUsageException {
        Frame<BasicValue> basicFrame = info.getBasicFrame(weavingLocation);
        final Frame<SourceValue> sourceFrame = info.getSourceFrame(weavingLocation);

        // NOTE: INVALID_SLOT is -1 and if an instruction is created with this slot it will generate an exception, but
        // in the code "exceptionSlot" is used only when "throwing" is true, so it shouldn't be a problem
        final int exceptionSlot = throwing? methodMaxLocals++ : INVALID_SLOT;

        // Iterate over a copy -- we will be modifying the underlying list.

        for (CodeElement element: instructions.toArray(new CodeElement[0])) {
            // Look for DynamicContext interface method invocations.
            final InvokeInstruction invokeInstruction = __getInvokeInterfaceInstruction(element, __DYNAMIC_CONTEXT_INTERNAL_NAME__);
            if (invokeInstruction == null) {
                continue;
            }
            // Handle individual method invocations.
            //
            // The instructions preceding the INVOKEINTERFACE instruction load
            // arguments on the stack, depending on the invoked method.
            //
            // TRICK: in some situations, the following code will generate a
            // VarInsnNode with a negative local slot. This will be
            // corrected in the fixLocalIndex() method.
            //
            // TODO LB: Is this still true? fixLocalIndex() is called after this method!
            // TODO LB: Split up switch legs into separate handler classes.
            final String methodName = invokeInstruction.name().stringValue();
            final MethodTypeDesc methodType = invokeInstruction.typeSymbol();
            final CodeElement afterInvoke = ClassFileHelper.nextInstruction(instructions, invokeInstruction);

            if ("getThis".equals(methodName)) {
                //               ALOAD (DynamicContext interface reference)
                // invokeInsn => INVOKEINTERFACE
                // Ensure getThis() returns null in static methods.
                final boolean isStatic = methodModel.flags().has(AccessFlag.STATIC);
                ClassFileHelper.insert(invokeInstruction,
                        isStatic ? ClassFileHelper.loadNull(): ClassFileHelper.loadThis(),
                        instructions);

                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, invokeInstruction),
                        instructions,
                        "rewriteDynamicContextCalls1");
                instructions.remove(invokeInstruction);
            } else if ("getException".equals(methodName)) {
                //               ALOAD (DynamicContext interface reference)
                // invokeInsn => INVOKEINTERFACE

                // Ensure getException() returns null outside an exception block.
                ClassFileHelper.insert(invokeInstruction,
                        throwing?
                                ClassFileHelper.loadObjectVar(exceptionSlot) :
                                ClassFileHelper.loadNull(),
                        instructions
                );
                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, invokeInstruction),
                        instructions, "rewriteDynamicContextCalls2");
                instructions.remove(invokeInstruction);
            } else if ("getStackValue".equals(methodName)) {
                //               ALOAD (DynamicContext interface reference)
                //               ICONST/BIPUSH/LDC (stack item index)
                //               LDC/GETSTATIC (expected object type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction valueTypeIns = ClassFileHelper.previousRealInstruction(
                        instructions, invokeInstruction);
                final Instruction itemIndexIns = ClassFileHelper.previousRealInstruction(
                        instructions, valueTypeIns);
                ConstantDesc expectedType = __expectTypeConstLoad(valueTypeIns, "DynamicContext", methodName, "type");
                TypeKind realExpectedType;
                try {
                    TypeKind primitiveType = ClassFileHelper.unboxToPrimitive(expectedType);
                    if (primitiveType == null) {
                        realExpectedType = TypeKind.from(ClassFileHelper.resolveConstantDesc(expectedType));
                    } else {
                        realExpectedType = primitiveType;
                    }
                } catch (Exception e) {
                    throw new InvalidContextUsageException (
                            "%s: Could not resolve ConstantDesc %s when accessing stack",
                            __location (snippet, invokeInstruction), expectedType
                    );
                }


                if (basicFrame != null) {
                    final int itemIndex = __expectIntConstLoad(itemIndexIns, "DynamicContext", methodName, "itemIndex");
                    final int itemCount = basicFrame.getStackSize();

                    // Prevent accessing slots outside the stack frame.
                    if (itemIndex < 0 || itemCount <= itemIndex) {
                        throw new InvalidContextUsageException (
                                "%s: accessing stack (item %d) outside the stack frame (%d items)",
                                __location (snippet, invokeInstruction), itemIndex, itemCount
                        );
                    }

                    // Check that the expected type matches the actual type.
                    TypeKind actualType = ClassFileFrameHelper.getStackByIndex(basicFrame, itemIndex).getTypeKind();
                    if (!realExpectedType.equals(actualType)) {
                        throw new InvalidContextUsageException (
                                "%s: expected %s but found %s when accessing stack item %d",
                                __location (snippet, invokeInstruction), expectedType, actualType, itemIndex
                        );
                    }

                    // Duplicate the desired stack value and store it in a local
                    // variable. Then load it back from there in place of the
                    // context method invocation.
                    final int varSize = ClassFileFrameHelper.dupStack(
                            sourceFrame, itemIndex, realExpectedType.upperBound(), methodMaxLocals, this.methodInstructions
                    );
                    ClassFileHelper.insertBefore(
                            afterInvoke, ClassFileHelper.loadVar(realExpectedType, methodMaxLocals), instructions);
                    methodMaxLocals += varSize;
                } else {
                    // Cannot access the stack -- return type-specific default.
                    // TODO warn user that weaving location is unreachable.
                    ClassFileHelper.insertBefore(afterInvoke, ClassFileHelper.loadDefault(realExpectedType), instructions);
                }

                __boxIfPrimitiveBefore(realExpectedType, afterInvoke, instructions);
                // Remove the invocation sequence.
                __removeInstruction(
                        Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(
                                instructions, itemIndexIns),
                        instructions, "rewriteDynamicContextCalls3");
                instructions.remove(itemIndexIns);
                instructions.remove(valueTypeIns);
                instructions.remove(invokeInstruction);
                __removeIfCheckCast(afterInvoke, instructions);
            } else if ("getMethodArgumentValue".equals (methodName)) {
                //               ALOAD (DynamicContext interface reference)
                //               ICONST/BIPUSH/LDC (argument index)
                //               LDC/GETSTATIC (expected object type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction valueTypeIns = ClassFileHelper.previousRealInstruction(
                        instructions, invokeInstruction);
                final Instruction paramIndexIns = ClassFileHelper.previousRealInstruction(
                        instructions, valueTypeIns);

                if (basicFrame == null) {
                    // TODO warn user that weaving location is unreachable.
                    basicFrame = info.getRetFrame();
                }

                // Prevent accessing invalid arguments.
                final int paramIndex = __expectIntConstLoad(
                        paramIndexIns, "DynamicContext", methodName, "argumentIndex");

                final int paramCount = methodModel.methodTypeSymbol().parameterCount();

                if (paramIndex < 0 || paramCount <= paramIndex) {
                    throw new InvalidContextUsageException (
                            "%s: accessing invalid parameter %d (method only has %d)",
                            __location (snippet, invokeInstruction), paramIndex, paramCount
                    );
                }

                // Check that the expected type matches the actual argument type.
                final ConstantDesc expectedType = __expectTypeConstLoad(
                        valueTypeIns, "DynamicContext", methodName, "type");
                final TypeKind realExpectedType;
                try {
                    TypeKind primitiveType = ClassFileHelper.unboxToPrimitive(expectedType);
                    if (primitiveType == null) {
                        realExpectedType = TypeKind.from(expectedType.resolveConstantDesc(MethodHandles.lookup()).getClass());
                    } else {
                        realExpectedType = primitiveType;
                    }
                } catch (Exception e) {
                    throw new InvalidContextUsageException (
                            "%s: Could not resolve ConstantDesc %s when accessing stack",
                            __location (snippet, invokeInstruction), expectedType
                    );
                }

                final int paramSlot = ClassFileHelper.getParameterSlot(methodModel, paramIndex);
                final TypeKind actualType = basicFrame.getLocal(paramSlot).getTypeKind();

                if (!realExpectedType.equals(actualType)) {
                    throw new InvalidContextUsageException (
                            "%s: expected %s but found %s when accessing method parameter %d",
                            __location (snippet, invokeInstruction), expectedType, actualType, paramIndex
                    );
                }
                // Load the argument from a local variable slot. Box primitive
                // values -- we remove unnecessary boxing later.
                ClassFileHelper.insertBefore(
                        afterInvoke,
                        ClassFileHelper.loadVar(realExpectedType, paramSlot),
                        instructions);
                __boxIfPrimitiveBefore(realExpectedType, afterInvoke, instructions);

                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, paramIndexIns),
                        instructions, "rewriteDynamicContextCalls4");
                instructions.remove(paramIndexIns);
                instructions.remove(valueTypeIns);
                instructions.remove(invokeInstruction);
                __removeIfCheckCast(afterInvoke, instructions);
            } else if ("getLocalVariableValue".equals (methodName)) {
                //               ALOAD (DynamicContext interface reference)
                //               ICONST/BIPUSH/LDC (variable slot argument)
                //               LDC/GETSTATIC (expected object type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction valueTypeIns = ClassFileHelper.previousRealInstruction(
                        instructions, invokeInstruction);
                final Instruction slotIndexIns = ClassFileHelper.previousRealInstruction(
                        instructions, valueTypeIns);

                if (basicFrame == null) {
                    // TODO warn user that weaving location is unreachable.
                    basicFrame = info.getRetFrame();
                }

                // Prevent accessing invalid variable slots.
                final int varSlot = __expectIntConstLoad (
                       slotIndexIns, "DynamicContext", methodName, "slotIndex"
                );

                final int varSlotCount = basicFrame.getLocals();

                if (varSlot < 0 || varSlotCount <= varSlot) {
                    throw new InvalidContextUsageException (
                            "%s: accessing invalid variable slot (%d) -- method only has %d slots",
                            __location (snippet, invokeInstruction), varSlot, varSlotCount
                    );
                }

                // Check that the expected type matches the actual argument type.
                final ConstantDesc expectedType = __expectTypeConstLoad(
                        valueTypeIns, "DynamicContext", methodName, "type");
                final TypeKind realExpectedType;
                try {
                    TypeKind primitiveType = ClassFileHelper.unboxToPrimitive(expectedType);
                    if (primitiveType == null) {
                        realExpectedType = TypeKind.from(expectedType.resolveConstantDesc(MethodHandles.lookup()).getClass());
                    } else {
                        realExpectedType = primitiveType;
                    }
                } catch (Exception e) {
                    throw new InvalidContextUsageException (
                            "%s: Could not resolve ConstantDesc %s when accessing stack",
                            __location (snippet, invokeInstruction), expectedType
                    );
                }
                final TypeKind actualType = basicFrame.getLocal(varSlot).getTypeKind();

                 if (!realExpectedType.equals(actualType)) {
                    throw new InvalidContextUsageException (
                            "%s: expected %s but found %s when accessing variable slot %d",
                            __location (snippet, invokeInstruction), expectedType, actualType, varSlot
                    );
                }

                // Load the variable from a local variable slot. Box primitive
                // values -- we remove unnecessary boxing later.
                ClassFileHelper.insertBefore(afterInvoke,
                        ClassFileHelper.loadVar(realExpectedType, varSlot),
                        instructions);
                __boxIfPrimitiveBefore(realExpectedType, afterInvoke, instructions);
                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, slotIndexIns),
                        instructions, "rewriteDynamicContextCalls5");
                instructions.remove(slotIndexIns);
                instructions.remove(valueTypeIns);
                instructions.remove(invokeInstruction);
                __removeIfCheckCast(afterInvoke, instructions);
            } else  if (
                    "getInstanceFieldValue".equals (methodName)
                    && methodType.parameterCount() == 4
            ) {
                //               ALOAD (DynamicContext interface reference)
                //               ALOAD (owner reference)
                //               LDC/GETSTATIC (owner type)
                //               LDC (field name String)
                //               LDC/GETSTATIC (field type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction fieldTypeIns = ClassFileHelper.previousRealInstruction(instructions, invokeInstruction);
                final Instruction fieldNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldTypeIns);
                final Instruction ownerTypeIns = ClassFileHelper.previousRealInstruction(instructions, fieldNameIns);

                final ConstantDesc fieldType = __expectTypeConstLoad(
                        fieldTypeIns, "DynamicContext", methodName, "fieldType");
                // Get the field value. Box primitive values (based on
                // the field type, not the expected type) -- we remove
                // unnecessary boxing later.
                final String fieldName = __expectStringConstLoad(
                        fieldNameIns, "DynamicContext", methodName, "fieldName");
                final ConstantDesc ownerType = __expectTypeConstLoad(
                        ownerTypeIns, "DynamicContext", methodName, "ownerType");
                final ClassDesc realFieldDesc;
                final ClassDesc realOwnerType;
                try {
                    realFieldDesc = ClassFileHelper.resolveConstantDesc(fieldType);
                    realOwnerType = ClassFileHelper.resolveConstantDesc(ownerType);
                } catch (Exception e) {
                    throw new InvalidContextUsageException (
                            "%s: Could not resolve ConstantDesc %s or %s when accessing stack",
                            __location (snippet, invokeInstruction), fieldType, ownerType
                    );
                }

                ClassFileHelper.insertBefore(
                        afterInvoke,
                        ClassFileHelper.getField(realOwnerType.descriptorString(), fieldName, realFieldDesc.descriptorString()),
                        instructions);
                __boxIfPrimitiveBefore(TypeKind.from(realFieldDesc), afterInvoke, instructions);
                // Remove the invocation sequence.
                //
                // BUT, keep the owner reference load instruction.
                final Instruction ownerLoadIns = ClassFileHelper.previousRealInstruction(
                        instructions, ownerTypeIns);
                final Instruction ifaceLoadIns = ClassFileHelper.previousRealInstruction(
                        instructions, ownerLoadIns);

                __removeInstruction(Opcode.ALOAD, ifaceLoadIns, instructions, "rewriteDynamicContextCalls6");
                // keep the owner load instruction
                instructions.remove(ownerTypeIns);
                instructions.remove(fieldNameIns);
                instructions.remove(fieldTypeIns);
                instructions.remove(invokeInstruction);

                __removeIfCheckCast(afterInvoke, instructions);
            } else if (
                    "getInstanceFieldValue".equals(methodName)
                    && methodType.parameterCount() == 5
            ) {
                //               ALOAD (DynamicContext interface reference)
                //               ALOAD (owner reference)
                //               LDC (owner name String)
                //               LDC (field name String)
                //               LDC (field descriptor String)
                //               LDC/GETSTATIC (expected value type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction valueTypeIns = ClassFileHelper.previousRealInstruction(instructions, invokeInstruction);
                final Instruction fieldDescIns = ClassFileHelper.previousRealInstruction(instructions, valueTypeIns);
                final Instruction fieldNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldDescIns);
                final Instruction ownerNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldNameIns);

                // TODO Check that the expected type matches the described field type.
                final ConstantDesc valueType = __expectTypeConstLoad(
                        valueTypeIns, "DynamicContext", methodName, "type");

                // Get the field value. Box primitive values (based on
                // the field type, not the expected type) -- we remove
                // unnecessary boxing later.
                //
                final String fieldDesc = __expectStringConstLoad (
                        fieldDescIns, "DynamicContext", methodName, "fieldDesc");
                final String fieldName = __expectStringConstLoad (
                        fieldNameIns, "DynamicContext", methodName, "fieldName");
                final String ownerName = __expectStringConstLoad (
                        ownerNameIns, "DynamicContext", methodName, "ownerName");

                ClassFileHelper.insertBefore(
                        afterInvoke,
                        ClassFileHelper.getField(ownerName,fieldName, fieldDesc),
                        instructions);
                __boxIfPrimitiveBefore(TypeKind.fromDescriptor(fieldDesc), afterInvoke, instructions);

                // Remove the invocation sequence.
                // BUT, keep the owner reference load instruction.
                final Instruction ownerLoadIns = ClassFileHelper.previousRealInstruction(instructions, ownerNameIns);
                final Instruction ifaceLoadIns = ClassFileHelper.previousRealInstruction(instructions, ownerLoadIns);

                __removeInstruction(Opcode.ALOAD, ifaceLoadIns, instructions, "rewriteDynamicContextCalls7");
                instructions.remove(ownerNameIns);
                instructions.remove(fieldNameIns);
                instructions.remove(fieldDescIns);
                instructions.remove(valueTypeIns);
                instructions.remove(invokeInstruction);
                __removeIfCheckCast(afterInvoke, instructions);
            } else if (
                    "getStaticFieldValue".equals (methodName)
                            && methodType.parameterCount() == 3
            ) {
                //               ALOAD (interface reference)
                //               LDC/GETSTATIC (owner type)
                //               LDC (field name String)
                //               LDC/GETSTATIC (field type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction fieldTypeIns = ClassFileHelper.previousRealInstruction(instructions, invokeInstruction);
                final Instruction fieldNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldTypeIns);
                final Instruction ownerTypeIns = ClassFileHelper.previousRealInstruction(instructions, fieldNameIns);

                final ConstantDesc ownerType = __expectTypeConstLoad(
                        ownerTypeIns, "DynamicContext", methodName, "ownerType");
                final String fieldName = __expectStringConstLoad(
                        fieldNameIns, "DynamicContext", methodName, "fieldName");
                final ConstantDesc fieldType = __expectTypeConstLoad(
                        fieldTypeIns, "DynamicContext", methodName, "fieldType"
                );
                final ClassDesc realFieldDesc;
                final ClassDesc realOwnerType;
                try {
                    realFieldDesc = ClassFileHelper.resolveConstantDesc(fieldType);
                    realOwnerType = ClassFileHelper.resolveConstantDesc(ownerType);
                } catch (Exception e) {
                    throw new InvalidContextUsageException (
                            "%s: Could not resolve ConstantDesc %s or %s when accessing stack",
                            __location (snippet, invokeInstruction), fieldType, ownerType
                    );
                }
                // Get the static field value. Box primitive values (based on
                // the field type, not the expected type) -- we remove
                // unnecessary boxing later.
                ClassFileHelper.insertBefore(
                        afterInvoke,
                        ClassFileHelper.getStatic(realOwnerType.descriptorString(), fieldName, realFieldDesc.descriptorString()),
                        instructions);
                __boxIfPrimitiveBefore(TypeKind.fromDescriptor(realFieldDesc.descriptorString()), afterInvoke, instructions);
                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, ownerTypeIns),
                        instructions, "rewriteDynamicContextCalls8");
                instructions.remove(ownerTypeIns);
                instructions.remove(fieldNameIns);
                instructions.remove(fieldTypeIns);
                instructions.remove(invokeInstruction);
                __removeIfCheckCast(afterInvoke, instructions);
            } else if (
                    "getStaticFieldValue".equals (methodName)
                            && methodType.parameterCount() == 4
            ) {
                //               ALOAD (interface reference)
                //               LDC (owner name String)
                //               LDC (field name String)
                //               LDC (field descriptor String)
                //               LDC/GETSTATIC (expected value type)
                // invokeInsn => INVOKEINTERFACE
                final Instruction valueTypeIns = ClassFileHelper.previousRealInstruction(instructions, invokeInstruction);
                final Instruction fieldDescIns = ClassFileHelper.previousRealInstruction(instructions, valueTypeIns);
                final Instruction fieldNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldDescIns);
                final Instruction ownerNameIns = ClassFileHelper.previousRealInstruction(instructions, fieldNameIns);

                // TODO Check that the expected type matches the described field type.
                final ConstantDesc valueType = __expectTypeConstLoad (
                        valueTypeIns, "DynamicContext", methodName, "valueType"
                );
                // Get the static field value. Box primitive values (based on
                // the field type, not the expected type) -- we remove
                // unnecessary boxing later.
                final String fieldDesc = __expectStringConstLoad (
                        fieldDescIns, "DynamicContext", methodName, "fieldDesc");
                final String fieldName = __expectStringConstLoad (
                        fieldNameIns, "DynamicContext", methodName, "fieldName");
                final String ownerName = __expectStringConstLoad (
                        ownerNameIns, "DynamicContext", methodName, "ownerName");

                ClassFileHelper.insertBefore(afterInvoke,
                        ClassFileHelper.getStatic(ownerName, fieldName, fieldDesc),
                        instructions);
                __boxIfPrimitiveBefore(TypeKind.fromDescriptor(fieldDesc), afterInvoke, instructions);
                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, ownerNameIns),
                        instructions, "rewriteDynamicContextCalls9");
                instructions.remove(ownerNameIns);
                instructions.remove(fieldNameIns);
                instructions.remove(fieldDescIns);
                instructions.remove(valueTypeIns);
                instructions.remove(invokeInstruction);

                __removeIfCheckCast(afterInvoke, instructions);
            } else {
                throw new DiSLFatalException (
                        "%s: unsupported DynamicContext method %s%s",
                        __location (snippet, invokeInstruction), methodName, methodType
                );
            }
        }

        __removeUnecessaryBoxing(instructions);
        // In a throwing context, store the exception being thrown into a local
        // variable at the beginning, and re-throw the exception at the end.
        if (throwing) {
            instructions.addFirst(ClassFileHelper.storeObjectVar(exceptionSlot));
            instructions.add(ClassFileHelper.loadObjectVar(exceptionSlot));
            instructions.add(ThrowInstruction.of());
        }
    }


    private void __boxIfPrimitiveBefore(
            final TypeKind typeKind, final CodeElement location, final List<CodeElement> instructions
    ) {
        if (!typeKind.equals(TypeKind.REFERENCE)) {
            ClassFileHelper.insertBefore(
                    location,
                    ClassFileHelper.boxValueOnStack(typeKind),
                    instructions
            );
        }
    }


    /**
     * Removes CHECKCAST instruction that may be used by the compiler before
     * storing the return value from a method with a generic return type. The
     * instruction leaves its argument on the stack, so there is no need to
     * adjust the stack.
     */
    private static void __removeIfCheckCast(
            final CodeElement element, final List<CodeElement> instructions
    ) {
        final Instruction checkCastInstruction = ClassFileHelper.firstNextRealInstruction(
                instructions, element);
        if (checkCastInstruction.opcode().equals(Opcode.CHECKCAST)) {
            instructions.remove(checkCastInstruction);
        }
    }


    /**
     * Removes unnecessary boxing and un-boxing operations. These are
     * invocations of the static <i>boxType</i>.valueOf() method immediately
     * followed by an invocation of the virtual <i>primitiveType</i>Value()
     * method, with both invocations having the same owner class, and with the
     * argument type of the valueOf() method matching the return type of the
     * <i>primitiveType</i>Value() method.
     */
    private void __removeUnecessaryBoxing(final List<CodeElement> instructions) {
        // Iterate over a copy -- we will be modifying the underlying list.
        for (CodeElement head: instructions.toArray(new CodeElement[0])) {
            if (!(head instanceof InvokeInstruction toValueIns)) {
                continue;
            }

            // Check for INVOKEVIRTUAL (head) following INVOKESTATIC (tail).
            if (!(toValueIns.opcode() == Opcode.INVOKEVIRTUAL)) {
                continue;
            }
            final Instruction tail = ClassFileHelper.previousRealInstruction(
                    instructions, head
            );
            if (!(tail instanceof InvokeInstruction valueOfIns)) {
                continue;
            }
            if (!(valueOfIns.opcode() == Opcode.INVOKESTATIC)) {
                continue;
            }

            // Check method names.
            if (!toValueIns.name().stringValue().endsWith("Value")) {
                continue;
            }

            if (!valueOfIns.name().stringValue().endsWith("valueOf")) {
                continue;
            }

            // Check that the valueOf() invocation is done on a class
            // corresponding to a primitive type and that both methods
            // have the same owner class.
            if (!valueOfIns.owner().asSymbol().isPrimitive()) {
                continue;
            }
            if (!valueOfIns.owner().asSymbol().equals(toValueIns.owner().asSymbol())) {
                continue;
            }

            // Check that the argument of the valueOf() method matches
            // the return type of the <targetType>Value() method.
            final ClassDesc valueOfArgType = valueOfIns.typeSymbol().parameterType(0);
            final ClassDesc toValueRetType = toValueIns.typeSymbol().returnType();

            if (!valueOfArgType.equals(toValueRetType)) {
                continue;
            }

            // The match is complete -- remove the pair of invocations.
            instructions.remove(valueOfIns);
            instructions.remove(toValueIns);
        }
    }


    // Fix the stack operand index of each stack-based instruction
    // according to the maximum number of locals in the target method node.
    // NOTE that the field maxLocals of the method node will be automatically
    // updated.
    private int fixLocalIndex(final int currentMax, final List<CodeElement> instructions) {
        __shiftLocalSlots(currentMax, instructions);
        return __calcMaxLocals(currentMax, instructions);
    }


    // TODO I also made a similar function in ClassFileHelper, maybe I can use only one
    //  the one in ClassFileHelper (getMaxLocals) also take in consideration if the method is
    //  static or not
    private int __calcMaxLocals(final int initial, final List<CodeElement> instructions) {
        int result = initial;
        for (CodeElement element: instructions) {
            switch (element) {
                case LoadInstruction loadInstruction ->
                        result = switch (loadInstruction.opcode()) {
                    case Opcode.LLOAD, Opcode.LLOAD_0, Opcode.LLOAD_1, Opcode.LLOAD_2, Opcode.LLOAD_3,
                         Opcode.LLOAD_W, Opcode.DLOAD, Opcode.DLOAD_0, Opcode.DLOAD_1, Opcode.DLOAD_2,
                         Opcode.DLOAD_3, Opcode.DLOAD_W -> Math.max(loadInstruction.slot() + 2, result);
                    default -> Math.max(loadInstruction.slot() + 1, result);
                };
                case StoreInstruction storeInstruction ->
                        result = switch (storeInstruction.opcode()) {
                    case Opcode.LSTORE, Opcode.LSTORE_0, Opcode.LSTORE_1, Opcode.LSTORE_2, Opcode.LSTORE_3,
                         Opcode.LSTORE_W, Opcode.DSTORE, Opcode.DSTORE_0, Opcode.DSTORE_1, Opcode.DSTORE_2,
                         Opcode.DSTORE_3, Opcode.DSTORE_W -> Math.max(storeInstruction.slot() + 2, result);
                    default -> Math.max(storeInstruction.slot() + 1, result);
                };
                case IncrementInstruction incrementInstruction -> result = Math.max(incrementInstruction.slot() + 1, result);
                default -> {}
            }
        }

        return result;
    }

    private void __shiftLocalSlots(final int amount, final List<CodeElement> instructions) {
        for (int index = 0; index < instructions.size(); index++) {
            switch (instructions.get(index)) {
                case LoadInstruction loadInstruction -> instructions.set(
                        index,
                        LoadInstruction.of(loadInstruction.typeKind(), loadInstruction.slot() + amount)
                );
                case StoreInstruction storeInstruction -> instructions.set(
                        index,
                        StoreInstruction.of(storeInstruction.typeKind(), storeInstruction.slot() + amount)
                );
                case IncrementInstruction incrementInstruction -> instructions.set(
                        index,
                        IncrementInstruction.of(incrementInstruction.slot() + amount, incrementInstruction.constant())
                );
                default -> {}
            }
        }
    }

    //

    private static final String __ARGUMENT_CONTEXT_INTERNAL_NAME__ =
        ArgumentContext.class.getName().replace('.', '/');


    /**
     * Replaces instruction sequences invoking the {@link ArgumentContext}
     * interface methods with context-specific constants.
     */
    private void rewriteArgumentContextCalls(int position, final int totalCount, TypeKind type,
                                             final List<CodeElement> instructions) {
        // Iterate over a copy -- we will be modifying the underlying list.
        for (final CodeElement element: instructions.toArray(new CodeElement[0])) {

            // Look for ArgumentContext interface method invocations.
            final InvokeInstruction invokeInstruction = __getInvokeInterfaceInstruction(
                    element, __ARGUMENT_CONTEXT_INTERNAL_NAME__
            );

            if (invokeInstruction == null) {
                continue;
            }

            // Handle individual methods.
            //
            // They are all parameter-less and invoked using the same byte-code
            // sequence:
            //
            //               ALOAD (ArgumentContext interface reference)
            // invokeInsn => INVOKEINTERFACE
            final String methodName = invokeInstruction.name().stringValue();
            switch (methodName) {
                case "getPosition" -> ClassFileHelper.insert(
                        element,
                        ClassFileHelper.loadConst(position),
                        instructions);
                case "getTotalCount" -> ClassFileHelper.insert(
                        element,
                        ClassFileHelper.loadConst(totalCount),
                        instructions);
                case "getTypeDescriptor" -> ClassFileHelper.insert(
                        element,
                        ClassFileHelper.loadConst(type.upperBound().descriptorString()),
                        instructions);
                default -> throw new DiSLFatalException (
                        "%s: unsupported ArgumentContext method %s()",
                        __location (snippet, invokeInstruction), methodName
                );
            }

            Instruction toRemove = ClassFileHelper.previousRealInstruction(instructions, invokeInstruction);
            __removeInstruction(Opcode.ALOAD, toRemove, instructions, "rewriteArgumentContextCalls");
            instructions.remove(invokeInstruction);
        }
    }


    // combine processors into an instruction list
    // NOTE that these processors are for the current method
    private List<CodeElement> procInMethod(final ProcInstance processor) throws InvalidContextUsageException {
        final List<CodeElement> result = new ArrayList<>();

        for (final ProcMethodInstance processorMethod: processor.getMethods()) {
            final Code code = processorMethod.getCode();

            final int position = processorMethod.getArgIndex();
            final int totalCount = processorMethod.getArgsCount();
            final TypeKind typekind = processorMethod.getKind().primaryType();
            final List<CodeElement> instructions = code.getInstructions();
            rewriteArgumentContextCalls(position, totalCount, typekind, instructions);

            instructions.add(ClassFileHelper.storeVar(typekind, 0));
            __shiftLocalSlots(snippetMaxLocals, instructions);
            snippetMaxLocals = __calcMaxLocals(snippetMaxLocals + typekind.slotSize(), instructions);
            instructions.addFirst(
                    ClassFileHelper.loadVar(
                            typekind,
                            ClassFileHelper.getParameterSlot(
                                    methodModel,
                                    processorMethod.getArgIndex()) - methodMaxLocals
                    )
            );

            result.addAll(instructions);
            exceptionCatches.addAll(code.getTryCatchBlocks());
            // TODO Since the classFile every element in the classFile is immutable we cannot add new try-catch block
            //  so the idea is to store them in a temporary list, in the end the class will all be re-computed by
            //  the ClassFile, but this list will be used in the PartialEvaluator

        }
        return result;
    }

    // combine processors into an instruction list
    // NOTE that these processors are for the callee
    private List<CodeElement> procAtCallSite(final ProcInstance processor) throws InvalidContextUsageException {

        final Frame<SourceValue> frame = info.getSourceFrame(weavingLocation);

        final List<CodeElement> result = new ArrayList<>();
        for (final ProcMethodInstance pmi: processor.getMethods()) {
            final Code code = pmi.getCode().clone();

            final int index = pmi.getArgIndex();
            final int total = pmi.getArgsCount();
            TypeKind type = pmi.getKind().primaryType(); // TODO LB: Why not get the type directly from pmi?
            final List<CodeElement> instructionsList = code.getInstructions();
            rewriteArgumentContextCalls(index, total, type, instructionsList);

            // Duplicate call site arguments and store them into local vars.
            final SourceValue sourceValue = ClassFileFrameHelper.getStackByIndex(frame, total -1 -index);
            for (final CodeElement argLoadIns: sourceValue.instructions) {
                // TODO the original version directly insert the instructions in the original method
                //  this is the original comment:
                // TRICK: the value has to be set properly because
                // method code will be not adjusted by fixLocalIndex
                ClassFileHelper.insert(
                        argLoadIns,
                        // TODO the original is (method.maxLocals + maxLocals), in this case I tried to replicate by keeping
                        //  two separate count for the max locals
                        ClassFileHelper.storeVar(type, methodMaxLocals + snippetMaxLocals),
                        methodInstructions);
                ClassFileHelper.insert(
                        argLoadIns,
                        StackInstruction.of(type.slotSize() == 2? Opcode.DUP2 : Opcode.DUP),
                        methodInstructions);
            }

            __shiftLocalSlots(snippetMaxLocals, instructionsList);
            snippetMaxLocals = __calcMaxLocals(snippetMaxLocals + type.slotSize(), instructionsList);
            result.addAll(instructionsList);
            // TODO the original also edit the method to add all the try catch blocks, but
            //  is not possible with the classFile, so I am adding them in a list to keep track of them
            exceptionCatches.addAll(code.getTryCatchBlocks());
        }

        return result;
    }

    // rewrite calls to ArgumentProcessorContext.apply()

    private void rewriteArgumentProcessorContextApplications(
            final PIResolver piResolver, final List<CodeElement> instructions
    ) throws InvalidContextUsageException {
        for (final int instructionIndex: code.getInvokedProcessors().keySet()) {

            final CodeElement element = instructionsArray[instructionIndex];

            final ProcInstance processor = piResolver.get(shadow, instructionIndex);
            if (processor != null) {
                if (processor.getMode() == ArgumentProcessorMode.METHOD_ARGS) {
                    ClassFileHelper.insertAll(element, procInMethod(processor), instructions);
                } else {
                    ClassFileHelper.insertAll(element, procAtCallSite(processor), instructions);
                }
            } else {
                // TODO remove this once tested!!!
                System.out.println("!!!!Warning: processor is null!!!!");
            }

            // Remove the invocation sequence.
            final int index = instructions.indexOf(element);
            List<CodeElement> elementsToRemove = new ArrayList<>();
            for (int i = index; i >= index - 3; i--) {
                elementsToRemove.add(instructions.get(i));
            }
            instructions.removeAll(elementsToRemove);
        }
    }


    private List<CodeElement> __createGetArgsCode(final String methodDescriptor,
                                                  final int firstSlot) {
        final List<CodeElement> result = new ArrayList<>();
        MethodTypeDesc methodTypeDesc = MethodTypeDesc.ofDescriptor(methodDescriptor);

        ClassDesc[] argTypes = methodTypeDesc.parameterArray();
        result.add(ClassFileHelper.loadConst(argTypes.length));
        result.add(NewReferenceArrayInstruction.of(
                ConstantPoolBuilder.of().classEntry(
                        ClassDesc.ofDescriptor(Object.class.descriptorString())
                )
        ));


        int argSlot = firstSlot;
        for (int argIndex = 0; argIndex < argTypes.length; argIndex++) {
            // Top of the stack contains the array reference.
            // Store method argument into an array:
            //
            // DUP
            // ICONST element index
            //      xLOAD from argument slot
            //      optional: box primitive-type values
            // AASTORE
            result.add(StackInstruction.of(Opcode.DUP));
            result.add(ClassFileHelper.loadConst(argIndex));

            final ClassDesc argType = argTypes[argIndex];
            TypeKind typeKind = TypeKind.fromDescriptor(argType.descriptorString());
            result.add(
                    ClassFileHelper.loadVar(
                            typeKind, argSlot
                    )
            );
            if (!ClassFileHelper.isReferenceType(typeKind)) {
                result.add(ClassFileHelper.boxValueOnStack(typeKind));
            }

            result.add(ArrayStoreInstruction.of(Opcode.AASTORE));
            // advance argument slot according to argument size
            argSlot += typeKind.slotSize();
        }


        return result;
    }

    //

    private static final String __ARGUMENT_PROCESSOR_CONTEXT_INTERNAL_NAME__ =
        ArgumentProcessorContext.class.getName().replace('.', '/');

    private void rewriteArgumentProcessorContextCalls(final List<CodeElement> instructions) throws InvalidContextUsageException{
        // Iterate over a copy -- we will be modifying the underlying list.
        for (final CodeElement element: instructions.toArray(new CodeElement[0])) {
            // Iterate over a copy -- we will be modifying the underlying list.
            InvokeInstruction invokeIns = __getInvokeInterfaceInstruction(
                    element, __ARGUMENT_PROCESSOR_CONTEXT_INTERNAL_NAME__);
            if (invokeIns == null) {
                continue;
            }
            // Handle individual methods.
            final String methodName = invokeIns.name().stringValue();
            final boolean isStatic = invokeIns.opcode() == Opcode.INVOKESTATIC;

            if ("getArgs".equals(methodName)) {
                //               ALOAD (ArgumentProcessorContext reference)
                //               GETSTATIC (ArgumentProcessorMode enum reference)
                // invokeInsn => INVOKEINTERFACE
                final Instruction enumLoadIns = ClassFileHelper.previousRealInstruction(instructions, invokeIns);
                final ArgumentProcessorMode mode = __expectEnumConstLoad(
                        ArgumentProcessorMode.class, enumLoadIns,
                        "ArgumentProcessorContext", methodName, "mode");

                List<CodeElement> getArgIns = null;
                if (mode == ArgumentProcessorMode.METHOD_ARGS) {
                    final int thisOffset = isStatic ? 0 : 1;
                    final int firstSlot = thisOffset - methodMaxLocals;
                    getArgIns = __createGetArgsCode(methodModel.methodTypeSymbol().descriptorString(), firstSlot);
                } else if (mode == ArgumentProcessorMode.CALLSITE_ARGS) {
                    final Instruction calleeIns = ClassFileHelper.firstNextRealInstruction(instructions, shadow.getRegionStart());
                    if (!(calleeIns instanceof InvokeInstruction callee)) {
                        throw new DiSLFatalException (
                                "%s: unexpected bytecode at call site in %s.%s() "+
                                        "when applying ArgumentProcessorContext.getArgs() ",
                                __location (snippet, invokeIns),
                                JavaNames.internalToType (shadow.getClassModel().thisClass().asInternalName()),
                                shadow.getMethodModel().methodName().stringValue()
                        );
                    }

                    final MethodTypeDesc calleeDesc = callee.typeSymbol();
                    final ClassDesc[] argTypes = calleeDesc.parameterArray();

                    final Frame<SourceValue> frame = info.getSourceFrame(calleeIns);
                    if (frame == null) {
                        throw new DiSLFatalException (
                                "%s: failed to obtain source frame at call site in %s.%s() "+
                                        "when applying ArgumentProcessorContext.getArgs() ",
                                __location (snippet, invokeIns),
                                JavaNames.internalToType (shadow.getClassModel().thisClass().asInternalName()),
                                shadow.getMethodModel().methodName().stringValue()
                        );
                    }

                    int argSlot = 0;
                    for (int argIndex = 0; argIndex < argTypes.length; argIndex++) {
                        final SourceValue source = ClassFileFrameHelper.getStackByIndex(frame, argTypes.length - 1 - argIndex);
                        final ClassDesc argType = argTypes[argIndex];

                        for (final CodeElement itr: source.instructions) {
                            // TODO this is the original comment, is it still valid for the classFile?
                            // TRICK: the value has to be set properly because
                            // method code will be not adjusted by fixLocalIndex
                            ClassFileHelper.insert(
                                    itr,
                                    ClassFileHelper.storeVar(
                                            TypeKind.from(argType),
                                            methodMaxLocals + snippetMaxLocals + argSlot // the original: method.maxLocals + maxLocals + argSlot
                                    ),
                                    methodInstructions);
                            ClassFileHelper.insert(
                                    itr,
                                    StackInstruction.of(TypeKind.from(argType).slotSize() == 2? Opcode.DUP2 : Opcode.DUP),
                                    methodInstructions);
                        }

                        argSlot += TypeKind.from(argType).slotSize();
                    }
                    getArgIns = __createGetArgsCode(calleeDesc.descriptorString(), snippetMaxLocals);
                    snippetMaxLocals = __calcMaxLocals(snippetMaxLocals + argSlot, getArgIns);

                } else {
                    throw new DiSLFatalException (
                            "%s: unsupported argument processor mode: %s",
                            __location (snippet, invokeIns), mode
                    );
                }

                // Insert getArgs() code and remove the invocation sequence.
                ClassFileHelper.insertAll(invokeIns, getArgIns, instructions);

                __removeInstruction(Opcode.ALOAD, ClassFileHelper.previousRealInstruction(instructions, enumLoadIns), instructions, "rewriteArgumentProcessorContextCalls1");
                instructions.remove(enumLoadIns);
                instructions.remove(invokeIns);

            } else if ("getReceiver".equals(methodName)) {
                //               ALOAD (ArgumentProcessorContext reference)
                //               GETSTATIC (ArgumentProcessorMode enum reference)
                // invokeInsn => INVOKEINTERFACE
                final Instruction enumLoadIns = ClassFileHelper.previousRealInstruction(instructions, invokeIns);
                final ArgumentProcessorMode mode = __expectEnumConstLoad (
                        ArgumentProcessorMode.class, enumLoadIns,
                        "ArgumentProcessorContext", methodName, "mode"
                );

                if (mode == ArgumentProcessorMode.METHOD_ARGS) {
                    // Return null as receiver for static methods.
                    ClassFileHelper.insert(invokeIns,
                            isStatic ? ClassFileHelper.loadNull() :
                                    // originally it was passed -methodMaxLocals
                                    ClassFileHelper.loadThis(),
                            instructions);
                } else if (mode == ArgumentProcessorMode.CALLSITE_ARGS) {
                    final Instruction callee = ClassFileHelper.firstNextRealInstruction(this.methodInstructions, shadow.getRegionStart());
                    // after testing, I determined that the RegionStart element is contained in the method instructions (in the original since it was a linked
                    // list I wrongly assumed that it was instead contained in the "instruction" parameter of this method)
                    if (!(callee instanceof InvokeInstruction calleeInvoke)) {
                        throw new DiSLFatalException("In snippet "
                                + snippet.getOriginClassName() + "."
                                + snippet.getOriginMethodName()
                                + " - unexpected bytecode when applying"
                                + " \"ArgumentProcessorContext.getReceiver\""
                                + " expected an InvokeInstruction, got instead: "
                                + (callee == null? "null": callee.toString())
                        );
                    }
                    final Frame<SourceValue> frame = info.getSourceFrame(callee);

                    if (frame == null) {
                        throw new DiSLFatalException("In snippet "
                                + snippet.getOriginClassName() + "."
                                + snippet.getOriginMethodName()
                                + " - unexpected bytecode when applying"
                                + " \"ArgumentProcessorContext.getReceiver\"");
                    }

                    if (invokeIns.opcode() == Opcode.INVOKESTATIC) {
                        // Return null receiver for static method invocations.
                        ClassFileHelper.insert(element, ClassFileHelper.loadNull(), instructions);
                    } else {
                        final MethodTypeDesc methodTypeDesc = calleeInvoke.typeSymbol();
                        final SourceValue source = ClassFileFrameHelper.getStackByIndex(
                                frame, methodTypeDesc.parameterCount());

                        for (final CodeElement itr: source.instructions) {
                            // TODO original comment:
                            // TRICK: the slot has to be set properly because
                            // method code will be not adjusted by fixLocalIndex
                            ClassFileHelper.insert(
                                    itr,
                                    ClassFileHelper.storeObjectVar(methodMaxLocals + snippetMaxLocals), // original: method.maxLocals + maxLocals
                                    methodInstructions);
                            ClassFileHelper.insert(
                                    itr,
                                    StackInstruction.of(Opcode.DUP),
                                    methodInstructions);
                        }

                        ClassFileHelper.insert(element, ClassFileHelper.loadObjectVar(snippetMaxLocals), instructions);
                        snippetMaxLocals++;
                    }

                } else {
                    throw new DiSLFatalException ("unsupported mode: %s", mode);
                }

                // Remove the invocation sequence.
                __removeInstruction(Opcode.ALOAD,
                        ClassFileHelper.previousRealInstruction(instructions, enumLoadIns),
                        instructions, "rewriteArgumentProcessorContextCalls2");
                instructions.remove(enumLoadIns);
                instructions.remove(invokeIns);


            } else {
                throw new DiSLFatalException (
                        "%s: unsupported ArgumentProcessorContext method %s()",
                        __location (snippet, invokeIns), methodName
                );
            }
        }
    }


    public List<CodeElement> getInstrumentedSnippetInstructions() {
        return snippetInstructions;
    }

    public List<CodeElement> getInstrumentedMethodInstructions() {
        return methodInstructions;
    }

    public List<ExceptionCatch> getExceptionCatches() {
        return exceptionCatches;
    }

    public int getMethodMaxLocals() {
        return methodMaxLocals;
    }

    public int getSnippetMaxLocals() {
        return snippetMaxLocals;
    }

    public List <ExceptionCatch> getTCBs () {
        return code.getTryCatchBlocks ();
    }


    public void transform(
            final SCGenerator staticInfo, final PIResolver piResolver, final boolean throwing
    ) throws InvalidContextUsageException, AnalyzerException {
        rewriteArgumentProcessorContextApplications(piResolver, snippetInstructions);
        rewriteArgumentProcessorContextCalls(snippetInstructions);
        rewriteStaticContextCalls(staticInfo, snippetInstructions);
        rewriteClassContextCalls(snippetInstructions);

        methodMaxLocals = fixLocalIndex(methodMaxLocals, snippetInstructions);

        optimize(snippetInstructions);
        rewriteDynamicContextCalls(throwing, snippetInstructions);
    }


    private void optimize(final List<CodeElement> instructions) throws AnalyzerException {
        final String peConfig = System.getProperty (PROP_PE, "");
        if ((peConfig.length() < 2) || (!peConfig.toUpperCase ().startsWith("O"))) {
            return;
        }
        final char option = peConfig.charAt(1);

        final PartialEvaluator pe = new PartialEvaluator(
                instructions, exceptionCatches, methodModel.methodTypeSymbol(), methodModel.flags());


        if (option >= '1' && option <= '3')  {
            for (int i = 0; i < (option - '0'); i++) {
                if (!pe.evaluate()) {
                    break;
                }
            }
        } else if (option == 'x') {
            while (true) {
                if (!pe.evaluate()) {
                    break;
                }
            }
        }
    }


    private <T extends Enum<T>> T __expectEnumConstLoad(
            final Class<T> enumType, final CodeElement codeElement, final String iFace,
            final String method, final String param) throws InvalidContextUsageException {
            final T result = ClassFileHelper.getEnumConstOperand(enumType, codeElement);
            __ensureOperandNotNull(result, codeElement, iFace, method, param, enumType.getSimpleName());
            return result;
    }


    private int __expectIntConstLoad(final CodeElement codeElement, final String iFace,
                                     final String method, final String param) throws InvalidContextUsageException {
        final Integer result = ClassFileHelper.getIntConstantOperand(codeElement);
        __ensureOperandNotNull(result, codeElement, iFace, method, param, "integer");
        return result;
    }

    private String __expectStringConstLoad(final CodeElement codeElement, final String iFace,
                                           final String method, final String param) throws InvalidContextUsageException {
        final String result = ClassFileHelper.getStringConstOperand(codeElement);
        // TODO in the test dynamiccontext "result" is null when "getStaticFieldValue" is invoked at line 20 of the snippet, I suspect this is due to
        //  the method being invoked with FieldAccessStaticContext variables instead of constants
        __ensureOperandNotNull(result, codeElement, iFace, method, param, "String");
        return result;
    }

    private ConstantDesc __expectTypeConstLoad(final CodeElement codeElement, final String iFace,
                                               final String method, final String param) throws InvalidContextUsageException {
        final ConstantDesc result = ClassFileHelper.getTypeConstOperand(codeElement);
        __ensureOperandNotNull(result, codeElement, iFace, method, param, "Class");
        return result;
    }

    private void __ensureOperandNotNull(
            final Object operand, final CodeElement codeElement, final String iFace,
            final String method, final String param, final String kind
    ) throws InvalidContextUsageException {
        if (operand == null) {
            throw new InvalidContextUsageException (
                    "%s: the '%s' argument of %s.%s() MUST be a %s literal",
                    __location (snippet, codeElement), param, iFace, method, kind
            );
        }
    }

    //

    /**
     * Returns a {@link InvokeInstruction} instance if the given instruction
     * invokes the specified interface, {@code null} otherwise. The interface
     * name needs to be in JVM internal representation.
     */
    private static InvokeInstruction __getInvokeInterfaceInstruction(final CodeElement codeElement, final String internalIfName) {
        if (codeElement instanceof InvokeInstruction invokeInstruction) {
                                                                                                // TODO is asInternalName the correct name to check???
            if (invokeInstruction.opcode() == Opcode.INVOKEINTERFACE && internalIfName.equals(invokeInstruction.owner().asInternalName())) {
                return invokeInstruction;
            }
        }

        return null;
    }


    /**
     * Removes a matching instruction from the instruction list, otherwise
     * throws a {@link DiSLFatalException}.
     */
    private static void __removeInstruction(final Opcode expected, final CodeElement element, final List<CodeElement> instructions, String from) {
        char initialLetter = expected.name().charAt(0);
        if (element instanceof Instruction instruction
                && instruction.opcode().kind().equals(expected.kind())
                && instruction.opcode().name().startsWith(String.valueOf(initialLetter))
        ) {
            instructions.remove(instruction);
        } else {
            String additionalInfo = "no additional info.";
            if (element instanceof Instruction instruction) {
                additionalInfo = "instruction kind = " + instruction.opcode().kind() + "\n";
                additionalInfo += "expected kind = " + expected.kind() + "\n";
                additionalInfo += "initialLetter = " + initialLetter + "\n";
                additionalInfo += "origin error: " + from;
            }
            throw new DiSLFatalException (
                    "refusing to remove instruction: expected %s, found %s | additional info: %s\n",
                    expected.name(), element.toString(), additionalInfo
            );
        }

    }

    private static String __location(final Snippet snippet, final CodeElement codeElement) {
        return String.format (
                "snippet %s.%s%s",
                snippet.getOriginClassName(), snippet.getOriginMethodName(),
                ClassFileHelper.formatLineNo(" at line %d ", codeElement, snippet.getCode().getInstructions())
        );
    }
}
