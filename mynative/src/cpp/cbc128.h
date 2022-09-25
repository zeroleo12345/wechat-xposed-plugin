//
// Created by zlx on 2017/12/26.
//

#ifndef XPOSED_6510_CBC128_H
#define XPOSED_6510_CBC128_H

#include <stdint.h>

typedef void (*block128_f)(const unsigned char in[16], unsigned char out[16], const void *key);

void CRYPTO_cbc128_encrypt(const unsigned char *in, unsigned char *out, size_t len, const void *key, unsigned char ivec[16], block128_f block);

void CRYPTO_cbc128_decrypt(const unsigned char *in, unsigned char *out, size_t len, const void *key, unsigned char ivec[16], block128_f block);

#endif //XPOSED_6510_CBC128_H
