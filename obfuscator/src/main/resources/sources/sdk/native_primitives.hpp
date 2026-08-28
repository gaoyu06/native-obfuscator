/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef NATIVE_OBFUSCATOR_SDK_NATIVE_PRIMITIVES_HPP
#define NATIVE_OBFUSCATOR_SDK_NATIVE_PRIMITIVES_HPP

#include "jni.h"

namespace native_obfuscator::sdk {

bool register_natives(JNIEnv *env);

}

#endif
