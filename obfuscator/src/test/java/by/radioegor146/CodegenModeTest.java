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

    private CodegenMode parseCodegen(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("codegenMode");
        field.setAccessible(true);
        return (CodegenMode) field.get(runner);
    }
}
