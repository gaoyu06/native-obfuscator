/*
 * Derived from kokke/tiny-AES-c at revision
 * 23856752fbd139da0b8ca6e471a13d5bcc99a08d.
 *
 * This is free and unencumbered software released into the public domain.
 * See UNLICENSE.txt in this directory.
 *
 * Local changes: retain only the AES-256 key schedule and block-encryption
 * surface needed by GCM, and use SDK-specific symbol names.
 */
#ifndef NATIVE_OBFUSCATOR_THIRD_PARTY_TINY_AES_H
#define NATIVE_OBFUSCATOR_THIRD_PARTY_TINY_AES_H

#include <stdint.h>

#define TINY_AES_BLOCK_LENGTH 16
#define TINY_AES_256_KEY_LENGTH 32
#define TINY_AES_256_KEY_EXPANSION_SIZE 240

typedef struct tiny_aes_256_context {
    uint8_t round_key[TINY_AES_256_KEY_EXPANSION_SIZE];
} tiny_aes_256_context;

void tiny_aes_256_init(
        tiny_aes_256_context *context,
        const uint8_t key[TINY_AES_256_KEY_LENGTH]);

void tiny_aes_256_encrypt_block(
        const tiny_aes_256_context *context,
        uint8_t block[TINY_AES_BLOCK_LENGTH]);

#endif
