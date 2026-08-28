#include <cstdint>

#ifndef NATIVE_JVM_INTERP_HPP_GUARD
#define NATIVE_JVM_INTERP_HPP_GUARD

namespace native_jvm::interp {

    constexpr std::uint16_t ISA_VERSION = 2;

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

    bool execute_i(const method_desc &method, frame &current_frame,
                   std::int32_t *result) noexcept;
}

#endif
