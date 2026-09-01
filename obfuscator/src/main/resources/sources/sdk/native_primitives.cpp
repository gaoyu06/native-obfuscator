/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#include "native_primitives.hpp"
#include "aes_gcm.hpp"
#include "c_api.h"
#include "third_party/sha-2/sha-256.h"

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <new>

namespace {

constexpr uint32_t SDK_ABI_VERSION = 1;
constexpr uint64_t SHA256_SIZE = 32;
constexpr uint64_t SHA256_BLOCK_SIZE = 64;
constexpr uint64_t MAX_SHA256_INPUT_SIZE =
        std::numeric_limits<uint64_t>::max() / 8;
constexpr uint64_t AES_GCM_TAG_SIZE =
        native_obfuscator::sdk::aes_gcm::TAG_SIZE;
const uint8_t EMPTY_INPUT = 0;

bool valid_bytes(no_sdk_bytes_v1 bytes) {
    return bytes.size == 0 || bytes.data != nullptr;
}

bool representable_size(uint64_t size) {
    return size <= static_cast<uint64_t>(std::numeric_limits<std::size_t>::max());
}

void throw_new(JNIEnv *env, const char *class_name, const char *message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class == nullptr) {
        return;
    }
    env->ThrowNew(exception_class, message);
    env->DeleteLocalRef(exception_class);
}

struct byte_array_copy {
    std::unique_ptr<uint8_t[]> data;
    std::size_t size = 0;
    bool sensitive = false;

    ~byte_array_copy() {
        if (sensitive && data) {
            volatile uint8_t *byte = data.get();
            for (std::size_t remaining = size; remaining != 0; --remaining) {
                *byte++ = 0;
            }
        }
    }
};

bool copy_byte_array(JNIEnv *env, jbyteArray input, const char *argument_name,
                     byte_array_copy &output, bool sensitive = false) {
    if (input == nullptr) {
        throw_new(env, "java/lang/NullPointerException", argument_name);
        return false;
    }

    const jsize length = env->GetArrayLength(input);
    if (env->ExceptionCheck()) {
        return false;
    }

    output.size = static_cast<std::size_t>(length);
    output.sensitive = sensitive;
    if (length == 0) {
        return true;
    }

    output.data.reset(new (std::nothrow) uint8_t[output.size]);
    if (!output.data) {
        throw_new(env, "java/lang/OutOfMemoryError",
                  "Unable to copy native primitive input");
        return false;
    }

    env->GetByteArrayRegion(
            input,
            0,
            length,
            reinterpret_cast<jbyte *>(output.data.get()));
    return !env->ExceptionCheck();
}

no_sdk_bytes_v1 as_bytes(const byte_array_copy &input) {
    return {
            input.size == 0 ? nullptr : input.data.get(),
            static_cast<uint64_t>(input.size)
    };
}

bool allocate_bytes(
        JNIEnv *env,
        std::size_t size,
        byte_array_copy &output) {
    output.size = size;
    if (size == 0) {
        return true;
    }
    output.data.reset(new (std::nothrow) uint8_t[size]);
    if (!output.data) {
        throw_new(env, "java/lang/OutOfMemoryError",
                  "Unable to allocate native primitive output");
        return false;
    }
    return true;
}

jbyteArray to_byte_array(JNIEnv *env, const byte_array_copy &input) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(input.size));
    if (result == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    if (input.size != 0) {
        env->SetByteArrayRegion(
                result,
                0,
                static_cast<jsize>(input.size),
                reinterpret_cast<const jbyte *>(input.data.get()));
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(result);
            return nullptr;
        }
    }
    return result;
}

void throw_status(JNIEnv *env, no_sdk_status_v1 status) {
    if (status == NO_SDK_AUTHENTICATION_FAILED_V1) {
        throw_new(env, "javax/crypto/AEADBadTagException",
                  "AES-256-GCM authentication failed");
        return;
    }
    if (status == NO_SDK_NULL_V1 || status == NO_SDK_INVALID_ARGUMENT_V1) {
        throw_new(env, "java/lang/IllegalArgumentException",
                  "Invalid native primitive arguments");
        return;
    }
    if (status == NO_SDK_SIZE_OVERFLOW_V1) {
        throw_new(env, "java/lang/IllegalArgumentException",
                  "Native primitive input is too large");
        return;
    }
    throw_new(env, "java/lang/InternalError", "Native primitive operation failed");
}

