package by.radioegor146.ir.emit;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits the typed IR directly through {@link CppAst}; it has no dependency on
 * the legacy snippet/property machinery.
 */
public final class IrCppEmitter {
    private int edgeTemporaryId;

    public String emitBody(IrMethod method) {
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
                statements.add(declaration(instruction.getResult()));
            }
        }

        for (IrBlock block : method.getBlocks()) {
            statements.add(new CppAst.Label(label(block)));
            for (IrInstruction instruction : block.getInstructions()) {
                statements.add(emitInstruction(instruction));
            }
            emitTerminator(statements, block, block.getTerminator());
        }
        return CppAst.render(statements, 1);
    }

    private CppAst.Declaration declaration(IrValue value) {
        return new CppAst.Declaration(value.getType().getCppType(), variableName(value));
    }

    private CppAst.Statement emitInstruction(IrInstruction instruction) {
        if (instruction instanceof IrNodes.Const) {
            IrNodes.Const constant = (IrNodes.Const) instruction;
            return new CppAst.Assignment(variable(constant.getResult()),
                    new CppAst.IntLiteral(constant.getValue()));
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
            return new CppAst.Assignment(variable(binary.getResult()),
                    new CppAst.Cast("jint", wrapped));
        }
        throw new IllegalStateException("Unknown IR instruction " + instruction.getClass());
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

    private String variableName(IrValue value) {
        return "v" + value.getId();
    }

    private String label(IrBlock block) {
        return "B" + block.getId();
    }
}
