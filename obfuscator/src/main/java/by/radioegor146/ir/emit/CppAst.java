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

    public static final class LongLiteral implements Expression {
        private final long value;

        public LongLiteral(long value) {
            this.value = value;
        }

        @Override
        public String render() {
            if (value == Long.MIN_VALUE) {
                return "((jlong) 0x8000000000000000ULL)";
            }
            return Long.toString(value) + "LL";
        }
    }

    /**
     * Materializes the exact JVM constant bits without relying on the host
     * compiler's spelling or canonicalization of NaN literals.
     */
    public static final class FloatBitsLiteral implements Expression {
        private final int rawBits;

        public FloatBitsLiteral(int rawBits) {
            this.rawBits = rawBits;
        }

        @Override
        public String render() {
            return "([]() { uint32_t bits = 0x"
                    + String.format("%08x", rawBits)
                    + "U; jfloat value; std::memcpy(&value, &bits, sizeof(value)); "
                    + "return value; }())";
        }
    }

    public static final class DoubleBitsLiteral implements Expression {
        private final long rawBits;

        public DoubleBitsLiteral(long rawBits) {
            this.rawBits = rawBits;
        }

        @Override
        public String render() {
            return "([]() { uint64_t bits = 0x"
                    + String.format("%016x", rawBits)
                    + "ULL; jdouble value; std::memcpy(&value, &bits, sizeof(value)); "
                    + "return value; }())";
        }
    }

    public static final class NullLiteral implements Expression {
        @Override
        public String render() {
            return "nullptr";
        }
    }

    public static final class InitializerList implements Expression {
        private final List<Expression> values;

        public InitializerList(List<Expression> values) {
            this.values = immutableExpressions(values);
        }

        @Override
        public String render() {
            return "{ " + renderArguments(values) + " }";
        }
    }

    public static final class ArrayAccess implements Expression {
        private final String array;
        private final int index;

        public ArrayAccess(String array, int index) {
            this.array = identifier(array);
            if (index < 0) {
                throw new IllegalArgumentException("Negative C++ array index");
            }
            this.index = index;
        }

        @Override
        public String render() {
            return array + "[" + index + "]";
        }
    }

    public static final class Subscript implements Expression {
        private final Expression array;
        private final Expression index;

        public Subscript(Expression array, Expression index) {
            this.array = Objects.requireNonNull(array, "array");
            this.index = Objects.requireNonNull(index, "index");
        }

        @Override
        public String render() {
            return "(" + array.render() + ")[" + index.render() + "]";
        }
    }

    public static final class StringPoolPointer implements Expression {
        private final long offset;

        public StringPoolPointer(long offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("Negative string-pool offset");
            }
            this.offset = offset;
        }

        @Override
        public String render() {
            return "((char *)(string_pool + " + offset + "LL))";
        }
    }

    public static final class Unary implements Expression {
        private final String operator;
        private final Expression operand;

        public Unary(String operator, Expression operand) {
            if (!"!".equals(operator) && !"-".equals(operator) && !"~".equals(operator)
                    && !"&".equals(operator)) {
                throw new IllegalArgumentException("Unsupported C++ unary operator: " + operator);
            }
            this.operator = operator;
            this.operand = Objects.requireNonNull(operand, "operand");
        }

        @Override
        public String render() {
            return "(" + operator + operand.render() + ")";
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

    public static final class Conditional implements Expression {
        private final Expression condition;
        private final Expression trueValue;
        private final Expression falseValue;

        public Conditional(Expression condition, Expression trueValue,
                           Expression falseValue) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.trueValue = Objects.requireNonNull(trueValue, "trueValue");
            this.falseValue = Objects.requireNonNull(falseValue, "falseValue");
        }

        @Override
        public String render() {
            return "(" + condition.render() + " ? " + trueValue.render()
                    + " : " + falseValue.render() + ")";
        }
    }

    public static final class Cast implements Expression {
        private final String type;
        private final Expression expression;

        public Cast(String type, Expression expression) {
            this.type = typeToken(type);
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        @Override
        public String render() {
            return "(" + type + ") " + expression.render();
        }
    }

    public static final class Call implements Expression {
        private final String function;
        private final List<Expression> arguments;

        public Call(String function, List<Expression> arguments) {
            this.function = qualifiedIdentifier(function);
            this.arguments = immutableExpressions(arguments);
        }

        @Override
        public String render() {
            return function + "(" + renderArguments(arguments) + ")";
        }
    }

    public static final class TemplateCall implements Expression {
        private final String function;
        private final int templateArgument;
        private final List<Expression> arguments;

        public TemplateCall(String function, int templateArgument,
                            List<Expression> arguments) {
            this.function = qualifiedIdentifier(function);
            this.templateArgument = templateArgument;
            this.arguments = immutableExpressions(arguments);
        }

        @Override
        public String render() {
            return function + "<" + templateArgument + ">("
                    + renderArguments(arguments) + ")";
        }
    }

    public static final class MemberCall implements Expression {
        private final Expression receiver;
        private final String access;
        private final String method;
        private final List<Expression> arguments;

        public MemberCall(Expression receiver, boolean pointerAccess, String method,
                          List<Expression> arguments) {
            this.receiver = Objects.requireNonNull(receiver, "receiver");
            this.access = pointerAccess ? "->" : ".";
            this.method = identifier(method);
            this.arguments = immutableExpressions(arguments);
        }

        @Override
        public String render() {
            return receiver.render() + access + method + "(" + renderArguments(arguments) + ")";
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
            this.type = typeToken(type);
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
        private final Expression target;
        private final Expression value;

        public Assignment(Expression target, Expression value) {
            this.target = Objects.requireNonNull(target, "target");
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public void write(Printer printer) {
            printer.line(target.render() + " = " + value.render() + ";");
        }
    }

    public static final class ExpressionStatement implements Statement {
        private final Expression expression;

        public ExpressionStatement(Expression expression) {
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        @Override
        public void write(Printer printer) {
            printer.line(expression.render() + ";");
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
            this.falseBlock = falseBlock;
        }

        @Override
        public void write(Printer printer) {
            String renderedCondition = condition.render();
            if (!renderedCondition.startsWith("(") || !renderedCondition.endsWith(")")) {
                renderedCondition = "(" + renderedCondition + ")";
            }
            printer.line("if " + renderedCondition + " {");
            printer.indent++;
            printer.write(trueBlock.statements);
            printer.indent--;
            if (falseBlock == null) {
                printer.line("}");
            } else {
                printer.line("} else {");
                printer.indent++;
                printer.write(falseBlock.statements);
                printer.indent--;
                printer.line("}");
            }
        }
    }

    public static final class Switch implements Statement {
        private final Expression selector;
        private final List<Integer> keys;
        private final List<Block> caseBlocks;
        private final Block defaultBlock;

        public Switch(Expression selector, List<Integer> keys, List<Block> caseBlocks,
                      Block defaultBlock) {
            this.selector = Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(keys, "keys");
            Objects.requireNonNull(caseBlocks, "caseBlocks");
            if (keys.size() != caseBlocks.size()) {
                throw new IllegalArgumentException(
                        "Switch keys and case blocks must have the same size");
            }
            List<Integer> checkedKeys = new ArrayList<>();
            List<Block> checkedBlocks = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                Integer key = Objects.requireNonNull(keys.get(i), "key");
                if (checkedKeys.contains(key)) {
                    throw new IllegalArgumentException("Duplicate switch key " + key);
                }
                checkedKeys.add(key);
                checkedBlocks.add(Objects.requireNonNull(caseBlocks.get(i), "caseBlock"));
            }
            this.keys = Collections.unmodifiableList(checkedKeys);
            this.caseBlocks = Collections.unmodifiableList(checkedBlocks);
            this.defaultBlock = Objects.requireNonNull(defaultBlock, "defaultBlock");
        }

        @Override
        public void write(Printer printer) {
            printer.line("switch (" + selector.render() + ") {");
            printer.indent++;
            for (int i = 0; i < keys.size(); i++) {
                printer.line("case " + new IntLiteral(keys.get(i)).render() + ": {");
                printer.indent++;
                printer.write(caseBlocks.get(i).statements);
                printer.indent--;
                printer.line("}");
            }
            printer.line("default: {");
            printer.indent++;
            printer.write(defaultBlock.statements);
            printer.indent--;
            printer.line("}");
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

    private static String typeToken(String value) {
        Objects.requireNonNull(value, "type");
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*\\*?")) {
            throw new IllegalArgumentException("Invalid C++ type: " + value);
        }
        return value;
    }

    private static String qualifiedIdentifier(String value) {
        Objects.requireNonNull(value, "qualifiedIdentifier");
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*(::[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid qualified C++ identifier: " + value);
        }
        return value;
    }

    private static String allowedOperator(String value) {
        if ("+".equals(value) || "-".equals(value) || "*".equals(value)
                || "/".equals(value) || "%".equals(value)
                || "==".equals(value) || "!=".equals(value) || "<".equals(value)
                || ">=".equals(value) || ">".equals(value) || "<=".equals(value)
                || "||".equals(value) || "&&".equals(value)
                || "&".equals(value) || "|".equals(value)
                || "^".equals(value) || "<<".equals(value) || ">>".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported C++ operator: " + value);
    }

    private static List<Expression> immutableExpressions(List<Expression> expressions) {
        Objects.requireNonNull(expressions, "expressions");
        List<Expression> copy = new ArrayList<>();
        for (Expression expression : expressions) {
            copy.add(Objects.requireNonNull(expression, "expression"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String renderArguments(List<Expression> arguments) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(arguments.get(i).render());
        }
        return result.toString();
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
