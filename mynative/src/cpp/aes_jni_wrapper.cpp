/*
// Created by zlx on 2017/12/26.
note: 测试例子 /home/zlx/android-study/test/cpp/test3/hello.cpp
*/

#include "native-lib.h"
#include "aes.h"
#include "Base64.h"
#include <android/log.h>
#include "aes_jni_wrapper.h"

#define KEY_BYTE 16

#ifdef __cplusplus
extern "C"
#endif
void _autofill(char array_key[KEY_BYTE], char* str_key){
    // note: 当key或iv不足16个字符的时候, 后面补字符'0'; 当超过16个字符的时候, 截断为前面16个字符
    int pos = 0;
    for(; pos < strlen(str_key); pos++) {   // 赋值
        if( pos >= KEY_BYTE ) break;    // 超过16个字符时, 截断
        array_key[pos] = str_key[pos];
    }

    for(; pos < KEY_BYTE; pos++) {  // 不足16位, 自动补字符'0'
        array_key[pos] = '0';
    }
    array_key[KEY_BYTE] = '\0';
}

#ifdef __cplusplus
extern "C"
#endif
char* encrypt_string_and_b64(char* data, char* key, char* iv) {
    // note: 调用注意释放内存, 调用 delete[] encrypt_data;
    if (false) {
        data = "INIpLr7NJIhhsdBuMsIeQwtcyErTBHTCg6kG6hQcwLfG5doMnyIhheQbLkq/h5b8";
        key = "hookGetMessage()";
        iv = "afterHookFuction";
    }

    // note: jstring转换成char*
    char encrypt_string[8192] = {0};
    AES_KEY aes;
    // 调整key
    char array_key[KEY_BYTE+1] = {0};
    _autofill(array_key, key);
    // 调整iv
    char array_iv[KEY_BYTE+1] = {0};
    _autofill(array_iv, (char*)iv);
    int data_len = strlen(data);

    // 打印
    LOG_D( "\n" );
    LOG_D( "data:%s, len:%d\n", data, data_len );
    LOG_D( "key:%s, len:%d\n", array_key, strlen(array_key) );
    LOG_D( "iv:%s, len:%d\n", array_iv, strlen(array_iv) );
    LOG_D( "usebase64:\n" );

    // 加密
    int nTotal = ( data_len / AES_BLOCK_SIZE + 1 ) * AES_BLOCK_SIZE;
    char *enc_s = new char[nTotal + 1];
    memset(enc_s, 0, nTotal + 1);
    int nNumber;
    if (data_len % 16 > 0){
        nNumber = nTotal - data_len;
    } else {
        nNumber = 16;
    }
    memset(enc_s, nNumber, nTotal);
    memcpy(enc_s, data, data_len);
    if (AES_set_encrypt_key((unsigned char *) array_key, 128, &aes) < 0) {
        LOG_E("AES_set_encrypt_key error");
        return NULL;
    }
    AES_cbc_encrypt((unsigned char *) enc_s, (unsigned char *) encrypt_string, nTotal, &aes, (unsigned char *) array_iv);
    LOG_D("hex_encrypt data:");
    phex(encrypt_string, nTotal);
    //LOG_D("len:%d\n", nTotal);
    char *b64_encrypt_data = b64encode((const unsigned char *) encrypt_string, nTotal); // TODO: malloc返回的, 需要释放!
    LOG_D( "b64_encrypt data:%s, len:%d\n", b64_encrypt_data, strlen(b64_encrypt_data) );
    LOG_D("b64 encrypt_string hex:");
    phex(b64_encrypt_data, strlen(b64_encrypt_data) );

    // note: 释放
    delete []enc_s;
    enc_s = NULL;
    return b64_encrypt_data;
}

#ifdef __cplusplus
extern "C"
#endif
char* decrypt_b64string(char* b64data, char* key, char* iv) {
    // note: 调用注意释放内存, 调用 delete[] decrypt_data;
    if(false){
        b64data = "ak5qHtExXtVhQJQNIBZFLQr5ImZ34WRLZOD86vJbq-W5myda7tjlf2plTcw6TjzG";
        key = "vwtuJLHIFG";
        iv = "robot_wxid";
    }

    char encrypt_string[8192] = {0};
    AES_KEY aes;
    // 调整key
    char array_key[KEY_BYTE+1] = {0};
    _autofill(array_key, key);
    // 调整iv
    char array_iv[KEY_BYTE+1] = {0};
    _autofill(array_iv, (char*)iv);
    int nTotal = 0;

    // 打印
    LOG_D( "\n" );
    LOG_D( "data:%s, len:%d\n", b64data, strlen(b64data) );
    LOG_D( "key:%s, len:%d\n", array_key, strlen(array_key) );
    LOG_D( "iv:%s, len:%d\n", array_iv, strlen(array_iv) );
    LOG_D( "usebase64:\n" );

    // 解密
    char *decode = b64decode((const unsigned char *) b64data, nTotal);
    if (AES_set_decrypt_key((unsigned char *) array_key, 128, &aes) < 0) {
        LOG_E("AES_set_decrypt_key error");
        return NULL;
    }
    AES_cbc_decrypt((unsigned char *)decode, (unsigned char *)encrypt_string, nTotal, &aes,(unsigned char *)array_iv);
    int decryptSize = strlen(encrypt_string);
    int k = decryptSize;
    for (int i = 0; i < decryptSize; i++) {
        if ((int) (encrypt_string[i]) <= 16) {
            k = i;
            break;
        }
    }
    // 释放资源
    delete []decode;
    decode = NULL;

    char *decrypt_data = new char[k + 1];
    memset(decrypt_data, 0, k+1);
    strncpy(decrypt_data, encrypt_string, k);
    LOG_D( "decrypt_data:%s, len:%d\n", decrypt_data, strlen(decrypt_data) );
    return decrypt_data;
}