package ch.usi.dag.disl.weaver;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.usi.dag.disl.CustomCodeElements.FutureLabelTarget;
import ch.usi.dag.disl.coderep.ClassFileCodeTransformer;
import ch.usi.dag.disl.util.ClassFileAnalyzer.AnalyzerException;
import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.annotation.After;
import ch.usi.dag.disl.annotation.AfterReturning;
import ch.usi.dag.disl.annotation.AfterThrowing;
import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.exception.InvalidContextUsageException;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.disl.processor.generator.PIResolver;
import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;
import ch.usi.dag.disl.snippet.Snippet;
import ch.usi.dag.disl.snippet.SnippetCode;
import ch.usi.dag.disl.staticcontext.generator.SCGenerator;
import ch.usi.dag.disl.util.MethodModelCopy;

// The weaver instruments byte-codes into java class.
public class Weaver {


    private static FutureLabelTarget getEndLabel(
            List<CodeElement> instructions, CodeElement instruction, CodeBuilder codeBuilder
    ) {
        if (ClassFileHelper.nextRealInstruction(instructions, instruction) != null) {
            final Label branchLabel = codeBuilder.newLabel();
            final FutureLabelTarget branch = new FutureLabelTarget(branchLabel);
            ClassFileHelper.insert(instruction, branch, instructions);

            final BranchInstruction jump = BranchInstruction.of(Opcode.GOTO, branchLabel);

            ClassFileHelper.insert(instruction, jump, instructions);
            instruction = jump;
        }

        final Label label = codeBuilder.newLabel();
        final FutureLabelTarget futureLabelTarget = new FutureLabelTarget(label);
        ClassFileHelper.insert(instruction, futureLabelTarget, instructions);
        return futureLabelTarget;
    }


    public static HandlerAndException getExceptionCatchBlock(
            List<CodeElement> instructions, List<ExceptionCatch> exceptionCatches,
            CodeElement start, CodeElement end, CodeBuilder codeBuilder
    ) {
        int newStartOffset = instructions.indexOf(start);
        final int newEndOffset = instructions.indexOf(end);
        final Map<Label, CodeElement> labelTargetMap = ClassFileHelper.getLabelTargetMap(instructions);


        for (final ExceptionCatch tcb: exceptionCatches) {
            final int startOffset = instructions.indexOf(labelTargetMap.get(tcb.tryStart()));
            final int endOffset = instructions.indexOf(labelTargetMap.get(tcb.tryEnd()));

            if (
                    ClassFileHelper.offsetBefore(instructions, newStartOffset, startOffset)
                    && ClassFileHelper.offsetBefore(instructions, startOffset, newEndOffset)
                    && ClassFileHelper.offsetBefore(instructions, newEndOffset, endOffset)
            ) {
                newStartOffset = startOffset;
            } else if (
                    ClassFileHelper.offsetBefore(instructions, startOffset, newStartOffset)
                    && ClassFileHelper.offsetBefore(instructions, newStartOffset, endOffset)
                    && ClassFileHelper.offsetBefore(instructions, endOffset, newEndOffset)
            ) {
                newStartOffset = endOffset;
            }
        }

        start = instructions.get(newStartOffset);
        end = instructions.get(newEndOffset);

        if (start instanceof FutureLabelTarget futureLabelTarget) {
            final FutureLabelTarget endLabelTarget = getEndLabel(instructions, end, codeBuilder);
            // futureLabelTarget.getLabel() could be null, to avoid null pointer exception here we can add a new label
            // TODO this might not be needed need to investigate once everything is working
            if (futureLabelTarget.getLabel() == null) {
                futureLabelTarget.setLabel(codeBuilder.newLabel());
            }
            ExceptionCatch exceptionCatch = ExceptionCatch.of(endLabelTarget.getLabel(), futureLabelTarget.getLabel(), endLabelTarget.getLabel());
            return new HandlerAndException(exceptionCatch, endLabelTarget);
        } else {
            final LabelTarget startLabelTarget = (LabelTarget) start;
            final FutureLabelTarget endLabelTarget = getEndLabel(instructions, end, codeBuilder);

            ExceptionCatch exceptionCatch = ExceptionCatch.of(endLabelTarget.getLabel(), startLabelTarget.label(), endLabelTarget.getLabel());
            return new HandlerAndException(exceptionCatch, endLabelTarget);
        }
    }

