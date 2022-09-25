package com.example.zlx.mynative;

import android.util.Log;

import com.example.zlx.mynative.AuthArg;

public class JNIUtils {
    static boolean is_init = false;
    public static void init_so(String path, String sign){
        try{
            if( is_init ) return;
            is_init = true;
            Log.d(BuildConfig.TAG, "JNI init");
            System.load(path+"/libnative-lib.so");      // 必须使用绝对路径
            //System.loadLibrary("native-lib");     //在Xposed框架下无法使用
            Log.i(BuildConfig.TAG, "JNI init success!");
            JNIUtils.Cinit(sign);
        }catch (Throwable e){
            Log.e(BuildConfig.TAG, "JNIUtils error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }

    //java调C中的方法都需要用native声明且方法名必须和c的方法名一样
    public static native void Cinit(String sign);
    public static native String getString();
    public static native String createUUID(long mostSigBits, long leastSigBits, String wordtips);
    public static native String CencryptToken(AuthArg autharg);
    public static native String encryptToken(AuthArg autharg);
    public static native String decryptToken(String b64_encrypt_user_token, String b64_encrypt_last_token, AuthArg autharg);
    public static native String CdecryptToken(String b64_encrypt_user_token, String b64_encrypt_last_token, AuthArg autharg);
    public static native String getKey();
    //note: 以下用于加密字符串
    public static native String Cencode(String string);
    public static native String Cdecode(String b64string);
    public static native String getEncodeKey();
}