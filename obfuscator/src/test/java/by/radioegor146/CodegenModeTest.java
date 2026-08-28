package by.radioegor146;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodegenModeTest {
    @Test
    public void cliDefaultsToLegacy() throws Exception {
        assertEquals(CodegenMode.LEGACY, parseCodegen("input.jar", "output"));
    }

    @Test
    public void cliAcceptsIr() throws Exception {
        assertEquals(CodegenMode.IR,
                parseCodegen("input.jar", "output", "--codegen=ir"));
    }

    @Test
    public void cliDefaultsToDirectIrLowering() throws Exception {
        assertEquals(IrLoweringMode.DIRECT,
                parseIrLowering("input.jar", "output", "--codegen=ir"));
    }

    @Test
    public void cliAcceptsEvaluatorIrLowering() throws Exception {
        assertEquals(IrLoweringMode.EVAL,
                parseIrLowering("input.jar", "output",
                        "--codegen=ir", "--ir-lower=eval"));
    }

    private CodegenMode parseCodegen(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("codegenMode");
        field.setAccessible(true);
        return (CodegenMode) field.get(runner);
    }

    private IrLoweringMode parseIrLowering(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("irLoweringMode");
        field.setAccessible(true);
        return (IrLoweringMode) field.get(runner);
    }
}
