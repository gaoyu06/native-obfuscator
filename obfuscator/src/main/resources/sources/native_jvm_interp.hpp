#include <cstdint>

#ifndef NATIVE_JVM_INTERP_HPP_GUARD
#define NATIVE_JVM_INTERP_HPP_GUARD

namespace native_jvm::interp {

    constexpr std::uint16_t ISA_VERSION = 2;

    enum class opcode : std::uint8_t {
        ipush = 1,
        iload = 2,
        istore = 3,
        iadd = 4,
        isub = 5,
        ifeq = 6,
        ifne = 7,
        iflt = 8,
        ifge = 9,
        ifgt = 10,
        ifle = 11,
        if_icmpeq = 12,
        if_icmpne = 13,
        if_icmplt = 14,
        if_icmpge = 15,
        if_icmpgt = 16,
        if_icmple = 17,
        goto_ = 18,
        ireturn = 19,
        imul = 20,
        iand = 21,
        ior = 22,
        ixor = 23,
        ishl = 24,
        ishr = 25,
        iushr = 26,
        ineg = 27,
        idiv = 28,
        irem = 29
    };

    enum class execution_result : std::uint8_t {
        success,
        invalid_stream,
        arithmetic_exception
    };

    struct method_desc {
        std::uint16_t isa_version;
        std::uint16_t max_stack;
        std::uint16_t max_locals;
        const std::uint8_t *code;
        std::uint32_t code_len;
    };

    struct frame {
        std::int32_t *locals;
        std::int32_t *stack;
    };

    execution_result execute_i(const method_desc &method, frame &current_frame,
                               std::int32_t *result) noexcept;
}

#endif
