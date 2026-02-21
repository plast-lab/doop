package org.clyze.doop.dex;

import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.BooleanEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ByteEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.CharEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.DoubleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FloatEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.LongEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.NullEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ShortEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import org.checkerframework.checker.nullness.qual.Nullable;

import static org.clyze.doop.common.JavaFactWriter.str;

public class InitialValue {
    final @Nullable String value;
    final InitialValue.IVType type;

    enum IVType { NUMBER, STRING, OTHER }

    InitialValue(EncodedValue e) {
        if (e instanceof IntEncodedValue) {
            value = str(((IntEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof LongEncodedValue) {
            value = String.valueOf(((LongEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof FloatEncodedValue) {
            value = String.valueOf(((FloatEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof DoubleEncodedValue) {
            value = String.valueOf(((DoubleEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof ByteEncodedValue) {
            value = String.valueOf(((ByteEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof ShortEncodedValue) {
            value = String.valueOf(((ShortEncodedValue) e).getValue());
            type = InitialValue.IVType.NUMBER;
        } else if (e instanceof CharEncodedValue) {
            value = String.valueOf(((CharEncodedValue) e).getValue());
            type = InitialValue.IVType.OTHER;
        } else if (e instanceof StringEncodedValue) {
            value = ((StringEncodedValue) e).getValue();
            type = InitialValue.IVType.STRING;
        } else if (e instanceof BooleanEncodedValue) {
            value = String.valueOf(((BooleanEncodedValue) e).getValue());
            type = InitialValue.IVType.OTHER;
        } else if (e instanceof NullEncodedValue) {
            value = null;
            type = InitialValue.IVType.OTHER;
        } else if (e instanceof ArrayEncodedValue) {
            System.err.println("Array encoded values are not yet suported.");
            value = null;
            type = InitialValue.IVType.OTHER;
        } else {
            System.err.println("Cannot handle encoded value of class " + e.getClass());
            value = null;
            type = InitialValue.IVType.OTHER;
        }
    }
}
