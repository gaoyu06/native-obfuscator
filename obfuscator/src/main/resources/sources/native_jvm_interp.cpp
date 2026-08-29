#include "native_jvm_interp.hpp"

#include <cstring>
#include <limits>

namespace native_jvm::interp {
    namespace {
        bool read_u16(const method_desc &method, std::uint32_t &pc,
                      std::uint16_t &value) noexcept {
            if (pc > method.code_len || method.code_len - pc < 2) {
                return false;
            }
            value = static_cast<std::uint16_t>(method.code[pc]) |
                    static_cast<std::uint16_t>(
                            static_cast<std::uint16_t>(method.code[pc + 1]) << 8);
            pc += 2;
            return true;
        }

        bool read_u32(const method_desc &method, std::uint32_t &pc,
                      std::uint32_t &value) noexcept {
            if (pc > method.code_len || method.code_len - pc < 4) {
                return false;
            }
            value = static_cast<std::uint32_t>(method.code[pc]) |
                    (static_cast<std::uint32_t>(method.code[pc + 1]) << 8) |
                    (static_cast<std::uint32_t>(method.code[pc + 2]) << 16) |
                    (static_cast<std::uint32_t>(method.code[pc + 3]) << 24);
            pc += 4;
            return true;
        }

        bool read_i32(const method_desc &method, std::uint32_t &pc,
                      std::int32_t &value) noexcept {
            std::uint32_t bits;
            if (!read_u32(method, pc, bits)) {
                return false;
            }
            std::memcpy(&value, &bits, sizeof(value));
            return true;
        }

        std::int32_t from_unsigned(std::uint32_t value) noexcept {
            std::int32_t result;
            std::memcpy(&result, &value, sizeof(result));
            return result;
        }

        std::uint32_t arithmetic_shift_right(std::uint32_t value,
                                             std::uint32_t distance) noexcept {
            if (distance == 0) {
                return value;
            }
            std::uint32_t shifted = value >> distance;
            if ((value & 0x80000000U) != 0) {
                shifted |= ~std::uint32_t{0} << (32U - distance);
            }
            return shifted;
        }

        bool valid_target(const method_desc &method,
                          std::uint32_t target) noexcept {
            return target < method.code_len;
        }
    }

