//
// Created by zlx on 2018/2/18.
//

#ifndef XPOSED_6510_CHECKSIGNATURE_H
#define XPOSED_6510_CHECKSIGNATURE_H

#include <jni.h>

//合法的APP包名
//合法的 debug.keystore 的 SHA1 值
static const char *keystore_sha1 = "570b5b875dde41bce5d5544d8df5a450b164b3bf";

/**
 * 校验APP 包名和签名是否合法
 *
 * 返回值为1 表示合法
 */
jstring CbytesToHexString(JNIEnv *env, jbyteArray array);
jbyteArray ChexStringToBytes(JNIEnv *env, jstring str);

#define checkSignature(ret) \
do {\
    if (strcasecmp(g_sign, keystore_sha1) != 0) {\
        LOG_E("checkSignature fail");\
        return ret;\
    }\
}while(0);

#endif //XPOSED_6510_CHECKSIGNATURE_H
