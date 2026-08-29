#include "native_jvm_eval.hpp"

#include <cstring>
#include <limits>
#include <vector>

namespace native_jvm::ir_eval {
namespace {
    constexpr std::uint8_t OP_CONST_I32 = 0x01;
    constexpr std::uint8_t OP_MOVE = 0x02;
    constexpr std::uint8_t OP_IADD = 0x10;
    constexpr std::uint8_t OP_ISUB = 0x11;
    constexpr std::uint8_t OP_IMUL = 0x12;
    constexpr std::uint8_t OP_IAND = 0x13;
    constexpr std::uint8_t OP_IOR = 0x14;
    constexpr std::uint8_t OP_IXOR = 0x15;
    constexpr std::uint8_t OP_ISHL = 0x16;
    constexpr std::uint8_t OP_ISHR = 0x17;
    constexpr std::uint8_t OP_IUSHR = 0x18;
    constexpr std::uint8_t OP_JUMP = 0x20;
    constexpr std::uint8_t OP_BRANCH = 0x21;
    constexpr std::uint8_t OP_RETURN_I32 = 0x22;
    constexpr std::uint8_t OP_LLOAD = 0x23;
    constexpr std::uint8_t OP_LSTORE = 0x24;
    constexpr std::uint8_t OP_LADD = 0x25;
    constexpr std::uint8_t OP_LSUB = 0x26;
    constexpr std::uint8_t OP_LMUL = 0x27;
    constexpr std::uint8_t OP_LRETURN = 0x28;
    constexpr std::uint8_t OP_I2L = 0x29;
    constexpr std::uint8_t OP_L2I = 0x2a;
    constexpr std::uint8_t OP_LDIV = 0x2b;
    constexpr std::uint8_t OP_LREM = 0x2c;
    constexpr std::uint8_t OP_LAND = 0x2d;
    constexpr std::uint8_t OP_LOR = 0x2e;
    constexpr std::uint8_t OP_LXOR = 0x2f;
    constexpr std::uint8_t OP_LSHL = 0x30;
    constexpr std::uint8_t OP_LSHR = 0x31;
    constexpr std::uint8_t OP_LUSHR = 0x32;

    constexpr std::uint16_t ZERO_REGISTER = 0xffff;
    constexpr std::size_t HEADER_SIZE = 8;

    class Reader {
    public:
        Reader(const std::uint8_t *data, std::size_t size)
                : data_(data), size_(size), position_(0), valid_(data != nullptr) {
        }

        std::uint8_t u8() {
            if (position_ >= size_) {
                valid_ = false;
                return 0;
            }
            return data_[position_++];
        }

        std::uint16_t u16() {
            std::uint16_t value = u8();
            value |= static_cast<std::uint16_t>(u8()) << 8U;
            return value;
        }

        std::uint32_t u32() {
            std::uint32_t value = u8();
            value |= static_cast<std::uint32_t>(u8()) << 8U;
            value |= static_cast<std::uint32_t>(u8()) << 16U;
            value |= static_cast<std::uint32_t>(u8()) << 24U;
            return value;
        }

        bool jump(std::uint32_t target) {
            if (target < HEADER_SIZE || target >= size_) {
                valid_ = false;
                return false;
            }
            position_ = target;
            return true;
        }

        bool valid() const {
            return valid_;
        }

    private:
        const std::uint8_t *data_;
        std::size_t size_;
        std::size_t position_;
        bool valid_;
    };

