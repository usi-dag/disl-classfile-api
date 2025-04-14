package ch.usi.dag.disl.util;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class should contain all information that MethodModel has, but it keeps a copied version of the instructions,
 * this is because in the original version of the instructions some element might be repeated multiple times (they are represented by the same object in memory)
 * this cause problems when retrieving them. This class exist also to make it easier to pass around informations of the MethodModel
 */
public class MethodModelCopy {

    final MethodModel original;
    final Utf8Entry methodName;
    final Utf8Entry methodType;
    final MethodTypeDesc methodTypeSymbol;
    final AccessFlags flags;
    final Optional<ClassModel> parent;

    final boolean hasCode;
    final List<CodeElement> instructions;
    final List<ExceptionCatch> exceptionHandlers;

    final int maxStack;
    final int maxLocals;


    public MethodModelCopy(MethodModel methodModel) {
        this.original = methodModel;
        this.methodName = methodModel.methodName();
        this.methodType = methodModel.methodType();
        this.methodTypeSymbol = methodModel.methodTypeSymbol();
        this.flags = methodModel.flags();
        this.parent = methodModel.parent();

        if (methodModel.code().isEmpty()) {
            this.hasCode = false;
            this.instructions = new ArrayList<>();
            this.exceptionHandlers = new ArrayList<>();
            this.maxLocals = 0;
            this.maxStack = 0;
        } else {
            CodeModel codeModel = methodModel.code().get();
            this.hasCode = true;
            // TODO make it an immutable list if there is no need to change it (set the second param to true)
            this.instructions = ClassFileInstructionClone.copyList(codeModel.elementList(), false);
            this.exceptionHandlers = codeModel.exceptionHandlers();

            Optional<CodeAttribute> attribute = codeModel.findAttribute(Attributes.code());
            if (attribute.isPresent()) {
                CodeAttribute codeAttribute = attribute.get();
                this.maxStack = codeAttribute.maxStack();
                this.maxLocals = codeAttribute.maxLocals();
            } else {
                this.maxLocals = ClassFileHelper.getMaxLocals(this.instructions, this.methodTypeSymbol, this.flags);
                this.maxStack = 0; // TODO this should not be really useful, so there is no need to calculate it
            }
        }
    }

    public Utf8Entry methodName() {
        return methodName;
    }

    public Utf8Entry methodType() {
        return methodType;
    }

    public MethodTypeDesc methodTypeSymbol() {
        return methodTypeSymbol;
    }

    public AccessFlags flags() {
        return flags;
    }

    public Optional<ClassModel> parent() {
        return parent;
    }

    public boolean hasCode() {
        return hasCode;
    }

    public int maxStack() {
        return maxStack;
    }

    public int maxLocals() {
        return maxLocals;
    }

    public List<CodeElement> instructions() {
        return instructions;
    }

    public List<ExceptionCatch> exceptionHandlers() {
        return exceptionHandlers;
    }

    public MethodModel getOriginal() {
        return original;
    }
}