jint JNICALL jni_abi_version(JNIEnv *, jclass) {
    return static_cast<jint>(no_sdk_abi_version_v1());
}

jbyteArray JNICALL jni_sha256(JNIEnv *env, jclass, jbyteArray input) {
    byte_array_copy copied_input;
    if (!copy_byte_array(env, input, "input", copied_input)) {
        return nullptr;
    }

    uint8_t digest[SHA256_SIZE];
    no_sdk_mut_bytes_v1 output = {digest, SHA256_SIZE};
    const no_sdk_status_v1 status =
            no_sdk_sha256_v1(as_bytes(copied_input), output);
    if (status != NO_SDK_OK_V1) {
        throw_status(env, status);
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(SHA256_SIZE));
    if (result == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(SHA256_SIZE),
            reinterpret_cast<const jbyte *>(digest));
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(result);
        return nullptr;
    }
    return result;
}

jbyteArray JNICALL jni_hmac_sha256(
        JNIEnv *env, jclass, jbyteArray key, jbyteArray message) {
    byte_array_copy copied_key;
    byte_array_copy copied_message;
    if (!copy_byte_array(env, key, "key", copied_key, true) ||
        !copy_byte_array(env, message, "message", copied_message)) {
        return nullptr;
    }

    uint8_t digest[SHA256_SIZE];
    no_sdk_mut_bytes_v1 output = {digest, SHA256_SIZE};
    const no_sdk_status_v1 status = no_sdk_hmac_sha256_v1(
            as_bytes(copied_key), as_bytes(copied_message), output);
    if (status != NO_SDK_OK_V1) {
        throw_status(env, status);
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(SHA256_SIZE));
    if (result == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(SHA256_SIZE),
            reinterpret_cast<const jbyte *>(digest));
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(result);
        return nullptr;
    }
    return result;
}

jbyteArray JNICALL jni_aes_256_gcm_encrypt(
        JNIEnv *env,
        jclass,
        jbyteArray key,
        jbyteArray nonce,
        jbyteArray plaintext,
        jbyteArray aad) {
    byte_array_copy copied_key;
    byte_array_copy copied_nonce;
    byte_array_copy copied_plaintext;
    byte_array_copy copied_aad;
    if (!copy_byte_array(env, key, "key", copied_key, true) ||
        !copy_byte_array(env, nonce, "nonce", copied_nonce) ||
        !copy_byte_array(env, plaintext, "plaintext", copied_plaintext) ||
        !copy_byte_array(env, aad, "aad", copied_aad)) {
        return nullptr;
    }
    if (copied_plaintext.size >
        static_cast<std::size_t>(std::numeric_limits<jsize>::max()) -
                AES_GCM_TAG_SIZE) {
        throw_new(env, "java/lang/IllegalArgumentException",
                  "AES-256-GCM plaintext is too large");
        return nullptr;
    }

    byte_array_copy output;
    if (!allocate_bytes(
            env,
            copied_plaintext.size + AES_GCM_TAG_SIZE,
            output)) {
        return nullptr;
    }
    const no_sdk_status_v1 status = no_sdk_aes_256_gcm_encrypt_v1(
            as_bytes(copied_key),
            as_bytes(copied_nonce),
            as_bytes(copied_plaintext),
            as_bytes(copied_aad),
            {output.data.get(), static_cast<uint64_t>(output.size)});
    if (status != NO_SDK_OK_V1) {
        throw_status(env, status);
        return nullptr;
    }
    return to_byte_array(env, output);
}

jbyteArray JNICALL jni_aes_256_gcm_decrypt(
        JNIEnv *env,
        jclass,
        jbyteArray key,
        jbyteArray nonce,
        jbyteArray ciphertext_and_tag,
        jbyteArray aad) {
    byte_array_copy copied_key;
    byte_array_copy copied_nonce;
    byte_array_copy copied_ciphertext;
    byte_array_copy copied_aad;
    if (!copy_byte_array(env, key, "key", copied_key, true) ||
        !copy_byte_array(env, nonce, "nonce", copied_nonce) ||
        !copy_byte_array(
                env,
                ciphertext_and_tag,
                "ciphertextAndTag",
                copied_ciphertext) ||
        !copy_byte_array(env, aad, "aad", copied_aad)) {
        return nullptr;
    }

    const std::size_t plaintext_size =
            copied_ciphertext.size < AES_GCM_TAG_SIZE
            ? 0
            : copied_ciphertext.size - AES_GCM_TAG_SIZE;
    byte_array_copy output;
    if (!allocate_bytes(env, plaintext_size, output)) {
        return nullptr;
    }
    const no_sdk_status_v1 status = no_sdk_aes_256_gcm_decrypt_v1(
            as_bytes(copied_key),
            as_bytes(copied_nonce),
            as_bytes(copied_ciphertext),
            as_bytes(copied_aad),
            {
                    output.size == 0 ? nullptr : output.data.get(),
                    static_cast<uint64_t>(output.size)
            });
    if (status != NO_SDK_OK_V1) {
        throw_status(env, status);
        return nullptr;
    }
    return to_byte_array(env, output);
}

