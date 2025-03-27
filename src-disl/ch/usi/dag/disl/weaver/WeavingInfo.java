package ch.usi.dag.disl.weaver;

import java.lang.classfile.*;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ch.usi.dag.disl.CustomCodeElements.FutureLabelTarget;
import ch.usi.dag.disl.util.ClassFileAnalyzer.BasicValue;
import ch.usi.dag.disl.util.ClassFileAnalyzer.Frame;
import ch.usi.dag.disl.util.ClassFileAnalyzer.SourceValue;
import ch.usi.dag.disl.util.ClassFileFrameHelper;
import ch.usi.dag.disl.util.ClassFileHelper;

import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;
import ch.usi.dag.disl.snippet.Snippet;

public class WeavingInfo {

    private ArrayList<Snippet> sortedSnippets;

    private Map<CodeElement, Frame<BasicValue>> basicFrameMap;
    private Map<CodeElement, Frame<SourceValue>> sourceFrameMap;

    private Frame<BasicValue> retFrame;

    public WeavingInfo(ClassModel classModel,
                       MethodModel methodModel,
                       Map<Snippet, List<Shadow>> snippetMarkings,
                       List<CodeElement> instructionsToInstrument,
                       List<ExceptionCatch> exceptions) {

        sortedSnippets = new ArrayList<>(snippetMarkings.keySet());
        Collections.sort(sortedSnippets);

        List<Label> tcbEnd = new LinkedList<>();

        for (ExceptionCatch exceptionCatch: exceptions) {
            tcbEnd.add(exceptionCatch.tryEnd());
        }

        // initialize weaving start
        for (Snippet snippet: sortedSnippets) {
            for (Shadow shadow: snippetMarkings.get(snippet)) {
                WeavingRegion region = shadow.getWeavingRegion();
                CodeElement start = region.getStart();
                FutureLabelTarget lStart = new FutureLabelTarget();
                ClassFileHelper.insertBefore(start, lStart, instructionsToInstrument);
                region.setStart(lStart);
            }
        }

        // first pass: adjust weaving end for one-instruction shadow
        for (Snippet snippet: sortedSnippets) {
            for (Shadow shadow: snippetMarkings.get(snippet)) {
                WeavingRegion region = shadow.getWeavingRegion();

                if (region.getEnds() == null) {
                    List<CodeElement> ends = new LinkedList<>();
                    for (CodeElement end: shadow.getRegionEnds()) {
                        if (ClassFileHelper.isBranch(end)) {
                            // TODO should I pass the original list instead???
                            end = ClassFileHelper.previousInstruction(instructionsToInstrument, end);
                        }
                        ends.add(end);
                    }

                    region.setEnds(ends);
                }
            }
        }

        // second pass: calculate weaving location
        for (Snippet snippet: sortedSnippets) {
            for (Shadow shadow: snippetMarkings.get(snippet)) {
                WeavingRegion region = shadow.getWeavingRegion();
                List<CodeElement> ends = new LinkedList<>();

                for (CodeElement end: region.getEnds()) {
                    FutureLabelTarget lEnd = new FutureLabelTarget();
                    ClassFileHelper.insert(end, lEnd, instructionsToInstrument);
                    ends.add(lEnd);
                }

                region.setEnds(ends);

                FutureLabelTarget lThrowStart = new FutureLabelTarget();
                ClassFileHelper.insertBefore(region.getAfterThrowStart(), lThrowStart, instructionsToInstrument);
                region.setAfterThrowStart(lThrowStart);

                FutureLabelTarget lThrowEnd = new FutureLabelTarget();
                ClassFileHelper.insert(region.getAfterThrowEnd(), lThrowEnd, instructionsToInstrument);
                region.setAfterThrowEnd(lThrowEnd);

            }
        }

        basicFrameMap = ClassFileFrameHelper.createBasicMapping(classModel.thisClass().asSymbol(), methodModel);
        sourceFrameMap = ClassFileFrameHelper.createSourceMapping(classModel.thisClass().asSymbol(), methodModel);

        Instruction last = ClassFileHelper.firstPreviousRealInstruction(instructionsToInstrument, instructionsToInstrument.getLast());

        retFrame = basicFrameMap.get(last);
    }

    public ArrayList<Snippet> getSortedSnippets() {
        return sortedSnippets;
    }

    public Frame<BasicValue> getBasicFrame(CodeElement instr) {
        return basicFrameMap.get(instr);
    }

    public Frame<BasicValue> getRetFrame() {
        return retFrame;
    }

    public Frame<SourceValue> getSourceFrame(CodeElement instr) {
        return sourceFrameMap.get(instr);
    }

    public boolean stackNotEmpty(CodeElement loc) {
        return basicFrameMap.get(loc).getStackSize() > 0;
    }

    public List<StoreInstruction> backupStack(CodeElement loc, int startFrom) {
        return ClassFileFrameHelper.enter(basicFrameMap.get(loc), startFrom);
    }

    public List<LoadInstruction> restoreStack(CodeElement loc, int startFrom) {
        return ClassFileFrameHelper.exit(basicFrameMap.get(loc), startFrom);
    }

    public int getStackHeight(CodeElement loc) {
        return ClassFileFrameHelper.getOffset(basicFrameMap.get(loc));
    }

}
