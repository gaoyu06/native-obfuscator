#include "jni.h"

#include <cstddef>
#include <cstdint>

#ifndef NATIVE_JVM_EVAL_HPP_GUARD
#define NATIVE_JVM_EVAL_HPP_GUARD

namespace native_jvm::ir_eval {
    jint evaluate_i32(JNIEnv *env, const std::uint8_t *data, std::size_t size,
                      const jlong *arguments, std::size_t argument_count);
    jlong evaluate_i64(JNIEnv *env, const std::uint8_t *data, std::size_t size,
                       const jlong *arguments, std::size_t argument_count);
}

#endif
