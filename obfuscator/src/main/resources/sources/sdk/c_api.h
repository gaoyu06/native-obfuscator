/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef NATIVE_OBFUSCATOR_SDK_C_API_H
#define NATIVE_OBFUSCATOR_SDK_C_API_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum no_sdk_status_v1 {
    NO_SDK_OK_V1 = 0,
    NO_SDK_NULL_V1 = 1,
    NO_SDK_INVALID_ARGUMENT_V1 = 2,
    NO_SDK_BUFFER_TOO_SMALL_V1 = 3,
    NO_SDK_SIZE_OVERFLOW_V1 = 4,
    NO_SDK_INTERNAL_V1 = 5
} no_sdk_status_v1;

typedef struct no_sdk_bytes_v1 {
    const uint8_t *data;
    uint64_t size;
} no_sdk_bytes_v1;

typedef struct no_sdk_mut_bytes_v1 {
    uint8_t *data;
    uint64_t capacity;
} no_sdk_mut_bytes_v1;

uint32_t no_sdk_abi_version_v1(void);

no_sdk_status_v1 no_sdk_sha256_v1(
        no_sdk_bytes_v1 input,
        no_sdk_mut_bytes_v1 output_32);

no_sdk_status_v1 no_sdk_equal_constant_time_v1(
        no_sdk_bytes_v1 left,
        no_sdk_bytes_v1 right,
        uint8_t *equal);

#ifdef __cplusplus
}
#endif

#endif
