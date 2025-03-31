package ch.usi.dag.disl.weaver.peClassFile;


import ch.usi.dag.disl.util.ClassFileAnalyzer.Value;

public class ConstValue implements Value {

    public final static Object NULL = new Object();

    /**
     * The size of this value.
     */
    public final int size;

    public Object cst;

    public ConstValue(int size) {
        this(size, null);
    }

    public ConstValue(int size, Object cst) {
        this.size = size;
        this.cst = cst;
    }

    @Override
    public int getSize() {
        return size;
    }

}