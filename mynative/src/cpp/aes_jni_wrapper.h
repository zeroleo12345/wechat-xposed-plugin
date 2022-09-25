//
// Created by zlx on 2017/12/27.
//

#ifndef XPOSED_6510_AES_JNI_WRAPPER_H
#define XPOSED_6510_AES_JNI_WRAPPER_H

#ifdef __cplusplus
extern "C"
#endif
char* encrypt_string_and_b64(char* data, char* key, char* iv);

#ifdef __cplusplus
extern "C"
#endif
char* decrypt_b64string(char* b64data, char* key, char* iv);

#endif //XPOSED_6510_AES_JNI_WRAPPER_H
