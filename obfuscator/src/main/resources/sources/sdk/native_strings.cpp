/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#include "native_strings.hpp"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <new>

namespace {

void throw_new(JNIEnv *env, const char *class_name, const char *message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class == nullptr) {
        return;
    }
    env->ThrowNew(exception_class, message);
    env->DeleteLocalRef(exception_class);
}

bool require_string(JNIEnv *env, jstring value, const char *argument_name) {
    if (value != nullptr) {
        return true;
    }
    throw_new(env, "java/lang/NullPointerException", argument_name);
    return false;
}

class critical_string_chars {
public:
    critical_string_chars(JNIEnv *env, jstring value)
            : env_(env), value_(value),
              chars_(static_cast<const jchar *>(
                      env->GetStringCritical(value, nullptr))) {
    }

    ~critical_string_chars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringCritical(value_, chars_);
        }
    }

    const jchar *get() const {
        return chars_;
    }

private:
    JNIEnv *env_;
    jstring value_;
    const jchar *chars_;
};

class string_chars {
public:
    string_chars(JNIEnv *env, jstring value)
            : env_(env), value_(value), chars_(env->GetStringChars(value, nullptr)) {
    }

    ~string_chars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringChars(value_, chars_);
        }
    }

    const jchar *get() const {
        return chars_;
    }

private:
    JNIEnv *env_;
    jstring value_;
    const jchar *chars_;
};

bool copy_string(JNIEnv *env, jstring value, jsize size, jchar *output) {
    if (size == 0) {
        return true;
    }
    string_chars chars(env, value);
    if (chars.get() == nullptr) {
        return false;
    }
    std::copy_n(chars.get(), size, output);
    return true;
}

jint JNICALL jni_length(JNIEnv *env, jclass, jstring value) {
    return native_jvm::strings::length(env, value);
}

jint JNICALL jni_hash_code(JNIEnv *env, jclass, jstring value) {
    return native_jvm::strings::hash_code(env, value);
}

jstring JNICALL jni_concat(
        JNIEnv *env, jclass, jstring left, jstring right) {
    return native_jvm::strings::concat(env, left, right);
}

}

namespace native_jvm::strings {

jsize length(JNIEnv *env, jstring value) {
    if (!require_string(env, value, "value")) {
        return 0;
    }
    return env->GetStringLength(value);
}

jint hash_code(JNIEnv *env, jstring value) {
    if (!require_string(env, value, "value")) {
        return 0;
    }

    const jsize size = env->GetStringLength(value);
    if (size == 0 || env->ExceptionCheck()) {
        return 0;
    }

    critical_string_chars chars(env, value);
    if (chars.get() == nullptr) {
        return 0;
    }

    std::uint32_t hash = 0;
    for (jsize index = 0; index < size; ++index) {
        hash = hash * 31U + static_cast<std::uint32_t>(chars.get()[index]);
    }
    return static_cast<jint>(hash);
}

jstring concat(JNIEnv *env, jstring left, jstring right) {
    if (!require_string(env, left, "left") ||
        !require_string(env, right, "right")) {
        return nullptr;
    }

    const jsize left_size = env->GetStringLength(left);
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const jsize right_size = env->GetStringLength(right);
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (left_size > std::numeric_limits<jsize>::max() - right_size) {
        throw_new(env, "java/lang/OutOfMemoryError",
                  "Concatenated string is too large");
        return nullptr;
    }

    const jsize result_size = left_size + right_size;
    if (result_size == 0) {
        const jchar empty = 0;
        return env->NewString(&empty, 0);
    }

    std::unique_ptr<jchar[]> result(
            new (std::nothrow) jchar[static_cast<std::size_t>(result_size)]);
    if (!result) {
        throw_new(env, "java/lang/OutOfMemoryError",
                  "Unable to allocate concatenated string");
        return nullptr;
    }

    if (!copy_string(env, left, left_size, result.get()) ||
        !copy_string(env, right, right_size, result.get() + left_size)) {
        return nullptr;
    }
    return env->NewString(result.get(), result_size);
}

}

namespace native_obfuscator::sdk {

bool register_native_strings(JNIEnv *env) {
    jclass strings_class = env->FindClass("by/radioegor146/sdk/NativeStrings");
    if (strings_class == nullptr || env->ExceptionCheck()) {
        return false;
    }

    JNINativeMethod methods[] = {
            {
                    const_cast<char *>("nativeLength"),
                    const_cast<char *>("(Ljava/lang/String;)I"),
                    reinterpret_cast<void *>(&jni_length)
            },
            {
                    const_cast<char *>("nativeHashCode"),
                    const_cast<char *>("(Ljava/lang/String;)I"),
                    reinterpret_cast<void *>(&jni_hash_code)
            },
            {
                    const_cast<char *>("nativeConcat"),
                    const_cast<char *>(
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
                    reinterpret_cast<void *>(&jni_concat)
            }
    };
    const jint result = env->RegisterNatives(
            strings_class,
            methods,
            static_cast<jint>(sizeof(methods) / sizeof(methods[0])));
    env->DeleteLocalRef(strings_class);
    return result == JNI_OK && !env->ExceptionCheck();
}

}
