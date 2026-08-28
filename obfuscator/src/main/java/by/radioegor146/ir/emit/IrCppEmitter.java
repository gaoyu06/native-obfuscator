package by.radioegor146.ir.emit;

import by.radioegor146.CachedFieldInfo;
import by.radioegor146.CachedMethodInfo;
import by.radioegor146.MethodContext;
import by.radioegor146.ir.IrBlock;
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
import java.util.List;

/**
 * Emits the typed IR directly through {@link CppAst}; it has no dependency on
 * the legacy snippet/property machinery.
 */
public final class IrCppEmitter {
    private int edgeTemporaryId;

    public String emitBody(IrMethod method) {
        return emitBody(method, null);
    }

    public String emitBody(IrMethod method, MethodContext context) {
        edgeTemporaryId = 0;
        List<CppAst.Statement> statements = new ArrayList<>();
        statements.add(new CppAst.Comment("IR codegen: " + method.getOwner() + "."
                + method.getName() + method.getDescriptor()));

        // Declare all SSA carriers before labels so C++ gotos never cross an
        // initialized automatic variable.
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
                statements.addAll(emitInstruction(method, instruction, context));
            }
            emitTerminator(statements, block, block.getTerminator());
        }
        return CppAst.render(statements, 1);
    }

    private CppAst.Declaration declaration(IrValue value) {
        return new CppAst.Declaration(value.getType().getCppType(), variableName(value));
    }

    private List<CppAst.Statement> emitInstruction(IrMethod method, IrInstruction instruction,
                                                   MethodContext context) {
        if (instruction instanceof IrNodes.Const) {
            IrNodes.Const constant = (IrNodes.Const) instruction;
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(constant.getResult()),
                            new CppAst.IntLiteral(constant.getValue())));
        }
        if (instruction instanceof IrNodes.Binary) {
            IrNodes.Binary binary = (IrNodes.Binary) instruction;
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
                default:
                    throw new IllegalStateException("Unknown binary operation "
                            + binary.getOperation());
            }
            CppAst.Expression wrapped = new CppAst.Binary(
                    new CppAst.Cast("uint32_t", expression(binary.getLeft())),
                    operator,
                    new CppAst.Cast("uint32_t", expression(binary.getRight())));
            return Collections.<CppAst.Statement>singletonList(
                    new CppAst.Assignment(variable(binary.getResult()),
                            new CppAst.Cast("jint", wrapped)));
        }
        if (context == null) {
            throw new IllegalStateException(
                    "JNI IR instructions require a method emission context");
        }
        if (instruction instanceof IrNodes.GetField) {
            return emitGetField(method, (IrNodes.GetField) instruction, context);
        }
        if (instruction instanceof IrNodes.PutField) {
            return emitPutField(method, (IrNodes.PutField) instruction, context);
        }
        if (instruction instanceof IrNodes.Invoke) {
            return emitInvoke(method, (IrNodes.Invoke) instruction, context);
        }
        throw new IllegalStateException("Unknown IR instruction " + instruction.getClass());
    }

    private List<CppAst.Statement> emitGetField(IrMethod method, IrNodes.GetField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(), "GETFIELD Int npe",
                field.getSourceLine(), context));
        statements.add(new CppAst.Assignment(variable(field.getResult()),
                memberCall("env", "GetIntField", expression(field.getReceiver()),
                        array("cfields", slots.memberId))));
        statements.add(exceptionCheck(method));
        return statements;
    }

    private List<CppAst.Statement> emitPutField(IrMethod method, IrNodes.PutField field,
                                                MethodContext context) {
        CacheSlots slots = cacheField(method, field.getOwner(), field.getName(),
                field.getDescriptor(), context);
        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        statements.add(nullCheck(method, field.getReceiver(), "PUTFIELD Int npe",
                field.getSourceLine(), context));
        statements.add(new CppAst.ExpressionStatement(
                memberCall("env", "SetIntField", expression(field.getReceiver()),
                        array("cfields", slots.memberId), expression(field.getValue()))));
        statements.add(exceptionCheck(method));
        return statements;
    }

    private List<CppAst.Statement> emitInvoke(IrMethod method, IrNodes.Invoke invoke,
                                              MethodContext context) {
        boolean staticInvoke = invoke.getKind() == IrNodes.Invoke.Kind.STATIC;
        CachedMethodInfo info = new CachedMethodInfo(invoke.getOwner(), invoke.getName(),
                invoke.getDescriptor(), staticInvoke);
        CacheSlots slots = cacheMember(method, invoke.getOwner(),
                context.getCachedMethods().getId(info), "cmethods",
                staticInvoke ? "GetStaticMethodID" : "GetMethodID",
                context.getStringPool().getOffset(invoke.getName()),
                context.getStringPool().getOffset(invoke.getDescriptor()), context);

        List<CppAst.Statement> statements = new ArrayList<>(slots.initialization);
        if (!staticInvoke) {
            statements.add(nullCheck(method, invoke.getReceiver(), "INVOKEVIRTUAL Int npe",
                    invoke.getSourceLine(), context));
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
        statements.add(exceptionCheck(method));
        return statements;
    }

    private CacheSlots cacheField(IrMethod method, String owner, String name,
                                  String descriptor, MethodContext context) {
        CachedFieldInfo info = new CachedFieldInfo(owner, name, descriptor, false);
        return cacheMember(method, owner, context.getCachedFields().getId(info), "cfields",
                "GetFieldID", context.getStringPool().getOffset(name),
                context.getStringPool().getOffset(descriptor), context);
    }

    private CacheSlots cacheMember(IrMethod method, String owner, int memberId,
                                   String memberArray, String lookupMethod, long nameOffset,
                                   long descriptorOffset, MethodContext context) {
        int classId = context.getCachedClasses().getId(owner);
        int classStringId = context.getCachedStrings().getId(owner.replace('/', '.'));
        List<CppAst.Statement> statements = emitClassCache(method, classId, classStringId);

        CppAst.Expression memberSlot = array(memberArray, memberId);
        CppAst.Expression lookup = memberCall("env", lookupMethod,
                array("cclasses", classId), pool(nameOffset), pool(descriptorOffset));
        List<CppAst.Statement> initializeMember = Arrays.<CppAst.Statement>asList(
                new CppAst.Assignment(memberSlot, lookup),
                exceptionCheck(method));
        statements.add(new CppAst.If(new CppAst.Unary("!", memberSlot),
                new CppAst.Block(initializeMember), null));
        return new CacheSlots(classId, memberId, statements);
    }

    private List<CppAst.Statement> emitClassCache(IrMethod method, int classId,
                                                   int classStringId) {
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
                exceptionCheck(method));
        return new ArrayList<>(Collections.<CppAst.Statement>singletonList(
                new CppAst.If(cacheMissing, new CppAst.Block(initializeClass), null)));
    }

    private CppAst.Statement nullCheck(IrMethod method, IrValue receiver, String error,
                                       int sourceLine, MethodContext context) {
        CppAst.Expression throwCall = new CppAst.Call("utils::throw_re", Arrays.asList(
                variable("env"),
                pool(context.getStringPool().getOffset("java/lang/NullPointerException")),
                pool(context.getStringPool().getOffset(error)),
                new CppAst.IntLiteral(sourceLine)));
        return new CppAst.If(
                new CppAst.Binary(expression(receiver), "==", new CppAst.NullLiteral()),
                new CppAst.Block(Arrays.<CppAst.Statement>asList(
                        new CppAst.ExpressionStatement(throwCall), earlyReturn(method))),
                null);
    }

    private CppAst.Statement exceptionCheck(IrMethod method) {
        return new CppAst.If(new CppAst.Binary(
                memberCall("env", "ExceptionCheck"), "!=", new CppAst.IntLiteral(0)),
                new CppAst.Block(Collections.singletonList(earlyReturn(method))), null);
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

    private void emitTerminator(List<CppAst.Statement> statements, IrBlock predecessor,
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
        if (terminator instanceof IrNodes.Return) {
            IrValue value = ((IrNodes.Return) terminator).getValue();
            statements.add(new CppAst.Return(value == null ? null : expression(value)));
            return;
        }
        throw new IllegalStateException("Unknown IR terminator " + terminator.getClass());
    }

    private List<CppAst.Statement> edgeTransfer(IrBlock predecessor, IrBlock target) {
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
        statements.add(new CppAst.Goto(label(target)));
        return statements;
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
}
