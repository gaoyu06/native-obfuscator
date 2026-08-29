package by.radioegor146;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import picocli.CommandLine;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void methodProcessingConvenienceDefaultRemainsLegacy() {
        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);

        assertFalse(MethodProcessor.shouldProcess(constructor));
        assertTrue(MethodProcessor.shouldProcess(constructor, CodegenMode.IR));
    }

    private CodegenMode parseCodegen(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("codegenMode");
        field.setAccessible(true);
        return (CodegenMode) field.get(runner);
    }
}
