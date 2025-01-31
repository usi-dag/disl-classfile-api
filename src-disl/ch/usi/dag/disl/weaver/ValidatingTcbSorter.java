package ch.usi.dag.disl.weaver;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.util.AsmHelper;
import ch.usi.dag.disl.util.AsmHelper.Insns;


class ValidatingTcbSorter {

    private static void __sortAndUpdate (
        final List<TryCatchBlockNode> tcbs, final InsnList insns
    ) {
        Collections.sort (tcbs, new Comparator <TryCatchBlockNode> () {
            @Override
            public int compare (
                final TryCatchBlockNode tcbNode1, final TryCatchBlockNode tcbNode2
            ) {
                final int orderBySize = Integer.compare (
                    blockLength (tcbNode1), blockLength (tcbNode2)
                );

                if (orderBySize == 0) {
                    return Integer.compare (
                        __startIdx (tcbNode1.start, insns),
                        __startIdx (tcbNode2.start, insns)
                    );

                } else {
                    return orderBySize;
                }
            }


            private int blockLength (final TryCatchBlockNode tcbNode) {
                return __endIdx (tcbNode.end, insns) - __startIdx (tcbNode.start, insns);
            }
        });

        // Update index of each try-catch-block.
        for (int i = 0; i < tcbs.size (); i++) {
            tcbs.get (i).updateIndex (i);
        }
    }

    private static int __startIdx (final LabelNode label, final InsnList insns) {
        return insns.indexOf (Insns.FORWARD.firstRealInsn (label));
    }

    private static int __endIdx (final LabelNode label, final InsnList insns) {
        return insns.indexOf (label);
    }


    private static void __validate (
        final List<TryCatchBlockNode> tryCatchBlocks, final InsnList insns
    ) {
        final TryCatchBlockNode [] tcbs = tryCatchBlocks.toArray (
            new TryCatchBlockNode [tryCatchBlocks.size ()]
        );

        for (int i = 0; i < tcbs.length - 1; i++) {
            final int istart = __startIdx (tcbs [i].start, insns);
            final int iend = __endIdx (tcbs [i].end, insns);

            for (int j = i + 1; j < tcbs.length; j++) {
                final int jstart = __startIdx (tcbs [j].start, insns);
                final int jend = __endIdx (tcbs [j].end, insns);

                if ((
                    AsmHelper.offsetBefore (insns, istart, jstart) // I starts before J starts
                    && AsmHelper.offsetBefore (insns, iend, jend) // I ends before J ends
                    && AsmHelper.offsetBefore (insns, jstart, iend) // but J starts before I ends
                ) || (
                    AsmHelper.offsetBefore (insns, jstart, istart) // J starts before I starts
                    && AsmHelper.offsetBefore (insns, jend, iend) // J ends before I ends
                    && AsmHelper.offsetBefore (insns, istart, jend) // but I starts before J ends
                )) {
                    throw new DiSLFatalException (
                        "Overlapping exception handlers #%d [%d,%d) and #%d [%d, %d)",
                        i, istart, iend, j, jstart, jend
                    );
                }
            }
        }
    }


    /**
     * Sorts exceptions handlers from inner to outer and ensures that don't
     * overlap.
     *
     * @throws DiSLFataException
     *         If any of the exception handlers overlap.
     */
    public static void sortTcbs (final MethodNode method) {
        __sortAndUpdate (method.tryCatchBlocks, method.instructions);
        __validate (method.tryCatchBlocks, method.instructions);
    }

    //

    @SuppressWarnings ("unused")
    private static void __printTcbs (final MethodNode method) {
        for (final TryCatchBlockNode tcb : method.tryCatchBlocks) {
            final int startIdx = method.instructions.indexOf (tcb.start);
            final int endIdx = method.instructions.indexOf (tcb.end);
            System.err.printf ("%d -> %d (%d)\n", startIdx, endIdx, endIdx - startIdx);
        }
    }
}