    execution_result execute_i(const method_desc &method, frame &current_frame,
                               std::int32_t *result) noexcept {
        if (method.isa_version != ISA_VERSION || method.code == nullptr ||
                method.code_len == 0 || current_frame.locals == nullptr ||
                current_frame.stack == nullptr || result == nullptr) {
            return execution_result::invalid_stream;
        }

        std::uint32_t pc = 0;
        std::uint16_t sp = 0;

        while (pc < method.code_len) {
            opcode current = static_cast<opcode>(method.code[pc++]);
            switch (current) {
                case opcode::ipush: {
                    std::int32_t value;
                    if (!read_i32(method, pc, value) ||
                            sp >= method.max_stack) {
                        return execution_result::invalid_stream;
                    }
                    current_frame.stack[sp++] = value;
                    break;
                }
                case opcode::iload: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) ||
                            local >= method.max_locals ||
                            sp >= method.max_stack) {
                        return execution_result::invalid_stream;
                    }
                    current_frame.stack[sp++] = current_frame.locals[local];
                    break;
                }
                case opcode::istore: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) ||
                            local >= method.max_locals || sp == 0) {
                        return execution_result::invalid_stream;
                    }
                    current_frame.locals[local] = current_frame.stack[--sp];
                    break;
                }
                case opcode::iadd:
                case opcode::isub:
                case opcode::imul:
                case opcode::iand:
                case opcode::ior:
                case opcode::ixor:
                case opcode::ishl:
                case opcode::ishr:
                case opcode::iushr:
                case opcode::idiv:
                case opcode::irem: {
                    if (sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    std::int32_t left_value = current_frame.stack[sp - 2];
                    std::int32_t right_value = current_frame.stack[sp - 1];
                    std::uint32_t left =
                            static_cast<std::uint32_t>(
                                    left_value);
                    std::uint32_t right =
                            static_cast<std::uint32_t>(
                                    right_value);
                    std::uint32_t value;
                    switch (current) {
                        case opcode::iadd:
                            value = left + right;
                            break;
                        case opcode::isub:
                            value = left - right;
                            break;
                        case opcode::imul:
                            value = left * right;
                            break;
                        case opcode::iand:
                            value = left & right;
                            break;
                        case opcode::ior:
                            value = left | right;
                            break;
                        case opcode::ixor:
                            value = left ^ right;
                            break;
                        case opcode::ishl:
                            value = left << (right & 0x1fU);
                            break;
                        case opcode::ishr:
                            value = arithmetic_shift_right(
                                    left, right & 0x1fU);
                            break;
                        case opcode::iushr:
                            value = left >> (right & 0x1fU);
                            break;
                        case opcode::idiv:
                            if (right_value == 0) {
                                return execution_result::arithmetic_exception;
                            }
                            if (left_value ==
                                    std::numeric_limits<std::int32_t>::min() &&
                                    right_value == -1) {
                                value = left;
                            } else {
                                value = static_cast<std::uint32_t>(
                                        left_value / right_value);
                            }
                            break;
                        case opcode::irem:
                            if (right_value == 0) {
                                return execution_result::arithmetic_exception;
                            }
                            if (left_value ==
                                    std::numeric_limits<std::int32_t>::min() &&
                                    right_value == -1) {
                                value = 0;
                            } else {
                                value = static_cast<std::uint32_t>(
                                        left_value % right_value);
                            }
                            break;
                        default:
                            return execution_result::invalid_stream;
                    }
                    current_frame.stack[sp - 2] = from_unsigned(value);
                    --sp;
                    break;
                }
                case opcode::ineg: {
                    if (sp == 0) {
                        return execution_result::invalid_stream;
                    }
                    std::uint32_t value = 0U -
                            static_cast<std::uint32_t>(
                                    current_frame.stack[sp - 1]);
                    current_frame.stack[sp - 1] = from_unsigned(value);
                    break;
                }
                case opcode::ifeq:
                case opcode::ifne:
                case opcode::iflt:
                case opcode::ifge:
                case opcode::ifgt:
                case opcode::ifle: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) || sp == 0) {
                        return execution_result::invalid_stream;
                    }
                    std::int32_t value = current_frame.stack[--sp];
                    bool taken =
                            (current == opcode::ifeq && value == 0) ||
                            (current == opcode::ifne && value != 0) ||
                            (current == opcode::iflt && value < 0) ||
                            (current == opcode::ifge && value >= 0) ||
                            (current == opcode::ifgt && value > 0) ||
                            (current == opcode::ifle && value <= 0);
                    if (taken) {
                        if (!valid_target(method, target)) {
                            return execution_result::invalid_stream;
                        }
                        pc = target;
                    }
                    break;
                }
                case opcode::if_icmpeq:
                case opcode::if_icmpne:
                case opcode::if_icmplt:
                case opcode::if_icmpge:
                case opcode::if_icmpgt:
                case opcode::if_icmple: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) || sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    std::int32_t right = current_frame.stack[--sp];
                    std::int32_t left = current_frame.stack[--sp];
                    bool taken =
                            (current == opcode::if_icmpeq && left == right) ||
                            (current == opcode::if_icmpne && left != right) ||
                            (current == opcode::if_icmplt && left < right) ||
                            (current == opcode::if_icmpge && left >= right) ||
                            (current == opcode::if_icmpgt && left > right) ||
                            (current == opcode::if_icmple && left <= right);
                    if (taken) {
                        if (!valid_target(method, target)) {
                            return execution_result::invalid_stream;
                        }
                        pc = target;
                    }
                    break;
                }
                case opcode::goto_: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) ||
                            !valid_target(method, target)) {
                        return execution_result::invalid_stream;
                    }
                    pc = target;
                    break;
                }
                case opcode::ireturn:
                    if (sp == 0) {
                        return execution_result::invalid_stream;
                    }
                    *result = current_frame.stack[--sp];
                    return execution_result::success;
                default:
                    return execution_result::invalid_stream;
            }
        }
        return execution_result::invalid_stream;
    }
}
