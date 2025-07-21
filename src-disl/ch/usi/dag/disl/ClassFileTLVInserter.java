package ch.usi.dag.disl;

import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.ReflectionHelper;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class ClassFileTLVInserter {

    private static final ClassDesc __threadType__ = ClassDesc.ofDescriptor(Thread.class.descriptorString());
    private static final String __currentThreadName__ = "currentThread";
    private static final Method currentThreadMethod = ReflectionHelper.getMethod (Thread.class, __currentThreadName__);
    private static final MethodTypeDesc __currentThreadType__ = ClassFileHelper.getMethodDescriptor(currentThreadMethod);


    public static void insertThreadLocalVariables(final Set<ThreadLocalVar> threadLocals,
                                                  ClassBuilder classBuilder,
                                                  final MethodModel methodInitializer) {

        classBuilder.withMethod(methodInitializer.methodName(), methodInitializer.methodType(), methodInitializer.flags().flagsMask(), methodBuilder -> {
            for (MethodElement methodElement: methodInitializer) {
                if (methodElement instanceof CodeModel codeModel) {
                    methodBuilder.withCode(codeBuilder -> {
                        // Insert initialization code for each thread local variable.
                        // The code for inheritable variables has the following structure:
                        //
                        //   if (Thread.currentThread() != null) {
                        //     this.value = Thread.currentThread().value;
                        //   } else {
                        //     this.value = <initialization code> | <type-specific default>
                        //   }
                        //
                        // The code for initialized variables has the following structure:
                        //     this.value = <initialization code> | <type-specific default>
                        //
                        // The <initialization code> could be an object constructor or array constructor or
                        // also a specific primitive
                        for (final ThreadLocalVar tlv: threadLocals) {

                            codeBuilder.aload(0);  // load "this" on the stack, for the final PUTFIELD.

                            final Label setValueLabel = codeBuilder.newLabel();

                            if (tlv.isInheritable()) {

                                // Get initial value from the current thread. If the
                                // current thread is invalid, use the type-specific
                                // default value.
                                final Label getDefaultValueLabel = codeBuilder.newLabel();

                                loadCurrentThread(codeBuilder);
                                codeBuilder.ifnull(getDefaultValueLabel);

                                loadCurrentThread(codeBuilder);
                                getThreadField(codeBuilder, tlv);
                                codeBuilder.goto_(setValueLabel);

                                codeBuilder.labelBinding(getDefaultValueLabel);
                            }

                            // Load the initialization code on the stack. If there is no
                            // predefined initialization is present, a type-specific default is used.
                            loadInitialValue(codeBuilder, tlv);

                            // Store the value into the corresponding field. This stores
                            // either the "inherited" or the predefined/default value.
                            codeBuilder.labelBinding(setValueLabel);
                            putThreadField(codeBuilder, tlv);
                        }

                        // add the original initializer code
                        for (CodeElement codeElement: codeModel) {
                            codeBuilder.with(codeElement);
                        }
                    });
                } else {
                    methodBuilder.with(methodElement);
                }
            }
        });
    }

    private static void loadCurrentThread(CodeBuilder codeBuilder) {
        codeBuilder.invokestatic(__threadType__, __currentThreadName__, __currentThreadType__);
    }

    private static void loadInitialValue(CodeBuilder codeBuilder, final ThreadLocalVar tlv) {
        final List<CodeElement> initializerInstructions = tlv.getInitializerInstructions();
        if (initializerInstructions.isEmpty()) {
            codeBuilder.with(
                    ClassFileHelper.loadDefault(tlv.getTypeKind())
            );
        } else {
            for (CodeElement element: initializerInstructions) {
                codeBuilder.with(element);
            }
        }
    }


    private static void getThreadField(CodeBuilder codeBuilder, final ThreadLocalVar tlv) {
        codeBuilder.getfield(__threadType__, tlv.getName(), tlv.getType());
    }

    private static void putThreadField(CodeBuilder codeBuilder, final ThreadLocalVar tlv) {
        codeBuilder.putfield(__threadType__, tlv.getName(), tlv.getType());
    }

}
