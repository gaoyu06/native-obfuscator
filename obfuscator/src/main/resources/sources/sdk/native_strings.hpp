/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef NATIVE_OBFUSCATOR_SDK_NATIVE_STRINGS_HPP
#define NATIVE_OBFUSCATOR_SDK_NATIVE_STRINGS_HPP

#include "jni.h"

namespace native_jvm::strings {

jsize length(JNIEnv *env, jstring value);

jint hash_code(JNIEnv *env, jstring value);

jstring concat(JNIEnv *env, jstring left, jstring right);

}

namespace native_obfuscator::sdk {

bool register_native_strings(JNIEnv *env);

}

#endif
