#include "native_jvm_interp.hpp"

#include <cstring>

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
            std::uint8_t current = method.code[pc++];
            switch (current) {
                case $op0: {
                    std::int32_t value;
                    if (!read_i32(method, pc, value) || sp >= method.max_stack) {
                        return false;
                    }
                    current_frame.stack[sp++] = value;
                    break;
                }
                case $op1: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) || local >= method.max_locals ||
                            sp >= method.max_stack) {
                        return false;
                    }
                    current_frame.stack[sp++] = current_frame.locals[local];
                    break;
                }
                case $op2: {
                    std::uint16_t local;
                    if (!read_u16(method, pc, local) || local >= method.max_locals ||
                            sp == 0) {
                        return false;
                    }
                    current_frame.locals[local] = current_frame.stack[--sp];
                    break;
                }
                case $op3:
                case $op4:
                case $op19:
                case $op20:
                case $op21:
                case $op22:
                case $op23: {
                    if (sp < 2) {
                        return false;
                    }
                    std::uint32_t left =
                            static_cast<std::uint32_t>(current_frame.stack[sp - 2]);
                    std::uint32_t right =
                            static_cast<std::uint32_t>(current_frame.stack[sp - 1]);
                    std::uint32_t value;
                    switch (current) {
                        case $op3:
                            value = left + right;
                            break;
                        case $op4:
                            value = left - right;
                            break;
                        case $op19:
                            value = left * right;
                            break;
                        case $op20:
                            value = left ^ right;
                            break;
                        case $op21:
                            value = left << (right & 31u);
                            break;
                        case $op22:
                            value = left >> (right & 31u);
                            break;
                        case $op23: {
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
                case $op5:
                case $op6:
                case $op7:
                case $op8:
                case $op9:
                case $op10: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) ||
                            !valid_target(method, target) || sp == 0) {
                        return false;
                    }
                    std::int32_t value = current_frame.stack[--sp];
                    bool taken = (current == $op5 && value == 0) ||
                            (current == $op6 && value != 0) ||
                            (current == $op7 && value < 0) ||
                            (current == $op8 && value >= 0) ||
                            (current == $op9 && value > 0) ||
                            (current == $op10 && value <= 0);
                    if (taken) {
                        pc = target;
                    }
                    break;
                }
                case $op11:
                case $op12:
                case $op13:
                case $op14:
                case $op15:
                case $op16: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) ||
                            !valid_target(method, target) || sp < 2) {
                        return false;
                    }
                    std::int32_t right = current_frame.stack[--sp];
                    std::int32_t left = current_frame.stack[--sp];
                    bool taken = (current == $op11 && left == right) ||
                            (current == $op12 && left != right) ||
                            (current == $op13 && left < right) ||
                            (current == $op14 && left >= right) ||
                            (current == $op15 && left > right) ||
                            (current == $op16 && left <= right);
                    if (taken) {
                        pc = target;
                    }
                    break;
                }
                case $op17: {
                    std::uint32_t target;
                    if (!read_u32(method, pc, target) || !valid_target(method, target)) {
                        return false;
                    }
                    pc = target;
                    break;
                }
                case $op18:
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
