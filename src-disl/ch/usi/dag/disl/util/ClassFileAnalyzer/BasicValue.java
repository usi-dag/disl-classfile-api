package ch.usi.dag.disl.util.ClassFileAnalyzer;
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.


// this code was ported from ASM, but is modified to work with the Java CLass File API
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;

public class BasicValue implements Value{

    /** An uninitialized value. */
    public static final BasicValue UNINITIALIZED_VALUE = new BasicValue();

    /** A byte, boolean, char, short, or int value. */
    public static final BasicValue INT_VALUE = new BasicValue(TypeKind.INT);

    /** A float value. */
    public static final BasicValue FLOAT_VALUE = new BasicValue(TypeKind.FLOAT);

    /** A long value. */
    public static final BasicValue LONG_VALUE = new BasicValue(TypeKind.LONG);

    /** A double value. */
    public static final BasicValue DOUBLE_VALUE = new BasicValue(TypeKind.DOUBLE);

    /** An object or array reference value. */
    public static final BasicValue REFERENCE_VALUE = new BasicValue(TypeKind.REFERENCE);

    /** A return address value (produced by a jsr instruction). */
    public static final BasicValue RETURNADDRESS_VALUE = new BasicValue(TypeKind.VOID);

    // TODO not sure if I need both, so for now I keep them both, but I will try to use
    //  TypeKind and remove ClassDesc
    private final TypeKind typeKind;

    public BasicValue(final ClassDesc type) {
        if (type == null) {
            this.typeKind = null;
        } else {
            this.typeKind = TypeKind.from(type);
        }
    }

    public BasicValue(final TypeKind typeKind) {
        this.typeKind = typeKind;
    }

    public BasicValue() {
        this.typeKind = null;
    }


    public TypeKind getTypeKind() {
        return this.typeKind;
    }

    /**
     * Returns the size of this value in 32 bits words. This size should be 1 for byte, boolean, char,
     * short, int, float, object and array types, and 2 for long and double.
     *
     * @return either 1 or 2.
     */
    @Override
    public int getSize() {
        return TypeKind.LONG.equals(typeKind) || TypeKind.DOUBLE.equals(typeKind) ? 2 : 1;
    }

    public boolean isReference() {
        return typeKind != null && typeKind.equals(TypeKind.REFERENCE);
    }

    @Override
    public int hashCode() {
        return typeKind == null? 0 : typeKind.hashCode();
    }

    @Override
    public boolean equals(final Object value) {
        if (value == this) {
            return true;
        } else if (value instanceof BasicValue basicValue) {
            if (typeKind == null) {
                return basicValue.typeKind == null;
            } else {
                return typeKind.equals(basicValue.typeKind);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if (this == UNINITIALIZED_VALUE) {
            return ".";
        } else if (this == RETURNADDRESS_VALUE) {
            return "A";
        } else if (this == REFERENCE_VALUE) {
            return "R";
        } else {
            return typeKind.upperBound().descriptorString();
        }
    }
}
