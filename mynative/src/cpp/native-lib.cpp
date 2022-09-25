/**
 * //note: 不使用 JNI_Onload 时, 函数定义需导出, 并定义如下 JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_encryptToken(JNIEnv *env, jobject obj,  jobject autharg)
 * //note: 当函数带参数的时候, 不能使用如下定义 void Cinit(JNIEnv *env, jobject sign)
 */

// include头文件, 名加后缀.h, 方法名一定要和.h文件中的方法名称一样
#include "native-lib.h"
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <elf.h>
#include <sys/mman.h>
#include <string.h>
#include "aes_jni_wrapper.h"
#include "check_sign.h"
// note: 函数声明
void init_getString() __attribute__((constructor));
//jstring getString(JNIEnv*) __attribute__((section (".mytext")));
unsigned long getLibAddr();

const char * g_sign = NULL;
// note 函数实现
__attribute__((section (".mytext")))
void JNICALL Cinit(JNIEnv *env, jobject obj, jobject sign)
{
    g_sign = env->GetStringUTFChars((jstring)sign, 0);
    LOG_D("g_sign: %s", g_sign);
//    env->ReleaseStringUTFChars((jstring)sign, g_sign);
};

// note 函数实现
__attribute__((section (".mytext")))
jstring getString(JNIEnv* env){
    return env->NewStringUTF("Native method return!");
};

void init_getString(){
    char name[15];
    unsigned int nblock;
    unsigned int nsize;
    unsigned long base;
    unsigned long text_addr;
    unsigned int i;
    Elf32_Ehdr *ehdr;
    Elf32_Shdr *shdr;

    base = getLibAddr();

    ehdr = (Elf32_Ehdr *)base;
    text_addr = ehdr->e_shoff + base;

    nblock = ehdr->e_entry >> 16;
    nsize = ehdr->e_entry & 0xffff;

    __android_log_print(ANDROID_LOG_INFO, "JNITag", "nblock =  0x%x,nsize:%d", nblock,nsize);
    __android_log_print(ANDROID_LOG_INFO, "JNITag", "base =  0x%x", text_addr);
    printf("nblock = %d\n", nblock);

    if(mprotect((void *) (text_addr / PAGE_SIZE * PAGE_SIZE), 4096 * nsize, PROT_READ | PROT_EXEC | PROT_WRITE) != 0){
        puts("mem privilege change failed");
        __android_log_print(ANDROID_LOG_INFO, "JNITag", "mem privilege change failed");
    }

    for(i=0;i< nblock; i++){
        char *addr = (char*)(text_addr + i);
        *addr = ~(*addr);
    }

    if(mprotect((void *) (text_addr / PAGE_SIZE * PAGE_SIZE), 4096 * nsize, PROT_READ | PROT_EXEC) != 0){
        puts("mem privilege change failed");
    }
    puts("Decrypt success");
}

unsigned long getLibAddr(){
    unsigned long ret = 0;
    char name[] = "libnative-lib.so";   // note: 注意修改!
    char buf[4096], *temp;
    int pid;
    FILE *fp;
    pid = getpid();
    sprintf(buf, "/proc/%d/maps", pid);
    fp = fopen(buf, "r");
    if(fp == NULL)
    {
        puts("open failed");
        goto _error;
    }
    while(fgets(buf, sizeof(buf), fp)){
        if(strstr(buf, name)){
            temp = strtok(buf, "-");
            ret = strtoul(temp, NULL, 16);
            break;
        }
    }
    _error:
    fclose(fp);
    return ret;
}

