package by.radioegor146.ir.emit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Small structured C++ AST used by the IR path. It intentionally has no raw
 * statement node, so opcode lowering cannot escape into snippet text.
 */
public final class CppAst {
    private CppAst() {
    }

    public interface Expression {
        String render();
    }

    public interface Statement {
        void write(Printer printer);
    }

    public static final class Variable implements Expression {
        private final String name;

        public Variable(String name) {
            this.name = identifier(name);
        }

        @Override
        public String render() {
            return name;
        }
    }

    public static final class IntLiteral implements Expression {
        private final int value;

        public IntLiteral(int value) {
            this.value = value;
        }

        @Override
        public String render() {
            if (value == Integer.MIN_VALUE) {
                return "((jint) 0x80000000U)";
            }
            return Integer.toString(value);
        }
    }

    public static final class Binary implements Expression {
        private final Expression left;
        private final String operator;
        private final Expression right;

        public Binary(Expression left, String operator, Expression right) {
            this.left = Objects.requireNonNull(left, "left");
            this.operator = allowedOperator(operator);
            this.right = Objects.requireNonNull(right, "right");
        }

        @Override
        public String render() {
            return "(" + left.render() + " " + operator + " " + right.render() + ")";
        }
    }

    public static final class Cast implements Expression {
        private final String type;
        private final Expression expression;

        public Cast(String type, Expression expression) {
            this.type = identifier(type);
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        @Override
        public String render() {
            return "(" + type + ") " + expression.render();
        }
    }

    public static final class Declaration implements Statement {
        private final String type;
        private final String name;
        private final Expression initializer;

        public Declaration(String type, String name) {
            this(type, name, null);
        }

        public Declaration(String type, String name, Expression initializer) {
            this.type = identifier(type);
            this.name = identifier(name);
            this.initializer = initializer;
        }

        @Override
        public void write(Printer printer) {
            printer.line(type + " " + name
                    + (initializer == null ? "" : " = " + initializer.render()) + ";");
        }
    }

    public static final class Assignment implements Statement {
        private final Variable target;
        private final Expression value;

        public Assignment(Variable target, Expression value) {
            this.target = Objects.requireNonNull(target, "target");
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public void write(Printer printer) {
            printer.line(target.render() + " = " + value.render() + ";");
        }
    }

    public static final class Label implements Statement {
        private final String name;

        public Label(String name) {
            this.name = identifier(name);
        }

        @Override
        public void write(Printer printer) {
            printer.line(name + ":");
        }
    }

    public static final class Goto implements Statement {
        private final String target;

        public Goto(String target) {
            this.target = identifier(target);
        }

        @Override
        public void write(Printer printer) {
            printer.line("goto " + target + ";");
        }
    }

    public static final class Return implements Statement {
        private final Expression value;

        public Return(Expression value) {
            this.value = value;
        }

        @Override
        public void write(Printer printer) {
            printer.line(value == null ? "return;" : "return " + value.render() + ";");
        }
    }

    public static final class Block implements Statement {
        private final List<Statement> statements;

        public Block(List<Statement> statements) {
            this.statements = Collections.unmodifiableList(new ArrayList<>(statements));
        }

        @Override
        public void write(Printer printer) {
            printer.line("{");
            printer.indent++;
            printer.write(statements);
            printer.indent--;
            printer.line("}");
        }
    }

    public static final class If implements Statement {
        private final Expression condition;
        private final Block trueBlock;
        private final Block falseBlock;

        public If(Expression condition, Block trueBlock, Block falseBlock) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.trueBlock = Objects.requireNonNull(trueBlock, "trueBlock");
            this.falseBlock = Objects.requireNonNull(falseBlock, "falseBlock");
        }

        @Override
        public void write(Printer printer) {
            printer.line("if " + condition.render() + " {");
            printer.indent++;
            printer.write(trueBlock.statements);
            printer.indent--;
            printer.line("} else {");
            printer.indent++;
            printer.write(falseBlock.statements);
            printer.indent--;
            printer.line("}");
        }
    }

    public static final class Comment implements Statement {
        private final String text;

        public Comment(String text) {
            this.text = Objects.requireNonNull(text, "text").replace('\n', ' ');
        }

        @Override
        public void write(Printer printer) {
            printer.line("// " + text);
        }
    }

    public static String render(List<Statement> statements, int initialIndent) {
        Printer printer = new Printer(initialIndent);
        printer.write(statements);
        return printer.output.toString();
    }

    private static String identifier(String value) {
        Objects.requireNonNull(value, "identifier");
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid C++ identifier: " + value);
        }
        return value;
    }

    private static String allowedOperator(String value) {
        if ("+".equals(value) || "-".equals(value) || "*".equals(value)
                || "==".equals(value) || "!=".equals(value) || "<".equals(value)
                || ">=".equals(value) || ">".equals(value) || "<=".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported C++ operator: " + value);
    }

    private static final class Printer {
        private final StringBuilder output = new StringBuilder();
        private int indent;

        private Printer(int indent) {
            this.indent = indent;
        }

        private void write(List<Statement> statements) {
            for (Statement statement : statements) {
                statement.write(this);
            }
        }

        private void line(String text) {
            for (int i = 0; i < indent; i++) {
                output.append("    ");
            }
            output.append(text).append('\n');
        }
    }
}
