package by.radioegor146;

/**
 * Control-flow transforms applied to the IR before direct C++ emission.
 *
 * <p>{@link #OFF} is the default. {@link #BASIC} inserts always-true fake
 * branches and flattens normal control flow through a dispatcher. Evaluator
 * and interpreter backends skip these transforms.
 */
public enum ControlFlowObfuscationMode {
    OFF,
    BASIC;

    public boolean enabled() {
        return this == BASIC;
    }
}
