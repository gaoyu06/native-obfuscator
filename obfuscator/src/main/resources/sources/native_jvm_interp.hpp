#include "jni.h"

#include <cstdint>

#ifndef NATIVE_JVM_INTERP_HPP_GUARD
#define NATIVE_JVM_INTERP_HPP_GUARD

namespace native_jvm::interp {

    constexpr std::uint16_t ISA_VERSION = 4;

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
        irem = 29,
        lpush = 30,
        lload = 31,
        lstore = 32,
        ladd = 33,
        lsub = 34,
        lmul = 35,
        land = 36,
        lor = 37,
        lxor = 38,
        lshl = 39,
        lshr = 40,
        lushr = 41,
        lneg = 42,
        lreturn = 43,
        ldiv = 44,
        lrem = 45,
        aconst_null = 46,
        aload = 47,
        astore = 48,
        areturn = 49,
        ifnull = 50,
        ifnonnull = 51,
        athrow = 52
    };

    enum class execution_result : std::uint8_t {
        success,
        invalid_stream,
        pending_exception
    };

    struct exception_handler {
        std::uint32_t start_pc;
        std::uint32_t end_pc;
        std::uint32_t handler_pc;
        const char *catch_type;
    };

    struct method_desc {
        std::uint16_t isa_version;
        std::uint16_t max_stack;
        std::uint16_t max_locals;
        const std::uint8_t *code;
        std::uint32_t code_len;
        const exception_handler *exception_table = nullptr;
        std::uint32_t exception_table_len = 0;
    };

    struct frame {
        std::int32_t *locals;
        std::int32_t *stack;
        jobject *ref_locals = nullptr;
        jobject *ref_stack = nullptr;
        jthrowable pending_exception = nullptr;
    };

    void store_long(std::int32_t *slots, std::int64_t value) noexcept;

    execution_result execute_i(const method_desc &method, frame &current_frame,
                               std::int32_t *result,
                               JNIEnv *env = nullptr) noexcept;

    execution_result execute_j(const method_desc &method, frame &current_frame,
                               std::int64_t *result,
                               JNIEnv *env = nullptr) noexcept;

    execution_result execute_l(const method_desc &method, frame &current_frame,
                               jobject *result,
                               JNIEnv *env = nullptr) noexcept;
}

#endif
