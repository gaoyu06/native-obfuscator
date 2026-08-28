package by.radioegor146.interpreter;

import by.radioegor146.Util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Per-translation byte assignment for the integer interpreter operations.
 */
public final class InterpreterOpcodeMap {

    static final int PUSH = 0;
    static final int LOAD = 1;
    static final int STORE = 2;
    static final int ADD = 3;
    static final int SUBTRACT = 4;
    static final int BRANCH_EQ_ZERO = 5;
    static final int BRANCH_NE_ZERO = 6;
    static final int BRANCH_LT_ZERO = 7;
    static final int BRANCH_GE_ZERO = 8;
    static final int BRANCH_GT_ZERO = 9;
    static final int BRANCH_LE_ZERO = 10;
    static final int BRANCH_EQ = 11;
    static final int BRANCH_NE = 12;
    static final int BRANCH_LT = 13;
    static final int BRANCH_GE = 14;
    static final int BRANCH_GT = 15;
    static final int BRANCH_LE = 16;
    static final int JUMP = 17;
    static final int RETURN = 18;
    static final int MULTIPLY = 19;
    static final int XOR = 20;
    static final int SHIFT_LEFT = 21;
    static final int SHIFT_RIGHT_UNSIGNED = 22;
    static final int ROTATE_LEFT = 23;

    private static final int OPERATION_COUNT = 24;
    private static final SecureRandom SEED_SOURCE = new SecureRandom();
    private static final int[] STANDARD_VALUES = {
            0xa7, 0x31, 0xd4, 0x6b, 0xe2, 0x19, 0xc8, 0x45,
            0x9a, 0xf1, 0x2d, 0x74, 0xb6, 0x0f, 0x83, 0xdc,
            0x52, 0xae, 0x67, 0x3c, 0xf8, 0x21, 0x95, 0xca
    };

    private final long seed;
    private final int[] values;

    private InterpreterOpcodeMap(long seed, int[] values) {
        this.seed = seed;
        this.values = values.clone();
    }

    public static InterpreterOpcodeMap random() {
        return fromSeed(SEED_SOURCE.nextLong());
    }

    public static InterpreterOpcodeMap fromSeed(long seed) {
        List<Integer> candidates = new ArrayList<>(256);
        for (int value = 0; value <= 0xff; value++) {
            candidates.add(value);
        }
        Collections.shuffle(candidates, new Random(seed));
        int[] values = new int[OPERATION_COUNT];
        for (int i = 0; i < values.length; i++) {
            values[i] = candidates.get(i);
        }
        return new InterpreterOpcodeMap(seed, values);
    }

    static InterpreterOpcodeMap standard() {
        return new InterpreterOpcodeMap(0L, STANDARD_VALUES);
    }

    public long getSeed() {
        return seed;
    }

    int value(int operation) {
        if (operation < 0 || operation >= values.length) {
            throw new IllegalArgumentException("Unknown interpreter operation index " + operation);
        }
        return values[operation];
    }

    public String renderRuntimeSource() {
        String source = Util.readResource("sources/native_jvm_interp.cpp");
        for (int i = values.length - 1; i >= 0; i--) {
            source = source.replace("$op" + i,
                    String.format(Locale.ROOT, "0x%02x", values[i]));
        }
        return source;
    }
}
