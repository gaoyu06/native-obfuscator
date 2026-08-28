package by.radioegor146;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodegenModeTest {
    @Test
    public void cliDefaultsToLegacy() {
        CommandLine.ParseResult result = new CommandLine(new Main.NativeObfuscatorRunner())
                .parseArgs("input.jar", "output");

        assertEquals(CodegenMode.LEGACY, result.valueFor("--codegen"));
    }

    @Test
    public void cliAcceptsIr() {
        CommandLine.ParseResult result = new CommandLine(new Main.NativeObfuscatorRunner())
                .parseArgs("input.jar", "output", "--codegen=ir");

        assertEquals(CodegenMode.IR, result.valueFor("--codegen"));
    }
}
