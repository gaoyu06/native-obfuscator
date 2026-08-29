/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * AES-GCM composition follows NIST SP 800-38D. The AES-256 block primitive is
 * the vendored tiny-AES-c subset in third_party/tiny-aes-c.
 */
#include "aes_gcm.hpp"
#include "third_party/tiny-aes-c/aes.h"

#include <cstring>

namespace native_obfuscator::sdk::aes_gcm {
namespace {

constexpr std::size_t BLOCK_SIZE = 16;

void secure_zero(void *data, std::size_t size) {
    volatile uint8_t *bytes = static_cast<volatile uint8_t *>(data);
    while (size-- != 0) {
        *bytes++ = 0;
    }
}

void encrypt_block(
        const tiny_aes_256_context &context,
        const uint8_t input[BLOCK_SIZE],
        uint8_t output[BLOCK_SIZE]) {
    std::memcpy(output, input, BLOCK_SIZE);
    tiny_aes_256_encrypt_block(&context, output);
}

void multiply_gf128(uint8_t value[BLOCK_SIZE], const uint8_t hash_key[BLOCK_SIZE]) {
    uint8_t product[BLOCK_SIZE] = {};
    uint8_t shifted[BLOCK_SIZE];
    std::memcpy(shifted, hash_key, BLOCK_SIZE);

    for (std::size_t bit = 0; bit < 128; ++bit) {
        const uint8_t selected = static_cast<uint8_t>(
                0U - ((value[bit / 8] >> (7 - bit % 8)) & 1U));
        for (std::size_t byte = 0; byte < BLOCK_SIZE; ++byte) {
            product[byte] ^= static_cast<uint8_t>(shifted[byte] & selected);
        }

        const uint8_t reduced = static_cast<uint8_t>(0U - (shifted[15] & 1U));
        for (std::size_t byte = BLOCK_SIZE - 1; byte != 0; --byte) {
            shifted[byte] = static_cast<uint8_t>(
                    (shifted[byte] >> 1) | (shifted[byte - 1] << 7));
        }
        shifted[0] = static_cast<uint8_t>(
                (shifted[0] >> 1) ^ (0xe1U & reduced));
    }

    std::memcpy(value, product, BLOCK_SIZE);
    secure_zero(product, sizeof(product));
    secure_zero(shifted, sizeof(shifted));
}

void ghash_block(
        uint8_t hash[BLOCK_SIZE],
        const uint8_t hash_key[BLOCK_SIZE],
        const uint8_t block[BLOCK_SIZE]) {
    for (std::size_t byte = 0; byte < BLOCK_SIZE; ++byte) {
        hash[byte] ^= block[byte];
    }
    multiply_gf128(hash, hash_key);
}

void ghash_data(
        uint8_t hash[BLOCK_SIZE],
        const uint8_t hash_key[BLOCK_SIZE],
        const uint8_t *data,
        std::size_t size) {
    while (size >= BLOCK_SIZE) {
        ghash_block(hash, hash_key, data);
        data += BLOCK_SIZE;
        size -= BLOCK_SIZE;
    }
    if (size != 0) {
        uint8_t final_block[BLOCK_SIZE] = {};
        std::memcpy(final_block, data, size);
        ghash_block(hash, hash_key, final_block);
        secure_zero(final_block, sizeof(final_block));
    }
}

void store_big_endian_64(uint64_t value, uint8_t output[8]) {
    for (std::size_t byte = 0; byte < 8; ++byte) {
        output[7 - byte] = static_cast<uint8_t>(value >> (byte * 8));
    }
}

void compute_tag(
        const tiny_aes_256_context &context,
        const uint8_t hash_key[BLOCK_SIZE],
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *aad,
        std::size_t aad_size,
        const uint8_t *ciphertext,
        std::size_t ciphertext_size,
        uint8_t tag[TAG_SIZE]) {
    uint8_t hash[BLOCK_SIZE] = {};
    ghash_data(hash, hash_key, aad, aad_size);
    ghash_data(hash, hash_key, ciphertext, ciphertext_size);

    uint8_t lengths[BLOCK_SIZE] = {};
    store_big_endian_64(static_cast<uint64_t>(aad_size) * 8, lengths);
    store_big_endian_64(
            static_cast<uint64_t>(ciphertext_size) * 8,
            lengths + 8);
    ghash_block(hash, hash_key, lengths);

    uint8_t initial_counter[BLOCK_SIZE] = {};
    std::memcpy(initial_counter, nonce, NONCE_SIZE);
    initial_counter[15] = 1;
    encrypt_block(context, initial_counter, tag);
    for (std::size_t byte = 0; byte < TAG_SIZE; ++byte) {
        tag[byte] ^= hash[byte];
    }

    secure_zero(hash, sizeof(hash));
    secure_zero(lengths, sizeof(lengths));
    secure_zero(initial_counter, sizeof(initial_counter));
}

void increment_counter(uint8_t counter[BLOCK_SIZE]) {
    for (std::size_t byte = BLOCK_SIZE; byte > NONCE_SIZE; --byte) {
        if (++counter[byte - 1] != 0) {
            break;
        }
    }
}

void crypt_counter(
        const tiny_aes_256_context &context,
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *input,
        std::size_t size,
        uint8_t *output) {
    uint8_t counter[BLOCK_SIZE] = {};
    std::memcpy(counter, nonce, NONCE_SIZE);
    counter[15] = 1;

    while (size != 0) {
        increment_counter(counter);
        uint8_t stream[BLOCK_SIZE];
        encrypt_block(context, counter, stream);
        const std::size_t block_size = size < BLOCK_SIZE ? size : BLOCK_SIZE;
        for (std::size_t byte = 0; byte < block_size; ++byte) {
            output[byte] = static_cast<uint8_t>(input[byte] ^ stream[byte]);
        }
        secure_zero(stream, sizeof(stream));
        input += block_size;
        output += block_size;
        size -= block_size;
    }

    secure_zero(counter, sizeof(counter));
}

bool tags_equal(
        const uint8_t left[TAG_SIZE],
        const uint8_t right[TAG_SIZE]) {
    volatile uint8_t difference = 0;
    for (std::size_t byte = 0; byte < TAG_SIZE; ++byte) {
        difference = static_cast<uint8_t>(difference | (left[byte] ^ right[byte]));
    }
    return difference == 0;
}

}

void encrypt(
        const uint8_t key[KEY_SIZE],
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *plaintext,
        std::size_t plaintext_size,
        const uint8_t *aad,
        std::size_t aad_size,
        uint8_t *ciphertext_and_tag) {
    tiny_aes_256_context context;
    tiny_aes_256_init(&context, key);

    uint8_t hash_key[BLOCK_SIZE] = {};
    tiny_aes_256_encrypt_block(&context, hash_key);
    crypt_counter(
            context,
            nonce,
            plaintext,
            plaintext_size,
            ciphertext_and_tag);
    compute_tag(
            context,
            hash_key,
            nonce,
            aad,
            aad_size,
            ciphertext_and_tag,
            plaintext_size,
            ciphertext_and_tag + plaintext_size);

    secure_zero(hash_key, sizeof(hash_key));
    secure_zero(&context, sizeof(context));
}

bool decrypt(
        const uint8_t key[KEY_SIZE],
        const uint8_t nonce[NONCE_SIZE],
        const uint8_t *ciphertext_and_tag,
        std::size_t ciphertext_size,
        const uint8_t *aad,
        std::size_t aad_size,
        uint8_t *plaintext) {
    tiny_aes_256_context context;
    tiny_aes_256_init(&context, key);

    uint8_t hash_key[BLOCK_SIZE] = {};
    tiny_aes_256_encrypt_block(&context, hash_key);
    uint8_t expected_tag[TAG_SIZE];
    compute_tag(
            context,
            hash_key,
            nonce,
            aad,
            aad_size,
            ciphertext_and_tag,
            ciphertext_size,
            expected_tag);

    const bool authenticated =
            tags_equal(expected_tag, ciphertext_and_tag + ciphertext_size);
    if (authenticated) {
        crypt_counter(
                context,
                nonce,
                ciphertext_and_tag,
                ciphertext_size,
                plaintext);
    }

    secure_zero(expected_tag, sizeof(expected_tag));
    secure_zero(hash_key, sizeof(hash_key));
    secure_zero(&context, sizeof(context));
    return authenticated;
}

}