    public record HandlerAndException(ExceptionCatch exceptionCatch, FutureLabelTarget futureLabelTarget){}

    // this is helper record class
    public record InstrumentedResult(List<CodeElement> instrumentedSnippetInstructions,
                                     List<CodeElement> instrumentedMethodInstructions,
                                     List<ExceptionCatch> exceptionCatches,
                                     int maxLocals) { }


    private static InstrumentedResult __insert(
            final MethodModelCopy methodModel,
            final SCGenerator staticInfoHolder, final PIResolver piResolver,
            final WeavingInfo info, final Snippet snippet, final SnippetCode code,
            final Shadow shadow, final CodeElement loc, final List<CodeElement> instructionsToInstrument,
            List<ExceptionCatch> exceptionCatches, int methodMaxLocals, CodeBuilder codeBuilder
    ) throws InvalidContextUsageException {

        int newMethodMaxLocals = methodMaxLocals;

        // exception handler will discard the stack and push the
        // exception object. Thus, before entering this snippet,
        // weaver must back up the stack and restore when exiting
        if (code.containsHandledException() && info.stackNotEmpty(loc)) {
            final List<StoreInstruction> backup = info.backupStack(loc, methodMaxLocals);
            final List<LoadInstruction> restore = info.restoreStack(loc, methodMaxLocals);

            newMethodMaxLocals = info.getStackHeight(loc);

            ClassFileHelper.insertAllBefore(loc, backup, instructionsToInstrument);
            ClassFileHelper.insertAll(loc, restore, instructionsToInstrument);
        }

        final WeavingCode wc = new WeavingCode(
                info, methodModel, code, snippet, shadow, loc, instructionsToInstrument, exceptionCatches , newMethodMaxLocals
        );

        try {
            wc.transform(staticInfoHolder, piResolver, false);

            List<CodeElement> instrumentedSnippet = wc.getInstrumentedSnippetInstructions();
            List<ExceptionCatch> instrumentedExceptions = wc.getExceptionCatches();
            List<CodeElement> instrumentedMethodInstructions = wc.getInstrumentedMethodInstructions();
            newMethodMaxLocals = wc.getMethodMaxLocals();

            // Inserting a snippet more than once in the code can causes problems due to the labels,
            // this happens because after a snippet is inserted a second time we also have TargetLabels that
            // are duplicated and this will confuse the ClassFile when the labels are being bound.
            List<CodeElement> alteredLabels = ClassFileHelper.replaceBranchAndLabelsTarget(instrumentedSnippet, codeBuilder);
            ClassFileHelper.insertAll(loc, alteredLabels, instrumentedMethodInstructions);

            return new InstrumentedResult(alteredLabels, instrumentedMethodInstructions, instrumentedExceptions, newMethodMaxLocals);

        } catch (AnalyzerException ex) {
            throw new InvalidContextUsageException("Exception while transforming: " + ex.getMessage());
        }
    }