jboolean JNICALL jni_constant_time_equals(
        JNIEnv *env, jclass, jbyteArray left, jbyteArray right) {
    if (left == nullptr) {
        throw_new(env, "java/lang/NullPointerException", "left");
        return JNI_FALSE;
    }
    if (right == nullptr) {
        throw_new(env, "java/lang/NullPointerException", "right");
        return JNI_FALSE;
    }

    const jsize left_length = env->GetArrayLength(left);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    const jsize right_length = env->GetArrayLength(right);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    if (left_length != right_length) {
        return JNI_FALSE;
    }

    byte_array_copy copied_left;
    byte_array_copy copied_right;
    if (!copy_byte_array(env, left, "left", copied_left) ||
        !copy_byte_array(env, right, "right", copied_right)) {
        return JNI_FALSE;
    }

    uint8_t equal = 0;
    const no_sdk_status_v1 status = no_sdk_equal_constant_time_v1(
            as_bytes(copied_left), as_bytes(copied_right), &equal);
    if (status != NO_SDK_OK_V1) {
        throw_status(env, status);
        return JNI_FALSE;
    }
    return equal == 0 ? JNI_FALSE : JNI_TRUE;
}

}

extern "C" uint32_t no_sdk_abi_version_v1(void) {
    return SDK_ABI_VERSION;
}

extern "C" no_sdk_status_v1 no_sdk_sha256_v1(
        no_sdk_bytes_v1 input,
        no_sdk_mut_bytes_v1 output_32) {
    if (!valid_bytes(input) || output_32.data == nullptr) {
        return NO_SDK_NULL_V1;
    }
    if (output_32.capacity < SHA256_SIZE) {
        return NO_SDK_BUFFER_TOO_SMALL_V1;
    }
    if (!representable_size(input.size) ||
        input.size > std::numeric_limits<uint64_t>::max() / 8) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }

    const uint8_t *data = input.size == 0 ? &EMPTY_INPUT : input.data;
    calc_sha_256(
            output_32.data,
            data,
            static_cast<std::size_t>(input.size));
    return NO_SDK_OK_V1;
}

extern "C" no_sdk_status_v1 no_sdk_hmac_sha256_v1(
        no_sdk_bytes_v1 key,
        no_sdk_bytes_v1 message,
        no_sdk_mut_bytes_v1 output_32) {
    if (!valid_bytes(key) || !valid_bytes(message) || output_32.data == nullptr) {
        return NO_SDK_NULL_V1;
    }
    if (output_32.capacity < SHA256_SIZE) {
        return NO_SDK_BUFFER_TOO_SMALL_V1;
    }
    if (!representable_size(key.size) || !representable_size(message.size) ||
        key.size > MAX_SHA256_INPUT_SIZE ||
        message.size > MAX_SHA256_INPUT_SIZE - SHA256_BLOCK_SIZE) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }

    uint8_t key_block[SHA256_BLOCK_SIZE] = {};
    if (key.size > SHA256_BLOCK_SIZE) {
        calc_sha_256(
                key_block,
                key.data,
                static_cast<std::size_t>(key.size));
    } else if (key.size != 0) {
        std::memcpy(
                key_block,
                key.data,
                static_cast<std::size_t>(key.size));
    }

    uint8_t inner_pad[SHA256_BLOCK_SIZE];
    uint8_t outer_pad[SHA256_BLOCK_SIZE];
    for (std::size_t i = 0; i < SHA256_BLOCK_SIZE; ++i) {
        inner_pad[i] = static_cast<uint8_t>(key_block[i] ^ 0x36);
        outer_pad[i] = static_cast<uint8_t>(key_block[i] ^ 0x5c);
    }

    uint8_t inner_digest[SHA256_SIZE];
    struct Sha_256 inner;
    sha_256_init(&inner, inner_digest);
    sha_256_write(&inner, inner_pad, sizeof(inner_pad));
    sha_256_write(
            &inner,
            message.size == 0 ? &EMPTY_INPUT : message.data,
            static_cast<std::size_t>(message.size));
    sha_256_close(&inner);

    struct Sha_256 outer;
    sha_256_init(&outer, output_32.data);
    sha_256_write(&outer, outer_pad, sizeof(outer_pad));
    sha_256_write(&outer, inner_digest, sizeof(inner_digest));
    sha_256_close(&outer);
    return NO_SDK_OK_V1;
}

