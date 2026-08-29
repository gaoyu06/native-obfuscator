package by.radioegor146.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A block-local SSA merge for either one JVM local or one operand-stack slot.
 */
public final class IrPhi {
    public enum SlotKind {
        LOCAL,
        STACK
    }

    private final IrValue result;
    private final SlotKind slotKind;
    private final int slotIndex;
    private final Map<IrBlock, IrValue> incoming = new LinkedHashMap<>();

    IrPhi(IrValue result, SlotKind slotKind, int slotIndex) {
        this.result = Objects.requireNonNull(result, "result");
        this.slotKind = Objects.requireNonNull(slotKind, "slotKind");
        this.slotIndex = slotIndex;
    }

    public IrValue getResult() {
        return result;
    }

    public SlotKind getSlotKind() {
        return slotKind;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void addIncoming(IrBlock predecessor, IrValue value) {
        incoming.put(Objects.requireNonNull(predecessor, "predecessor"),
                Objects.requireNonNull(value, "value"));
    }

    public Map<IrBlock, IrValue> getIncoming() {
        return Collections.unmodifiableMap(incoming);
    }
}