    public static void instrument(
            ClassModel classModel,
            MethodModelCopy methodModel,
            ClassBuilder classBuilder,
            final Map <Snippet, List <Shadow>> snippetShadows,
            final Set <SyntheticLocalVar> syntheticLocalVars,
            final Set <ThreadLocalVar> threadLocalVars,
            final SCGenerator staticInfoHolder, final PIResolver piResolver
    ) throws InvalidContextUsageException {
        // TODO I can instrument the method by adding the same method with the classBuilder, but with different code (insert the snippet inside)
        //  I could use: classBuilder.withMethodBody(...)   or   classBuilder.transformMethod(...)   need to check which is more convenient


        if (!methodModel.hasCode()) {
            // if the method is empty we just pass it as it is
            // TODO should throw instead???
            classBuilder.with(methodModel.getOriginal());
            return;
        }

        // first methodTransform to apply
        MethodTransform instrumentCode = instrumentCode(classModel, methodModel, snippetShadows, staticInfoHolder, piResolver);

        // second transform to apply
        MethodTransform static2Local = ClassFileCodeTransformer.static2Local(syntheticLocalVars, methodModel.methodTypeSymbol(), methodModel.flags());

        // third transform to apply, also need to test both versions
        MethodTransform rewriteThreadLocalVarAccessesCodeTransformer = ClassFileCodeTransformer.rewriteThreadLocalVarAccessesCodeTransformer2(
                threadLocalVars);

        classBuilder.transformMethod(
                methodModel.getOriginal(),
                instrumentCode.andThen(static2Local).andThen(rewriteThreadLocalVarAccessesCodeTransformer)
        );


        // TODO maybe "ValidatingTcbSorter.sortTcbs (methodNode)" is not required, a simple validation might suffice
        //  since the classfile should build all the info and throw errors if there are problems
    }