    jint bits_to_jint(std::uint32_t value) {
        static_assert(sizeof(jint) == sizeof(std::uint32_t),
                      "The evaluator requires a 32-bit jint");
        jint result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    std::uint64_t jlong_bits(jlong value) {
        static_assert(sizeof(jlong) == sizeof(std::uint64_t),
                      "The evaluator requires a 64-bit jlong");
        std::uint64_t result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    jlong bits_to_jlong(std::uint64_t value) {
        jlong result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    std::int64_t bits_to_int64(std::uint64_t value) {
        static_assert(sizeof(std::int64_t) == sizeof(std::uint64_t),
                      "The evaluator requires a 64-bit int64_t");
        std::int64_t result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    std::uint64_t int64_bits(std::int64_t value) {
        std::uint64_t result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    std::uint32_t arithmetic_shift_right(std::uint32_t value,
                                         std::uint32_t shift) {
        if (shift == 0) {
            return value;
        }
        const std::uint32_t shifted = value >> shift;
        if ((value & 0x80000000U) == 0) {
            return shifted;
        }
        return shifted | (0xffffffffU << (32U - shift));
    }

    std::uint64_t arithmetic_shift_right_64(std::uint64_t value,
                                            std::uint32_t shift) {
        if (shift == 0) {
            return value;
        }
        const std::uint64_t shifted = value >> shift;
        if ((value & 0x8000000000000000ULL) == 0) {
            return shifted;
        }
        return shifted | (~std::uint64_t{0} << (64U - shift));
    }

    bool valid_register(std::uint16_t index, std::size_t register_count) {
        return index < register_count;
    }

    bool branch_matches(std::uint8_t condition, jint left, jint right) {
        switch (condition) {
            case 0:
                return left == right;
            case 1:
                return left != right;
            case 2:
                return left < right;
            case 3:
                return left >= right;
            case 4:
                return left > right;
            case 5:
                return left <= right;
            default:
                return false;
        }
    }

    void throw_arithmetic_exception(JNIEnv *env, const char *message) {
        if (env == nullptr) {
            return;
        }
        jclass exception_class = env->FindClass("java/lang/ArithmeticException");
        if (exception_class == nullptr) {
            return;
        }
        env->ThrowNew(exception_class, message);
        env->DeleteLocalRef(exception_class);
    }

}

std::uint64_t evaluate_bits(JNIEnv *env, const std::uint8_t *data, std::size_t size,
                            const jlong *arguments, std::size_t argument_count) {
    Reader reader(data, size);
    if (size < HEADER_SIZE
            || reader.u8() != 0x4e
            || reader.u8() != 0x4a
            || reader.u8() != 0x45
            || reader.u8() != 1) {
        return 0;
    }

    const std::uint16_t register_count = reader.u16();
    const std::uint16_t encoded_argument_count = reader.u16();
    if (!reader.valid() || register_count == 0
            || encoded_argument_count != argument_count
            || argument_count > register_count
            || (argument_count != 0 && arguments == nullptr)) {
        return 0;
    }

    std::vector<std::uint64_t> registers(register_count, 0);
    for (std::size_t i = 0; i < argument_count; i++) {
        registers[i] = jlong_bits(arguments[i]);
    }

    while (reader.valid()) {
        const std::uint8_t opcode = reader.u8();
        switch (opcode) {
            case OP_CONST_I32: {
                const std::uint16_t destination = reader.u16();
                const std::uint32_t immediate = reader.u32();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())) {
                    return 0;
                }
                registers[destination] = immediate;
                break;
            }
            case OP_MOVE: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t source = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || !valid_register(source, registers.size())) {
                    return 0;
                }
                registers[destination] = registers[source];
                break;
            }
            case OP_IADD:
            case OP_ISUB:
            case OP_IMUL:
            case OP_IAND:
            case OP_IOR:
            case OP_IXOR:
            case OP_ISHL:
            case OP_ISHR:
            case OP_IUSHR: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t left = reader.u16();
                const std::uint16_t right = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || !valid_register(left, registers.size())
                        || !valid_register(right, registers.size())) {
                    return 0;
                }
                const std::uint32_t left_value =
                        static_cast<std::uint32_t>(registers[left]);
                const std::uint32_t right_value =
                        static_cast<std::uint32_t>(registers[right]);
                const std::uint32_t shift = right_value & 31U;
                std::uint32_t result;
                switch (opcode) {
                    case OP_IADD:
                        result = left_value + right_value;
                        break;
                    case OP_ISUB:
                        result = left_value - right_value;
                        break;
                    case OP_IMUL:
                        result = left_value * right_value;
                        break;
                    case OP_IAND:
                        result = left_value & right_value;
                        break;
                    case OP_IOR:
                        result = left_value | right_value;
                        break;
                    case OP_IXOR:
                        result = left_value ^ right_value;
                        break;
                    case OP_ISHL:
                        result = left_value << shift;
                        break;
                    case OP_ISHR:
                        result = arithmetic_shift_right(left_value, shift);
                        break;
                    case OP_IUSHR:
                        result = left_value >> shift;
                        break;
                    default:
                        return 0;
                }
                registers[destination] = result;
                break;
            }
            case OP_LLOAD: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t argument = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || argument >= argument_count) {
                    return 0;
                }
                registers[destination] = jlong_bits(arguments[argument]);
                break;
            }
            case OP_LSTORE: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t source = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || !valid_register(source, registers.size())) {
                    return 0;
                }
                registers[destination] = registers[source];
                break;
            }
            case OP_LADD:
            case OP_LSUB:
            case OP_LMUL:
            case OP_LDIV:
            case OP_LREM:
            case OP_LAND:
            case OP_LOR:
            case OP_LXOR:
            case OP_LSHL:
            case OP_LSHR:
            case OP_LUSHR: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t left = reader.u16();
                const std::uint16_t right = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || !valid_register(left, registers.size())
                        || !valid_register(right, registers.size())) {
                    return 0;
                }
                switch (opcode) {
                    case OP_LADD:
                        registers[destination] = registers[left] + registers[right];
                        break;
                    case OP_LSUB:
                        registers[destination] = registers[left] - registers[right];
                        break;
                    case OP_LMUL:
                        registers[destination] = registers[left] * registers[right];
                        break;
                    case OP_LDIV:
                    case OP_LREM: {
                        const std::int64_t left_value = bits_to_int64(registers[left]);
                        const std::int64_t right_value = bits_to_int64(registers[right]);
                        if (right_value == 0) {
                            throw_arithmetic_exception(env,
                                    opcode == OP_LDIV ? "LDIV / by 0" : "LREM % by 0");
                            return 0;
                        }
                        if (left_value == std::numeric_limits<std::int64_t>::min()
                                && right_value == -1) {
                            registers[destination] = opcode == OP_LDIV
                                    ? int64_bits(left_value) : 0;
                        } else {
                            registers[destination] = int64_bits(opcode == OP_LDIV
                                    ? left_value / right_value
                                    : left_value % right_value);
                        }
                        break;
                    }
                    case OP_LAND:
                        registers[destination] = registers[left] & registers[right];
                        break;
                    case OP_LOR:
                        registers[destination] = registers[left] | registers[right];
                        break;
                    case OP_LXOR:
                        registers[destination] = registers[left] ^ registers[right];
                        break;
                    case OP_LSHL:
                        registers[destination] = registers[left]
                                << (static_cast<std::uint32_t>(registers[right]) & 63U);
                        break;
                    case OP_LSHR:
                        registers[destination] = arithmetic_shift_right_64(
                                registers[left],
                                static_cast<std::uint32_t>(registers[right]) & 63U);
                        break;
                    case OP_LUSHR:
                        registers[destination] = registers[left]
                                >> (static_cast<std::uint32_t>(registers[right]) & 63U);
                        break;
                    default:
                        return 0;
                }
                break;
            }
            case OP_I2L:
            case OP_L2I: {
                const std::uint16_t destination = reader.u16();
                const std::uint16_t source = reader.u16();
                if (!reader.valid()
                        || !valid_register(destination, registers.size())
                        || !valid_register(source, registers.size())) {
                    return 0;
                }
                if (opcode == OP_I2L) {
                    const jint value = bits_to_jint(
                            static_cast<std::uint32_t>(registers[source]));
                    registers[destination] = jlong_bits(static_cast<jlong>(value));
                } else {
                    registers[destination] =
                            static_cast<std::uint32_t>(registers[source]);
                }
                break;
            }
            case OP_JUMP: {
                const std::uint32_t target = reader.u32();
                if (!reader.valid() || !reader.jump(target)) {
                    return 0;
                }
                break;
            }
            case OP_BRANCH: {
                const std::uint8_t condition = reader.u8();
                const std::uint16_t left = reader.u16();
                const std::uint16_t right = reader.u16();
                const std::uint32_t true_target = reader.u32();
                const std::uint32_t false_target = reader.u32();
                if (!reader.valid() || condition > 5
                        || !valid_register(left, registers.size())
                        || (right != ZERO_REGISTER
                        && !valid_register(right, registers.size()))) {
                    return 0;
                }
                const jint left_value = bits_to_jint(
                        static_cast<std::uint32_t>(registers[left]));
                const jint right_value = right == ZERO_REGISTER ? 0 : bits_to_jint(
                        static_cast<std::uint32_t>(registers[right]));
                const std::uint32_t target =
                        branch_matches(condition, left_value, right_value)
                                ? true_target : false_target;
                if (!reader.jump(target)) {
                    return 0;
                }
                break;
            }
            case OP_RETURN_I32: {
                const std::uint16_t source = reader.u16();
                if (!reader.valid() || !valid_register(source, registers.size())) {
                    return 0;
                }
                return static_cast<std::uint32_t>(registers[source]);
            }
            case OP_LRETURN: {
                const std::uint16_t source = reader.u16();
                if (!reader.valid() || !valid_register(source, registers.size())) {
                    return 0;
                }
                return registers[source];
            }
            default:
                return 0;
        }
    }
    return 0;
}

jint evaluate_i32(JNIEnv *env, const std::uint8_t *data, std::size_t size,
                  const jlong *arguments, std::size_t argument_count) {
    return bits_to_jint(static_cast<std::uint32_t>(
            evaluate_bits(env, data, size, arguments, argument_count)));
}

jlong evaluate_i64(JNIEnv *env, const std::uint8_t *data, std::size_t size,
                   const jlong *arguments, std::size_t argument_count) {
    return bits_to_jlong(evaluate_bits(env, data, size, arguments, argument_count));
}
}
