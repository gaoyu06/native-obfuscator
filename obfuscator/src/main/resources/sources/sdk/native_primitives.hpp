/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef NATIVE_OBFUSCATOR_SDK_NATIVE_PRIMITIVES_HPP
#define NATIVE_OBFUSCATOR_SDK_NATIVE_PRIMITIVES_HPP

#include "jni.h"

namespace native_obfuscator::sdk {

jint abi_version();

jbyteArray sha256(JNIEnv *env, jbyteArray input);

jbyteArray hmac_sha256(JNIEnv *env, jbyteArray key, jbyteArray message);

jbyteArray aes256_gcm_encrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray plaintext);

jbyteArray aes256_gcm_encrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray plaintext, jbyteArray aad);

jbyteArray aes256_gcm_decrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray ciphertext_and_tag);

jbyteArray aes256_gcm_decrypt(JNIEnv *env, jbyteArray key, jbyteArray nonce,
                              jbyteArray ciphertext_and_tag, jbyteArray aad);

jboolean constant_time_equals(JNIEnv *env, jbyteArray left, jbyteArray right);

bool register_natives(JNIEnv *env);

}

#endif
