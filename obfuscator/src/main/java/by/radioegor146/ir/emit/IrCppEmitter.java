package by.radioegor146.ir.emit;

import by.radioegor146.CachedFieldInfo;
import by.radioegor146.CachedMethodInfo;
import by.radioegor146.HiddenMethodsPool;
import by.radioegor146.MethodContext;
import by.radioegor146.bytecode.MethodHandleUtils;
import by.radioegor146.bytecode.PreprocessorUtils;
import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrExceptionEdge;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Emits the typed IR directly through {@link CppAst}; it has no dependency on
 * the legacy snippet/property machinery.
 */
public final class IrCppEmitter {
    private int edgeTemporaryId;
    private int arrayTemporaryId;
    private Map<HandlerSet, String> dispatchLabels;

    public String emitBody(IrMethod method) {
        return emitBody(method, null);
    }

    public String emitBody(IrMethod method, MethodContext context) {
        edgeTemporaryId = 0;
        arrayTemporaryId = 0;
        dispatchLabels = collectDispatchLabels(method);
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(new CppAst.Comment("IR codegen: " + method.getOwner() + "."
                + method.getName() + method.getDescriptor()));

        // Declare all SSA carriers before labels so C++ gotos never cross an
        // initialized automatic variable.
        if (!dispatchLabels.isEmpty()) {
            statements.add(new CppAst.Declaration("jthrowable", "caught_exception"));
        }
        for (IrBlock block : method.getBlocks()) {
            for (IrPhi phi : block.getPhis()) {
                statements.add(declaration(phi.getResult()));
            }
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction.getResult() != null) {
                    statements.add(declaration(instruction.getResult()));
                }
            }
        }
        if (method.isSynchronizedMethod()) {
            statements.addAll(emitSynchronizedEnter(method));
        }

        for (IrBlock block : method.getBlocks()) {
            statements.add(new CppAst.Label(label(block)));
            for (IrInstruction instruction : block.getInstructions()) {
                statements.addAll(emitInstruction(method, block, instruction, context));
            }
            emitTerminator(method, context, statements, block, block.getTerminator());
        }
        if (!dispatchLabels.isEmpty()) {
            if (context == null) {
                throw new IllegalStateException(
                        "Exception dispatch requires a method emission context");
            }
            emitDispatches(method, context, statements);
        }
        return CppAst.render(statements, 1);
    }

    private CppAst.Declaration declaration(IrValue value) {
        return new CppAst.Declaration(value.getType().getCppType(), variableName(value));
    }

    private List<CppAst.Statement> emitInstruction(IrMethod method, IrBlock block,
                                                   IrInstruction instruction,
                                                   MethodContext context) {
        if (instruction instanceof IrNodes.CaughtException) {
            return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                    variable(instruction.getResult()),
                    new CppAst.Cast("jobject", variable("caught_exception"))));
        }
        if (instruction instanceof IrNodes.Const) {
            IrNodes.Const constant = (IrNodes.Const) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.IntLiteral(constant.getValue())));
        }
        if (instruction instanceof IrNodes.LongConst) {
            IrNodes.LongConst constant = (IrNodes.LongConst) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.LongLiteral(constant.getValue())));
        }
        if (instruction instanceof IrNodes.FloatConst) {
            IrNodes.FloatConst constant = (IrNodes.FloatConst) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.FloatBitsLiteral(constant.getRawBits())));
        }
        if (instruction instanceof IrNodes.DoubleConst) {
            IrNodes.DoubleConst constant = (IrNodes.DoubleConst) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.DoubleBitsLiteral(constant.getRawBits())));
        }
        if (instruction instanceof IrNodes.NullReference) {
            IrNodes.NullReference constant = (IrNodes.NullReference) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.NullLiteral()));
        }
        if (instruction instanceof IrNodes.Binary) {
            return emitBinary((IrNodes.Binary) instruction);
        }
        if (instruction instanceof IrNodes.LongBinary) {
            return emitLongBinary((IrNodes.LongBinary) instruction);
        }
        if (instruction instanceof IrNodes.LongShift) {
            return emitLongShift((IrNodes.LongShift) instruction);
        }
        if (instruction instanceof IrNodes.FloatingBinary) {
            return emitFloatingBinary((IrNodes.FloatingBinary) instruction);
        }
        if (instruction instanceof IrNodes.FloatingUnary) {
            return emitFloatingUnary((IrNodes.FloatingUnary) instruction);
        }
        if (instruction instanceof IrNodes.FloatingCompare) {
            return emitFloatingCompare((IrNodes.FloatingCompare) instruction);
        }
        if (instruction instanceof IrNodes.LongCompare) {
            return emitLongCompare((IrNodes.LongCompare) instruction);
        }
        if (instruction instanceof IrNodes.Unary) {
            return emitUnary((IrNodes.Unary) instruction);
        }
        if (instruction instanceof IrNodes.LongUnary) {
            return emitLongUnary((IrNodes.LongUnary) instruction);
        }
        if (instruction instanceof IrNodes.Conversion) {
            return emitConversion((IrNodes.Conversion) instruction);
        }
        if (context == null) {
            throw new IllegalStateException(
                    "JNI IR instructions require a method emission context");
        }
        if (instruction instanceof IrNodes.StringConst) {
            return emitStringConst((IrNodes.StringConst) instruction, context);
        }
        if (instruction instanceof IrNodes.ClassConst) {
            return emitClassConst(method, block, (IrNodes.ClassConst) instruction, context);
        }
        if (instruction instanceof IrNodes.IntDivRem) {
            return emitIntDivRem(method, block, (IrNodes.IntDivRem) instruction, context);
        }
        if (instruction instanceof IrNodes.LongDivRem) {
            return emitLongDivRem(method, block, (IrNodes.LongDivRem) instruction, context);
        }
        if (instruction instanceof IrNodes.NewObject) {
            return emitNewObject(method, block, (IrNodes.NewObject) instruction, context);
        }
        if (instruction instanceof IrNodes.NewArray) {
            return emitNewArray(method, block, (IrNodes.NewArray) instruction, context);
        }
        if (instruction instanceof IrNodes.NewObjectArray) {
            return emitNewObjectArray(method, block,
                    (IrNodes.NewObjectArray) instruction, context);
        }
        if (instruction instanceof IrNodes.MultiNewArray) {
            return emitMultiNewArray(method, block,
                    (IrNodes.MultiNewArray) instruction, context);
        }
        if (instruction instanceof IrNodes.ArrayLength) {
            return emitArrayLength(method, block, (IrNodes.ArrayLength) instruction, context);
        }
        if (instruction instanceof IrNodes.ArrayLoad) {
            return emitArrayLoad(method, block, (IrNodes.ArrayLoad) instruction, context);
        }
        if (instruction instanceof IrNodes.ArrayStore) {
            return emitArrayStore(method, block, (IrNodes.ArrayStore) instruction, context);
        }
        if (instruction instanceof IrNodes.StringLength) {
            return emitStringLength(method, block, (IrNodes.StringLength) instruction, context);
        }
        if (instruction instanceof IrNodes.CheckCast) {
            return emitCheckCast(method, block, (IrNodes.CheckCast) instruction, context);
        }
        if (instruction instanceof IrNodes.InstanceOf) {
            return emitInstanceOf(method, block, (IrNodes.InstanceOf) instruction, context);
        }
        if (instruction instanceof IrNodes.MonitorEnter) {
            return emitMonitorEnter(method, block, (IrNodes.MonitorEnter) instruction,
                    context);
        }
        if (instruction instanceof IrNodes.MonitorExit) {
            return emitMonitorExit(method, block, (IrNodes.MonitorExit) instruction,
                    context);
        }
        if (instruction instanceof IrNodes.GetField) {
            return emitGetField(method, block, (IrNodes.GetField) instruction, context);
        }
        if (instruction instanceof IrNodes.PutField) {
            return emitPutField(method, block, (IrNodes.PutField) instruction, context);
        }
        if (instruction instanceof IrNodes.GetStaticField) {
            return emitGetStaticField(method, block, (IrNodes.GetStaticField) instruction,
                    context);
        }
        if (instruction instanceof IrNodes.PutStaticField) {
            return emitPutStaticField(method, block, (IrNodes.PutStaticField) instruction,
                    context);
        }
        if (instruction instanceof IrNodes.Invoke) {
            return emitInvoke(method, block, (IrNodes.Invoke) instruction, context);
        }
        throw new IllegalStateException("Unknown IR instruction " + instruction.getClass());
    }

    private List<CppAst.Statement> emitBinary(IrNodes.Binary binary) {
        CppAst.Expression left = expression(binary.getLeft());
        CppAst.Expression right = expression(binary.getRight());
        CppAst.Expression value;
        switch (binary.getOperation()) {
            case ADD:
                value = wrappingArithmetic(left, "+", right);
                break;
            case SUBTRACT:
                value = wrappingArithmetic(left, "-", right);
                break;
            case MULTIPLY:
                value = wrappingArithmetic(left, "*", right);
                break;
            case AND:
                value = wrappingArithmetic(left, "&", right);
                break;
            case OR:
                value = wrappingArithmetic(left, "|", right);
                break;
            case XOR:
                value = wrappingArithmetic(left, "^", right);
                break;
            case SHL:
                value = new CppAst.Cast("jint", new CppAst.Binary(
                        new CppAst.Cast("uint32_t", left), "<<", shiftAmount(right)));
                break;
            case SHR:
                value = new CppAst.Cast("jint", new CppAst.Binary(
                        new CppAst.Cast("int32_t", left), ">>", shiftAmount(right)));
                break;
            case USHR:
                value = new CppAst.Cast("jint", new CppAst.Binary(
                        new CppAst.Cast("uint32_t", left), ">>", shiftAmount(right)));
                break;
            default:
                throw new IllegalStateException("Unknown binary operation "
                        + binary.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(binary.getResult()), value));
    }

    private CppAst.Expression wrappingArithmetic(CppAst.Expression left, String operator,
                                                 CppAst.Expression right) {
        return new CppAst.Cast("jint", new CppAst.Binary(
                new CppAst.Cast("uint32_t", left), operator,
                new CppAst.Cast("uint32_t", right)));
    }

    private List<CppAst.Statement> emitLongBinary(IrNodes.LongBinary binary) {
        String operator;
        switch (binary.getOperation()) {
            case ADD:
                operator = "+";
                break;
            case SUBTRACT:
                operator = "-";
                break;
            case MULTIPLY:
                operator = "*";
                break;
            case AND:
                operator = "&";
                break;
            case OR:
                operator = "|";
                break;
            case XOR:
                operator = "^";
                break;
            default:
                throw new IllegalStateException("Unknown long binary operation "
                        + binary.getOperation());
        }
        CppAst.Expression value = new CppAst.Cast("jlong", new CppAst.Binary(
                new CppAst.Cast("uint64_t", expression(binary.getLeft())), operator,
                new CppAst.Cast("uint64_t", expression(binary.getRight()))));
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(binary.getResult()), value));
    }

    private List<CppAst.Statement> emitLongShift(IrNodes.LongShift shift) {
        CppAst.Expression value;
        switch (shift.getOperation()) {
            case SHL:
                value = new CppAst.Binary(
                        new CppAst.Cast("uint64_t", expression(shift.getValue())), "<<",
                        longShiftAmount(expression(shift.getCount())));
                break;
            case SHR:
                value = new CppAst.Binary(
                        new CppAst.Cast("int64_t", expression(shift.getValue())), ">>",
                        longShiftAmount(expression(shift.getCount())));
                break;
            case USHR:
                value = new CppAst.Binary(
                        new CppAst.Cast("uint64_t", expression(shift.getValue())), ">>",
                        longShiftAmount(expression(shift.getCount())));
                break;
            default:
                throw new IllegalStateException("Unknown long shift operation "
                        + shift.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                variable(shift.getResult()), new CppAst.Cast("jlong", value)));
    }

    private List<CppAst.Statement> emitFloatingBinary(IrNodes.FloatingBinary binary) {
        CppAst.Expression left = expression(binary.getLeft());
        CppAst.Expression right = expression(binary.getRight());
        CppAst.Expression value;
        switch (binary.getOperation()) {
            case ADD:
                value = new CppAst.Binary(left, "+", right);
                break;
            case SUBTRACT:
                value = new CppAst.Binary(left, "-", right);
                break;
            case MULTIPLY:
                value = new CppAst.Binary(left, "*", right);
                break;
            case DIVIDE:
                // C++ floating division has the JVM's Inf/NaN behavior and
                // does not share the integer divide-by-zero exceptional path.
                value = new CppAst.Binary(left, "/", right);
                break;
            case REMAINDER:
                value = new CppAst.Call("std::fmod", Arrays.asList(left, right));
                break;
            default:
                throw new IllegalStateException("Unknown floating binary operation "
                        + binary.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(binary.getResult()), value));
    }

    private List<CppAst.Statement> emitFloatingUnary(IrNodes.FloatingUnary unary) {
        return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                variable(unary.getResult()),
                new CppAst.Unary("-", expression(unary.getOperand()))));
    }

    private List<CppAst.Statement> emitFloatingCompare(IrNodes.FloatingCompare compare) {
        CppAst.Expression left = expression(compare.getLeft());
        CppAst.Expression right = expression(compare.getRight());
        CppAst.Expression nan = new CppAst.Binary(
                new CppAst.Call("std::isnan", Collections.singletonList(left)),
                "||",
                new CppAst.Call("std::isnan", Collections.singletonList(right)));
        CppAst.Expression ordered = new CppAst.Conditional(
                new CppAst.Binary(left, ">", right),
                new CppAst.IntLiteral(1),
                new CppAst.Conditional(new CppAst.Binary(left, "<", right),
                        new CppAst.IntLiteral(-1), new CppAst.IntLiteral(0)));
        CppAst.Expression result = new CppAst.Conditional(nan,
                new CppAst.IntLiteral(compare.getNanResult().getValue()), ordered);
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(compare.getResult()), result));
    }

    private List<CppAst.Statement> emitLongCompare(IrNodes.LongCompare compare) {
        // LCMP is the ordered half of emitFloatingCompare on the signed i64
        // carrier: longs have no NaN, so there is no std::isnan guard. A
        // subtract-based lowering would misorder on overflow, so this stays a
        // direct signed three-way compare.
        CppAst.Expression left = new CppAst.Cast("int64_t",
                expression(compare.getLeft()));
        CppAst.Expression right = new CppAst.Cast("int64_t",
                expression(compare.getRight()));
        CppAst.Expression value = new CppAst.Conditional(
                new CppAst.Binary(left, ">", right),
                new CppAst.IntLiteral(1),
                new CppAst.Conditional(new CppAst.Binary(left, "<", right),
                        new CppAst.IntLiteral(-1), new CppAst.IntLiteral(0)));
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(compare.getResult()), value));
    }

    private CppAst.Expression shiftAmount(CppAst.Expression right) {
        return new CppAst.Binary(new CppAst.Cast("uint32_t", right), "&",
                new CppAst.IntLiteral(31));
    }

    private CppAst.Expression longShiftAmount(CppAst.Expression count) {
        return new CppAst.Binary(new CppAst.Cast("uint32_t", count), "&",
                new CppAst.IntLiteral(63));
    }

    private List<CppAst.Statement> emitUnary(IrNodes.Unary unary) {
        CppAst.Expression operand = expression(unary.getOperand());
        CppAst.Expression value;
        switch (unary.getOperation()) {
            case NEGATE:
                value = new CppAst.Cast("jint",
                        new CppAst.Unary("-", new CppAst.Cast("uint32_t", operand)));
                break;
            case I2B:
                value = new CppAst.Cast("jint", new CppAst.Cast("jbyte", operand));
                break;
            case I2S:
                value = new CppAst.Cast("jint", new CppAst.Cast("jshort", operand));
                break;
            case I2C:
                value = new CppAst.Cast("jint", new CppAst.Cast("jchar", operand));
                break;
            default:
                throw new IllegalStateException("Unknown unary operation "
                        + unary.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(unary.getResult()), value));
    }

    private List<CppAst.Statement> emitLongUnary(IrNodes.LongUnary unary) {
        CppAst.Expression operand = expression(unary.getOperand());
        CppAst.Expression value;
        switch (unary.getOperation()) {
            case NEGATE:
                // Negating Long.MIN_VALUE wraps to itself under JVM rules, so
                // negate on the unsigned carrier to avoid signed C++ overflow.
                value = new CppAst.Cast("jlong",
                        new CppAst.Unary("-", new CppAst.Cast("uint64_t", operand)));
                break;
            default:
                throw new IllegalStateException("Unknown long unary operation "
                        + unary.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.Assignment(variable(unary.getResult()), value));
    }

    private List<CppAst.Statement> emitConversion(IrNodes.Conversion conversion) {
        CppAst.Expression value;
        switch (conversion.getOperation()) {
            case I2L:
                value = new CppAst.Cast("jlong", expression(conversion.getOperand()));
                break;
            case L2I:
                value = new CppAst.Cast("jint", new CppAst.Cast("uint32_t",
                        expression(conversion.getOperand())));
                break;
            case I2F:
                value = new CppAst.Cast("jfloat", expression(conversion.getOperand()));
                break;
            case F2I:
                value = floatingToIntegral(conversion, IrType.I32);
                break;
            case L2F:
                value = new CppAst.Cast("jfloat", expression(conversion.getOperand()));
                break;
            case F2L:
                value = floatingToIntegral(conversion, IrType.I64);
                break;
            case I2D:
                value = new CppAst.Cast("jdouble", expression(conversion.getOperand()));
                break;
            case D2I:
                value = floatingToIntegral(conversion, IrType.I32);
                break;
            case L2D:
                value = new CppAst.Cast("jdouble", expression(conversion.getOperand()));
                break;
            case D2L:
                value = floatingToIntegral(conversion, IrType.I64);
                break;
            case F2D:
                value = new CppAst.Cast("jdouble", expression(conversion.getOperand()));
                break;
            case D2F:
                value = new CppAst.Cast("jfloat", expression(conversion.getOperand()));
                break;
            default:
                throw new IllegalStateException("Unknown conversion "
                        + conversion.getOperation());
        }
        return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                variable(conversion.getResult()), value));
    }

    private CppAst.Expression floatingToIntegral(IrNodes.Conversion conversion,
                                                 IrType resultType) {
        CppAst.Expression operand = expression(conversion.getOperand());
        String sourceType = conversion.getOperand().getType().getCppType();
        String targetType = resultType.getCppType();
        CppAst.Expression minimum = resultType == IrType.I32
                ? new CppAst.IntLiteral(Integer.MIN_VALUE)
                : new CppAst.LongLiteral(Long.MIN_VALUE);
        CppAst.Expression maximum = resultType == IrType.I32
                ? new CppAst.IntLiteral(Integer.MAX_VALUE)
                : new CppAst.LongLiteral(Long.MAX_VALUE);
        CppAst.Expression ordinary = new CppAst.Cast(targetType, operand);
        CppAst.Expression below = new CppAst.Conditional(
                new CppAst.Binary(operand, "<=", new CppAst.Cast(sourceType, minimum)),
                minimum, ordinary);
        CppAst.Expression above = new CppAst.Conditional(
                new CppAst.Binary(operand, ">=", new CppAst.Cast(sourceType, maximum)),
                maximum, below);
        return new CppAst.Conditional(
                new CppAst.Call("std::isnan", Collections.singletonList(operand)),
                new CppAst.IntLiteral(0), above);
    }

    private List<CppAst.Statement> emitIntDivRem(IrMethod method, IrBlock block,
                                                 IrNodes.IntDivRem binary,
                                                 MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<CppAst.Statement> divideByZero = new ArrayList<>();
        divideByZero.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/ArithmeticException")),
                        pool(context.getStringPool().getOffset(
                                binary.getOperation() == IrNodes.IntDivRem.Operation.DIVIDE
                                        ? "IDIV / by 0" : "IREM % by 0")),
                        new CppAst.IntLiteral(binary.getSourceLine())))));
        divideByZero.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(expression(binary.getRight()), "==",
                new CppAst.IntLiteral(0)), new CppAst.Block(divideByZero), null));

        CppAst.Expression overflow = new CppAst.Binary(
                new CppAst.Binary(expression(binary.getLeft()), "==",
                        new CppAst.IntLiteral(Integer.MIN_VALUE)),
                "&&",
                new CppAst.Binary(expression(binary.getRight()), "==",
                        new CppAst.IntLiteral(-1)));
        CppAst.Assignment overflowResult = new CppAst.Assignment(variable(binary.getResult()),
                new CppAst.IntLiteral(
                        binary.getOperation() == IrNodes.IntDivRem.Operation.DIVIDE
                                ? Integer.MIN_VALUE : 0));
        CppAst.Expression quotient = new CppAst.Cast("jint", new CppAst.Binary(
                new CppAst.Cast("int32_t", expression(binary.getLeft())),
                binary.getOperation().getCppOperator(),
                new CppAst.Cast("int32_t", expression(binary.getRight()))));
        CppAst.Assignment ordinaryResult = new CppAst.Assignment(
                variable(binary.getResult()), quotient);
        statements.add(new CppAst.If(overflow,
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(overflowResult)),
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(ordinaryResult))));
        return statements;
    }

    private List<CppAst.Statement> emitLongDivRem(IrMethod method, IrBlock block,
                                                  IrNodes.LongDivRem binary,
                                                  MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<CppAst.Statement> divideByZero = new ArrayList<>();
        divideByZero.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/ArithmeticException")),
                        pool(context.getStringPool().getOffset(
                                binary.getOperation() == IrNodes.LongDivRem.Operation.DIVIDE
                                        ? "LDIV / by 0" : "LREM % by 0")),
                        new CppAst.IntLiteral(binary.getSourceLine())))));
        divideByZero.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(expression(binary.getRight()), "==",
                new CppAst.LongLiteral(0)), new CppAst.Block(divideByZero), null));

        CppAst.Expression overflow = new CppAst.Binary(
                new CppAst.Binary(expression(binary.getLeft()), "==",
                        new CppAst.LongLiteral(Long.MIN_VALUE)),
                "&&",
                new CppAst.Binary(expression(binary.getRight()), "==",
                        new CppAst.LongLiteral(-1)));
        CppAst.Assignment overflowResult = new CppAst.Assignment(variable(binary.getResult()),
                binary.getOperation() == IrNodes.LongDivRem.Operation.DIVIDE
                        ? new CppAst.LongLiteral(Long.MIN_VALUE)
                        : new CppAst.LongLiteral(0));
        CppAst.Expression quotient = new CppAst.Cast("jlong", new CppAst.Binary(
                new CppAst.Cast("int64_t", expression(binary.getLeft())),
                binary.getOperation().getCppOperator(),
                new CppAst.Cast("int64_t", expression(binary.getRight()))));
        CppAst.Assignment ordinaryResult = new CppAst.Assignment(
                variable(binary.getResult()), quotient);
        statements.add(new CppAst.If(overflow,
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(overflowResult)),
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(ordinaryResult))));
        return statements;
    }

    private List<CppAst.Statement> emitStringConst(IrNodes.StringConst constant,
                                                   MethodContext context) {
        int stringId = context.getCachedStrings().getId(constant.getValue());
        return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                variable(constant.getResult()),
                new CppAst.Cast("jobject", array("cstrings", stringId))));
    }

    private List<CppAst.Statement> emitClassConst(IrMethod method, IrBlock block,
                                                  IrNodes.ClassConst constant,
                                                  MethodContext context) {
        int classId = context.getCachedClasses().getId(constant.getClassName());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                constant.getClassName());
        statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(constant.getResult()),
                new CppAst.Cast("jobject", array("cclasses", classId))));
        return statements;
    }

    private List<CppAst.Statement> emitNewObject(IrMethod method, IrBlock block,
                                                 IrNodes.NewObject object,
                                                 MethodContext context) {
        int classId = context.getCachedClasses().getId(object.getClassName());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                object.getClassName());
        statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(object.getResult()),
                new CppAst.Cast("jobject",
                        memberCall("env", "AllocObject", array("cclasses", classId)))));
        CppAst.Expression failed = new CppAst.Binary(
                new CppAst.Binary(expression(object.getResult()), "==",
                        new CppAst.NullLiteral()),
                "||",
                new CppAst.Binary(memberCall("env", "ExceptionCheck"), "!=",
                        new CppAst.IntLiteral(0)));
        statements.add(new CppAst.If(failed,
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitNewArray(IrMethod method, IrBlock block,
                                                IrNodes.NewArray array,
                                                MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        IrNodes.ArrayType arrayType = array.getArrayType();
        List<CppAst.Statement> negativeLength = new ArrayList<>();
        negativeLength.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/NegativeArraySizeException")),
                        pool(context.getStringPool().getOffset(
                                "NEWARRAY " + arrayType.getDisplayName() + " array size < 0")),
                        new CppAst.IntLiteral(array.getSourceLine())))));
        negativeLength.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(expression(array.getLength()), "<",
                new CppAst.IntLiteral(0)), new CppAst.Block(negativeLength), null));
        statements.add(new CppAst.Assignment(variable(array.getResult()),
                new CppAst.Cast("jobject",
                        memberCall("env", "New" + arrayType.getJniCarrier() + "Array",
                                expression(array.getLength())))));
        CppAst.Expression failed = new CppAst.Binary(
                new CppAst.Binary(expression(array.getResult()), "==",
                        new CppAst.NullLiteral()),
                "||",
                new CppAst.Binary(memberCall("env", "ExceptionCheck"), "!=",
                        new CppAst.IntLiteral(0)));
        statements.add(new CppAst.If(failed,
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitMultiNewArray(IrMethod method, IrBlock block,
                                                     IrNodes.MultiNewArray array,
                                                     MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        for (int dimension = 0; dimension < array.getDimensions().size(); dimension++) {
            List<CppAst.Statement> negative = new ArrayList<>();
            negative.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                    Arrays.asList(variable("env"),
                            pool(context.getStringPool().getOffset(
                                    "java/lang/NegativeArraySizeException")),
                            pool(context.getStringPool().getOffset(
                                    "MULTIANEWARRAY dimension " + dimension + " size < 0")),
                            new CppAst.IntLiteral(array.getSourceLine())))));
            negative.addAll(exceptionalExit(method, block));
            statements.add(new CppAst.If(new CppAst.Binary(
                    expression(array.getDimensions().get(dimension)), "<",
                    new CppAst.IntLiteral(0)), new CppAst.Block(negative), null));
        }

        Type arrayType = Type.getType(array.getDescriptor());
        Type elementType = arrayType.getElementType();
        List<CppAst.Expression> dimensions = new ArrayList<>();
        for (IrValue dimension : array.getDimensions()) {
            dimensions.add(expression(dimension));
        }
        List<CppAst.Expression> arguments = new ArrayList<>();
        arguments.add(variable("env"));
        if (elementType.getSort() == Type.OBJECT) {
            arguments.add(variable("classloader"));
        }
        arguments.add(new CppAst.IntLiteral(arrayType.getDimensions()));
        arguments.add(new CppAst.IntLiteral(array.getDimensions().size()));
        arguments.add(pool(context.getStringPool().getOffset(
                elementType.getSort() == Type.OBJECT
                        ? elementType.getInternalName() : elementType.getDescriptor())));
        arguments.add(new CppAst.IntLiteral(array.getSourceLine()));
        arguments.add(new CppAst.InitializerList(dimensions));

        CppAst.Expression allocation = elementType.getSort() == Type.OBJECT
                ? new CppAst.Call("utils::create_multidim_array", arguments)
                : new CppAst.TemplateCall("utils::create_multidim_array_value",
                elementType.getSort(), arguments);
        statements.add(new CppAst.Assignment(variable(array.getResult()),
                new CppAst.Cast("jobject", allocation)));
        CppAst.Expression failed = new CppAst.Binary(
                new CppAst.Binary(expression(array.getResult()), "==",
                        new CppAst.NullLiteral()),
                "||",
                new CppAst.Binary(memberCall("env", "ExceptionCheck"), "!=",
                        new CppAst.IntLiteral(0)));
        statements.add(new CppAst.If(failed,
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitNewObjectArray(IrMethod method, IrBlock block,
                                                      IrNodes.NewObjectArray array,
                                                      MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<CppAst.Statement> negativeLength = new ArrayList<>();
        negativeLength.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/NegativeArraySizeException")),
                        pool(context.getStringPool().getOffset(
                                "ANEWARRAY object array size < 0")),
                        new CppAst.IntLiteral(array.getSourceLine())))));
        negativeLength.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(expression(array.getLength()), "<",
                new CppAst.IntLiteral(0)), new CppAst.Block(negativeLength), null));

        int classId = context.getCachedClasses().getId(array.getComponentType());
        statements.addAll(emitClassCache(method, block, classId, context,
                array.getComponentType()));
        statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));

        statements.add(new CppAst.Assignment(variable(array.getResult()),
                new CppAst.Cast("jobject", memberCall("env", "NewObjectArray",
                        expression(array.getLength()), array("cclasses", classId),
                        new CppAst.NullLiteral()))));
        CppAst.Expression failed = new CppAst.Binary(
                new CppAst.Binary(expression(array.getResult()), "==",
                        new CppAst.NullLiteral()),
                "||",
                new CppAst.Binary(memberCall("env", "ExceptionCheck"), "!=",
                        new CppAst.IntLiteral(0)));
        statements.add(new CppAst.If(failed,
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitArrayLength(IrMethod method, IrBlock block,
                                                   IrNodes.ArrayLength length,
                                                   MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, length.getArray(), "ARRAYLENGTH npe",
                length.getSourceLine(), context, block));
        statements.add(new CppAst.Assignment(variable(length.getResult()),
                memberCall("env", "GetArrayLength",
                        new CppAst.Cast("jarray", expression(length.getArray())))));
        return statements;
    }

    private List<CppAst.Statement> emitArrayLoad(IrMethod method, IrBlock block,
                                                 IrNodes.ArrayLoad load,
                                                 MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        IrNodes.ArrayType arrayType = load.getArrayType();
        boolean reference = arrayType == IrNodes.ArrayType.REFERENCE;
        statements.add(nullCheck(method, load.getArray(),
                arrayType.getLoadMnemonic().toUpperCase(Locale.ROOT) + " npe",
                load.getSourceLine(), context, block));
        String temporary = arrayType.getLoadMnemonic() + arrayTemporaryId++;
        List<CppAst.Statement> scoped = new ArrayList<>();
        if (reference) {
            scoped.add(new CppAst.Declaration("jobject", temporary,
                    memberCall("env", "GetObjectArrayElement",
                            new CppAst.Cast("jobjectArray", expression(load.getArray())),
                            expression(load.getIndex()))));
        } else if (arrayType == IrNodes.ArrayType.BOOLEAN_OR_BYTE) {
            scoped.add(new CppAst.Declaration("jint", temporary,
                    new CppAst.Cast("jint", new CppAst.Call("utils::baload",
                            Arrays.asList(variable("env"),
                                    new CppAst.Cast("jarray",
                                            expression(load.getArray())),
                                    expression(load.getIndex()))))));
        } else {
            String jniType = arrayJniType(arrayType);
            scoped.add(new CppAst.Declaration(jniType, temporary,
                    new CppAst.IntLiteral(0)));
            scoped.add(new CppAst.ExpressionStatement(new CppAst.MemberCall(variable("env"),
                    true, "Get" + arrayType.getJniCarrier() + "ArrayRegion", Arrays.asList(
                            new CppAst.Cast(jniType + "Array", expression(load.getArray())),
                            expression(load.getIndex()), new CppAst.IntLiteral(1),
                            new CppAst.Unary("&", variable(temporary))))));
        }
        scoped.add(exceptionCheck(method, block));
        scoped.add(new CppAst.Assignment(variable(load.getResult()), variable(temporary)));
        statements.add(new CppAst.Block(scoped));
        return statements;
    }

    private List<CppAst.Statement> emitArrayStore(IrMethod method, IrBlock block,
                                                  IrNodes.ArrayStore store,
                                                  MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        IrNodes.ArrayType arrayType = store.getArrayType();
        boolean reference = arrayType == IrNodes.ArrayType.REFERENCE;
        statements.add(nullCheck(method, store.getArray(),
                arrayType.getStoreMnemonic().toUpperCase(Locale.ROOT) + " npe",
                store.getSourceLine(), context, block));
        List<CppAst.Statement> scoped = new ArrayList<>();
        if (reference) {
            scoped.add(new CppAst.ExpressionStatement(memberCall("env",
                    "SetObjectArrayElement",
                    new CppAst.Cast("jobjectArray", expression(store.getArray())),
                    expression(store.getIndex()), expression(store.getValue()))));
        } else if (arrayType == IrNodes.ArrayType.BOOLEAN_OR_BYTE) {
            scoped.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::bastore",
                    Arrays.asList(variable("env"),
                            new CppAst.Cast("jarray", expression(store.getArray())),
                            expression(store.getIndex()), expression(store.getValue())))));
        } else {
            String temporary = arrayType.getStoreMnemonic() + arrayTemporaryId++;
            String jniType = arrayJniType(arrayType);
            CppAst.Expression value = expression(store.getValue());
            if (arrayType == IrNodes.ArrayType.BOOLEAN) {
                value = new CppAst.Cast(jniType, new CppAst.Binary(
                        new CppAst.Cast("uint32_t", value), "&",
                        new CppAst.IntLiteral(1)));
            } else if (arrayType.getElementType() == IrType.I32
                    && arrayType != IrNodes.ArrayType.INT) {
                value = new CppAst.Cast(jniType, value);
            }
            scoped.add(new CppAst.Declaration(jniType, temporary, value));
            scoped.add(new CppAst.ExpressionStatement(new CppAst.MemberCall(variable("env"),
                    true, "Set" + arrayType.getJniCarrier() + "ArrayRegion", Arrays.asList(
                            new CppAst.Cast(jniType + "Array", expression(store.getArray())),
                            expression(store.getIndex()), new CppAst.IntLiteral(1),
                            new CppAst.Unary("&", variable(temporary))))));
        }
        scoped.add(exceptionCheck(method, block));
        statements.add(new CppAst.Block(scoped));
        return statements;
    }

    private String arrayJniType(IrNodes.ArrayType arrayType) {
        return "j" + arrayType.getJniCarrier().toLowerCase(Locale.ROOT);
    }

    private List<CppAst.Statement> emitStringLength(IrMethod method, IrBlock block,
                                                    IrNodes.StringLength length,
                                                    MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, length.getReceiver(), "String.length npe",
                length.getSourceLine(), context, block));
        statements.add(new CppAst.Assignment(variable(length.getResult()),
                memberCall("env", "GetStringLength",
                        new CppAst.Cast("jstring", expression(length.getReceiver())))));
        return statements;
    }

    private List<CppAst.Statement> emitCheckCast(IrMethod method, IrBlock block,
                                                 IrNodes.CheckCast checkCast,
                                                 MethodContext context) {
        int classId = context.getCachedClasses().getId(checkCast.getTargetType());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                checkCast.getTargetType());
        statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));

        CppAst.Expression nonNull = new CppAst.Binary(expression(checkCast.getOperand()), "!=",
                new CppAst.NullLiteral());
        CppAst.Expression notInstance = new CppAst.Binary(
                memberCall("env", "IsInstanceOf", expression(checkCast.getOperand()),
                        array("cclasses", classId)),
                "==", new CppAst.IntLiteral(0));
        List<CppAst.Statement> failed = new ArrayList<>();
        failed.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/ClassCastException")),
                        pool(context.getStringPool().getOffset(
                                "CHECKCAST " + checkCast.getTargetType())),
                        new CppAst.IntLiteral(checkCast.getSourceLine())))));
        failed.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(nonNull, "&&", notInstance),
                new CppAst.Block(failed), null));
        statements.add(new CppAst.Assignment(variable(checkCast.getResult()),
                expression(checkCast.getOperand())));
        return statements;
    }

    private List<CppAst.Statement> emitInstanceOf(IrMethod method, IrBlock block,
                                                  IrNodes.InstanceOf instanceOf,
                                                  MethodContext context) {
        int classId = context.getCachedClasses().getId(instanceOf.getTargetType());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                instanceOf.getTargetType());
        statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(instanceOf.getResult()),
                new CppAst.IntLiteral(0)));

        CppAst.Assignment test = new CppAst.Assignment(variable(instanceOf.getResult()),
                new CppAst.Cast("jint", memberCall("env", "IsInstanceOf",
                        expression(instanceOf.getOperand()), array("cclasses", classId))));
        statements.add(new CppAst.If(new CppAst.Binary(expression(instanceOf.getOperand()),
                "!=", new CppAst.NullLiteral()), new CppAst.Block(
                Collections.<CppAst.Statement>singletonList(test)), null));
        return statements;
    }

    private List<CppAst.Statement> emitMonitorEnter(IrMethod method, IrBlock block,
                                                    IrNodes.MonitorEnter monitor,
                                                    MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, monitor.getMonitor(), "MONITORENTER npe",
                monitor.getSourceLine(), context, block));
        statements.add(new CppAst.If(monitorOperationFailed("MonitorEnter",
                expression(monitor.getMonitor())),
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitMonitorExit(IrMethod method, IrBlock block,
                                                   IrNodes.MonitorExit monitor,
                                                   MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, monitor.getMonitor(), "MONITOREXIT npe",
                monitor.getSourceLine(), context, block));
        statements.add(new CppAst.If(monitorOperationFailed("MonitorExit",
                expression(monitor.getMonitor())),
                new CppAst.Block(exceptionalExit(method, block)), null));
        return statements;
    }

    private List<CppAst.Statement> emitGetField(IrMethod method, IrBlock block,
                                                IrNodes.GetField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(),
                "GETFIELD " + fieldCarrier(field.getDescriptor()) + " npe",
                field.getSourceLine(), context, block));
        CppAst.Expression read = memberCall("env",
                fieldAccessor(true, false, field.getDescriptor()),
                expression(field.getReceiver()), array("cfields", slots.memberId));
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                widenIntCarrier(Type.getType(field.getDescriptor()), read)));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitPutField(IrMethod method, IrBlock block,
                                                IrNodes.PutField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(),
                "PUTFIELD " + fieldCarrier(field.getDescriptor()) + " npe",
                field.getSourceLine(), context, block));
        statements.add(new CppAst.ExpressionStatement(
                memberCall("env", fieldAccessor(false, false, field.getDescriptor()),
                        expression(field.getReceiver()), array("cfields", slots.memberId),
                        narrowIntCarrier(Type.getType(field.getDescriptor()),
                                expression(field.getValue())))));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitGetStaticField(IrMethod method, IrBlock block,
                                                      IrNodes.GetStaticField field,
                                                      MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), true, context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        CppAst.Expression read = memberCall("env",
                fieldAccessor(true, true, field.getDescriptor()),
                array("cclasses", slots.classId), array("cfields", slots.memberId));
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                widenIntCarrier(Type.getType(field.getDescriptor()), read)));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitPutStaticField(IrMethod method, IrBlock block,
                                                      IrNodes.PutStaticField field,
                                                      MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), true, context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(new CppAst.ExpressionStatement(memberCall("env",
                fieldAccessor(false, true, field.getDescriptor()),
                array("cclasses", slots.classId), array("cfields", slots.memberId),
                narrowIntCarrier(Type.getType(field.getDescriptor()),
                        expression(field.getValue())))));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private String fieldAccessor(boolean get, boolean staticField, String descriptor) {
        return (get ? "Get" : "Set") + (staticField ? "Static" : "")
                + fieldCarrier(descriptor) + "Field";
    }

    private String fieldCarrier(String descriptor) {
        Type type = Type.getType(descriptor);
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return "Boolean";
            case Type.BYTE:
                return "Byte";
            case Type.CHAR:
                return "Char";
            case Type.SHORT:
                return "Short";
            case Type.INT:
                return "Int";
            case Type.LONG:
                return "Long";
            case Type.FLOAT:
                return "Float";
            case Type.DOUBLE:
                return "Double";
            case Type.OBJECT:
            case Type.ARRAY:
                return "Object";
            default:
                throw new IllegalArgumentException(
                        "Unsupported field descriptor " + descriptor);
        }
    }

    private List<CppAst.Statement> emitInvoke(IrMethod method, IrBlock block,
                                              IrNodes.Invoke invoke,
                                              MethodContext context) {
        MethodInsnNode bytecodeInvoke = new MethodInsnNode(Opcodes.INVOKESTATIC,
                invoke.getOwner(), invoke.getName(), invoke.getDescriptor(), false);
        if (PreprocessorUtils.isLookupLocal(bytecodeInvoke)) {
            List<CppAst.Statement> statements = new ArrayList<>();
            statements.add(new CppAst.If(new CppAst.Unary("!", variable("lookup")),
                    new CppAst.Block(Arrays.<CppAst.Statement>asList(
                            new CppAst.Assignment(variable("lookup"),
                                    new CppAst.Call("utils::get_lookup", Arrays.asList(
                                            variable("env"), variable("clazz")))),
                            exceptionCheck(method, block))), null));
            statements.add(new CppAst.Assignment(variable(invoke.getResult()),
                    variable("lookup")));
            return statements;
        }
        if (PreprocessorUtils.isClassLoaderLocal(bytecodeInvoke)) {
            return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                    variable(invoke.getResult()), variable("classloader")));
        }
        if (PreprocessorUtils.isClassLocal(bytecodeInvoke)) {
            return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                    variable(invoke.getResult()), variable("clazz")));
        }
        if (PreprocessorUtils.isLinkCallSiteMethod(bytecodeInvoke)) {
            List<CppAst.Expression> arguments = new ArrayList<>();
            arguments.add(variable("env"));
            for (IrValue argument : invoke.getArguments()) {
                arguments.add(expression(argument));
            }
            List<CppAst.Statement> statements = new ArrayList<>();
            statements.add(new CppAst.Assignment(variable(invoke.getResult()),
                    new CppAst.Call("utils::link_call_site", arguments)));
            statements.add(exceptionCheck(method, block));
            return statements;
        }
        if (PreprocessorUtils.isInvokeReverse(bytecodeInvoke)) {
            HiddenMethodsPool.HiddenMethod helper =
                    MethodHandleUtils.getInvokeReverseHelper(
                            context.obfuscator, invoke.getDescriptor());
            return emitInvoke(method, block, helperInvoke(invoke, helper,
                    invoke.getArguments()), context);
        }
        if ("java/lang/invoke/MethodHandle".equals(invoke.getOwner())
                && ("invokeExact".equals(invoke.getName())
                || "invoke".equals(invoke.getName()))
                && invoke.getKind() == IrNodes.Invoke.Kind.VIRTUAL) {
            HiddenMethodsPool.HiddenMethod helper =
                    MethodHandleUtils.getInvokeHelper(context.obfuscator,
                            context.clazz, invoke.getName(), invoke.getDescriptor());
            List<IrValue> arguments = new ArrayList<>();
            arguments.add(invoke.getReceiver());
            arguments.addAll(invoke.getArguments());
            return emitInvoke(method, block, helperInvoke(invoke, helper, arguments),
                    context);
        }

        boolean staticInvoke = invoke.getKind() == IrNodes.Invoke.Kind.STATIC;
        boolean specialInvoke = invoke.getKind() == IrNodes.Invoke.Kind.SPECIAL;
        CachedMethodInfo info = new CachedMethodInfo(invoke.getOwner(), invoke.getName(),
                invoke.getDescriptor(), staticInvoke);
        CacheSlots slots = cacheMember(method, invoke.getOwner(),
                context.getCachedMethods().getId(info), "cmethods",
                staticInvoke ? "GetStaticMethodID" : "GetMethodID",
                context.getStringPool().getOffset(invoke.getName()),
                context.getStringPool().getOffset(invoke.getDescriptor()), context, block);

        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        if (!staticInvoke) {
            statements.add(nullCheck(method, invoke.getReceiver(),
                    invoke.getKind().getMnemonic() + " npe",
                    invoke.getSourceLine(), context, block));
        }

        List<CppAst.Expression> arguments = new ArrayList<>();
        arguments.add(staticInvoke
                ? array("cclasses", slots.classId) : expression(invoke.getReceiver()));
        if (specialInvoke) {
            arguments.add(array("cclasses", slots.classId));
        }
        arguments.add(array("cmethods", slots.memberId));
        Type[] argumentTypes = Type.getArgumentTypes(invoke.getDescriptor());
        for (int i = 0; i < invoke.getArguments().size(); i++) {
            arguments.add(narrowIntCarrier(argumentTypes[i],
                    expression(invoke.getArguments().get(i))));
        }
        CppAst.Expression call = new CppAst.MemberCall(variable("env"), true,
                invokeCallMethod(invoke), arguments);
        if (invoke.getResult() == null) {
            statements.add(new CppAst.ExpressionStatement(call));
        } else {
            statements.add(new CppAst.Assignment(variable(invoke.getResult()),
                    widenIntCarrier(Type.getReturnType(invoke.getDescriptor()), call)));
        }
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private IrNodes.Invoke helperInvoke(IrNodes.Invoke original,
                                        HiddenMethodsPool.HiddenMethod helper,
                                        List<IrValue> arguments) {
        return new IrNodes.Invoke(original.getResult(), IrNodes.Invoke.Kind.STATIC,
                helper.getClassNode().name, helper.getMethodNode().name,
                helper.getMethodNode().desc, null, arguments,
                original.getBytecodeOffset(), original.getSourceLine());
    }

    private String invokeCallMethod(IrNodes.Invoke invoke) {
        String prefix;
        if (invoke.getKind() == IrNodes.Invoke.Kind.STATIC) {
            prefix = "CallStatic";
        } else if (invoke.getKind() == IrNodes.Invoke.Kind.SPECIAL) {
            prefix = "CallNonvirtual";
        } else {
            prefix = "Call";
        }
        Type returnType = Type.getReturnType(invoke.getDescriptor());
        String carrier;
        switch (returnType.getSort()) {
            case Type.VOID:
                carrier = "Void";
                break;
            case Type.BOOLEAN:
                carrier = "Boolean";
                break;
            case Type.BYTE:
                carrier = "Byte";
                break;
            case Type.CHAR:
                carrier = "Char";
                break;
            case Type.SHORT:
                carrier = "Short";
                break;
            case Type.INT:
                carrier = "Int";
                break;
            case Type.LONG:
                carrier = "Long";
                break;
            case Type.FLOAT:
                carrier = "Float";
                break;
            case Type.DOUBLE:
                carrier = "Double";
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                carrier = "Object";
                break;
            default:
                throw new IllegalStateException("Unsupported invoke return descriptor "
                        + returnType);
        }
        return prefix + carrier + "Method";
    }

    private CppAst.Expression narrowIntCarrier(Type type, CppAst.Expression value) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return new CppAst.Cast("jboolean", new CppAst.Binary(
                        new CppAst.Cast("uint32_t", value), "&",
                        new CppAst.IntLiteral(1)));
            case Type.BYTE:
                return new CppAst.Cast("jbyte", value);
            case Type.CHAR:
                return new CppAst.Cast("jchar", value);
            case Type.SHORT:
                return new CppAst.Cast("jshort", value);
            default:
                return value;
        }
    }

    private CppAst.Expression widenIntCarrier(Type type, CppAst.Expression value) {
        int sort = type.getSort();
        if (sort >= Type.BOOLEAN && sort < Type.INT) {
            return new CppAst.Cast("jint", value);
        }
        return value;
    }

    private CacheSlots cacheField(IrMethod method, String owner, String name,
                                  String descriptor, MethodContext context,
                                  IrBlock block) {
        return cacheField(method, owner, name, descriptor, false, context, block);
    }

    private CacheSlots cacheField(IrMethod method, String owner, String name,
                                  String descriptor, boolean staticField,
                                  MethodContext context, IrBlock block) {
        CachedFieldInfo info = new CachedFieldInfo(owner, name, descriptor, staticField);
        return cacheMember(method, owner, context.getCachedFields().getId(info), "cfields",
                staticField ? "GetStaticFieldID" : "GetFieldID",
                context.getStringPool().getOffset(name),
                context.getStringPool().getOffset(descriptor), context, block);
    }

    private CacheSlots cacheMember(IrMethod method, String owner, int memberId,
                                   String memberArray, String lookupMethod, long nameOffset,
                                   long descriptorOffset, MethodContext context,
                                   IrBlock block) {
        int classId = context.getCachedClasses().getId(owner);
        List<CppAst.Statement> statements = emitClassCache(method, block, classId,
                context, owner);

        CppAst.Expression memberSlot = array(memberArray, memberId);
        CppAst.Expression lookup = memberCall("env", lookupMethod,
                array("cclasses", classId), pool(nameOffset), pool(descriptorOffset));
        List<CppAst.Statement> initializeMember = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(memberSlot, lookup),
                exceptionCheck(method, block));
        statements.add(new CppAst.If(new CppAst.Unary("!", memberSlot),
                new CppAst.Block(initializeMember), null));
        return new CacheSlots(classId, memberId, statements);
    }

    private List<CppAst.Statement> emitClassCache(IrMethod method, IrBlock block,
                                                  int classId, MethodContext context,
                                                  String className) {
        CppAst.Expression classSlot = array("cclasses", classId);
        CppAst.Expression cacheMissing = new CppAst.Binary(
                new CppAst.Unary("!", classSlot), "||",
                memberCall("env", "IsSameObject", classSlot, new CppAst.NullLiteral()));

        String resolvedName = "resolved_class_" + classId;
        CppAst.Variable resolved = variable(resolvedName);
        CppAst.Expression classLookup;
        if (className.startsWith("[")) {
            classLookup = memberCall("env", "FindClass",
                    pool(context.getStringPool().getOffset(className)));
        } else {
            int classStringId = context.getCachedStrings().getId(
                    className.replace('/', '.'));
            classLookup = new CppAst.Call("utils::find_class_wo_static", Arrays.asList(
                    variable("env"), variable("classloader"),
                    array("cstrings", classStringId)));
        }
        List<CppAst.Statement> publishClass = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(classSlot,
                        new CppAst.Cast("jclass", memberCall("env", "NewWeakGlobalRef", resolved))),
                new CppAst.ExpressionStatement(
                        memberCall("env", "DeleteLocalRef", resolved)));
        List<CppAst.Statement> resolveClass = Arrays.<CppAst.Statement>asList(
                new CppAst.Declaration("jclass", resolvedName, classLookup),
                new CppAst.If(new CppAst.Binary(resolved, "!=", new CppAst.NullLiteral()),
                        new CppAst.Block(publishClass), null));

        CppAst.Expression mutex = array("cclasses_mtx", classId);
        List<CppAst.Statement> initializeClass = Arrays.<CppAst.Statement>asList(
                new CppAst.ExpressionStatement(
                        new CppAst.MemberCall(mutex, false, "lock",
                                Collections.<CppAst.Expression>emptyList())),
                new CppAst.If(cacheMissing, new CppAst.Block(resolveClass), null),
                new CppAst.ExpressionStatement(
                        new CppAst.MemberCall(mutex, false, "unlock",
                                Collections.<CppAst.Expression>emptyList())),
                exceptionCheck(method, block));
        return new ArrayList<>(Collections.<CppAst.Statement>singletonList(
                new CppAst.If(cacheMissing, new CppAst.Block(initializeClass), null)));
    }

    private List<CppAst.Statement> emitSynchronizedEnter(IrMethod method) {
        return Collections.<CppAst.Statement>singletonList(new CppAst.If(
                monitorOperationFailed("MonitorEnter", synchronizedMonitor(method)),
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                        defaultReturn(method))), null));
    }

    private CppAst.Expression synchronizedMonitor(IrMethod method) {
        return variable(method.isStaticMethod() ? "clazz" : "obj");
    }

    private CppAst.Expression monitorOperationFailed(String operation,
                                                     CppAst.Expression monitor) {
        CppAst.Expression failedStatus = new CppAst.Binary(
                memberCall("env", operation, monitor), "!=", new CppAst.IntLiteral(0));
        CppAst.Expression pendingException = new CppAst.Binary(
                memberCall("env", "ExceptionCheck"), "!=", new CppAst.IntLiteral(0));
        return new CppAst.Binary(failedStatus, "||", pendingException);
    }

    private CppAst.Statement nullCheck(IrMethod method, IrValue receiver, String error,
                                       int sourceLine, MethodContext context,
                                       IrBlock block) {
        CppAst.Expression throwCall = new CppAst.Call("utils::throw_re", Arrays.asList(
                variable("env"),
                pool(context.getStringPool().getOffset("java/lang/NullPointerException")),
                pool(context.getStringPool().getOffset(error)),
                new CppAst.IntLiteral(sourceLine)));
        List<CppAst.Statement> failed = new ArrayList<>();
        failed.add(new CppAst.ExpressionStatement(throwCall));
        failed.addAll(exceptionalExit(method, block));
        return new CppAst.If(
                new CppAst.Binary(expression(receiver), "==", new CppAst.NullLiteral()),
                new CppAst.Block(failed),
                null);
    }

    private CppAst.Statement exceptionCheck(IrMethod method, IrBlock block) {
        return new CppAst.If(new CppAst.Binary(
                memberCall("env", "ExceptionCheck"), "!=", new CppAst.IntLiteral(0)),
                new CppAst.Block(exceptionalExit(method, block)), null);
    }

    private List<CppAst.Statement> exceptionalExit(IrMethod method, IrBlock block) {
        if (block.getExceptionEdges().isEmpty()) {
            return Collections.<CppAst.Statement>singletonList(earlyReturn(method));
        }
        List<CppAst.Statement> statements = new ArrayList<>();
        Set<IrBlock> transferredHandlers = new LinkedHashSet<>();
        for (IrExceptionEdge edge : block.getExceptionEdges()) {
            if (transferredHandlers.add(edge.getHandler())) {
                statements.addAll(phiCopies(block, edge.getHandler()));
            }
        }
        String dispatch = dispatchLabels.get(new HandlerSet(block.getExceptionEdges()));
        if (dispatch == null) {
            throw new IllegalStateException("Missing shared exception dispatch");
        }
        statements.add(new CppAst.Goto(dispatch));
        return statements;
    }

    private CppAst.Statement earlyReturn(IrMethod method) {
        if (!method.isSynchronizedMethod()) {
            return defaultReturn(method);
        }
        CppAst.Variable savedException = variable("synchronized_exception");
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(new CppAst.Declaration("jthrowable", "synchronized_exception",
                memberCall("env", "ExceptionOccurred")));
        statements.add(new CppAst.ExpressionStatement(
                memberCall("env", "ExceptionClear")));
        statements.add(new CppAst.If(
                monitorOperationFailed("MonitorExit", synchronizedMonitor(method)),
                new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                        defaultReturn(method))), null));
        statements.add(new CppAst.If(new CppAst.Binary(savedException, "!=",
                new CppAst.NullLiteral()), new CppAst.Block(
                Collections.<CppAst.Statement>singletonList(
                        new CppAst.ExpressionStatement(
                                memberCall("env", "Throw", savedException)))), null));
        statements.add(defaultReturn(method));
        return new CppAst.Block(statements);
    }

    private CppAst.Return defaultReturn(IrMethod method) {
        CppAst.Expression defaultValue;
        if (method.getReturnType() == IrType.VOID) {
            defaultValue = null;
        } else if (method.getReturnType() == IrType.REFERENCE) {
            defaultValue = new CppAst.NullLiteral();
        } else {
            defaultValue = new CppAst.IntLiteral(0);
        }
        return new CppAst.Return(defaultValue);
    }

    private CppAst.MemberCall memberCall(String receiver, String method,
                                         CppAst.Expression... arguments) {
        return new CppAst.MemberCall(variable(receiver), true, method,
                Arrays.asList(arguments));
    }

    private CppAst.ArrayAccess array(String name, int index) {
        return new CppAst.ArrayAccess(name, index);
    }

    private CppAst.StringPoolPointer pool(long offset) {
        return new CppAst.StringPoolPointer(offset);
    }

    private void emitTerminator(IrMethod method, MethodContext context,
                                List<CppAst.Statement> statements, IrBlock predecessor,
                                IrTerminator terminator) {
        if (terminator instanceof IrNodes.Goto) {
            IrBlock target = ((IrNodes.Goto) terminator).getTarget();
            statements.add(new CppAst.Block(edgeTransfer(predecessor, target)));
            return;
        }
        if (terminator instanceof IrNodes.Branch) {
            IrNodes.Branch branch = (IrNodes.Branch) terminator;
            CppAst.Expression right = branch.getRight() == null
                    ? new CppAst.IntLiteral(0) : expression(branch.getRight());
            CppAst.Expression condition = new CppAst.Binary(expression(branch.getLeft()),
                    branch.getCondition().getCppOperator(), right);
            statements.add(new CppAst.If(condition,
                    new CppAst.Block(edgeTransfer(predecessor, branch.getTrueTarget())),
                    new CppAst.Block(edgeTransfer(predecessor, branch.getFalseTarget()))));
            return;
        }
        if (terminator instanceof IrNodes.ReferenceBranch) {
            IrNodes.ReferenceBranch branch = (IrNodes.ReferenceBranch) terminator;
            CppAst.Expression condition = new CppAst.Binary(
                    expression(branch.getReference()),
                    branch.getCondition().getCppOperator(), new CppAst.NullLiteral());
            statements.add(new CppAst.If(condition,
                    new CppAst.Block(edgeTransfer(predecessor, branch.getTrueTarget())),
                    new CppAst.Block(edgeTransfer(predecessor, branch.getFalseTarget()))));
            return;
        }
        if (terminator instanceof IrNodes.ReferenceCompareBranch) {
            IrNodes.ReferenceCompareBranch branch =
                    (IrNodes.ReferenceCompareBranch) terminator;
            CppAst.Expression condition = new CppAst.Binary(
                    expression(branch.getLeft()),
                    branch.getCondition().getCppOperator(),
                    expression(branch.getRight()));
            statements.add(new CppAst.If(condition,
                    new CppAst.Block(edgeTransfer(predecessor, branch.getTrueTarget())),
                    new CppAst.Block(edgeTransfer(predecessor, branch.getFalseTarget()))));
            return;
        }
        if (terminator instanceof IrNodes.Switch) {
            IrNodes.Switch switchTerminator = (IrNodes.Switch) terminator;
            List<CppAst.Block> cases = new ArrayList<>();
            for (IrBlock target : switchTerminator.getTargets()) {
                cases.add(new CppAst.Block(edgeTransfer(predecessor, target)));
            }
            statements.add(new CppAst.Switch(expression(switchTerminator.getSelector()),
                    switchTerminator.getKeys(), cases,
                    new CppAst.Block(edgeTransfer(predecessor,
                            switchTerminator.getDefaultTarget()))));
            return;
        }
        if (terminator instanceof IrNodes.Return) {
            IrValue value = ((IrNodes.Return) terminator).getValue();
            CppAst.Expression returned = value == null ? null : expression(value);
            Type returnType = Type.getReturnType(method.getDescriptor());
            if (value != null) {
                if (value.getType() == IrType.REFERENCE
                        && returnType.getSort() == Type.ARRAY) {
                    returned = new CppAst.Cast("jarray", returned);
                } else if (value.getType() == IrType.I32) {
                    returned = narrowIntCarrier(returnType, returned);
                }
            }
            if (method.isSynchronizedMethod()) {
                statements.add(new CppAst.If(
                        monitorOperationFailed("MonitorExit", synchronizedMonitor(method)),
                        new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                                defaultReturn(method))), null));
            }
            statements.add(new CppAst.Return(returned));
            return;
        }
        if (terminator instanceof IrNodes.Throw) {
            if (context == null) {
                throw new IllegalStateException("ATHROW requires a method emission context");
            }
            IrValue exception = ((IrNodes.Throw) terminator).getException();
            CppAst.Expression throwNull = new CppAst.Call("utils::throw_re", Arrays.asList(
                    variable("env"),
                    pool(context.getStringPool().getOffset(
                            "java/lang/NullPointerException")),
                    pool(context.getStringPool().getOffset("ATHROW npe")),
                    new CppAst.IntLiteral(-1)));
            CppAst.Expression throwExisting = memberCall("env", "Throw",
                    new CppAst.Cast("jthrowable", expression(exception)));
            statements.add(new CppAst.If(new CppAst.Binary(expression(exception), "==",
                    new CppAst.NullLiteral()), new CppAst.Block(
                    Collections.<CppAst.Statement>singletonList(
                            new CppAst.ExpressionStatement(throwNull))),
                    new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                            new CppAst.ExpressionStatement(throwExisting)))));
            statements.add(exceptionCheck(method, predecessor));
            statements.add(earlyReturn(method));
            return;
        }
        throw new IllegalStateException("Unknown IR terminator " + terminator.getClass());
    }

    private List<CppAst.Statement> edgeTransfer(IrBlock predecessor, IrBlock target) {
        List<CppAst.Statement> statements = phiCopies(predecessor, target);
        statements.add(new CppAst.Goto(label(target)));
        return statements;
    }

    private List<CppAst.Statement> phiCopies(IrBlock predecessor, IrBlock target) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<String> temporaryNames = new ArrayList<>();
        for (IrPhi phi : target.getPhis()) {
            IrValue incoming = phi.getIncoming().get(predecessor);
            if (incoming == null) {
                throw new IllegalStateException("Missing incoming value from "
                        + predecessor.getName() + " to " + target.getName());
            }
            String temporary = "edge" + edgeTemporaryId++;
            temporaryNames.add(temporary);
            statements.add(new CppAst.Declaration(phi.getResult().getType().getCppType(),
                    temporary, expression(incoming)));
        }
        for (int i = 0; i < target.getPhis().size(); i++) {
            statements.add(new CppAst.Assignment(variable(target.getPhis().get(i).getResult()),
                    new CppAst.Variable(temporaryNames.get(i))));
        }
        return statements;
    }

    private Map<HandlerSet, String> collectDispatchLabels(IrMethod method) {
        Map<HandlerSet, String> labels = new LinkedHashMap<>();
        for (IrBlock block : method.getBlocks()) {
            if (block.getExceptionEdges().isEmpty()) {
                continue;
            }
            HandlerSet handlers = new HandlerSet(block.getExceptionEdges());
            if (!labels.containsKey(handlers)) {
                labels.put(handlers, "IR_CATCH_" + labels.size());
            }
        }
        return labels;
    }

    private void emitDispatches(IrMethod method, MethodContext context,
                                List<CppAst.Statement> statements) {
        for (Map.Entry<HandlerSet, String> dispatch : dispatchLabels.entrySet()) {
            statements.add(new CppAst.Label(dispatch.getValue()));
            statements.add(new CppAst.Assignment(variable("caught_exception"),
                    memberCall("env", "ExceptionOccurred")));
            statements.add(new CppAst.ExpressionStatement(
                    memberCall("env", "ExceptionClear")));

            boolean catchAll = false;
            for (IrExceptionEdge edge : dispatch.getKey().getEdges()) {
                if (edge.getCatchType() == null) {
                    statements.add(new CppAst.Goto(label(edge.getHandler())));
                    catchAll = true;
                    break;
                }
                int classId = context.getCachedClasses().getId(edge.getCatchType());
                CppAst.Expression matches = memberCall("env", "IsInstanceOf",
                        new CppAst.Cast("jobject", variable("caught_exception")),
                        array("cclasses", classId));
                statements.add(new CppAst.If(matches,
                        new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                                new CppAst.Goto(label(edge.getHandler())))), null));
            }
            if (!catchAll) {
                statements.add(new CppAst.ExpressionStatement(memberCall("env", "Throw",
                        variable("caught_exception"))));
                statements.add(earlyReturn(method));
            }
        }
    }

    private CppAst.Expression expression(IrValue value) {
        if (value.getKind() == IrValue.Kind.PARAMETER) {
            return new CppAst.Variable(value.getCppParameterName());
        }
        return variable(value);
    }

    private CppAst.Variable variable(IrValue value) {
        return new CppAst.Variable(variableName(value));
    }

    private CppAst.Variable variable(String name) {
        return new CppAst.Variable(name);
    }

    private String variableName(IrValue value) {
        return "v" + value.getId();
    }

    private String label(IrBlock block) {
        return "B" + block.getId();
    }

    private static final class CacheSlots {
        private final int classId;
        private final int memberId;
        private final List<CppAst.Statement> initialization;

        private CacheSlots(int classId, int memberId,
                           List<CppAst.Statement> initialization) {
            this.classId = classId;
            this.memberId = memberId;
            this.initialization = initialization;
        }
    }

    private static final class HandlerSet {
        private final List<IrExceptionEdge> edges;
        private final List<String> catchTypes;
        private final List<Integer> handlerIds;

        private HandlerSet(List<IrExceptionEdge> edges) {
            this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
            List<String> types = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (IrExceptionEdge edge : edges) {
                types.add(edge.getCatchType());
                ids.add(edge.getHandler().getId());
            }
            this.catchTypes = Collections.unmodifiableList(types);
            this.handlerIds = Collections.unmodifiableList(ids);
        }

        private List<IrExceptionEdge> getEdges() {
            return edges;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HandlerSet)) {
                return false;
            }
            HandlerSet that = (HandlerSet) other;
            return catchTypes.equals(that.catchTypes) && handlerIds.equals(that.handlerIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catchTypes, handlerIds);
        }
    }
}