///////////////////////////////////////////////////////////////////////////////////////////////////
#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
 jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_createUUID(JNIEnv *env, jobject obj,
                                                                                   jlong mostSigBits, jlong leastSigBits, jstring wordtips)
{
    jclass cls_uuid = env->FindClass("java/util/UUID");
    jmethodID mid_init = env->GetMethodID(cls_uuid, "<init>", "(JJ)V"); // note: 查找构造方法
    jobject obj_uuid = env->NewObject(cls_uuid, mid_init, mostSigBits, leastSigBits);   // new 对象

    jmethodID mid_toString = env->GetMethodID(cls_uuid, "toString", "()Ljava/lang/String;");    // note: 查找类成员方法
    jstring str_uuid = (jstring)env->CallObjectMethod(obj_uuid, mid_toString);  // note:  调用类成员方法
    /*
    const char *nativeString = env->GetStringUTFChars(str_uuid, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
    LOG_D("GetStringUTFChars:%s", nativeString);
    env->ReleaseStringUTFChars(str_uuid, nativeString);
     env->DeleteLocalRef(str_uuid);
    */
    jclass cls_main = env->FindClass("com/example/zlx/xposeapplication/Main");  //代码混淆后找不到
    jfieldID fid_d = env->GetStaticFieldID(cls_main, "d", "Ljava/lang/String;");    // note: 查找类静态成员字段
    env->SetStaticObjectField(cls_main, fid_d, str_uuid);

    /*
    //  参考文章: http://stackoverflow.com/questions/8301206/android-jni-c-simple-append-function
    // Get the UTF-8 characters that represent our java string
    const jbyte *sx = env->GetStringUTFChars(wordtips, NULL);
    const jbyte *sy = env->GetStringUTFChars(str_uuid, NULL);
    // Concatenate the two strings
    char *concatenated = new char[strlen(sx) + strlen(sy) + 1];
    strcpy(concatenated, sx);
    strcat(concatenated, sy);
    // Create java string from our concatenated C string
    jstring retval = env->NewStringUTF(concatenated);
    // Free the memory in sx
    env->ReleaseStringUTFChars(wordtips, sx);
    env->ReleaseStringUTFChars(str_uuid, sx);
    // Free the memory in concatenated
    delete []concatenated;
    */

    // call log
    jclass cls_xposedbridge = env->FindClass("de/robv/android/xposed/XposedBridge");
    jmethodID mid_log = env->GetStaticMethodID(cls_xposedbridge, "log", "(Ljava/lang/String;)V");   // note: 查找类静态方法
    env->CallStaticVoidMethod(cls_xposedbridge, mid_log, wordtips);
    env->CallStaticVoidMethod(cls_xposedbridge, mid_log, str_uuid);
    return str_uuid;
    //return env->NewStringUTF("just return jstring");
}

static jstring g_key = NULL;
static jstring g_last_token = NULL;

