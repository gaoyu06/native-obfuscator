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

        bool read_u64(const method_desc &method, std::uint32_t &pc,
                      std::uint64_t &value) noexcept {
            std::uint32_t low;
            std::uint32_t high;
            if (!read_u32(method, pc, low) ||
                    !read_u32(method, pc, high)) {
                return false;
            }
            value = static_cast<std::uint64_t>(low) |
                    (static_cast<std::uint64_t>(high) << 32U);
            return true;
        }

        std::int32_t from_unsigned(std::uint32_t value) noexcept {
            std::int32_t result;
            std::memcpy(&result, &value, sizeof(result));
            return result;
        }

        std::int64_t long_from_unsigned(std::uint64_t value) noexcept {
            std::int64_t result;
            std::memcpy(&result, &value, sizeof(result));
            return result;
        }

        std::uint64_t long_bits(const std::int32_t *slots) noexcept {
            return static_cast<std::uint32_t>(slots[0]) |
                    (static_cast<std::uint64_t>(
                            static_cast<std::uint32_t>(slots[1])) << 32U);
        }

        void store_long_bits(std::int32_t *slots,
                             std::uint64_t value) noexcept {
            slots[0] = from_unsigned(static_cast<std::uint32_t>(value));
            slots[1] = from_unsigned(static_cast<std::uint32_t>(value >> 32U));
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

        std::uint64_t arithmetic_shift_right(std::uint64_t value,
                                             std::uint32_t distance) noexcept {
            if (distance == 0) {
                return value;
            }
            std::uint64_t shifted = value >> distance;
            if ((value & 0x8000000000000000ULL) != 0) {
                shifted |= ~std::uint64_t{0} << (64U - distance);
            }
            return shifted;
        }

        bool valid_target(const method_desc &method,
                          std::uint32_t target) noexcept {
            return target < method.code_len;
        }
    }

    void store_long(std::int32_t *slots, std::int64_t value) noexcept {
        std::uint64_t bits;
        std::memcpy(&bits, &value, sizeof(bits));
        store_long_bits(slots, bits);
    }

    namespace {
    execution_result execute(const method_desc &method, frame &current_frame,
                             std::int32_t *int_result,
                             std::int64_t *long_result) noexcept {
        if (method.isa_version != ISA_VERSION || method.code == nullptr ||
                method.code_len == 0 || current_frame.locals == nullptr ||
                current_frame.stack == nullptr ||
                (int_result == nullptr) == (long_result == nullptr)) {
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
                case opcode::lpush: {
                    std::uint64_t value;
                    if (!read_u64(method, pc, value) ||
                            sp > method.max_stack ||
                            method.max_stack - sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    store_long_bits(current_frame.stack + sp, value);
                    sp += 2;
                    break;
                }
                case opcode::lload: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) ||
                            local >= method.max_locals ||
                            method.max_locals - local < 2 ||
                            sp > method.max_stack ||
                            method.max_stack - sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    current_frame.stack[sp] = current_frame.locals[local];
                    current_frame.stack[sp + 1] =
                            current_frame.locals[local + 1];
                    sp += 2;
                    break;
                }
                case opcode::lstore: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) ||
                            local >= method.max_locals ||
                            method.max_locals - local < 2 || sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    sp -= 2;
                    current_frame.locals[local] = current_frame.stack[sp];
                    current_frame.locals[local + 1] =
                            current_frame.stack[sp + 1];
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
                case opcode::ladd:
                case opcode::lsub:
                case opcode::lmul:
                case opcode::land:
                case opcode::lor:
                case opcode::lxor:
                case opcode::ldiv:
                case opcode::lrem: {
                    if (sp < 4) {
                        return execution_result::invalid_stream;
                    }
                    std::uint64_t left =
                            long_bits(current_frame.stack + sp - 4);
                    std::uint64_t right =
                            long_bits(current_frame.stack + sp - 2);
                    std::uint64_t value;
                    switch (current) {
                        case opcode::ladd:
                            value = left + right;
                            break;
                        case opcode::lsub:
                            value = left - right;
                            break;
                        case opcode::lmul:
                            value = left * right;
                            break;
                        case opcode::land:
                            value = left & right;
                            break;
                        case opcode::lor:
                            value = left | right;
                            break;
                        case opcode::lxor:
                            value = left ^ right;
                            break;
                        case opcode::ldiv:
                        case opcode::lrem: {
                            std::int64_t signed_left =
                                    long_from_unsigned(left);
                            std::int64_t signed_right =
                                    long_from_unsigned(right);
                            if (signed_right == 0) {
                                return execution_result::
                                        arithmetic_exception;
                            }
                            if (signed_left ==
                                    std::numeric_limits<
                                            std::int64_t>::min() &&
                                    signed_right == -1) {
                                value = current == opcode::ldiv ? left : 0;
                            } else if (current == opcode::ldiv) {
                                value = static_cast<std::uint64_t>(
                                        signed_left / signed_right);
                            } else {
                                value = static_cast<std::uint64_t>(
                                        signed_left % signed_right);
                            }
                            break;
                        }
                        default:
                            return execution_result::invalid_stream;
                    }
                    store_long_bits(current_frame.stack + sp - 4, value);
                    sp -= 2;
                    break;
                }
                case opcode::lshl:
                case opcode::lshr:
                case opcode::lushr: {
                    if (sp < 3) {
                        return execution_result::invalid_stream;
                    }
                    std::uint64_t left =
                            long_bits(current_frame.stack + sp - 3);
                    std::uint32_t distance =
                            static_cast<std::uint32_t>(
                                    current_frame.stack[sp - 1]) & 0x3fU;
                    std::uint64_t value;
                    if (current == opcode::lshl) {
                        value = left << distance;
                    } else if (current == opcode::lshr) {
                        value = arithmetic_shift_right(left, distance);
                    } else {
                        value = left >> distance;
                    }
                    store_long_bits(current_frame.stack + sp - 3, value);
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
                case opcode::lneg: {
                    if (sp < 2) {
                        return execution_result::invalid_stream;
                    }
                    std::uint64_t value = 0ULL -
                            long_bits(current_frame.stack + sp - 2);
                    store_long_bits(current_frame.stack + sp - 2, value);
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
                    if (sp == 0 || int_result == nullptr) {
                        return execution_result::invalid_stream;
                    }
                    *int_result = current_frame.stack[--sp];
                    return execution_result::success;
                case opcode::lreturn:
                    if (sp < 2 || long_result == nullptr) {
                        return execution_result::invalid_stream;
                    }
                    sp -= 2;
                    *long_result = long_from_unsigned(
                            long_bits(current_frame.stack + sp));
                    return execution_result::success;
                default:
                    return execution_result::invalid_stream;
            }
        }
        return execution_result::invalid_stream;
    }
    }

    execution_result execute_i(const method_desc &method, frame &current_frame,
                               std::int32_t *result) noexcept {
        return execute(method, current_frame, result, nullptr);
    }

    execution_result execute_j(const method_desc &method, frame &current_frame,
                               std::int64_t *result) noexcept {
        return execute(method, current_frame, nullptr, result);
    }
}