extern "C" no_sdk_status_v1 no_sdk_aes_256_gcm_encrypt_v1(
        no_sdk_bytes_v1 key_32,
        no_sdk_bytes_v1 nonce_12,
        no_sdk_bytes_v1 plaintext,
        no_sdk_bytes_v1 aad,
        no_sdk_mut_bytes_v1 ciphertext_and_tag) {
    if (!valid_bytes(key_32) || !valid_bytes(nonce_12) ||
        !valid_bytes(plaintext) || !valid_bytes(aad)) {
        return NO_SDK_NULL_V1;
    }
    if (key_32.size != native_obfuscator::sdk::aes_gcm::KEY_SIZE ||
        nonce_12.size != native_obfuscator::sdk::aes_gcm::NONCE_SIZE) {
        return NO_SDK_INVALID_ARGUMENT_V1;
    }
    if (!representable_size(plaintext.size) ||
        !representable_size(aad.size) ||
        plaintext.size > native_obfuscator::sdk::aes_gcm::MAX_TEXT_SIZE ||
        aad.size > native_obfuscator::sdk::aes_gcm::MAX_AAD_SIZE) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }
    const uint64_t required_size = plaintext.size + AES_GCM_TAG_SIZE;
    if (!representable_size(required_size)) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }
    if (ciphertext_and_tag.capacity < required_size) {
        return NO_SDK_BUFFER_TOO_SMALL_V1;
    }
    if (ciphertext_and_tag.data == nullptr) {
        return NO_SDK_NULL_V1;
    }

    native_obfuscator::sdk::aes_gcm::encrypt(
            key_32.data,
            nonce_12.data,
            plaintext.size == 0 ? nullptr : plaintext.data,
            static_cast<std::size_t>(plaintext.size),
            aad.size == 0 ? nullptr : aad.data,
            static_cast<std::size_t>(aad.size),
            ciphertext_and_tag.data);
    return NO_SDK_OK_V1;
}

extern "C" no_sdk_status_v1 no_sdk_aes_256_gcm_decrypt_v1(
        no_sdk_bytes_v1 key_32,
        no_sdk_bytes_v1 nonce_12,
        no_sdk_bytes_v1 ciphertext_and_tag,
        no_sdk_bytes_v1 aad,
        no_sdk_mut_bytes_v1 plaintext) {
    if (!valid_bytes(key_32) || !valid_bytes(nonce_12) ||
        !valid_bytes(ciphertext_and_tag) || !valid_bytes(aad)) {
        return NO_SDK_NULL_V1;
    }
    if (key_32.size != native_obfuscator::sdk::aes_gcm::KEY_SIZE ||
        nonce_12.size != native_obfuscator::sdk::aes_gcm::NONCE_SIZE ||
        ciphertext_and_tag.size < AES_GCM_TAG_SIZE) {
        return NO_SDK_INVALID_ARGUMENT_V1;
    }

    const uint64_t plaintext_size =
            ciphertext_and_tag.size - AES_GCM_TAG_SIZE;
    if (!representable_size(ciphertext_and_tag.size) ||
        !representable_size(aad.size) ||
        plaintext_size > native_obfuscator::sdk::aes_gcm::MAX_TEXT_SIZE ||
        aad.size > native_obfuscator::sdk::aes_gcm::MAX_AAD_SIZE) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }
    if (plaintext.capacity < plaintext_size) {
        return NO_SDK_BUFFER_TOO_SMALL_V1;
    }
    if (plaintext_size != 0 && plaintext.data == nullptr) {
        return NO_SDK_NULL_V1;
    }

    const bool authenticated = native_obfuscator::sdk::aes_gcm::decrypt(
            key_32.data,
            nonce_12.data,
            ciphertext_and_tag.data,
            static_cast<std::size_t>(plaintext_size),
            aad.size == 0 ? nullptr : aad.data,
            static_cast<std::size_t>(aad.size),
            plaintext_size == 0 ? nullptr : plaintext.data);
    return authenticated
           ? NO_SDK_OK_V1
           : NO_SDK_AUTHENTICATION_FAILED_V1;
}

