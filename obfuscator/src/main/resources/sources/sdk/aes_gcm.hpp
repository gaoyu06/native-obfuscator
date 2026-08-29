/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
#ifndef NATIVE_OBFUSCATOR_SDK_AES_GCM_HPP
#define NATIVE_OBFUSCATOR_SDK_AES_GCM_HPP

#include <cstddef>
#include <cstdint>

namespace native_obfuscator::sdk::aes_gcm {

constexpr std::size_t KEY_SIZE = 32;
constexpr std::size_t NONCE_SIZE = 12;
constexpr std::size_t TAG_SIZE = 16;
constexpr uint64_t MAX_TEXT_SIZE = (UINT64_C(1) << 36) - 32;
constexpr uint64_t MAX_AAD_SIZE = UINT64_MAX / 8;

void encrypt(
        const uint8_t key[KEY_SIZE],
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *plaintext,
        std::size_t plaintext_size,
        const uint8_t *aad,
        std::size_t aad_size,
        uint8_t *ciphertext_and_tag);

bool decrypt(
        const uint8_t key[KEY_SIZE],
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *ciphertext_and_tag,
        std::size_t ciphertext_size,
        const uint8_t *aad,
        std::size_t aad_size,
        uint8_t *plaintext);

}

#endif
