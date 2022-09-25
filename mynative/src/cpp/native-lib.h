
#include <jni.h>
/* Header for class com_example_zlx_mynative_JNIUtils */

#ifndef _Included_com_example_zlx_mynative_JNIUtils
#define _Included_com_example_zlx_mynative_JNIUtils

extern const char * g_sign;

//#define MY_DEBUG 1        //note: 在app.gradle里面定义
#define LOG_TAG    "zzz" // 这个是自定义的LOG的标识
#define LOG_D(...)  \
do{\
    if( MY_DEBUG ){\
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__);\
    }\
}while(0)
#define LOG_I(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__) // 定义LOGI类型
#define LOG_W(...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__) // 定义LOGW类型
#define LOG_E(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__) // 定义LOGE类型
#define LOG_F(...)  __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__) // 定义LOGF类型

#define phex(str, len)  \
do{\
    if( MY_DEBUG ){\
        char sDest[1024] = {0};\
        for(int idx = 0; idx < len; idx++){\
            sprintf( sDest+idx * 2, "%02x", (unsigned char) str[idx] );\
        }\
        LOG_D("%s", sDest);\
    }\
}while(0)

/*
void phex(char *str, int len) {
    char sDest[1024] = {0};
    //char szTmp[3];
    for(int idx = 0; idx < len; idx++){
        sprintf( sDest+idx * 2, "%02x", (unsigned char) str[idx] );
        //memcpy( &sDest[idx * 2], szTmp, 2 );
        // 方法2:
        //LOG_D("%02x", (unsigned char)str[idx]);
    }
    LOG_D("%s\n", sDest);
}
 */

#ifdef __cplusplus
extern "C" {
#endif

 jstring JNICALL createUUID(JNIEnv *, jobject, jlong, jlong, jstring);

 jstring JNICALL encryptToken(JNIEnv *, jobject, jobject);
 jstring JNICALL decryptToken(JNIEnv *, jobject, jstring, jstring, jobject);

 jstring JNICALL CencryptToken(JNIEnv *, jobject, jobject);
 jstring JNICALL CdecryptToken(JNIEnv *, jobject, jstring, jstring, jobject);

 jstring JNICALL getKey(JNIEnv *, jobject);
#ifdef __cplusplus
}
#endif

#endif
