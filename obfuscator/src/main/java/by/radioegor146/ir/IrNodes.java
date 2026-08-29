package by.radioegor146.ir;

import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase-two node set. The nested classes keep the deliberately small current
 * opcode surface visible in one place.
 */
public final class IrNodes {
    private IrNodes() {
    }

    /**
     * Materializes the exception selected by the shared dispatch for a handler.
     */
    public static final class CaughtException implements IrInstruction {
        private final IrValue result;
        private final int bytecodeOffset;

        public CaughtException(IrValue result, int bytecodeOffset) {
            this.result = requireReference(result, "result");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Const implements IrInstruction {
        private final IrValue result;
        private final int value;
        private final int bytecodeOffset;

        public Const(IrValue result, int value, int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public int getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class LongConst implements IrInstruction {
        private final IrValue result;
        private final long value;
        private final int bytecodeOffset;

        public LongConst(IrValue result, long value, int bytecodeOffset) {
            this.result = requireI64(result, "result");
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public long getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class NullReference implements IrInstruction {
        private final IrValue result;
        private final int bytecodeOffset;

        public NullReference(IrValue result, int bytecodeOffset) {
            this.result = requireReference(result, "result");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class GetField implements IrInstruction {
        private final IrValue result;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final IrValue receiver;
        private final int bytecodeOffset;
        private final int sourceLine;

        public GetField(IrValue result, String owner, String name, String descriptor,
                        IrValue receiver, int bytecodeOffset, int sourceLine) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.result = requireType(result, fieldType(this.descriptor), "result");
            this.receiver = requireReference(receiver, "receiver");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public IrValue getReceiver() {
            return receiver;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class PutField implements IrInstruction {
        private final String owner;
        private final String name;
        private final String descriptor;
        private final IrValue receiver;
        private final IrValue value;
        private final int bytecodeOffset;
        private final int sourceLine;

        public PutField(String owner, String name, String descriptor, IrValue receiver,
                        IrValue value, int bytecodeOffset, int sourceLine) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.receiver = requireReference(receiver, "receiver");
            this.value = requireType(value, fieldType(this.descriptor), "value");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return null;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public IrValue getReceiver() {
            return receiver;
        }

        public IrValue getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class GetStaticField implements IrInstruction {
        private final IrValue result;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final int bytecodeOffset;
        private final int sourceLine;

        public GetStaticField(IrValue result, String owner, String name, String descriptor,
                              int bytecodeOffset, int sourceLine) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.result = requireType(result, fieldType(this.descriptor), "result");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class PutStaticField implements IrInstruction {
        private final String owner;
        private final String name;
        private final String descriptor;
        private final IrValue value;
        private final int bytecodeOffset;
        private final int sourceLine;

        public PutStaticField(String owner, String name, String descriptor, IrValue value,
                              int bytecodeOffset, int sourceLine) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.value = requireType(value, fieldType(this.descriptor), "value");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return null;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public IrValue getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class Invoke implements IrInstruction {
        public enum Kind {
            STATIC("invokestatic"),
            VIRTUAL("invokevirtual"),
            SPECIAL("invokespecial");

            private final String mnemonic;

            Kind(String mnemonic) {
                this.mnemonic = mnemonic;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Kind kind;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final IrValue receiver;
        private final List<IrValue> arguments;
        private final int bytecodeOffset;
        private final int sourceLine;

        public Invoke(IrValue result, Kind kind, String owner, String name, String descriptor,
                      IrValue receiver, List<IrValue> arguments, int bytecodeOffset,
                      int sourceLine) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            if (result != null && result.getType() != IrType.I32
                    && result.getType() != IrType.I64
                    && result.getType() != IrType.REFERENCE) {
                throw new IllegalArgumentException(
                        "Invoke result must be i32, i64, or a reference value");
            }
            if (kind == Kind.SPECIAL
                    && (!"<init>".equals(name) || result != null)) {
                throw new IllegalArgumentException(
                        "Special invokes must be void constructor calls");
            }
            this.result = result;
            if (kind == Kind.STATIC) {
                if (receiver != null) {
                    throw new IllegalArgumentException("Static invoke cannot have a receiver");
                }
                this.receiver = null;
            } else {
                this.receiver = requireReference(receiver, "receiver");
            }
            List<IrValue> checkedArguments = new ArrayList<>();
            for (IrValue argument : Objects.requireNonNull(arguments, "arguments")) {
                IrValue checked = Objects.requireNonNull(argument, "argument");
                if (checked.getType() != IrType.I32
                        && checked.getType() != IrType.I64
                        && checked.getType() != IrType.REFERENCE) {
                    throw new IllegalArgumentException(
                            "Invoke arguments must be i32, i64, or reference values");
                }
                checkedArguments.add(checked);
            }
            this.arguments = Collections.unmodifiableList(checkedArguments);
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Kind getKind() {
            return kind;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public IrValue getReceiver() {
            return receiver;
        }

        public List<IrValue> getArguments() {
            return arguments;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class NewObject implements IrInstruction {
        private final IrValue result;
        private final String className;
        private final int bytecodeOffset;

        public NewObject(IrValue result, String className, int bytecodeOffset) {
            this.result = requireReference(result, "result");
            this.className = requireClassName(className);
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Unary implements IrInstruction {
        public enum Operation {
            NEGATE("ineg"),
            I2B("i2b"),
            I2S("i2s"),
            I2C("i2c");

            private final String mnemonic;

            Operation(String mnemonic) {
                this.mnemonic = mnemonic;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue operand;
        private final int bytecodeOffset;

        public Unary(IrValue result, Operation operation, IrValue operand, int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.operand = requireI32(operand, "operand");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getOperand() {
            return operand;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Conversion implements IrInstruction {
        public enum Operation {
            I2L("i2l", IrType.I32, IrType.I64),
            L2I("l2i", IrType.I64, IrType.I32);

            private final String mnemonic;
            private final IrType operandType;
            private final IrType resultType;

            Operation(String mnemonic, IrType operandType, IrType resultType) {
                this.mnemonic = mnemonic;
                this.operandType = operandType;
                this.resultType = resultType;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue operand;
        private final int bytecodeOffset;

        public Conversion(IrValue result, Operation operation, IrValue operand,
                          int bytecodeOffset) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.result = requireType(result, operation.resultType, "result");
            this.operand = requireType(operand, operation.operandType, "operand");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getOperand() {
            return operand;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class NewArray implements IrInstruction {
        private final IrValue result;
        private final IrValue length;
        private final int bytecodeOffset;
        private final int sourceLine;

        public NewArray(IrValue result, IrValue length, int bytecodeOffset, int sourceLine) {
            this.result = requireReference(result, "result");
            this.length = requireI32(length, "length");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getLength() {
            return length;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class NewObjectArray implements IrInstruction {
        private final IrValue result;
        private final IrValue length;
        private final String componentType;
        private final int bytecodeOffset;
        private final int sourceLine;

        public NewObjectArray(IrValue result, IrValue length, String componentType,
                              int bytecodeOffset, int sourceLine) {
            this.result = requireReference(result, "result");
            this.length = requireI32(length, "length");
            this.componentType = Objects.requireNonNull(componentType, "componentType");
            if (componentType.isEmpty()) {
                throw new IllegalArgumentException("componentType must not be empty");
            }
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getLength() {
            return length;
        }

        public String getComponentType() {
            return componentType;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class ArrayLength implements IrInstruction {
        private final IrValue result;
        private final IrValue array;
        private final int bytecodeOffset;
        private final int sourceLine;

        public ArrayLength(IrValue result, IrValue array, int bytecodeOffset, int sourceLine) {
            this.result = requireI32(result, "result");
            this.array = requireReference(array, "array");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getArray() {
            return array;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class ArrayLoad implements IrInstruction {
        private final IrValue result;
        private final IrValue array;
        private final IrValue index;
        private final int bytecodeOffset;
        private final int sourceLine;

        public ArrayLoad(IrValue result, IrValue array, IrValue index, int bytecodeOffset,
                         int sourceLine) {
            this.result = requireI32(result, "result");
            this.array = requireReference(array, "array");
            this.index = requireI32(index, "index");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getArray() {
            return array;
        }

        public IrValue getIndex() {
            return index;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class ArrayStore implements IrInstruction {
        private final IrValue array;
        private final IrValue index;
        private final IrValue value;
        private final int bytecodeOffset;
        private final int sourceLine;

        public ArrayStore(IrValue array, IrValue index, IrValue value, int bytecodeOffset,
                          int sourceLine) {
            this.array = requireReference(array, "array");
            this.index = requireI32(index, "index");
            this.value = requireI32(value, "value");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return null;
        }

        public IrValue getArray() {
            return array;
        }

        public IrValue getIndex() {
            return index;
        }

        public IrValue getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    /**
     * Dedicated {@code String.length()} intrinsic. It lowers to a single
     * {@code GetStringLength} call instead of a full {@code invokevirtual}
     * method-id lookup and {@code CallIntMethod}.
     */
    public static final class StringLength implements IrInstruction {
        private final IrValue result;
        private final IrValue receiver;
        private final int bytecodeOffset;
        private final int sourceLine;

        public StringLength(IrValue result, IrValue receiver, int bytecodeOffset,
                            int sourceLine) {
            this.result = requireI32(result, "result");
            this.receiver = requireReference(receiver, "receiver");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getReceiver() {
            return receiver;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class CheckCast implements IrInstruction {
        private final IrValue result;
        private final IrValue operand;
        private final String targetType;
        private final int bytecodeOffset;
        private final int sourceLine;

        public CheckCast(IrValue result, IrValue operand, String targetType,
                         int bytecodeOffset, int sourceLine) {
            this.result = requireReference(result, "result");
            this.operand = requireReference(operand, "operand");
            this.targetType = requireClassName(targetType);
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getOperand() {
            return operand;
        }

        public String getTargetType() {
            return targetType;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class InstanceOf implements IrInstruction {
        private final IrValue result;
        private final IrValue operand;
        private final String targetType;
        private final int bytecodeOffset;

        public InstanceOf(IrValue result, IrValue operand, String targetType,
                          int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.operand = requireReference(operand, "operand");
            this.targetType = requireClassName(targetType);
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public IrValue getOperand() {
            return operand;
        }

        public String getTargetType() {
            return targetType;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Binary implements IrInstruction {
        public enum Operation {
            ADD("iadd"),
            SUBTRACT("isub"),
            MULTIPLY("imul"),
            AND("iand"),
            OR("ior"),
            XOR("ixor"),
            SHL("ishl"),
            SHR("ishr"),
            USHR("iushr");

            private final String mnemonic;

            Operation(String mnemonic) {
                this.mnemonic = mnemonic;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue left;
        private final IrValue right;
        private final int bytecodeOffset;

        public Binary(IrValue result, Operation operation, IrValue left, IrValue right,
                      int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.left = requireI32(left, "left");
            this.right = requireI32(right, "right");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getLeft() {
            return left;
        }

        public IrValue getRight() {
            return right;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class LongBinary implements IrInstruction {
        public enum Operation {
            ADD("ladd"),
            SUBTRACT("lsub"),
            MULTIPLY("lmul");

            private final String mnemonic;

            Operation(String mnemonic) {
                this.mnemonic = mnemonic;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue left;
        private final IrValue right;
        private final int bytecodeOffset;

        public LongBinary(IrValue result, Operation operation, IrValue left, IrValue right,
                          int bytecodeOffset) {
            this.result = requireI64(result, "result");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.left = requireI64(left, "left");
            this.right = requireI64(right, "right");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getLeft() {
            return left;
        }

        public IrValue getRight() {
            return right;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class IntDivRem implements IrInstruction {
        public enum Operation {
            DIVIDE("idiv", "/"),
            REMAINDER("irem", "%");

            private final String mnemonic;
            private final String cppOperator;

            Operation(String mnemonic, String cppOperator) {
                this.mnemonic = mnemonic;
                this.cppOperator = cppOperator;
            }

            public String getMnemonic() {
                return mnemonic;
            }

            public String getCppOperator() {
                return cppOperator;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue left;
        private final IrValue right;
        private final int bytecodeOffset;
        private final int sourceLine;

        public IntDivRem(IrValue result, Operation operation, IrValue left, IrValue right,
                         int bytecodeOffset, int sourceLine) {
            this.result = requireI32(result, "result");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.left = requireI32(left, "left");
            this.right = requireI32(right, "right");
            this.bytecodeOffset = bytecodeOffset;
            this.sourceLine = sourceLine;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getLeft() {
            return left;
        }

        public IrValue getRight() {
            return right;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }

        public int getSourceLine() {
            return sourceLine;
        }
    }

    public static final class Goto implements IrTerminator {
        private final IrBlock target;
        private final int bytecodeOffset;

        public Goto(IrBlock target, int bytecodeOffset) {
            this.target = Objects.requireNonNull(target, "target");
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrBlock getTarget() {
            return target;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Collections.singletonList(target);
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Branch implements IrTerminator {
        public enum Condition {
            EQ("eq", "=="),
            NE("ne", "!="),
            LT("lt", "<"),
            GE("ge", ">="),
            GT("gt", ">"),
            LE("le", "<=");

            private final String mnemonic;
            private final String cppOperator;

            Condition(String mnemonic, String cppOperator) {
                this.mnemonic = mnemonic;
                this.cppOperator = cppOperator;
            }

            public String getMnemonic() {
                return mnemonic;
            }

            public String getCppOperator() {
                return cppOperator;
            }
        }

        private final Condition condition;
        private final IrValue left;
        private final IrValue right;
        private final IrBlock trueTarget;
        private final IrBlock falseTarget;
        private final int bytecodeOffset;

        public Branch(Condition condition, IrValue left, IrValue right, IrBlock trueTarget,
                      IrBlock falseTarget, int bytecodeOffset) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.left = requireI32(left, "left");
            this.right = right == null ? null : requireI32(right, "right");
            this.trueTarget = Objects.requireNonNull(trueTarget, "trueTarget");
            this.falseTarget = Objects.requireNonNull(falseTarget, "falseTarget");
            this.bytecodeOffset = bytecodeOffset;
        }

        public Condition getCondition() {
            return condition;
        }

        public IrValue getLeft() {
            return left;
        }

        /**
         * Null means that the JVM single-operand branch compares against zero.
         */
        public IrValue getRight() {
            return right;
        }

        public IrBlock getTrueTarget() {
            return trueTarget;
        }

        public IrBlock getFalseTarget() {
            return falseTarget;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Arrays.asList(trueTarget, falseTarget);
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class ReferenceBranch implements IrTerminator {
        public enum Condition {
            IS_NULL("ifnull", "=="),
            IS_NON_NULL("ifnonnull", "!=");

            private final String mnemonic;
            private final String cppOperator;

            Condition(String mnemonic, String cppOperator) {
                this.mnemonic = mnemonic;
                this.cppOperator = cppOperator;
            }

            public String getMnemonic() {
                return mnemonic;
            }

            public String getCppOperator() {
                return cppOperator;
            }
        }

        private final Condition condition;
        private final IrValue reference;
        private final IrBlock trueTarget;
        private final IrBlock falseTarget;
        private final int bytecodeOffset;

        public ReferenceBranch(Condition condition, IrValue reference, IrBlock trueTarget,
                               IrBlock falseTarget, int bytecodeOffset) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.reference = requireReference(reference, "reference");
            this.trueTarget = Objects.requireNonNull(trueTarget, "trueTarget");
            this.falseTarget = Objects.requireNonNull(falseTarget, "falseTarget");
            this.bytecodeOffset = bytecodeOffset;
        }

        public Condition getCondition() {
            return condition;
        }

        public IrValue getReference() {
            return reference;
        }

        public IrBlock getTrueTarget() {
            return trueTarget;
        }

        public IrBlock getFalseTarget() {
            return falseTarget;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Arrays.asList(trueTarget, falseTarget);
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Switch implements IrTerminator {
        private final IrValue selector;
        private final List<Integer> keys;
        private final List<IrBlock> targets;
        private final IrBlock defaultTarget;
        private final int bytecodeOffset;

        public Switch(IrValue selector, List<Integer> keys, List<IrBlock> targets,
                      IrBlock defaultTarget, int bytecodeOffset) {
            this.selector = requireI32(selector, "selector");
            Objects.requireNonNull(keys, "keys");
            Objects.requireNonNull(targets, "targets");
            if (keys.size() != targets.size()) {
                throw new IllegalArgumentException(
                        "Switch keys and targets must have the same size");
            }
            List<Integer> checkedKeys = new ArrayList<>();
            List<IrBlock> checkedTargets = new ArrayList<>();
            Set<Integer> uniqueKeys = new LinkedHashSet<>();
            for (int i = 0; i < keys.size(); i++) {
                Integer key = Objects.requireNonNull(keys.get(i), "key");
                if (!uniqueKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate switch key " + key);
                }
                checkedKeys.add(key);
                checkedTargets.add(Objects.requireNonNull(targets.get(i), "target"));
            }
            this.keys = Collections.unmodifiableList(checkedKeys);
            this.targets = Collections.unmodifiableList(checkedTargets);
            this.defaultTarget = Objects.requireNonNull(defaultTarget, "defaultTarget");
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrValue getSelector() {
            return selector;
        }

        public List<Integer> getKeys() {
            return keys;
        }

        public List<IrBlock> getTargets() {
            return targets;
        }

        public IrBlock getDefaultTarget() {
            return defaultTarget;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            Set<IrBlock> successors = new LinkedHashSet<>(targets);
            successors.add(defaultTarget);
            return Collections.unmodifiableList(new ArrayList<>(successors));
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Return implements IrTerminator {
        private final IrValue value;
        private final int bytecodeOffset;

        public Return(IrValue value, int bytecodeOffset) {
            if (value != null) {
                IrType type = value.getType();
                if (type != IrType.I32 && type != IrType.I64
                        && type != IrType.REFERENCE) {
                    throw new IllegalArgumentException(
                            "value must be i32, i64, or reference, got " + type);
                }
            }
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrValue getValue() {
            return value;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Collections.emptyList();
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Throw implements IrTerminator {
        private final IrValue exception;
        private final int bytecodeOffset;

        public Throw(IrValue exception, int bytecodeOffset) {
            this.exception = requireReference(exception, "exception");
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrValue getException() {
            return exception;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Collections.emptyList();
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    private static IrValue requireI32(IrValue value, String name) {
        return requireType(value, IrType.I32, name);
    }

    private static IrValue requireI64(IrValue value, String name) {
        return requireType(value, IrType.I64, name);
    }

    private static IrValue requireType(IrValue value, IrType type, String name) {
        IrValue result = Objects.requireNonNull(value, name);
        if (result.getType() != type) {
            throw new IllegalArgumentException(name + " must be " + type
                    + ", got " + result.getType());
        }
        return result;
    }

    private static IrValue requireReference(IrValue value, String name) {
        return requireType(value, IrType.REFERENCE, name);
    }

    private static IrType fieldType(String descriptor) {
        Type type;
        try {
            type = Type.getType(descriptor);
        } catch (IllegalArgumentException malformedDescriptor) {
            throw new IllegalArgumentException("Invalid field descriptor " + descriptor,
                    malformedDescriptor);
        }
        if (type.getSort() == Type.INT) {
            return IrType.I32;
        }
        if (type.getSort() == Type.LONG) {
            return IrType.I64;
        }
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            return IrType.REFERENCE;
        }
        throw new IllegalArgumentException("Unsupported field descriptor " + descriptor);
    }

    private static String requireClassName(String className) {
        String result = Objects.requireNonNull(className, "targetType");
        if (result.isEmpty()) {
            throw new IllegalArgumentException("targetType must not be empty");
        }
        return result;
    }
}
