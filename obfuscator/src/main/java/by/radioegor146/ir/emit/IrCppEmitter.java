package by.radioegor146.ir.emit;

import by.radioegor146.CachedFieldInfo;
import by.radioegor146.CachedMethodInfo;
import by.radioegor146.MethodContext;
import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrExceptionEdge;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        if (instruction instanceof IrNodes.Binary) {
            return emitBinary((IrNodes.Binary) instruction);
        }
        if (instruction instanceof IrNodes.Unary) {
            return emitUnary((IrNodes.Unary) instruction);
        }
        if (context == null) {
            throw new IllegalStateException(
                    "JNI IR instructions require a method emission context");
        }
        if (instruction instanceof IrNodes.IntDivRem) {
            return emitIntDivRem(method, block, (IrNodes.IntDivRem) instruction, context);
        }
        if (instruction instanceof IrNodes.NewArray) {
            return emitNewArray(method, block, (IrNodes.NewArray) instruction, context);
        }
        if (instruction instanceof IrNodes.NewObjectArray) {
            return emitNewObjectArray(method, block,
                    (IrNodes.NewObjectArray) instruction, context);
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

    private CppAst.Expression shiftAmount(CppAst.Expression right) {
        return new CppAst.Binary(new CppAst.Cast("uint32_t", right), "&",
                new CppAst.IntLiteral(31));
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

    private List<CppAst.Statement> emitNewArray(IrMethod method, IrBlock block,
                                                IrNodes.NewArray array,
                                                MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        List<CppAst.Statement> negativeLength = new ArrayList<>();
        negativeLength.add(new CppAst.ExpressionStatement(new CppAst.Call("utils::throw_re",
                Arrays.asList(variable("env"),
                        pool(context.getStringPool().getOffset(
                                "java/lang/NegativeArraySizeException")),
                        pool(context.getStringPool().getOffset(
                                "NEWARRAY Int array size < 0")),
                        new CppAst.IntLiteral(array.getSourceLine())))));
        negativeLength.addAll(exceptionalExit(method, block));
        statements.add(new CppAst.If(new CppAst.Binary(expression(array.getLength()), "<",
                new CppAst.IntLiteral(0)), new CppAst.Block(negativeLength), null));
        statements.add(new CppAst.Assignment(variable(array.getResult()),
                new CppAst.Cast("jobject",
                        memberCall("env", "NewIntArray", expression(array.getLength())))));
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
        int classStringId = context.getCachedStrings().getId(
                array.getComponentType().replace('/', '.'));
        statements.addAll(emitClassCache(method, block, classId, classStringId));
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
        statements.add(nullCheck(method, load.getArray(), "IALOAD npe",
                load.getSourceLine(), context, block));
        String temporary = "iaload" + arrayTemporaryId++;
        List<CppAst.Statement> scoped = new ArrayList<>();
        scoped.add(new CppAst.Declaration("jint", temporary, new CppAst.IntLiteral(0)));
        scoped.add(new CppAst.ExpressionStatement(new CppAst.MemberCall(variable("env"), true,
                "GetIntArrayRegion", Arrays.asList(
                        new CppAst.Cast("jintArray", expression(load.getArray())),
                        expression(load.getIndex()), new CppAst.IntLiteral(1),
                        new CppAst.Unary("&", variable(temporary))))));
        scoped.add(exceptionCheck(method, block));
        scoped.add(new CppAst.Assignment(variable(load.getResult()), variable(temporary)));
        statements.add(new CppAst.Block(scoped));
        return statements;
    }

    private List<CppAst.Statement> emitArrayStore(IrMethod method, IrBlock block,
                                                  IrNodes.ArrayStore store,
                                                  MethodContext context) {
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(nullCheck(method, store.getArray(), "IASTORE npe",
                store.getSourceLine(), context, block));
        String temporary = "iastore" + arrayTemporaryId++;
        List<CppAst.Statement> scoped = new ArrayList<>();
        scoped.add(new CppAst.Declaration("jint", temporary, expression(store.getValue())));
        scoped.add(new CppAst.ExpressionStatement(new CppAst.MemberCall(variable("env"), true,
                "SetIntArrayRegion", Arrays.asList(
                        new CppAst.Cast("jintArray", expression(store.getArray())),
                        expression(store.getIndex()), new CppAst.IntLiteral(1),
                        new CppAst.Unary("&", variable(temporary))))));
        scoped.add(exceptionCheck(method, block));
        statements.add(new CppAst.Block(scoped));
        return statements;
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

    private List<CppAst.Statement> emitGetField(IrMethod method, IrBlock block,
                                                IrNodes.GetField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(), "GETFIELD Int npe",
                field.getSourceLine(), context, block));
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                memberCall("env", "GetIntField", expression(field.getReceiver()),
                        array("cfields", slots.memberId))));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitPutField(IrMethod method, IrBlock block,
                                                IrNodes.PutField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(), "PUTFIELD Int npe",
                field.getSourceLine(), context, block));
        statements.add(new CppAst.ExpressionStatement(
                memberCall("env", "SetIntField", expression(field.getReceiver()),
                        array("cfields", slots.memberId), expression(field.getValue()))));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitGetStaticField(IrMethod method, IrBlock block,
                                                      IrNodes.GetStaticField field,
                                                      MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), true, context, block);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                memberCall("env", "GetStaticIntField", array("cclasses", slots.classId),
                        array("cfields", slots.memberId))));
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
                "SetStaticIntField", array("cclasses", slots.classId),
                array("cfields", slots.memberId), expression(field.getValue()))));
        statements.add(exceptionCheck(method, block));
        return statements;
    }

    private List<CppAst.Statement> emitInvoke(IrMethod method, IrBlock block,
                                              IrNodes.Invoke invoke,
                                              MethodContext context) {
        boolean staticInvoke = invoke.getKind() == IrNodes.Invoke.Kind.STATIC;
        CachedMethodInfo info = new CachedMethodInfo(invoke.getOwner(), invoke.getName(),
                invoke.getDescriptor(), staticInvoke);
        CacheSlots slots = cacheMember(method, invoke.getOwner(),
                context.getCachedMethods().getId(info), "cmethods",
                staticInvoke ? "GetStaticMethodID" : "GetMethodID",
                context.getStringPool().getOffset(invoke.getName()),
                context.getStringPool().getOffset(invoke.getDescriptor()), context, block);

        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        if (!staticInvoke) {
            statements.add(nullCheck(method, invoke.getReceiver(), "INVOKEVIRTUAL Int npe",
                    invoke.getSourceLine(), context, block));
        }

        List<CppAst.Expression> arguments = new ArrayList<>();
        arguments.add(staticInvoke
                ? array("cclasses", slots.classId) : expression(invoke.getReceiver()));
        arguments.add(array("cmethods", slots.memberId));
        for (IrValue argument : invoke.getArguments()) {
            arguments.add(expression(argument));
        }
        statements.add(new CppAst.Assignment(variable(invoke.getResult()),
                new CppAst.MemberCall(variable("env"), true,
                        staticInvoke ? "CallStaticIntMethod" : "CallIntMethod", arguments)));
        statements.add(exceptionCheck(method, block));
        return statements;
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
        int classStringId = context.getCachedStrings().getId(owner.replace('/', '.'));
        List<CppAst.Statement> statements = emitClassCache(method, block, classId,
                classStringId);

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
                                                  int classId, int classStringId) {
        CppAst.Expression classSlot = array("cclasses", classId);
        CppAst.Expression cacheMissing = new CppAst.Binary(
                new CppAst.Unary("!", classSlot), "||",
                memberCall("env", "IsSameObject", classSlot, new CppAst.NullLiteral()));

        String resolvedName = "resolved_class_" + classId;
        CppAst.Variable resolved = variable(resolvedName);
        List<CppAst.Statement> publishClass = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(classSlot,
                        new CppAst.Cast("jclass", memberCall("env", "NewWeakGlobalRef", resolved))),
                new CppAst.ExpressionStatement(
                        memberCall("env", "DeleteLocalRef", resolved)));
        List<CppAst.Statement> resolveClass = Arrays.<CppAst.Statement>asList(
                new CppAst.Declaration("jclass", resolvedName,
                        new CppAst.Call("utils::find_class_wo_static", Arrays.asList(
                                variable("env"), variable("classloader"),
                                array("cstrings", classStringId)))),
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

    private CppAst.Return earlyReturn(IrMethod method) {
        return new CppAst.Return(method.getReturnType() == IrType.VOID
                ? null : new CppAst.IntLiteral(0));
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
            statements.add(new CppAst.Return(value == null ? null : expression(value)));
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
