

#include "aes.h"
#include "cbc128.h"

void AES_cbc_encrypt(const unsigned char *in, unsigned char *out, size_t len, const AES_KEY *key, unsigned char *ivec)
{
    CRYPTO_cbc128_encrypt(in, out, len, key, ivec, (block128_f) AES_encrypt);
}

void AES_cbc_decrypt(const unsigned char *in, unsigned char *out,
                     size_t len, const AES_KEY *key,
                     unsigned char *ivec)
{
    CRYPTO_cbc128_decrypt(in, out, len, key, ivec, (block128_f) AES_decrypt);
}