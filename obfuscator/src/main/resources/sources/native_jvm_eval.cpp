#include "native_jvm_eval.hpp"

#include <cstring>
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

    std::uint32_t jint_bits(jint value) {
        static_assert(sizeof(jint) == sizeof(std::uint32_t),
                      "The evaluator requires a 32-bit jint");
        std::uint32_t result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }

    jint bits_to_jint(std::uint32_t value) {
        jint result;
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
}

jint evaluate_i32(const std::uint8_t *data, std::size_t size,
                  const jint *arguments, std::size_t argument_count) {
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

    std::vector<jint> registers(register_count, 0);
    for (std::size_t i = 0; i < argument_count; i++) {
        registers[i] = arguments[i];
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
                registers[destination] = bits_to_jint(immediate);
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
                const std::uint32_t left_value = jint_bits(registers[left]);
                const std::uint32_t right_value = jint_bits(registers[right]);
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
                registers[destination] = bits_to_jint(result);
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
                const jint right_value = right == ZERO_REGISTER ? 0 : registers[right];
                const std::uint32_t target =
                        branch_matches(condition, registers[left], right_value)
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
                return registers[source];
            }
            default:
                return 0;
        }
    }
    return 0;
}
}
