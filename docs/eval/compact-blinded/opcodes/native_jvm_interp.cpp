#include "native_jvm_interp.hpp"

#include <cstring>

namespace native_jvm::interp {
    namespace {
        enum class opcode : std::uint8_t {
            ipush,
            iload,
            istore,
            iadd,
            isub,
            ifeq,
            ifne,
            iflt,
            ifge,
            ifgt,
            ifle,
            if_icmpeq,
            if_icmpne,
            if_icmplt,
            if_icmpge,
            if_icmpgt,
            if_icmple,
            goto_,
            ireturn,
            imul,
            ixor,
            ishl,
            iushr,
            irotl
        };

        struct opcode_decode_entry {
            std::uint8_t encoded;
            opcode decoded;
        };

        constexpr opcode_decode_entry opcode_decode_table[] = {
                {0xa7u, opcode::ipush},
                {0x31u, opcode::iload},
                {0xd4u, opcode::istore},
                {0x6bu, opcode::iadd},
                {0xe2u, opcode::isub},
                {0x19u, opcode::ifeq},
                {0xc8u, opcode::ifne},
                {0x45u, opcode::iflt},
                {0x9au, opcode::ifge},
                {0xf1u, opcode::ifgt},
                {0x2du, opcode::ifle},
                {0x74u, opcode::if_icmpeq},
                {0xb6u, opcode::if_icmpne},
                {0x0fu, opcode::if_icmplt},
                {0x83u, opcode::if_icmpge},
                {0xdcu, opcode::if_icmpgt},
                {0x52u, opcode::if_icmple},
                {0xaeu, opcode::goto_},
                {0x67u, opcode::ireturn},
                {0x3cu, opcode::imul},
                {0xf8u, opcode::ixor},
                {0x21u, opcode::ishl},
                {0x95u, opcode::iushr},
                {0xcau, opcode::irotl}
        };

        bool decode_opcode(std::uint8_t encoded, opcode &decoded) noexcept {
            for (const opcode_decode_entry &entry : opcode_decode_table) {
                if (entry.encoded == encoded) {
                    decoded = entry.decoded;
                    return true;
                }
            }
            return false;
        }

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

        bool valid_target(const method_desc &method, std::uint32_t target) noexcept {
            return target < method.code_len;
        }
    }

    bool execute_i(const method_desc &method, frame &current_frame,
                   std::int32_t *result) noexcept {
        if (method.isa_version != ISA_VERSION || method.code == nullptr ||
                method.code_len == 0 || current_frame.locals == nullptr ||
                current_frame.stack == nullptr || result == nullptr) {
            return false;
        }

        std::uint32_t pc = 0;
        std::uint16_t sp = 0;

        while (pc < method.code_len) {
            opcode current;
            if (!decode_opcode(method.code[pc++], current)) {
                return false;
            }
            switch (current) {
                case opcode::ipush: {
                    std::int32_t value;
                    if (!read_i32(method, pc, value) || sp >= method.max_stack) {
                        return false;
                    }
                    current_frame.stack[sp++] = value;
                    break;
                }
                case opcode::iload: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) || local >= method.max_locals ||
                            sp >= method.max_stack) {
                        return false;
                    }
                    current_frame.stack[sp++] = current_frame.locals[local];
                    break;
                }
                case opcode::istore: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) || local >= method.max_locals ||
                            sp == 0) {
                        return false;
                    }
                    current_frame.locals[local] = current_frame.stack[--sp];
                    break;
                }
                case opcode::iadd:
                case opcode::isub:
                case opcode::imul:
                case opcode::ixor:
                case opcode::ishl:
                case opcode::iushr:
                case opcode::irotl: {
                    if (sp < 2) {
                        return false;
                    }
                    std::uint32_t left =
                            static_cast<std::uint32_t>(current_frame.stack[sp - 2]);
                    std::uint32_t right =
                            static_cast<std::uint32_t>(current_frame.stack[sp - 1]);
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
                        case opcode::ixor:
                            value = left ^ right;
                            break;
                        case opcode::ishl:
                            value = left << (right & 31u);
                            break;
                        case opcode::iushr:
                            value = left >> (right & 31u);
                            break;
                        case opcode::irotl: {
                            std::uint32_t distance = right & 31u;
                            value = (left << distance) |
                                    (left >> ((32u - distance) & 31u));
                            break;
                        }
                        default:
                            return false;
                    }
                    current_frame.stack[sp - 2] = from_unsigned(value);
                    --sp;
                    break;
                }
                case opcode::ifeq:
                case opcode::ifne:
                case opcode::iflt:
                case opcode::ifge:
                case opcode::ifgt:
                case opcode::ifle: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) ||
                            !valid_target(method, target) || sp == 0) {
                        return false;
                    }
                    std::int32_t value = current_frame.stack[--sp];
                    bool taken = (current == opcode::ifeq && value == 0) ||
                            (current == opcode::ifne && value != 0) ||
                            (current == opcode::iflt && value < 0) ||
                            (current == opcode::ifge && value >= 0) ||
                            (current == opcode::ifgt && value > 0) ||
                            (current == opcode::ifle && value <= 0);
                    if (taken) {
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
                    if (!read_u32(method, pc, target) ||
                            !valid_target(method, target) || sp < 2) {
                        return false;
                    }
                    std::int32_t right = current_frame.stack[--sp];
                    std::int32_t left = current_frame.stack[--sp];
                    bool taken = (current == opcode::if_icmpeq && left == right) ||
                            (current == opcode::if_icmpne && left != right) ||
                            (current == opcode::if_icmplt && left < right) ||
                            (current == opcode::if_icmpge && left >= right) ||
                            (current == opcode::if_icmpgt && left > right) ||
                            (current == opcode::if_icmple && left <= right);
                    if (taken) {
                        pc = target;
                    }
                    break;
                }
                case opcode::goto_: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) || !valid_target(method, target)) {
                        return false;
                    }
                    pc = target;
                    break;
                }
                case opcode::ireturn:
                    if (sp == 0) {
                        return false;
                    }
                    *result = current_frame.stack[--sp];
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }
}
