package by.radioegor146.ir.backend;

import by.radioegor146.ir.IrMethod;

/**
 * Extension point between the finalized IR and a concrete method body.
 */
public interface MethodLoweringStrategy {
    boolean supports(IrMethod method);

    LoweredMethod lower(IrMethod method, LoweringContext context);
}