#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
 jstring JNICALL encryptToken(JNIEnv *env, jobject obj, jobject autharg)
{
    LOG_D("Java_com_example_zlx_mynative_JNIUtils_encryptToken START");
    // Class
    jclass cls_autharg = env->GetObjectClass(autharg); // 获取对象的class类
    if (cls_autharg == NULL) LOG_E("cls_autharg");
    // Field ID
    jfieldID fid_auth_type = env->GetFieldID(cls_autharg, "auth_type", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_auth_type == NULL) LOG_E("fid_auth_type");
    jfieldID fid_key = env->GetFieldID(cls_autharg, "key", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_key == NULL) LOG_E("fid_key");
    jfieldID fid_user_token = env->GetFieldID(cls_autharg, "user_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_user_token == NULL) LOG_E("fid_user_token");
    jfieldID fid_last_token = env->GetFieldID(cls_autharg, "last_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_last_token == NULL) LOG_E("fid_last_token");
    // Method ID
    jmethodID mid_is_i = env->GetMethodID(cls_autharg, "is_i", "()Z");    // note: 查找类成员方法ID   public boolean is_i(){}
    if (mid_is_i == NULL) LOG_E("mid_is_i");
    // Value
    jstring auth_type = (jstring)env->GetObjectField(autharg, fid_auth_type);   // note: 获取类字段, 不能使用 cls_autharg, 而要使用真实对象
//    jstring key = (jstring)env->GetObjectField(autharg, fid_key);
    jstring user_token = (jstring)env->GetObjectField(autharg, fid_user_token);
//    jstring last_token = (jstring)env->GetObjectField(autharg, fid_last_token);
    jboolean is_i = (jboolean)env->CallBooleanMethod(autharg, mid_is_i);
    // note: 生成随机key, 例如: str = myrandom.randomStr(10);
    LOG_D("create random secret");
    // public static String randomStr(int length) {
    jclass cls_random = env->FindClass("com/example/zlx/mybase/MyRandom");  //代码混淆后找不到
    if (cls_random == NULL) LOG_E("cls_random");
    jmethodID mid_randomStr = env->GetStaticMethodID(cls_random, "randomStr", "(I)Ljava/lang/String;");    // note: 查找类成员方法ID
    if (mid_randomStr == NULL) LOG_E("mid_randomStr");
    jint length = 10;
    jstring string_key = (jstring) env->CallStaticObjectMethod(cls_random, mid_randomStr, length);  // note:  调用类成员方法
    if( string_key == NULL ){ LOG_E("string_key"); }
    if (g_key == NULL) {
        g_key = (jstring) env->NewGlobalRef(string_key);
    } else {
        env->DeleteGlobalRef(g_key);
        g_key = (jstring) env->NewGlobalRef(string_key);
    }
//    env->SetObjectField(autharg, fid_key, string_key);  // note: 赋值 AutyArg.key
    // note: 生成加密串
    LOG_D("create encrypt token");
    jclass cls_myaes = env->FindClass("com/example/zlx/mybase/MyAES");  //代码混淆后找不到
    if (cls_myaes == NULL) LOG_E("cls_myaes");
    // note: 查找类成员方法 public static String encrypt_string_and_b64(String str, String key, String initVector) {
    jmethodID mid_encrypt_byte = env->GetStaticMethodID(cls_myaes, "encrypt_string_and_b64", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (mid_encrypt_byte == NULL) LOG_E("mid_encrypt_byte");
    jstring string_iv = env->NewStringUTF("robot_wxid");   // note: new String("")
    jstring string_token;
    if(is_i) string_token = user_token;
    else string_token = g_last_token;
    LOG_D("is_i:%d", is_i);
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(auth_type, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("auth_type:%s", nativeString);
        env->ReleaseStringUTFChars(auth_type, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(string_token, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("string_token:%s", nativeString);
        env->ReleaseStringUTFChars(string_token, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(string_key, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("string_key:%s", nativeString);
        env->ReleaseStringUTFChars(string_key, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(string_iv, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("string_iv:%s", nativeString);
        env->ReleaseStringUTFChars(string_iv, nativeString);
    }
    // note:  调用类成员方法  String b64_enc_data = MyAES.encrypt_string_and_b64( data, aes_key, iv );
    jstring string_encrypt_last_token = (jstring) env->CallStaticObjectMethod(cls_myaes, mid_encrypt_byte, string_token, string_key, string_iv);
    if( string_encrypt_last_token == NULL ){ LOG_E("string_encrypt_last_token"); }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(string_encrypt_last_token, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("string_encrypt_last_token:%s", nativeString);
        env->ReleaseStringUTFChars(string_encrypt_last_token, nativeString);
    }
    // note: 赋值 AuthArg.last_token = b64_encrypt_last_token
    env->SetObjectField(autharg, fid_last_token, string_encrypt_last_token);
    LOG_D("Java_com_example_zlx_mynative_JNIUtils_encryptToken END");
    jstring string_success = env->NewStringUTF("success");
    return string_success;
}

//note: 当不使用JNI_Onload()时, 函数定义需导出, 并定义如下 JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_decryptToken(JNIEnv *env, jobject obj, jstring b64_encrypt_user_token, jstring b64_encrypt_last_token, jobject autharg)
#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
 jstring JNICALL decryptToken(JNIEnv *env, jobject obj, jstring b64_encrypt_user_token, jstring b64_encrypt_last_token, jobject autharg)
{
    LOG_D("Java_com_example_zlx_mynative_JNIUtils_decryptToken START");

    // Class
    jclass cls_autharg = env->GetObjectClass(autharg); // 获取对象的class类
    if (cls_autharg == NULL) LOG_E("cls_autharg");

    // Field ID
    jfieldID fid_auth_type = env->GetFieldID(cls_autharg, "auth_type", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_auth_type == NULL) LOG_E("fid_auth_type");
    jfieldID fid_key = env->GetFieldID(cls_autharg, "key", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_key == NULL) LOG_E("fid_key");
    jfieldID fid_user_token = env->GetFieldID(cls_autharg, "user_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_user_token == NULL) LOG_E("fid_user_token");
    jfieldID fid_last_token = env->GetFieldID(cls_autharg, "last_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_last_token == NULL) LOG_E("fid_last_token");
    jfieldID fid_is_plugin_auth_success = env->GetFieldID(cls_autharg, "is_plugin_auth_success", "Z");    // note: 查找类成员字段ID
    if (fid_is_plugin_auth_success == NULL) LOG_E("is_plugin_auth_success");

    // Method ID
    jmethodID mid_is_i = env->GetMethodID(cls_autharg, "is_i", "()Z");    // note: 查找类成员方法ID   public boolean is_i(){}
    if (mid_is_i == NULL) LOG_E("mid_is_i");

    // Value
    jstring auth_type = (jstring)env->GetObjectField(autharg, fid_auth_type);
    jstring new_key = (jstring)env->GetObjectField(autharg, fid_key);
    jstring user_token = (jstring)env->GetObjectField(autharg, fid_user_token);
//    jstring last_token = (jstring)env->GetObjectField(autharg, fid_last_token);
    jboolean is_i = (jboolean)env->CallBooleanMethod(autharg, mid_is_i);
    //
    jstring string_iv = env->NewStringUTF("robot_wxid");   // note: new String("")
    // note: 解密 encrypt_user_token
    LOG_D("decrypt user token");
    jclass cls_myaes = env->FindClass("com/example/zlx/mybase/MyAES");  //代码混淆后找不到
    if( cls_myaes == NULL ) LOG_E("cls_myaes");
    // note: 查找类成员方法   public static String decrypt_b64string(String b64string, String key, String initVector) {
    jmethodID mid_decrypt_b64string_and_b64 = env->GetStaticMethodID(cls_myaes, "decrypt_b64string", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(b64_encrypt_user_token, 0);
        LOG_D("b64_encrypt_user_token:%s", nativeString);
        env->ReleaseStringUTFChars(b64_encrypt_user_token, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(g_key, 0);
        LOG_D("g_key:%s", nativeString);
        env->ReleaseStringUTFChars(g_key, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(string_iv, 0);
        LOG_D("string_iv:%s", nativeString);
        env->ReleaseStringUTFChars(string_iv, nativeString);
    }
    jstring decrypt_user_token = (jstring)env->CallStaticObjectMethod(cls_myaes, mid_decrypt_b64string_and_b64, b64_encrypt_user_token, g_key, string_iv);  // note:  调用类成员方法

    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(user_token, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("user_token:%s", nativeString);
        env->ReleaseStringUTFChars(user_token, nativeString);
    }
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(decrypt_user_token, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("decrypt_user_token:%s", nativeString);
        env->ReleaseStringUTFChars(decrypt_user_token, nativeString);
    }

    // note 判断是否相等 user_token 和 解密后 decrypt_user_token.
    jclass cls_textutils = env->FindClass("android/text/TextUtils");
    jmethodID mid_equals = env->GetStaticMethodID(cls_textutils, "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z");
    // note: public static boolean equals(CharSequence a, CharSequence b) {}      TextUtils.equals(String, String)
    jboolean is_equal = env->CallStaticBooleanMethod(cls_textutils, mid_equals, user_token, decrypt_user_token);  // note: jboolean = uint8_t
    LOG_D("is equal:%d", is_equal);

    if( is_equal ){

        // note 获取当前系统时间
        jclass cls_system = env->FindClass("java/lang/System");
        if( cls_system == NULL ) LOG_E("cls_system");
        jmethodID mid_currentTime = env->GetStaticMethodID(cls_system, "currentTimeMillis", "()J");
        if( mid_currentTime == NULL ) LOG_E("mid_currentTime");
        jlong ts = env->CallStaticLongMethod(cls_system, mid_currentTime);  // note: jlong = long long
        LOG_D("NDK currentTimeMillis:%lld", ts);

        // note 相等则赋值 MySync.currentTimeMillis     关键!!!!!!!!!!
        jclass cls_mysync = env->FindClass("com/example/zlx/xposeapplication/MySync");  //代码混淆后找不到
        if( cls_mysync == NULL ) LOG_E("cls_mysync");
        jfieldID fid_currentTimeMillis = env->GetStaticFieldID(cls_mysync, "currentTimeMillis", "J");    // note: 查找类静态成员字段
        if( fid_currentTimeMillis == NULL ) LOG_E("fid_currentTimeMillis");
        env->SetStaticLongField(cls_mysync, fid_currentTimeMillis, ts);

        // note  获取g_robot_wxid, 如果!="", 则赋值 MySync.g_robot_wxid    关键!!!!!!!!!
        jfieldID fid_g_robot_wxid = env->GetStaticFieldID(cls_mysync, "g_robot_wxid", "Ljava/lang/String;");    // note: 查找类静态成员字段
        if( fid_g_robot_wxid == NULL ) LOG_E("fid_g_robot_wxid");
        jstring g_robot_wxid = (jstring)env->GetStaticObjectField(cls_mysync, fid_g_robot_wxid);
        jstring empty_jstring = env->NewStringUTF("");   // note: new String("")
        // note: public static boolean equals(CharSequence a, CharSequence b) {}
        jclass cls_textutils = env->FindClass("android/text/TextUtils");
        jmethodID mid_equals = env->GetStaticMethodID(cls_textutils, "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z");
        jboolean is_empty = env->CallStaticBooleanMethod(cls_textutils, mid_equals, g_robot_wxid, empty_jstring);  // note: jboolean = uint8_t
        if( is_empty ) {
            env->SetStaticObjectField(cls_myaes, fid_g_robot_wxid, string_iv);
        }

        // note: 解密 encrypt_last_token, 例如: String decrypt_data = MyAES.decrypt_b64string( b64_enc_data, aes_key, iv );
        LOG_D("decrypt last token");
        jstring decrypt_last_token = (jstring)env->CallStaticObjectMethod(cls_myaes, mid_decrypt_b64string_and_b64, b64_encrypt_last_token, new_key, string_iv);  // note:  调用类成员方法
//        env->SetObjectField(autharg, fid_last_token, decrypt_last_token);   // note 赋新值
        if (g_last_token == NULL) {
            g_last_token = (jstring) env->NewGlobalRef(decrypt_last_token);
        } else {
            env->DeleteGlobalRef(g_last_token);
            g_last_token = (jstring) env->NewGlobalRef(decrypt_last_token);
        }
        LOG_D("Java_com_example_zlx_mynative_JNIUtils_decryptToken END");

    }

    if( ! is_equal ){
        env->SetBooleanField(autharg, fid_is_plugin_auth_success, false);
    } else{
        env->SetBooleanField(autharg, fid_is_plugin_auth_success, true);
    }

    jstring string_success = env->NewStringUTF("success");
    return string_success;
}


//note: 当不使用JNI_Onload()时, 函数定义需导出, 并定义如下 JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_getKey(JNIEnv *env, jobject obj)
#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
 jstring JNICALL getKey(JNIEnv *env, jobject obj)   // note 返回加密的Key
{
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(g_key, 0);
        LOG_D("g_key:%s", nativeString);
        env->ReleaseStringUTFChars(g_key, nativeString);
    }
    return g_key;
}

////////////////////////////////////////////////////////////////////////////////////////////////



//note: 当不使用JNI_Onload()时, 函数定义需导出, 并定义如下 JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_encryptToken(JNIEnv *env, jobject obj,  jobject autharg)
#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
jstring JNICALL CencryptToken(JNIEnv *env, jobject obj,
                              jobject autharg)
{
    LOG_D("CencryptToken START");
    // Class
    jclass cls_autharg = env->GetObjectClass(autharg); // 获取对象的class类
    if (cls_autharg == NULL) LOG_E("cls_autharg");

    // Field ID
    jfieldID fid_auth_type = env->GetFieldID(cls_autharg, "auth_type", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_auth_type == NULL) LOG_E("fid_auth_type");
    jfieldID fid_key = env->GetFieldID(cls_autharg, "key", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_key == NULL) LOG_E("fid_key");
    jfieldID fid_user_token = env->GetFieldID(cls_autharg, "user_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_user_token == NULL) LOG_E("fid_user_token");
    jfieldID fid_last_token = env->GetFieldID(cls_autharg, "last_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_last_token == NULL) LOG_E("fid_last_token");

    // Method ID
    jmethodID mid_is_i = env->GetMethodID(cls_autharg, "is_i", "()Z");    // note: 查找类成员方法ID   public boolean is_i(){}
    if (mid_is_i == NULL) LOG_E("mid_is_i");

    // Value
    jstring auth_type = (jstring)env->GetObjectField(autharg, fid_auth_type);   // note: 获取类字段, 不能使用 cls_autharg, 而要使用真实对象
//    jstring key = (jstring)env->GetObjectField(autharg, fid_key);
    jstring user_token = (jstring)env->GetObjectField(autharg, fid_user_token);
//    jstring last_token = (jstring)env->GetObjectField(autharg, fid_last_token);
    jboolean is_i = (jboolean)env->CallBooleanMethod(autharg, mid_is_i);
    // note: 生成随机key, 例如: str = myrandom.randomStr(10);
    LOG_D("create random secret");
    // public static String randomStr(int length) {
    jclass cls_random = env->FindClass("com/example/zlx/mybase/MyRandom");  //代码混淆后找不到
    if (cls_random == NULL) LOG_E("cls_random");
    jmethodID mid_randomStr = env->GetStaticMethodID(cls_random, "randomStr", "(I)Ljava/lang/String;");    // note: 查找类成员方法ID
    if (mid_randomStr == NULL) LOG_E("mid_randomStr");
    jint length = 10;
    jstring string_key = (jstring) env->CallStaticObjectMethod(cls_random, mid_randomStr, length);  // note:  调用类成员方法
    if( string_key == NULL ){ LOG_E("string_key"); }
    if (g_key == NULL) {
        g_key = (jstring) env->NewGlobalRef(string_key);
    } else {
        env->DeleteGlobalRef(g_key);
        g_key = (jstring) env->NewGlobalRef(string_key);
    }
//    env->SetObjectField(autharg, fid_key, string_key);  // note: 赋值 AutyArg.key
    // note: 生成加密串
    LOG_D("create encrypt token");
    jclass cls_myaes = env->FindClass("com/example/zlx/mybase/MyAES");  //代码混淆后找不到
    if (cls_myaes == NULL) LOG_E("cls_myaes");
    jstring string_token;
    if(is_i) string_token = user_token;
    else string_token = g_last_token;
    // note: jstring转换成char *, 后面需要释放malloc
    char *data = (char*)env->GetStringUTFChars(string_token, 0);
    char *key = (char*)env->GetStringUTFChars(string_key, 0);
    char *iv = "robot_wxid";
    // note:  调用类成员方法  String b64_enc_data = MyAES.encrypt_string_and_b64( string_token, string_key, string_iv );
    char* b64_encrypt_data = encrypt_string_and_b64(data, key, iv);
    jstring string_encrypt_last_token = env->NewStringUTF(b64_encrypt_data);
    // note: 释放malloc, new
    delete []b64_encrypt_data;
    b64_encrypt_data = NULL;
    env->ReleaseStringUTFChars(string_token, data);
    env->ReleaseStringUTFChars(string_key, key);
    // note: 赋值 AuthArg.last_token = b64_encrypt_last_token
    env->SetObjectField(autharg, fid_last_token, string_encrypt_last_token);
    LOG_D("CencryptToken END");
    return NULL;
}

//note: 当不使用JNI_Onload()时, 函数定义需导出, 并定义如下 JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_decryptToken(JNIEnv *env, jobject obj, jstring b64_encrypt_user_token, jstring b64_encrypt_last_token, jobject autharg)
#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
jstring JNICALL CdecryptToken(JNIEnv *env, jobject obj, jstring b64_encrypt_user_token, jstring b64_encrypt_last_token, jobject autharg)
{
    LOG_D("CdecryptToken START");

    // Class
    jclass cls_autharg = env->GetObjectClass(autharg); // 获取对象的class类
    if (cls_autharg == NULL) LOG_E("cls_autharg");

    // Field ID
    jfieldID fid_auth_type = env->GetFieldID(cls_autharg, "auth_type", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_auth_type == NULL) LOG_E("fid_auth_type");
    jfieldID fid_key = env->GetFieldID(cls_autharg, "key", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_key == NULL) LOG_E("fid_key");
    jfieldID fid_user_token = env->GetFieldID(cls_autharg, "user_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_user_token == NULL) LOG_E("fid_user_token");
    jfieldID fid_last_token = env->GetFieldID(cls_autharg, "last_token", "Ljava/lang/String;");    // note: 查找类成员字段ID
    if (fid_last_token == NULL) LOG_E("fid_last_token");
    jfieldID fid_is_plugin_auth_success = env->GetFieldID(cls_autharg, "is_plugin_auth_success", "Z");    // note: 查找类成员字段ID
    if (fid_is_plugin_auth_success == NULL) LOG_E("is_plugin_auth_success");

    // Method ID
    jmethodID mid_is_i = env->GetMethodID(cls_autharg, "is_i", "()Z");    // note: 查找类成员方法ID   public boolean is_i(){}
    if (mid_is_i == NULL) LOG_E("mid_is_i");

    // Value
    jstring auth_type = (jstring)env->GetObjectField(autharg, fid_auth_type);
    jstring new_key = (jstring)env->GetObjectField(autharg, fid_key);
    jstring user_token = (jstring)env->GetObjectField(autharg, fid_user_token);
//    jstring last_token = (jstring)env->GetObjectField(autharg, fid_last_token);
    jboolean is_i = (jboolean)env->CallBooleanMethod(autharg, mid_is_i);
    //
    // note: jstring转换成char *, 后面需要释放malloc
    char *b64data = (char*)env->GetStringUTFChars(b64_encrypt_user_token, 0);
    char *key = (char*)env->GetStringUTFChars(g_key, 0);
    char *iv = "robot_wxid";
    jstring string_iv = env->NewStringUTF(iv);   // note: new String("")
    // note: 解密 encrypt_user_token
    LOG_D("decrypt user token");
    // note:  jstring)env->CallStaticObjectMethod(cls_myaes, mid_decrypt_b64string_and_b64, b64_encrypt_user_token, g_key, string_iv);  // note:  调用类成员方法
    char* decrypt_data = decrypt_b64string(b64data, key, iv);
    jstring decrypt_user_token = env->NewStringUTF(decrypt_data);
    // note: 释放malloc, new
    delete []decrypt_data;
    decrypt_data = NULL;
    env->ReleaseStringUTFChars(b64_encrypt_user_token, b64data);
    env->ReleaseStringUTFChars(g_key, key);
    {
        // 打印jstring
        const char *nativeString = env->GetStringUTFChars(decrypt_user_token, 0); //GetStringUTFChars => ReleaseStringUTFChars; GetStringChars => ReleaseStringChars
        LOG_D("decrypt_user_token:%s", nativeString);
        env->ReleaseStringUTFChars(decrypt_user_token, nativeString);
    }

    // note 判断是否相等 user_token 和 解密后 decrypt_user_token.
    jclass cls_textutils = env->FindClass("android/text/TextUtils");
    jmethodID mid_equals = env->GetStaticMethodID(cls_textutils, "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z");
    // note: public static boolean equals(CharSequence a, CharSequence b) {}      TextUtils.equals(String, String)
    jboolean is_equal = env->CallStaticBooleanMethod(cls_textutils, mid_equals, user_token, decrypt_user_token);  // note: jboolean = uint8_t
    LOG_D("is equal:%d", is_equal);
    if( is_equal ){
        // note 获取当前系统时间
        jclass cls_system = env->FindClass("java/lang/System");
        if( cls_system == NULL ) LOG_E("cls_system");
        jmethodID mid_currentTime = env->GetStaticMethodID(cls_system, "currentTimeMillis", "()J");
        if( mid_currentTime == NULL ) LOG_E("mid_currentTime");
        jlong ts = env->CallStaticLongMethod(cls_system, mid_currentTime);  // note: jlong = long long
        LOG_D("NDK currentTimeMillis:%lld", ts);

        // note 相等则赋值 MySync.currentTimeMillis
        jclass cls_mysync = env->FindClass("com/example/zlx/xposeapplication/MySync");  //代码混淆后找不到
        if( cls_mysync == NULL ) LOG_E("cls_mysync");
        jfieldID fid_currentTimeMillis = env->GetStaticFieldID(cls_mysync, "currentTimeMillis", "J");    // note: 查找类静态成员字段
        if( fid_currentTimeMillis == NULL ) LOG_E("fid_currentTimeMillis");
        env->SetStaticLongField(cls_mysync, fid_currentTimeMillis, ts);
        LOG_D("1111111");
        // note  获取g_robot_wxid, 如果!="", 则赋值 MySync.g_robot_wxid
        jfieldID fid_g_robot_wxid = env->GetStaticFieldID(cls_mysync, "g_robot_wxid", "Ljava/lang/String;");    // note: 查找类静态成员字段
        if( fid_g_robot_wxid == NULL ) LOG_E("fid_g_robot_wxid");
        jstring g_robot_wxid = (jstring)env->GetStaticObjectField(cls_mysync, fid_g_robot_wxid);
        jstring empty_jstring = env->NewStringUTF("");   // note: new String("")
        // note: public static boolean equals(CharSequence a, CharSequence b) {}
        jclass cls_myaes = env->FindClass("com/example/zlx/mybase/MyAES");  //代码混淆后找不到
        if( cls_myaes == NULL ) LOG_E("cls_myaes");
        LOG_D("222222");
        jclass cls_textutils = env->FindClass("android/text/TextUtils");
        jmethodID mid_equals = env->GetStaticMethodID(cls_textutils, "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z");
        jboolean is_empty = env->CallStaticBooleanMethod(cls_textutils, mid_equals, g_robot_wxid, empty_jstring);  // note: jboolean = uint8_t
        if( is_empty ) {
            env->SetStaticObjectField(cls_myaes, fid_g_robot_wxid, string_iv);
        }

        // note: 解密 encrypt_last_token, 例如: String decrypt_data = MyAES.decrypt_b64string( b64_enc_data, aes_key, iv );
        LOG_D("decrypt last token");
        //jstring decrypt_last_token = (jstring)env->CallStaticObjectMethod(cls_myaes, mid_decrypt_b64string_and_b64, b64_encrypt_last_token, new_key, string_iv);  // note:  调用类成员方法
        char *b64data = (char*)env->GetStringUTFChars(b64_encrypt_last_token, 0);
        char *key = (char*)env->GetStringUTFChars(new_key, 0);
        char* decrypt_data = decrypt_b64string(b64data, key, iv);
        jstring decrypt_last_token = env->NewStringUTF(decrypt_data);
        // note: 释放malloc, new
        delete []decrypt_data;
        decrypt_data = NULL;
        env->ReleaseStringUTFChars(b64_encrypt_last_token, b64data);
        env->ReleaseStringUTFChars(new_key, key);

//        env->SetObjectField(autharg, fid_last_token, decrypt_last_token);   // note 赋新值
        if (g_last_token == NULL) {
            g_last_token = (jstring) env->NewGlobalRef(decrypt_last_token);
        } else {
            env->DeleteGlobalRef(g_last_token);
            g_last_token = (jstring) env->NewGlobalRef(decrypt_last_token);
        }
        LOG_D("CdecryptToken END");
    }

    if( ! is_equal ){
        env->SetBooleanField(autharg, fid_is_plugin_auth_success, false);
    } else{
        env->SetBooleanField(autharg, fid_is_plugin_auth_success, true);
    }

    return NULL;
}


#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
jstring JNICALL Cencode(JNIEnv *env, jobject obj, jstring str)
{
    return env->NewStringUTF("encode");
}

#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
jstring JNICALL Cdecode(JNIEnv *env, jobject obj, jstring b64_encrypt_str)
{
    //先进行apk被 二次打包的校验
    checkSignature(NULL);
    char *key = "password";
    char *iv = "robot_wxid";
    char *b64data = (char*)env->GetStringUTFChars(b64_encrypt_str, 0);
    char* decrypt_data = decrypt_b64string(b64data, key, iv);
    jstring decrypt_string = env->NewStringUTF(decrypt_data);
    // note: 释放malloc, new
    delete []decrypt_data;
    decrypt_data = NULL;
    env->ReleaseStringUTFChars(b64_encrypt_str, b64data);
    // 返回
    return decrypt_string;
}

#ifdef __cplusplus
extern "C"
#endif
__attribute__((section (".mytext")))
jstring JNICALL getEncodeKey(JNIEnv *env, jobject obj)   // note 返回编码字符串的Key
{
    return env->NewStringUTF("encodekey");
}
////////////////////////////////////////////////////////////////////////////////////////////////////
// 获取数组的大小
# define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
// 指定要注册的类，对应完整的java类名
#define JNIREG_CLASS "com/example/zlx/mynative/JNIUtils"

// Java和JNI函数的绑定表
static JNINativeMethod method_table[] = {
        { "Cinit", "(Ljava/lang/String;)V", (void*)Cinit },
        { "getString", "()Ljava/lang/String;", (void*)getString }, // note: JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_getString(JNIEnv *env, jobject obj)  或者 jstring getString(JNIEnv* env)
        { "getKey", "()Ljava/lang/String;", (void*)getKey }, // note: JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_getKey(JNIEnv *env, jobject obj)
        { "encryptToken", "(Lcom/example/zlx/mynative/AuthArg;)Ljava/lang/String;", (void*)encryptToken }, // note: JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_encryptToken(JNIEnv *env, jobject obj,  jobject autharg)
        { "CencryptToken", "(Lcom/example/zlx/mynative/AuthArg;)Ljava/lang/String;", (void*)CencryptToken },
        { "decryptToken", "(Ljava/lang/String;Ljava/lang/String;Lcom/example/zlx/mynative/AuthArg;)Ljava/lang/String;", (void*)decryptToken }, // note: JNIEXPORT jstring JNICALL Java_com_example_zlx_mynative_JNIUtils_decryptToken(JNIEnv *env, jobject obj, jstring b64_encrypt_user_token, jstring b64_encrypt_last_token, jobject autharg)
        { "CdecryptToken", "(Ljava/lang/String;Ljava/lang/String;Lcom/example/zlx/mynative/AuthArg;)Ljava/lang/String;", (void*)CdecryptToken },

        // note: 以下方法用于加密字符串
        { "Cencode", "(Ljava/lang/String;)Ljava/lang/String;", (void*)Cencode },
        { "Cdecode", "(Ljava/lang/String;)Ljava/lang/String;", (void*)Cdecode },
        { "getEncodeKey", "()Ljava/lang/String;", (void*)getEncodeKey },
};

// 注册native方法到java中
static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

int register_ndk_load(JNIEnv *env)
{
    // 调用注册方法
    return registerNativeMethods(env, JNIREG_CLASS, method_table, NELEM(method_table));
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        return result;
    }

    register_ndk_load(env);

    // 返回jni的版本
    return JNI_VERSION_1_4;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
