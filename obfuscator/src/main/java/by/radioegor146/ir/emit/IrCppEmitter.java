package by.radioegor146.ir.emit;

import by.radioegor146.CachedFieldInfo;
import by.radioegor146.CachedMethodInfo;
import by.radioegor146.DirectNativeCallMode;
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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Emits the typed IR directly through {@link CppAst}.
 */
public final class IrCppEmitter {
    private int edgeTemporaryId;
    private int arrayTemporaryId;
    private Map<HandlerSet, String> dispatchLabels;
    private IrInstruction currentInstruction;
    private Set<IrBlock> blocksReachingUnsafeJni;
    private boolean membersHoisted;
    private Map<Integer, String> liveClassLocals;
    private Set<Integer> hoistedFieldIds;
    private Set<Integer> hoistedMethodIds;
    private boolean arrayLengthCacheEnabled;
    private boolean intFieldCacheEnabled;
    private boolean intArrayPinEnabled;

    public String emitBody(IrMethod method) {
        return emitBody(method, null);
    }

    public String emitBody(IrMethod method, MethodContext context) {
        edgeTemporaryId = 0;
        arrayTemporaryId = 0;
        currentInstruction = null;
        membersHoisted = false;
        liveClassLocals = new LinkedHashMap<Integer, String>();
        hoistedFieldIds = new HashSet<Integer>();
        hoistedMethodIds = new HashSet<Integer>();
        arrayLengthCacheEnabled = methodHasArrayAccess(method);
        intFieldCacheEnabled = methodHasIntInstanceField(method);
        intArrayPinEnabled = methodHasIntArrayAccess(method);
        dispatchLabels = collectDispatchLabels(method);
        analyzeUnsafeJniReachability(method);
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(new CppAst.Comment("IR codegen: " + method.getOwner() + "."
                + method.getName() + method.getDescriptor()));

        // Declare all SSA carriers before labels so C++ gotos never cross an
        // initialized automatic variable.
        if (!dispatchLabels.isEmpty()) {
            statements.add(new CppAst.Declaration("jthrowable", "caught_exception"));
        }
        Set<IrValue> declared = new LinkedHashSet<>();
        for (IrBlock block : method.getBlocks()) {
            for (IrPhi phi : block.getPhis()) {
                if (declared.add(phi.getResult())) {
                    statements.add(declaration(phi.getResult()));
                }
            }
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction.getResult() != null
                        && declared.add(instruction.getResult())) {
                    statements.add(declaration(instruction.getResult()));
                }
                if (instruction instanceof IrNodes.Assign) {
                    IrValue target = ((IrNodes.Assign) instruction).getTarget();
                    if (declared.add(target)) {
                        statements.add(declaration(target));
                    }
                }
            }
        }
        if (arrayLengthCacheEnabled) {
            statements.add(new CppAst.Declaration("jobject", "arr_len_cached_array",
                    new CppAst.NullLiteral()));
            statements.add(new CppAst.Declaration("jsize", "arr_len_cached",
                    new CppAst.IntLiteral(0)));
        }
        if (intFieldCacheEnabled) {
            statements.add(new CppAst.Declaration("jobject", "fld_cached_obj",
                    new CppAst.NullLiteral()));
            statements.add(new CppAst.Declaration("jfieldID", "fld_cached_id",
                    new CppAst.NullLiteral()));
            statements.add(new CppAst.Declaration("jint", "fld_cached_i",
                    new CppAst.IntLiteral(0)));
            statements.add(new CppAst.Declaration("jint", "fld_cached_dirty",
                    new CppAst.IntLiteral(0)));
        }
        if (intArrayPinEnabled) {
            statements.add(new CppAst.Declaration("jobject", "pin_array",
                    new CppAst.NullLiteral()));
            statements.add(new CppAst.Declaration("jint*", "pin_int_elems",
                    new CppAst.NullLiteral()));
            statements.add(new CppAst.Declaration("jboolean", "pin_is_copy",
                    new CppAst.IntLiteral(0)));
            statements.add(new CppAst.Declaration("jint", "pin_dirty",
                    new CppAst.IntLiteral(0)));
        }
        if (method.isSynchronizedMethod()) {
            statements.addAll(emitSynchronizedEnter(method));
        }
        statements.addAll(initializeEntryPhis(method));
        if (context != null && !method.getBlocks().isEmpty()) {
            statements.addAll(hoistMembers(method, context));
            membersHoisted = true;
        }

        for (IrBlock block : method.getBlocks()) {
            statements.add(new CppAst.Label(label(block)));
            for (IrInstruction instruction : block.getInstructions()) {
                currentInstruction = instruction;
                statements.addAll(withCacheCommit(instruction,
                        emitInstruction(method, block, instruction, context)));
            }
            currentInstruction = null;
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
        if (instruction instanceof IrNodes.Assign) {
            IrNodes.Assign assign = (IrNodes.Assign) instruction;
            return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                    variable(assign.getTarget()), expression(assign.getSource())));
        }
        if (instruction instanceof IrNodes.OpaqueTrue) {
            return Collections.<CppAst.Statement>singletonList(new CppAst.Assignment(
                    variable(instruction.getResult()),
                    new CppAst.Call("utils::cf_opaque_true",
                            Collections.singletonList(variable("env")))));
        }
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
        if (instruction instanceof IrNodes.Intrinsic) {
            return emitIntrinsic(method, block, (IrNodes.Intrinsic) instruction, context);
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
        statements.add(new CppAst.If(new CppAst.Unary("!", classExpr(classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(constant.getResult()),
                new CppAst.Cast("jobject", classExpr(classId))));
        return statements;
    }

    private List<CppAst.Statement> emitNewObject(IrMethod method, IrBlock block,
                                                 IrNodes.NewObject object,
                                                 MethodContext context) {
        int classId = context.getCachedClasses().getId(object.getClassName());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                object.getClassName());
        statements.add(new CppAst.If(new CppAst.Unary("!", classExpr(classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(object.getResult()),
                new CppAst.Cast("jobject",
                        memberCall("env", "AllocObject", classExpr(classId)))));
        statements.add(exitIfNull(method, block, expression(object.getResult())));
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
        statements.add(exitIfNull(method, block, expression(array.getResult())));
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
        statements.add(exitIfNull(method, block, expression(array.getResult())));
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
        statements.add(new CppAst.If(new CppAst.Unary("!", classExpr(classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));

        statements.add(new CppAst.Assignment(variable(array.getResult()),
                new CppAst.Cast("jobject", memberCall("env", "NewObjectArray",
                        expression(array.getLength()), classExpr(classId),
                        new CppAst.NullLiteral()))));
        statements.add(exitIfNull(method, block, expression(array.getResult())));
        return statements;
    }

    private List<CppAst.Statement> emitArrayLength(IrMethod method, IrBlock block,
                                                   IrNodes.ArrayLength length,
                                                   MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, length.getArray(), "ARRAYLENGTH npe",
                length.getSourceLine(), context, block));
        if (arrayLengthCacheEnabled) {
            statements.addAll(refreshArrayLengthCache(length.getArray()));
            statements.add(new CppAst.Assignment(variable(length.getResult()),
                    variable("arr_len_cached")));
        } else {
            statements.add(new CppAst.Assignment(variable(length.getResult()),
                    memberCall("env", "GetArrayLength",
                            new CppAst.Cast("jarray", expression(length.getArray())))));
        }
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
        scoped.addAll(arrayBoundsCheck(method, block, load.getArray(), load.getIndex(),
                arrayType.getLoadMnemonic().toUpperCase(Locale.ROOT) + " oob",
                load.getSourceLine(), context));
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
        } else if (intArrayPinEnabled && arrayType == IrNodes.ArrayType.INT) {
            scoped.addAll(pinIntArray(method, block, load.getArray()));
            scoped.add(new CppAst.Declaration("jint", temporary,
                    new CppAst.Subscript(variable("pin_int_elems"),
                            expression(load.getIndex()))));
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
        scoped.addAll(arrayBoundsCheck(method, block, store.getArray(), store.getIndex(),
                arrayType.getStoreMnemonic().toUpperCase(Locale.ROOT) + " oob",
                store.getSourceLine(), context));
        if (reference) {
            scoped.add(new CppAst.ExpressionStatement(memberCall("env",
                    "SetObjectArrayElement",
                    new CppAst.Cast("jobjectArray", expression(store.getArray())),
                    expression(store.getIndex()), expression(store.getValue()))));
            addPoll(scoped, method, block);
        } else if (arrayType == IrNodes.ArrayType.BOOLEAN_OR_BYTE) {
            scoped.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::bastore",
                    Arrays.asList(variable("env"),
                            new CppAst.Cast("jarray", expression(store.getArray())),
                            expression(store.getIndex()), expression(store.getValue())))));
        } else if (intArrayPinEnabled && arrayType == IrNodes.ArrayType.INT) {
            scoped.addAll(pinIntArray(method, block, store.getArray()));
            scoped.add(new CppAst.Assignment(
                    new CppAst.Subscript(variable("pin_int_elems"),
                            expression(store.getIndex())),
                    expression(store.getValue())));
            scoped.add(new CppAst.Assignment(variable("pin_dirty"),
                    new CppAst.IntLiteral(1)));
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

    private List<CppAst.Statement> emitIntrinsic(IrMethod method, IrBlock block,
                                                 IrNodes.Intrinsic intrinsic,
                                                 MethodContext context) {
        List<IrValue> arguments = intrinsic.getArguments();
        List<CppAst.Statement> statements = new ArrayList<>();
        switch (intrinsic.getKind()) {
            case STRING_HASH_CODE:
                statements.add(nullCheck(method, arguments.get(0), "String.hashCode npe",
                        intrinsic.getSourceLine(), context, block));
                statements.add(exitIfFalse(method, block, new CppAst.Call(
                        "utils::string_hash_code", Arrays.asList(
                        variable("env"),
                        new CppAst.Cast("jstring", expression(arguments.get(0))),
                        new CppAst.Unary("&", variable(intrinsic.getResult()))))));
                return statements;
            case STRING_CHAR_AT:
                statements.add(nullCheck(method, arguments.get(0), "String.charAt npe",
                        intrinsic.getSourceLine(), context, block));
                statements.add(exitIfFalse(method, block, new CppAst.Call(
                        "utils::string_char_at", Arrays.asList(
                        variable("env"),
                        new CppAst.Cast("jstring", expression(arguments.get(0))),
                        expression(arguments.get(1)),
                        new CppAst.Unary("&", variable(intrinsic.getResult()))))));
                return statements;
            case STRING_IS_EMPTY:
                statements.add(nullCheck(method, arguments.get(0), "String.isEmpty npe",
                        intrinsic.getSourceLine(), context, block));
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Conditional(
                                new CppAst.Binary(
                                        memberCall("env", "GetStringLength",
                                                new CppAst.Cast("jstring",
                                                        expression(arguments.get(0)))),
                                        "==", new CppAst.IntLiteral(0)),
                                new CppAst.IntLiteral(1),
                                new CppAst.IntLiteral(0))));
                return statements;
            case ARRAYCOPY:
                statements.add(exitIfFalse(method, block, new CppAst.Call(
                        "utils::arraycopy", Arrays.asList(
                        variable("env"),
                        expression(arguments.get(0)),
                        expression(arguments.get(1)),
                        expression(arguments.get(2)),
                        expression(arguments.get(3)),
                        expression(arguments.get(4))))));
                return statements;
            case MATH_ABS_I:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        wrappingAbs(expression(arguments.get(0)), false)));
                return statements;
            case MATH_ABS_L:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        wrappingAbs(expression(arguments.get(0)), true)));
                return statements;
            case MATH_MIN_I:
            case MATH_MAX_I:
            case MATH_MIN_L:
            case MATH_MAX_L:
                boolean max = intrinsic.getKind() == IrNodes.Intrinsic.Kind.MATH_MAX_I
                        || intrinsic.getKind() == IrNodes.Intrinsic.Kind.MATH_MAX_L;
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Conditional(
                                new CppAst.Binary(expression(arguments.get(0)),
                                        max ? ">" : "<",
                                        expression(arguments.get(1))),
                                expression(arguments.get(0)),
                                expression(arguments.get(1)))));
                return statements;
            case BIT_COUNT_I:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Call("utils::bit_count_i",
                                Collections.singletonList(expression(arguments.get(0))))));
                return statements;
            case BIT_COUNT_L:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Call("utils::bit_count_j",
                                Collections.singletonList(expression(arguments.get(0))))));
                return statements;
            case NUMBER_OF_LEADING_ZEROS_I:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Call("utils::number_of_leading_zeros_i",
                                Collections.singletonList(expression(arguments.get(0))))));
                return statements;
            case NUMBER_OF_LEADING_ZEROS_L:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Call("utils::number_of_leading_zeros_j",
                                Collections.singletonList(expression(arguments.get(0))))));
                return statements;
            case SDK_ABI_VERSION:
                statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                        new CppAst.Call("native_obfuscator::sdk::abi_version",
                                Collections.emptyList())));
                return statements;
            case SDK_SHA256:
                return emitSdkCall(method, block, intrinsic,
                        "native_obfuscator::sdk::sha256", false);
            case SDK_HMAC_SHA256:
                return emitSdkCall(method, block, intrinsic,
                        "native_obfuscator::sdk::hmac_sha256", false);
            case SDK_AES256_GCM_ENCRYPT:
                return emitSdkCall(method, block, intrinsic,
                        "native_obfuscator::sdk::aes256_gcm_encrypt", false);
            case SDK_AES256_GCM_DECRYPT:
                return emitSdkCall(method, block, intrinsic,
                        "native_obfuscator::sdk::aes256_gcm_decrypt", false);
            case SDK_CONSTANT_TIME_EQUALS:
                return emitSdkCall(method, block, intrinsic,
                        "native_obfuscator::sdk::constant_time_equals", false);
            case SDK_STRING_LENGTH:
                return emitSdkCall(method, block, intrinsic,
                        "native_jvm::strings::length", true);
            case SDK_STRING_HASH_CODE:
                return emitSdkCall(method, block, intrinsic,
                        "native_jvm::strings::hash_code", true);
            case SDK_STRING_CONCAT:
                return emitSdkCall(method, block, intrinsic,
                        "native_jvm::strings::concat", true);
            default:
                throw new IllegalStateException("Unknown intrinsic " + intrinsic.getKind());
        }
    }

    private List<CppAst.Statement> emitSdkCall(IrMethod method, IrBlock block,
                                               IrNodes.Intrinsic intrinsic,
                                               String function, boolean stringArgs) {
        List<CppAst.Expression> args = new ArrayList<>();
        args.add(variable("env"));
        for (IrValue argument : intrinsic.getArguments()) {
            CppAst.Expression expr = expression(argument);
            expr = new CppAst.Cast(stringArgs ? "jstring" : "jbyteArray", expr);
            args.add(expr);
        }
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(new CppAst.Assignment(variable(intrinsic.getResult()),
                new CppAst.Call(function, args)));
        if (intrinsic.getResult() != null
                && intrinsic.getResult().getType() == IrType.REFERENCE) {
            statements.add(exitIfNull(method, block, expression(intrinsic.getResult())));
        } else {
            addPoll(statements, method, block);
        }
        return statements;
    }

    private CppAst.Expression wrappingAbs(CppAst.Expression value, boolean wide) {
        String unsignedType = wide ? "uint64_t" : "uint32_t";
        String signedType = wide ? "jlong" : "jint";
        CppAst.Expression zero = wide
                ? new CppAst.LongLiteral(0L) : new CppAst.IntLiteral(0);
        return new CppAst.Conditional(
                new CppAst.Binary(value, ">=", zero),
                value,
                new CppAst.Cast(signedType, new CppAst.Binary(
                        new CppAst.Cast(unsignedType, zero),
                        "-",
                        new CppAst.Cast(unsignedType, value))));
    }

    private List<CppAst.Statement> emitCheckCast(IrMethod method, IrBlock block,
                                                 IrNodes.CheckCast checkCast,
                                                 MethodContext context) {
        int classId = context.getCachedClasses().getId(checkCast.getTargetType());
        List<CppAst.Statement> statements = emitClassCache(method, block, classId, context,
                checkCast.getTargetType());
        statements.add(new CppAst.If(new CppAst.Unary("!", classExpr(classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));

        CppAst.Expression nonNull = new CppAst.Binary(expression(checkCast.getOperand()), "!=",
                new CppAst.NullLiteral());
        CppAst.Expression notInstance = new CppAst.Binary(
                memberCall("env", "IsInstanceOf", expression(checkCast.getOperand()),
                        classExpr(classId)),
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
        statements.add(new CppAst.If(new CppAst.Unary("!", classExpr(classId)),
                new CppAst.Block(exceptionalExit(method, block)), null));
        statements.add(new CppAst.Assignment(variable(instanceOf.getResult()),
                new CppAst.IntLiteral(0)));

        CppAst.Assignment test = new CppAst.Assignment(variable(instanceOf.getResult()),
                new CppAst.Cast("jint", memberCall("env", "IsInstanceOf",
                        expression(instanceOf.getOperand()), classExpr(classId))));
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
        CppAst.Expression fieldId = array("cfields", slots.memberId);
        if (intFieldCacheEnabled && cacheableIntInstanceField(field.getOwner(),
                field.getName(), field.getDescriptor(), context)) {
            CppAst.Expression receiver = expression(field.getReceiver());
            CppAst.Expression miss = new CppAst.Binary(
                    new CppAst.Binary(receiver, "!=", variable("fld_cached_obj")),
                    "||",
                    new CppAst.Binary(fieldId, "!=", variable("fld_cached_id")));
            List<CppAst.Statement> load = new ArrayList<>();
            load.addAll(flushIntFieldCache());
            load.add(new CppAst.Assignment(variable("fld_cached_obj"), receiver));
            load.add(new CppAst.Assignment(variable("fld_cached_id"), fieldId));
            load.add(new CppAst.Assignment(variable("fld_cached_i"),
                    memberCall("env", "GetIntField", receiver, fieldId)));
            load.add(new CppAst.Assignment(variable("fld_cached_dirty"),
                    new CppAst.IntLiteral(0)));
            statements.add(new CppAst.If(miss, new CppAst.Block(load), null));
            statements.add(new CppAst.Assignment(variable(field.getResult()),
                    variable("fld_cached_i")));
            return statements;
        }
        CppAst.Expression read = memberCall("env",
                fieldAccessor(true, false, field.getDescriptor()),
                expression(field.getReceiver()), fieldId);
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                widenIntCarrier(Type.getType(field.getDescriptor()), read)));
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
        CppAst.Expression fieldId = array("cfields", slots.memberId);
        if (intFieldCacheEnabled && cacheableIntInstanceField(field.getOwner(),
                field.getName(), field.getDescriptor(), context)) {
            CppAst.Expression receiver = expression(field.getReceiver());
            CppAst.Expression miss = new CppAst.Binary(
                    new CppAst.Binary(receiver, "!=", variable("fld_cached_obj")),
                    "||",
                    new CppAst.Binary(fieldId, "!=", variable("fld_cached_id")));
            List<CppAst.Statement> switchField = new ArrayList<>();
            switchField.addAll(flushIntFieldCache());
            switchField.add(new CppAst.Assignment(variable("fld_cached_obj"), receiver));
            switchField.add(new CppAst.Assignment(variable("fld_cached_id"), fieldId));
            statements.add(new CppAst.If(miss, new CppAst.Block(switchField), null));
            statements.add(new CppAst.Assignment(variable("fld_cached_i"),
                    expression(field.getValue())));
            statements.add(new CppAst.Assignment(variable("fld_cached_dirty"),
                    new CppAst.IntLiteral(1)));
            return statements;
        }
        statements.add(new CppAst.ExpressionStatement(
                memberCall("env", fieldAccessor(false, false, field.getDescriptor()),
                        expression(field.getReceiver()), fieldId,
                        narrowIntCarrier(Type.getType(field.getDescriptor()),
                                expression(field.getValue())))));
        return statements;
    }

    private List<CppAst.Statement> emitGetStaticField(IrMethod method, IrBlock block,
                                                      IrNodes.GetStaticField field,
                                                      MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), true, context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        Type fieldType = Type.getType(field.getDescriptor());
        CppAst.Expression read = memberCall("env",
                fieldAccessor(true, true, field.getDescriptor()),
                classExpr(slots.classId), array("cfields", slots.memberId));
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                widenIntCarrier(fieldType, read)));
        addPollAfterCall(statements, method, block,
                fieldType.getSort() == Type.OBJECT || fieldType.getSort() == Type.ARRAY
                        ? expression(field.getResult()) : null);
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
                classExpr(slots.classId), array("cfields", slots.memberId),
                narrowIntCarrier(Type.getType(field.getDescriptor()),
                        expression(field.getValue())))));
        addPoll(statements, method, block);
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
                            exitIfNull(method, block, variable("lookup")))), null));
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
            statements.add(exitIfNull(method, block, expression(invoke.getResult())));
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

        if (canDirectNativeCall(invoke, context)) {
            return emitDirectNativeCall(method, block, invoke, context);
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
                ? classExpr(slots.classId) : expression(invoke.getReceiver()));
        if (specialInvoke) {
            arguments.add(classExpr(slots.classId));
        }
        arguments.add(array("cmethods", slots.memberId));
        Type[] argumentTypes = Type.getArgumentTypes(invoke.getDescriptor());
        for (int i = 0; i < invoke.getArguments().size(); i++) {
            arguments.add(narrowIntCarrier(argumentTypes[i],
                    expression(invoke.getArguments().get(i))));
        }
        CppAst.Expression call = new CppAst.MemberCall(variable("env"), true,
                invokeCallMethod(invoke), arguments);
        Type returnType = Type.getReturnType(invoke.getDescriptor());
        if (invoke.getResult() == null) {
            statements.add(new CppAst.ExpressionStatement(call));
            addPoll(statements, method, block);
        } else {
            statements.add(new CppAst.Assignment(variable(invoke.getResult()),
                    widenIntCarrier(returnType, call)));
            addPollAfterCall(statements, method, block,
                    returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY
                            ? expression(invoke.getResult()) : null);
        }
        return statements;
    }

    private boolean canDirectNativeCall(IrNodes.Invoke invoke, MethodContext context) {
        if (context == null || !context.directNativeCall.enabled()) {
            return false;
        }
        if (invoke.getKind() != IrNodes.Invoke.Kind.STATIC) {
            return false;
        }
        if (context.clazz == null || !invoke.getOwner().equals(context.clazz.name)) {
            return false;
        }
        MethodNode callee = findOwnMethod(context, invoke.getName(), invoke.getDescriptor());
        if (!DirectNativeCallMode.calleeEligible(context.clazz, callee)) {
            return false;
        }
        if (invoke.getName().equals(context.method.name)
                && invoke.getDescriptor().equals(context.method.desc)) {
            return true;
        }
        Map<String, String> names = context.sameClassDirectNativeNames;
        return names != null
                && names.containsKey(invoke.getName() + invoke.getDescriptor());
    }

    private MethodNode findOwnMethod(MethodContext context, String name, String descriptor) {
        List<MethodNode> methods = context.clazz.methods;
        if (methods == null) {
            return null;
        }
        for (int i = 0; i < methods.size(); i++) {
            MethodNode candidate = methods.get(i);
            if (name.equals(candidate.name) && descriptor.equals(candidate.desc)) {
                return candidate;
            }
        }
        return null;
    }

    private String directNativeCppName(IrNodes.Invoke invoke, MethodContext context) {
        if (invoke.getName().equals(context.method.name)
                && invoke.getDescriptor().equals(context.method.desc)) {
            return MethodShellEmitter.cppNativeFunctionName(
                    context.method, context.methodIndex);
        }
        return context.sameClassDirectNativeNames.get(
                invoke.getName() + invoke.getDescriptor());
    }

    private List<CppAst.Statement> emitDirectNativeCall(IrMethod method, IrBlock block,
                                                        IrNodes.Invoke invoke,
                                                        MethodContext context) {
        List<CppAst.Statement> scoped = new ArrayList<CppAst.Statement>();
        scoped.add(new CppAst.If(
                new CppAst.Binary(memberCall("env", "PushLocalFrame",
                        new CppAst.IntLiteral(64)), "!=", new CppAst.IntLiteral(0)),
                new CppAst.Block(exceptionalExit(method, block)), null));

        List<CppAst.Expression> arguments = new ArrayList<CppAst.Expression>();
        arguments.add(variable("env"));
        arguments.add(variable("clazz"));
        Type[] argumentTypes = Type.getArgumentTypes(invoke.getDescriptor());
        for (int i = 0; i < invoke.getArguments().size(); i++) {
            arguments.add(narrowIntCarrier(argumentTypes[i],
                    expression(invoke.getArguments().get(i))));
        }
        CppAst.Expression call = new CppAst.Call(
                directNativeCppName(invoke, context), arguments);
        Type returnType = Type.getReturnType(invoke.getDescriptor());
        boolean objectResult = returnType.getSort() == Type.OBJECT
                || returnType.getSort() == Type.ARRAY;
        if (invoke.getResult() == null) {
            scoped.add(new CppAst.ExpressionStatement(call));
            scoped.add(new CppAst.ExpressionStatement(
                    memberCall("env", "PopLocalFrame", new CppAst.NullLiteral())));
            addPoll(scoped, method, block);
            return Collections.<CppAst.Statement>singletonList(new CppAst.Block(scoped));
        }
        if (objectResult) {
            String temporary = "direct_local" + arrayTemporaryId++;
            scoped.add(new CppAst.Declaration("jobject", temporary,
                    new CppAst.Cast("jobject", call)));
            scoped.add(new CppAst.Assignment(variable(invoke.getResult()),
                    memberCall("env", "PopLocalFrame", variable(temporary))));
            addPollAfterCall(scoped, method, block, expression(invoke.getResult()));
            return Collections.<CppAst.Statement>singletonList(new CppAst.Block(scoped));
        }
        scoped.add(new CppAst.Assignment(variable(invoke.getResult()),
                widenIntCarrier(returnType, call)));
        scoped.add(new CppAst.ExpressionStatement(
                memberCall("env", "PopLocalFrame", new CppAst.NullLiteral())));
        addPollAfterCall(scoped, method, block, null);
        return Collections.<CppAst.Statement>singletonList(new CppAst.Block(scoped));
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
        boolean hoisted = "cfields".equals(memberArray)
                ? hoistedFieldIds.contains(memberId)
                : hoistedMethodIds.contains(memberId);
        if (hoisted) {
            return new CacheSlots(classId, memberId, Collections.<CppAst.Statement>emptyList());
        }
        List<CppAst.Statement> statements = emitClassCache(method, block, classId,
                context, owner);

        CppAst.Expression memberSlot = array(memberArray, memberId);
        CppAst.Expression lookup = memberCall("env", lookupMethod,
                classExpr(classId), pool(nameOffset), pool(descriptorOffset));
        List<CppAst.Statement> initializeMember = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(memberSlot, lookup),
                exitIfNull(method, block, memberSlot));
        statements.add(new CppAst.If(new CppAst.Unary("!", memberSlot),
                new CppAst.Block(initializeMember), null));
        return new CacheSlots(classId, memberId, statements);
    }

    private CppAst.Expression classExpr(int classId) {
        String live = liveClassLocals.get(classId);
        if (live != null) {
            return variable(live);
        }
        return array("cclasses", classId);
    }

    private List<CppAst.Statement> emitClassCache(IrMethod method, IrBlock block,
                                                  int classId, MethodContext context,
                                                  String className) {
        if (membersHoisted && liveClassLocals.containsKey(classId)) {
            return new ArrayList<CppAst.Statement>();
        }
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
        return new CppAst.Binary(
                memberCall("env", operation, monitor), "!=", new CppAst.IntLiteral(0));
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

    private void addPoll(List<CppAst.Statement> statements, IrMethod method, IrBlock block) {
        if (!shouldPollPending(block)) {
            return;
        }
        statements.add(exceptionCheck(method, block));
    }

    private void addPollAfterCall(List<CppAst.Statement> statements, IrMethod method,
                                  IrBlock block, CppAst.Expression objectResult) {
        if (hasLaterUnsafeJni(block)) {
            statements.add(exceptionCheck(method, block));
            return;
        }
        if (block.getExceptionEdges().isEmpty()) {
            return;
        }
        if (objectResult == null) {
            statements.add(exceptionCheck(method, block));
            return;
        }
        CppAst.Expression pending = new CppAst.Binary(
                new CppAst.Binary(objectResult, "==", new CppAst.NullLiteral()),
                "&&",
                new CppAst.Binary(memberCall("env", "ExceptionCheck"), "!=",
                        new CppAst.IntLiteral(0)));
        statements.add(new CppAst.If(pending,
                new CppAst.Block(exceptionalExit(method, block)), null));
    }

    private CppAst.Statement exitIfNull(IrMethod method, IrBlock block,
                                        CppAst.Expression value) {
        return new CppAst.If(new CppAst.Binary(value, "==", new CppAst.NullLiteral()),
                new CppAst.Block(exceptionalExit(method, block)), null);
    }

    private CppAst.Statement exitIfFalse(IrMethod method, IrBlock block,
                                         CppAst.Expression value) {
        return new CppAst.If(new CppAst.Unary("!", value),
                new CppAst.Block(exceptionalExit(method, block)), null);
    }

    private List<CppAst.Statement> arrayBoundsCheck(IrMethod method, IrBlock block,
                                                    IrValue array, IrValue index,
                                                    String error, int sourceLine,
                                                    MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        CppAst.Expression length;
        if (arrayLengthCacheEnabled) {
            statements.addAll(refreshArrayLengthCache(array));
            length = variable("arr_len_cached");
        } else {
            String lengthName = "array_len" + arrayTemporaryId++;
            statements.add(new CppAst.Declaration("jsize", lengthName,
                    memberCall("env", "GetArrayLength",
                            new CppAst.Cast("jarray", expression(array)))));
            length = variable(lengthName);
        }
        List<CppAst.Statement> failed = new ArrayList<>();
        failed.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/ArrayIndexOutOfBoundsException")),
                        pool(context.getStringPool().getOffset(error)),
                        new CppAst.IntLiteral(sourceLine)))));
        failed.addAll(exceptionalExit(method, block));
        CppAst.Expression outOfBounds = new CppAst.Binary(
                new CppAst.Binary(expression(index), "<", new CppAst.IntLiteral(0)),
                "||",
                new CppAst.Binary(expression(index), ">=", length));
        statements.add(new CppAst.If(outOfBounds, new CppAst.Block(failed), null));
        return statements;
    }

    private boolean methodHasArrayAccess(IrMethod method) {
        for (IrBlock block : method.getBlocks()) {
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IrNodes.ArrayLoad
                        || instruction instanceof IrNodes.ArrayStore
                        || instruction instanceof IrNodes.ArrayLength) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean methodHasIntArrayAccess(IrMethod method) {
        for (IrBlock block : method.getBlocks()) {
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IrNodes.ArrayLoad
                        && ((IrNodes.ArrayLoad) instruction).getArrayType()
                        == IrNodes.ArrayType.INT) {
                    return true;
                }
                if (instruction instanceof IrNodes.ArrayStore
                        && ((IrNodes.ArrayStore) instruction).getArrayType()
                        == IrNodes.ArrayType.INT) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean methodHasIntInstanceField(IrMethod method) {
        for (IrBlock block : method.getBlocks()) {
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IrNodes.GetField
                        && "I".equals(((IrNodes.GetField) instruction).getDescriptor())) {
                    return true;
                }
                if (instruction instanceof IrNodes.PutField
                        && "I".equals(((IrNodes.PutField) instruction).getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean cacheableIntInstanceField(String owner, String name, String descriptor,
                                             MethodContext context) {
        if (!"I".equals(descriptor) || context == null || context.clazz == null) {
            return false;
        }
        if (!owner.equals(context.clazz.name) || context.clazz.fields == null) {
            return false;
        }
        for (int i = 0; i < context.clazz.fields.size(); i++) {
            FieldNode field = context.clazz.fields.get(i);
            if (name.equals(field.name) && descriptor.equals(field.desc)) {
                return (field.access & Opcodes.ACC_VOLATILE) == 0
                        && (field.access & Opcodes.ACC_STATIC) == 0;
            }
        }
        return false;
    }

    private List<CppAst.Statement> withCacheCommit(IrInstruction instruction,
                                                   List<CppAst.Statement> body) {
        if (!commitsCaches(instruction)) {
            return body;
        }
        List<CppAst.Statement> statements = new ArrayList<>(commitCaches());
        statements.addAll(body);
        return statements;
    }

    private boolean commitsCaches(IrInstruction instruction) {
        return instruction instanceof IrNodes.Invoke
                || instruction instanceof IrNodes.MonitorEnter
                || instruction instanceof IrNodes.MonitorExit
                || instruction instanceof IrNodes.GetStaticField
                || instruction instanceof IrNodes.PutStaticField
                || instruction instanceof IrNodes.NewObject
                || instruction instanceof IrNodes.NewArray
                || instruction instanceof IrNodes.NewObjectArray
                || instruction instanceof IrNodes.MultiNewArray
                || (instruction instanceof IrNodes.Intrinsic
                && performsUnsafeJni(instruction));
    }

    private List<CppAst.Statement> commitCaches() {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.addAll(flushIntFieldCache());
        statements.addAll(invalidateIntFieldCache());
        statements.addAll(releasePinnedArray());
        return statements;
    }

    private List<CppAst.Statement> flushIntFieldCache() {
        if (!intFieldCacheEnabled) {
            return Collections.emptyList();
        }
        List<CppAst.Statement> flush = Arrays.<CppAst.Statement>asList(
                new CppAst.ExpressionStatement(memberCall("env", "SetIntField",
                        variable("fld_cached_obj"), variable("fld_cached_id"),
                        variable("fld_cached_i"))),
                new CppAst.Assignment(variable("fld_cached_dirty"),
                        new CppAst.IntLiteral(0)));
        return Collections.<CppAst.Statement>singletonList(new CppAst.If(
                new CppAst.Binary(variable("fld_cached_dirty"), "!=",
                        new CppAst.IntLiteral(0)), new CppAst.Block(flush), null));
    }

    private List<CppAst.Statement> invalidateIntFieldCache() {
        if (!intFieldCacheEnabled) {
            return Collections.emptyList();
        }
        return Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(variable("fld_cached_obj"), new CppAst.NullLiteral()),
                new CppAst.Assignment(variable("fld_cached_id"), new CppAst.NullLiteral()),
                new CppAst.Assignment(variable("fld_cached_dirty"),
                        new CppAst.IntLiteral(0)));
    }

    private List<CppAst.Statement> releasePinnedArray() {
        if (!intArrayPinEnabled) {
            return Collections.emptyList();
        }
        CppAst.Expression mode = new CppAst.Conditional(
                new CppAst.Binary(variable("pin_dirty"), "!=", new CppAst.IntLiteral(0)),
                new CppAst.IntLiteral(0),
                new CppAst.IntLiteral(2));
        List<CppAst.Statement> release = Arrays.<CppAst.Statement>asList(
                new CppAst.ExpressionStatement(memberCall("env", "ReleaseIntArrayElements",
                        new CppAst.Cast("jintArray", variable("pin_array")),
                        variable("pin_int_elems"), mode)),
                new CppAst.Assignment(variable("pin_array"), new CppAst.NullLiteral()),
                new CppAst.Assignment(variable("pin_int_elems"), new CppAst.NullLiteral()),
                new CppAst.Assignment(variable("pin_dirty"), new CppAst.IntLiteral(0)));
        return Collections.<CppAst.Statement>singletonList(new CppAst.If(
                new CppAst.Binary(variable("pin_array"), "!=", new CppAst.NullLiteral()),
                new CppAst.Block(release), null));
    }

    private List<CppAst.Statement> pinIntArray(IrMethod method, IrBlock block, IrValue array) {
        List<CppAst.Statement> refresh = new ArrayList<>();
        refresh.addAll(releasePinnedArray());
        refresh.add(new CppAst.Assignment(variable("pin_int_elems"),
                memberCall("env", "GetIntArrayElements",
                        new CppAst.Cast("jintArray", expression(array)),
                        new CppAst.Unary("&", variable("pin_is_copy")))));
        refresh.add(exitIfNull(method, block, variable("pin_int_elems")));
        refresh.add(new CppAst.Assignment(variable("pin_array"), expression(array)));
        refresh.add(new CppAst.Assignment(variable("pin_dirty"), new CppAst.IntLiteral(0)));
        return Collections.<CppAst.Statement>singletonList(new CppAst.If(
                new CppAst.Binary(expression(array), "!=", variable("pin_array")),
                new CppAst.Block(refresh), null));
    }

    private List<CppAst.Statement> refreshArrayLengthCache(IrValue array) {
        List<CppAst.Statement> refresh = new ArrayList<>();
        refresh.add(new CppAst.Assignment(variable("arr_len_cached_array"),
                expression(array)));
        refresh.add(new CppAst.Assignment(variable("arr_len_cached"),
                memberCall("env", "GetArrayLength",
                        new CppAst.Cast("jarray", expression(array)))));
        return Collections.<CppAst.Statement>singletonList(new CppAst.If(
                new CppAst.Binary(expression(array), "!=",
                        variable("arr_len_cached_array")),
                new CppAst.Block(refresh), null));
    }

    private List<CppAst.Statement> hoistMembers(IrMethod method, MethodContext context) {
        IrBlock entry = method.getBlocks().get(0);
        Map<Integer, String> classNames = new LinkedHashMap<Integer, String>();
        List<Object[]> fieldMembers = new ArrayList<Object[]>();
        List<Object[]> methodMembers = new ArrayList<Object[]>();
        collectMemberNeeds(method, context, classNames, fieldMembers, methodMembers);

        List<CppAst.Statement> statements = new ArrayList<>();
        List<Integer> foreignClasses = new ArrayList<Integer>();
        for (Map.Entry<Integer, String> entryClass : classNames.entrySet()) {
            int classId = entryClass.getKey();
            String className = entryClass.getValue();
            if (className.equals(method.getOwner())) {
                liveClassLocals.put(classId, "clazz");
                continue;
            }
            String liveName = "live_c" + classId;
            statements.add(new CppAst.Declaration("jclass", liveName));
            liveClassLocals.put(classId, liveName);
            foreignClasses.add(classId);
        }
        for (int classId : foreignClasses) {
            String className = classNames.get(classId);
            statements.addAll(emitClassCache(method, entry, classId, context, className));
            statements.add(new CppAst.If(new CppAst.Unary("!", array("cclasses", classId)),
                    new CppAst.Block(exceptionalExit(method, entry)), null));
            statements.add(new CppAst.Assignment(variable(liveClassLocals.get(classId)),
                    new CppAst.Cast("jclass", memberCall("env", "NewLocalRef",
                            array("cclasses", classId)))));
            statements.add(new CppAst.If(new CppAst.Unary("!",
                    variable(liveClassLocals.get(classId))),
                    new CppAst.Block(exceptionalExit(method, entry)), null));
        }
        for (Object[] field : fieldMembers) {
            statements.addAll(hoistMemberLookup(method, entry,
                    (Integer) field[0], (Integer) field[1], "cfields",
                    (Boolean) field[2] ? "GetStaticFieldID" : "GetFieldID",
                    (Long) field[3], (Long) field[4]));
        }
        for (Object[] methodNeed : methodMembers) {
            statements.addAll(hoistMemberLookup(method, entry,
                    (Integer) methodNeed[0], (Integer) methodNeed[1], "cmethods",
                    (Boolean) methodNeed[2] ? "GetStaticMethodID" : "GetMethodID",
                    (Long) methodNeed[3], (Long) methodNeed[4]));
        }
        return statements;
    }

    private List<CppAst.Statement> hoistMemberLookup(IrMethod method, IrBlock entry,
                                                     int classId, int memberId,
                                                     String memberArray, String lookupMethod,
                                                     long nameOffset, long descriptorOffset) {
        CppAst.Expression memberSlot = array(memberArray, memberId);
        CppAst.Expression lookup = memberCall("env", lookupMethod,
                classExpr(classId), pool(nameOffset), pool(descriptorOffset));
        List<CppAst.Statement> initializeMember = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(memberSlot, lookup),
                exitIfNull(method, entry, memberSlot));
        return Collections.<CppAst.Statement>singletonList(
                new CppAst.If(new CppAst.Unary("!", memberSlot),
                        new CppAst.Block(initializeMember), null));
    }

    private void collectMemberNeeds(IrMethod method, MethodContext context,
                                    Map<Integer, String> classNames,
                                    List<Object[]> fieldMembers, List<Object[]> methodMembers) {
        Set<Integer> seenFields = new HashSet<Integer>();
        Set<Integer> seenMethods = new HashSet<Integer>();
        for (IrBlock block : method.getBlocks()) {
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IrNodes.ClassConst) {
                    noteClass(classNames, context,
                            ((IrNodes.ClassConst) instruction).getClassName());
                } else if (instruction instanceof IrNodes.NewObject) {
                    noteClass(classNames, context,
                            ((IrNodes.NewObject) instruction).getClassName());
                } else if (instruction instanceof IrNodes.NewObjectArray) {
                    noteClass(classNames, context,
                            ((IrNodes.NewObjectArray) instruction).getComponentType());
                } else if (instruction instanceof IrNodes.CheckCast) {
                    noteClass(classNames, context,
                            ((IrNodes.CheckCast) instruction).getTargetType());
                } else if (instruction instanceof IrNodes.InstanceOf) {
                    noteClass(classNames, context,
                            ((IrNodes.InstanceOf) instruction).getTargetType());
                } else if (instruction instanceof IrNodes.GetField) {
                    IrNodes.GetField field = (IrNodes.GetField) instruction;
                    noteField(classNames, fieldMembers, seenFields, context, field.getOwner(),
                            field.getName(), field.getDescriptor(), false);
                } else if (instruction instanceof IrNodes.PutField) {
                    IrNodes.PutField field = (IrNodes.PutField) instruction;
                    noteField(classNames, fieldMembers, seenFields, context, field.getOwner(),
                            field.getName(), field.getDescriptor(), false);
                } else if (instruction instanceof IrNodes.GetStaticField) {
                    IrNodes.GetStaticField field = (IrNodes.GetStaticField) instruction;
                    noteField(classNames, fieldMembers, seenFields, context, field.getOwner(),
                            field.getName(), field.getDescriptor(), true);
                } else if (instruction instanceof IrNodes.PutStaticField) {
                    IrNodes.PutStaticField field = (IrNodes.PutStaticField) instruction;
                    noteField(classNames, fieldMembers, seenFields, context, field.getOwner(),
                            field.getName(), field.getDescriptor(), true);
                } else if (instruction instanceof IrNodes.Invoke) {
                    IrNodes.Invoke invoke = (IrNodes.Invoke) instruction;
                    if (isCachedInvoke(invoke)) {
                        noteMethod(classNames, methodMembers, seenMethods, context, invoke);
                    }
                }
            }
        }
    }

    private void noteClass(Map<Integer, String> classNames, MethodContext context,
                           String className) {
        int classId = context.getCachedClasses().getId(className);
        if (!classNames.containsKey(classId)) {
            classNames.put(classId, className);
        }
    }

    private void noteField(Map<Integer, String> classNames, List<Object[]> fieldMembers,
                           Set<Integer> seenFields, MethodContext context, String owner,
                           String name, String descriptor, boolean staticField) {
        noteClass(classNames, context, owner);
        CachedFieldInfo info = new CachedFieldInfo(owner, name, descriptor, staticField);
        int memberId = context.getCachedFields().getId(info);
        if (!seenFields.add(memberId)) {
            return;
        }
        hoistedFieldIds.add(memberId);
        fieldMembers.add(new Object[]{
                context.getCachedClasses().getId(owner),
                memberId,
                staticField,
                context.getStringPool().getOffset(name),
                context.getStringPool().getOffset(descriptor)
        });
    }

    private void noteMethod(Map<Integer, String> classNames, List<Object[]> methodMembers,
                            Set<Integer> seenMethods, MethodContext context,
                            IrNodes.Invoke invoke) {
        noteClass(classNames, context, invoke.getOwner());
        boolean staticInvoke = invoke.getKind() == IrNodes.Invoke.Kind.STATIC;
        CachedMethodInfo info = new CachedMethodInfo(invoke.getOwner(), invoke.getName(),
                invoke.getDescriptor(), staticInvoke);
        int memberId = context.getCachedMethods().getId(info);
        if (!seenMethods.add(memberId)) {
            return;
        }
        hoistedMethodIds.add(memberId);
        methodMembers.add(new Object[]{
                context.getCachedClasses().getId(invoke.getOwner()),
                memberId,
                staticInvoke,
                context.getStringPool().getOffset(invoke.getName()),
                context.getStringPool().getOffset(invoke.getDescriptor())
        });
    }

    private boolean isCachedInvoke(IrNodes.Invoke invoke) {
        MethodInsnNode bytecodeInvoke = new MethodInsnNode(Opcodes.INVOKESTATIC,
                invoke.getOwner(), invoke.getName(), invoke.getDescriptor(), false);
        if (PreprocessorUtils.isLookupLocal(bytecodeInvoke)
                || PreprocessorUtils.isClassLoaderLocal(bytecodeInvoke)
                || PreprocessorUtils.isClassLocal(bytecodeInvoke)
                || PreprocessorUtils.isLinkCallSiteMethod(bytecodeInvoke)
                || PreprocessorUtils.isInvokeReverse(bytecodeInvoke)) {
            return false;
        }
        return !("java/lang/invoke/MethodHandle".equals(invoke.getOwner())
                && ("invokeExact".equals(invoke.getName())
                || "invoke".equals(invoke.getName()))
                && invoke.getKind() == IrNodes.Invoke.Kind.VIRTUAL);
    }

    private void analyzeUnsafeJniReachability(IrMethod method) {
        blocksReachingUnsafeJni = new HashSet<IrBlock>();
        boolean changed;
        do {
            changed = false;
            for (IrBlock block : method.getBlocks()) {
                if (blocksReachingUnsafeJni.contains(block)) {
                    continue;
                }
                if (blockHasLocalUnsafeJni(block)
                        || successorReachesUnsafeJni(block)) {
                    blocksReachingUnsafeJni.add(block);
                    changed = true;
                }
            }
        } while (changed);
    }

    private boolean blockHasLocalUnsafeJni(IrBlock block) {
        for (IrInstruction instruction : block.getInstructions()) {
            if (performsUnsafeJni(instruction)) {
                return true;
            }
        }
        return block.getTerminator() instanceof IrNodes.Throw;
    }

    private boolean successorReachesUnsafeJni(IrBlock block) {
        IrTerminator terminator = block.getTerminator();
        if (terminator == null || terminator instanceof IrNodes.Return
                || terminator instanceof IrNodes.Throw) {
            return false;
        }
        for (IrBlock successor : terminator.getSuccessors()) {
            if (blocksReachingUnsafeJni.contains(successor)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPollPending(IrBlock block) {
        return !block.getExceptionEdges().isEmpty() || hasLaterUnsafeJni(block);
    }

    private boolean hasLaterUnsafeJni(IrBlock block) {
        List<IrInstruction> instructions = block.getInstructions();
        int start = 0;
        if (currentInstruction != null) {
            int index = instructions.indexOf(currentInstruction);
            start = index < 0 ? 0 : index + 1;
        }
        for (int i = start; i < instructions.size(); i++) {
            if (performsUnsafeJni(instructions.get(i))) {
                return true;
            }
        }
        IrTerminator terminator = block.getTerminator();
        if (terminator instanceof IrNodes.Return) {
            return false;
        }
        if (terminator instanceof IrNodes.Throw) {
            return true;
        }
        for (IrBlock successor : terminator.getSuccessors()) {
            if (blocksReachingUnsafeJni.contains(successor)) {
                return true;
            }
        }
        return false;
    }

    private boolean performsUnsafeJni(IrInstruction instruction) {
        if (instruction instanceof IrNodes.Assign
                || instruction instanceof IrNodes.OpaqueTrue
                || instruction instanceof IrNodes.CaughtException
                || instruction instanceof IrNodes.Const
                || instruction instanceof IrNodes.LongConst
                || instruction instanceof IrNodes.FloatConst
                || instruction instanceof IrNodes.DoubleConst
                || instruction instanceof IrNodes.NullReference
                || instruction instanceof IrNodes.Binary
                || instruction instanceof IrNodes.LongBinary
                || instruction instanceof IrNodes.LongShift
                || instruction instanceof IrNodes.FloatingBinary
                || instruction instanceof IrNodes.FloatingUnary
                || instruction instanceof IrNodes.FloatingCompare
                || instruction instanceof IrNodes.LongCompare
                || instruction instanceof IrNodes.Unary
                || instruction instanceof IrNodes.LongUnary
                || instruction instanceof IrNodes.Conversion
                || instruction instanceof IrNodes.StringConst) {
            return false;
        }
        if (instruction instanceof IrNodes.Intrinsic) {
            switch (((IrNodes.Intrinsic) instruction).getKind()) {
                case MATH_ABS_I:
                case MATH_ABS_L:
                case MATH_MIN_I:
                case MATH_MAX_I:
                case MATH_MIN_L:
                case MATH_MAX_L:
                case BIT_COUNT_I:
                case BIT_COUNT_L:
                case NUMBER_OF_LEADING_ZEROS_I:
                case NUMBER_OF_LEADING_ZEROS_L:
                case SDK_ABI_VERSION:
                    return false;
                default:
                    return true;
            }
        }
        return true;
    }

    private List<CppAst.Statement> exceptionalExit(IrMethod method, IrBlock block) {
        List<CppAst.Statement> body = new ArrayList<>();
        if (block.getExceptionEdges().isEmpty()) {
            body.add(earlyReturn(method));
        } else {
            Set<IrBlock> transferredHandlers = new LinkedHashSet<>();
            for (IrExceptionEdge edge : block.getExceptionEdges()) {
                if (transferredHandlers.add(edge.getHandler())) {
                    body.addAll(phiCopies(block, edge.getHandler()));
                }
            }
            String dispatch = dispatchLabels.get(new HandlerSet(block.getExceptionEdges()));
            if (dispatch == null) {
                throw new IllegalStateException("Missing shared exception dispatch");
            }
            body.add(new CppAst.Goto(dispatch));
        }
        return Collections.<CppAst.Statement>singletonList(new CppAst.Block(body));
    }

    private CppAst.Statement earlyReturn(IrMethod method) {
        boolean commit = intFieldCacheEnabled || intArrayPinEnabled;
        List<CppAst.Statement> statements = new ArrayList<>();
        if (commit) {
            statements.add(new CppAst.Declaration("jthrowable", "pending_before_commit",
                    memberCall("env", "ExceptionOccurred")));
            statements.add(new CppAst.ExpressionStatement(
                    memberCall("env", "ExceptionClear")));
            statements.addAll(commitCaches());
        }
        if (method.isSynchronizedMethod()) {
            if (!commit) {
                statements.add(new CppAst.Declaration("jthrowable",
                        "synchronized_exception",
                        memberCall("env", "ExceptionOccurred")));
                statements.add(new CppAst.ExpressionStatement(
                        memberCall("env", "ExceptionClear")));
            }
            statements.add(new CppAst.If(
                    monitorOperationFailed("MonitorExit", synchronizedMonitor(method)),
                    new CppAst.Block(Collections.<CppAst.Statement>singletonList(
                            defaultReturn(method))), null));
            if (!commit) {
                statements.add(new CppAst.If(new CppAst.Binary(
                        variable("synchronized_exception"), "!=",
                        new CppAst.NullLiteral()), new CppAst.Block(
                        Collections.<CppAst.Statement>singletonList(
                                new CppAst.ExpressionStatement(memberCall("env",
                                        "Throw", variable("synchronized_exception"))))),
                        null));
            }
        }
        if (commit) {
            statements.add(new CppAst.If(new CppAst.Binary(
                    variable("pending_before_commit"), "!=",
                    new CppAst.NullLiteral()), new CppAst.Block(
                    Collections.<CppAst.Statement>singletonList(
                            new CppAst.ExpressionStatement(memberCall("env", "Throw",
                                    variable("pending_before_commit"))))), null));
        }
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
            statements.addAll(commitCaches());
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
            statements.addAll(exceptionalExit(method, predecessor));
            return;
        }
        throw new IllegalStateException("Unknown IR terminator " + terminator.getClass());
    }

    private List<CppAst.Statement> edgeTransfer(IrBlock predecessor, IrBlock target) {
        List<CppAst.Statement> statements = phiCopies(predecessor, target);
        statements.add(new CppAst.Goto(label(target)));
        return statements;
    }

    private List<CppAst.Statement> initializeEntryPhis(IrMethod method) {
        if (method.getBlocks().isEmpty()) {
            return Collections.emptyList();
        }
        IrBlock entry = method.getBlocks().get(0);
        List<CppAst.Statement> statements = new ArrayList<>();
        int localSlot = 0;
        for (IrValue parameter : method.getParameters()) {
            for (IrPhi phi : entry.getPhis()) {
                if (phi.getSlotKind() == IrPhi.SlotKind.LOCAL
                        && phi.getSlotIndex() == localSlot) {
                    statements.add(new CppAst.Assignment(
                            variable(phi.getResult()), expression(parameter)));
                }
            }
            localSlot += parameter.getType().getJvmSlots();
        }
        return statements;
    }

    private List<CppAst.Statement> phiCopies(IrBlock predecessor, IrBlock target) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<IrPhi> copied = new ArrayList<>();
        List<String> temporaryNames = new ArrayList<>();
        for (IrPhi phi : target.getPhis()) {
            IrValue incoming = phi.getIncoming().get(predecessor);
            if (incoming == null) {
                continue;
            }
            String temporary = "edge" + edgeTemporaryId++;
            copied.add(phi);
            temporaryNames.add(temporary);
            statements.add(new CppAst.Declaration(phi.getResult().getType().getCppType(),
                    temporary, expression(incoming)));
        }
        for (int i = 0; i < copied.size(); i++) {
            statements.add(new CppAst.Assignment(variable(copied.get(i).getResult()),
                    new CppAst.Variable(temporaryNames.get(i))));
        }
        return statements;
    }

    private List<CppAst.Statement> assignCaughtExceptionToStackPhis(IrBlock handler) {
        List<CppAst.Statement> statements = new ArrayList<>();
        for (IrPhi phi : handler.getPhis()) {
            if (phi.getSlotKind() == IrPhi.SlotKind.STACK) {
                statements.add(new CppAst.Assignment(variable(phi.getResult()),
                        new CppAst.Cast("jobject", variable("caught_exception"))));
            }
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
            statements.addAll(commitCaches());

            boolean catchAll = false;
            for (IrExceptionEdge edge : dispatch.getKey().getEdges()) {
                List<CppAst.Statement> taken = new ArrayList<>(
                        assignCaughtExceptionToStackPhis(edge.getHandler()));
                taken.add(new CppAst.Goto(label(edge.getHandler())));
                if (edge.getCatchType() == null) {
                    statements.addAll(taken);
                    catchAll = true;
                    break;
                }
                int classId = context.getCachedClasses().getId(edge.getCatchType());
                CppAst.Expression matches = memberCall("env", "IsInstanceOf",
                        new CppAst.Cast("jobject", variable("caught_exception")),
                        array("cclasses", classId));
                statements.add(new CppAst.If(matches, new CppAst.Block(taken), null));
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