    private static MethodTransform instrumentCode(
            ClassModel classModel,
            MethodModelCopy methodModel,
            final Map <Snippet, List <Shadow>> snippetShadows,
            final SCGenerator staticInfoHolder, final PIResolver piResolver
    ) {
        // TODO I can instrument the method by adding the same method with the classBuilder, but with different code (insert the snippet inside)
        //  I could use: classBuilder.withMethodBody(...)   or   classBuilder.transformMethod(...)   need to check which is more convenient


        if (!methodModel.hasCode()) {
            // if the method is empty we just pass it as it is since there is nothing to instrument
            return ClassFileBuilder::with;
        } else {
            return (methodBuilder, methodElement) -> {
                if (methodElement instanceof CodeModel) {
                    methodBuilder.withCode(codeBuilder -> {

                        try {
                            // since we do not want to edit the original list of instruction we copy all element in a new one
                            List<CodeElement> codeToInstrument = new ArrayList<>(methodModel.instructions());
                            List<ExceptionCatch> exceptions = methodModel.exceptionHandlers();
                            int maxLocals = methodModel.maxLocals();

                            final WeavingInfo info = new WeavingInfo(classModel, methodModel, snippetShadows, codeToInstrument, exceptions);
                            List<ExceptionCatch> exceptionsToAdd = new ArrayList<>();

                            for (final Snippet snippet : info.getSortedSnippets()) {
                                final List<Shadow> shadows = snippetShadows.get(snippet);
                                final SnippetCode code = snippet.getCode();

                                // skip snippet with empty code
                                if (code == null) {
                                    continue;
                                }
                                // Instrument
                                // TODO LB: Extract transformations for individual annotation types.

                                // For @Before snippets, insert the snippet code just before the region entry.
                                if (snippet.hasAnnotation(Before.class)) {
                                    int phaseMaxLocals = maxLocals;
                                    for (final Shadow shadow : shadows) {
                                        final CodeElement loc = shadow.getWeavingRegion().getStart();
                                        InstrumentedResult result = __insert(
                                                methodModel, staticInfoHolder, piResolver, info, snippet, code, shadow,
                                                loc, codeToInstrument, exceptions, maxLocals, codeBuilder);

                                        codeToInstrument = result.instrumentedMethodInstructions;
                                        exceptions = result.exceptionCatches;
                                        // Reset method max locals after each snippet, but keep
                                        // track of the max locals for all snippets in this phase.
                                        phaseMaxLocals = Math.max(result.maxLocals, phaseMaxLocals);
                                    }
                                    // Set max locals to the max level reached in this phase.
                                    maxLocals = phaseMaxLocals;

                                }

                                // For regular after (after returning), insert the snippet after each adjusted exit of a region.
                                if (snippet.hasAnnotation(AfterReturning.class) || snippet.hasAnnotation(After.class)) {
                                    int phaseMaxLocals = maxLocals;
                                    for (final Shadow shadow : shadows) {
                                        for (final CodeElement loc : shadow.getWeavingRegion().getEnds()) {
                                            InstrumentedResult result = __insert(
                                                    methodModel, staticInfoHolder, piResolver, info, snippet, code, shadow,
                                                    loc, codeToInstrument, exceptions, maxLocals, codeBuilder);

                                            codeToInstrument = result.instrumentedMethodInstructions;
                                            exceptions = result.exceptionCatches;
                                            // Reset method max locals after each snippet, but keep
                                            // track of the max locals for all snippets in this phase.
                                            phaseMaxLocals = Math.max(result.maxLocals, phaseMaxLocals);
                                        }
                                    }
                                    // Set max locals to the max level reached in this phase.
                                    maxLocals = phaseMaxLocals;
                                }


                                // For exceptional after (after throwing), wrap the region with
                                // a try-finally clause and append the snippet as an exception handler.
                                if (snippet.hasAnnotation(AfterThrowing.class) || snippet.hasAnnotation(After.class)) {
                                    int phaseMaxLocals = maxLocals;

                                    for (final Shadow shadow : shadows) {
                                        // after-throwing inserts the snippet once, and marks
                                        // the start and the very end as the scope
                                        final WeavingRegion region = shadow.getWeavingRegion();
                                        final CodeElement loc = region.getAfterThrowEnd();

                                        final WeavingCode wc = new WeavingCode(
                                                info, methodModel, code, snippet, shadow,
                                                loc, codeToInstrument, exceptions, maxLocals);

                                        wc.transform(staticInfoHolder, piResolver, true);

                                        HandlerAndException handlerAndException = getExceptionCatchBlock(
                                                codeToInstrument, exceptions, region.getAfterThrowStart(), loc, codeBuilder);

                                        codeToInstrument = wc.getInstrumentedMethodInstructions();
                                        exceptions = wc.getExceptionCatches();

                                        //boolean before4 = ClassFileHelper.findDoubleLabel(codeToInstrument);
                                        //List<CodeElement> copy = codeToInstrument.stream().toList();

                                        // replace the labels
                                        List<CodeElement> instructionWithLabelReplaced = ClassFileHelper.replaceBranchAndLabelsTarget(wc.getInstrumentedSnippetInstructions(), codeBuilder);
                                        ClassFileHelper.insertAll(handlerAndException.futureLabelTarget, instructionWithLabelReplaced, codeToInstrument);
                                        //boolean after4 = ClassFileHelper.findDoubleLabel(codeToInstrument);
                                        exceptionsToAdd.add(handlerAndException.exceptionCatch); // since exception is an unmodifiable list

                                        phaseMaxLocals = Math.max(phaseMaxLocals, wc.getMethodMaxLocals());
                                    }

                                    maxLocals = phaseMaxLocals;
                                }

                            }

                            // TODO is it necessary to add the exceptions?? or the ClassFile will
                            //  compute them automatically????
                            exceptionsToAdd.addAll(exceptions);
                            for (ExceptionCatch exceptionCatch : exceptionsToAdd) {
                                codeBuilder.with(exceptionCatch);
                            }

                            for (CodeElement element : codeToInstrument) {
                                if (element instanceof FutureLabelTarget futureLabelTarget) {
                                    if (futureLabelTarget.hasLabel()) {
                                        codeBuilder.labelBinding(futureLabelTarget.getLabel());
                                    }
                                } else {
                                    codeBuilder.with(element);
                                }
                            }
                        } catch (Exception e) {
//                            WriteInfo info = WriteInfo.getInstance();
//                            info.writeLine(">>> Exception in instrumentCode method " + e.getClass());
//
//                            if (e.getMessage() != null) {
//                                info.writeLine(e.getMessage());
//                            }
//                            for ( StackTraceElement element: e.getStackTrace()) {
//                                info.writeLine(element.toString());
//                            }
//                            info.writeLine("<<< End Exception in instrumentCode method");
                            System.out.println(">>>> " + e.getMessage());
                            for (StackTraceElement stackTraceElement: e.getStackTrace()) {
                                System.out.println(stackTraceElement);
                            }
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    methodBuilder.with(methodElement);
                }
            };
        }
    }


}
