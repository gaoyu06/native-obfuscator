/*
 * Derived from kokke/tiny-AES-c at revision
 * 23856752fbd139da0b8ca6e471a13d5bcc99a08d.
 *
 * This is free and unencumbered software released into the public domain.
 * See UNLICENSE.txt in this directory.
 *
 * Local changes: retain only the AES-256 key schedule and block-encryption
 * surface needed by GCM, rename symbols, and compile the source as C++.
 */
#include "aes.h"

#include <cstring>

namespace {

constexpr unsigned AES_COLUMNS = 4;
constexpr unsigned AES_256_KEY_WORDS = 8;
constexpr unsigned AES_256_ROUNDS = 14;

using aes_state = uint8_t[4][4];

const uint8_t SBOX[256] = {
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5,
        0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0,
        0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc,
        0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a,
        0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0,
        0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b,
        0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85,
        0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5,
        0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17,
        0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88,
        0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c,
        0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9,
        0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6,
        0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e,
        0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94,
        0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68,
        0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
};

const uint8_t RCON[8] = {
        0x8d, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40
};

void expand_key(uint8_t *round_key, const uint8_t *key) {
    std::memcpy(round_key, key, TINY_AES_256_KEY_LENGTH);

    uint8_t temporary[4];
    for (unsigned word = AES_256_KEY_WORDS;
         word < AES_COLUMNS * (AES_256_ROUNDS + 1);
         ++word) {
        const unsigned previous = (word - 1) * 4;
        std::memcpy(temporary, round_key + previous, sizeof(temporary));

        if (word % AES_256_KEY_WORDS == 0) {
            const uint8_t first = temporary[0];
            temporary[0] = SBOX[temporary[1]];
            temporary[1] = SBOX[temporary[2]];
            temporary[2] = SBOX[temporary[3]];
            temporary[3] = SBOX[first];
            temporary[0] ^= RCON[word / AES_256_KEY_WORDS];
        } else if (word % AES_256_KEY_WORDS == 4) {
            for (uint8_t &value : temporary) {
                value = SBOX[value];
            }
        }

        const unsigned destination = word * 4;
        const unsigned source = (word - AES_256_KEY_WORDS) * 4;
        for (unsigned byte = 0; byte < 4; ++byte) {
            round_key[destination + byte] =
                    static_cast<uint8_t>(round_key[source + byte] ^ temporary[byte]);
        }
    }
}

void add_round_key(uint8_t round, aes_state *state, const uint8_t *round_key) {
    for (uint8_t column = 0; column < 4; ++column) {
        for (uint8_t row = 0; row < 4; ++row) {
            (*state)[column][row] ^=
                    round_key[round * AES_COLUMNS * 4 + column * AES_COLUMNS + row];
        }
    }
}

void substitute_bytes(aes_state *state) {
    for (uint8_t row = 0; row < 4; ++row) {
        for (uint8_t column = 0; column < 4; ++column) {
            (*state)[column][row] = SBOX[(*state)[column][row]];
        }
    }
}

void shift_rows(aes_state *state) {
    uint8_t temporary = (*state)[0][1];
    (*state)[0][1] = (*state)[1][1];
    (*state)[1][1] = (*state)[2][1];
    (*state)[2][1] = (*state)[3][1];
    (*state)[3][1] = temporary;

    temporary = (*state)[0][2];
    (*state)[0][2] = (*state)[2][2];
    (*state)[2][2] = temporary;
    temporary = (*state)[1][2];
    (*state)[1][2] = (*state)[3][2];
    (*state)[3][2] = temporary;

    temporary = (*state)[0][3];
    (*state)[0][3] = (*state)[3][3];
    (*state)[3][3] = (*state)[2][3];
    (*state)[2][3] = (*state)[1][3];
    (*state)[1][3] = temporary;
}

uint8_t multiply_by_x(uint8_t value) {
    return static_cast<uint8_t>(
            (value << 1) ^ (((value >> 7) & 1U) * 0x1bU));
}

void mix_columns(aes_state *state) {
    for (uint8_t column = 0; column < 4; ++column) {
        const uint8_t first = (*state)[column][0];
        const uint8_t all = static_cast<uint8_t>(
                (*state)[column][0] ^ (*state)[column][1] ^
                (*state)[column][2] ^ (*state)[column][3]);
        uint8_t pair = static_cast<uint8_t>(
                (*state)[column][0] ^ (*state)[column][1]);
        (*state)[column][0] ^= static_cast<uint8_t>(multiply_by_x(pair) ^ all);
        pair = static_cast<uint8_t>((*state)[column][1] ^ (*state)[column][2]);
        (*state)[column][1] ^= static_cast<uint8_t>(multiply_by_x(pair) ^ all);
        pair = static_cast<uint8_t>((*state)[column][2] ^ (*state)[column][3]);
        (*state)[column][2] ^= static_cast<uint8_t>(multiply_by_x(pair) ^ all);
        pair = static_cast<uint8_t>((*state)[column][3] ^ first);
        (*state)[column][3] ^= static_cast<uint8_t>(multiply_by_x(pair) ^ all);
    }
}

void encrypt(aes_state *state, const uint8_t *round_key) {
    add_round_key(0, state, round_key);
    for (uint8_t round = 1; round <= AES_256_ROUNDS; ++round) {
        substitute_bytes(state);
        shift_rows(state);
        if (round != AES_256_ROUNDS) {
            mix_columns(state);
        }
        add_round_key(round, state, round_key);
    }
}

}

void tiny_aes_256_init(
        tiny_aes_256_context *context,
        const uint8_t key[TINY_AES_256_KEY_LENGTH]) {
    expand_key(context->round_key, key);
}

void tiny_aes_256_encrypt_block(
        const tiny_aes_256_context *context,
        uint8_t block[TINY_AES_BLOCK_LENGTH]) {
    encrypt(reinterpret_cast<aes_state *>(block), context->round_key);
}