extern "C" no_sdk_status_v1 no_sdk_equal_constant_time_v1(
        no_sdk_bytes_v1 left,
        no_sdk_bytes_v1 right,
        uint8_t *equal) {
    if (!valid_bytes(left) || !valid_bytes(right) || equal == nullptr) {
        return NO_SDK_NULL_V1;
    }
    if (!representable_size(left.size) || !representable_size(right.size)) {
        return NO_SDK_SIZE_OVERFLOW_V1;
    }

    *equal = 0;
    if (left.size != right.size) {
        return NO_SDK_OK_V1;
    }

    volatile uint8_t different = 0;
    for (std::size_t i = 0; i < static_cast<std::size_t>(left.size); ++i) {
        different = static_cast<uint8_t>(different | (left.data[i] ^ right.data[i]));
    }
    *equal = different == 0 ? 1 : 0;
    return NO_SDK_OK_V1;
}

namespace native_obfuscator::sdk {

jint abi_version() {
    return jni_abi_version(nullptr, nullptr);
}

jbyteArray sha256(JNIEnv *env, jbyteArray input) {
    return jni_sha256(env, nullptr, input);
}

jbyteArray hmac_sha256(JNIEnv *env, jbyteArray key, jbyteArray message) {
    return jni_hmac_sha256(env, nullptr, key, message);
}

jbyteArray aes256_gcm_encrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray plaintext, jbyteArray aad) {
    return jni_aes_256_gcm_encrypt(env, nullptr, key, nonce, plaintext, aad);
}

jbyteArray aes256_gcm_encrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray plaintext) {
    jbyteArray empty = env->NewByteArray(0);
    if (empty == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jbyteArray result = aes256_gcm_encrypt(env, key, nonce, plaintext, empty);
    env->DeleteLocalRef(empty);
    return result;
}

jbyteArray aes256_gcm_decrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray ciphertext_and_tag, jbyteArray aad) {
    return jni_aes_256_gcm_decrypt(
            env, nullptr, key, nonce, ciphertext_and_tag, aad);
}

jbyteArray aes256_gcm_decrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray ciphertext_and_tag) {
    jbyteArray empty = env->NewByteArray(0);
    if (empty == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jbyteArray result = aes256_gcm_decrypt(
            env, key, nonce, ciphertext_and_tag, empty);
    env->DeleteLocalRef(empty);
    return result;
}

jboolean constant_time_equals(JNIEnv *env, jbyteArray left, jbyteArray right) {
    return jni_constant_time_equals(env, nullptr, left, right);
}

bool register_natives(JNIEnv *env) {
    jclass primitives_class = env->FindClass(
            "by/radioegor146/nativeobfuscator/NativePrimitives");
    if (primitives_class == nullptr || env->ExceptionCheck()) {
        return false;
    }

    JNINativeMethod methods[] = {
            {
                    const_cast<char *>("nativeAbiVersion"),
                    const_cast<char *>("()I"),
                    reinterpret_cast<void *>(&jni_abi_version)
            },
            {
                    const_cast<char *>("nativeSha256"),
                    const_cast<char *>("([B)[B"),
                    reinterpret_cast<void *>(&jni_sha256)
            },
            {
                    const_cast<char *>("nativeHmacSha256"),
                    const_cast<char *>("([B[B)[B"),
                    reinterpret_cast<void *>(&jni_hmac_sha256)
            },
            {
                    const_cast<char *>("nativeAes256GcmEncrypt"),
                    const_cast<char *>("([B[B[B[B)[B"),
                    reinterpret_cast<void *>(&jni_aes_256_gcm_encrypt)
            },
            {
                    const_cast<char *>("nativeAes256GcmDecrypt"),
                    const_cast<char *>("([B[B[B[B)[B"),
                    reinterpret_cast<void *>(&jni_aes_256_gcm_decrypt)
            },
            {
                    const_cast<char *>("nativeConstantTimeEquals"),
                    const_cast<char *>("([B[B)Z"),
                    reinterpret_cast<void *>(&jni_constant_time_equals)
            }
    };
    const jint result = env->RegisterNatives(
            primitives_class,
            methods,
            static_cast<jint>(sizeof(methods) / sizeof(methods[0])));
    env->DeleteLocalRef(primitives_class);
    return result == JNI_OK && !env->ExceptionCheck();
}

}
