package com.example.zlx.mybase;

//import java.util.Base64;      //note. Java平台
import android.util.Base64;     //note. Android平台

/**
 * Created by zlx on 2018/8/5.
 */

public class MyBase64 {

    /**
     * BASE64 解密
     */
    public static byte[] b64decode(String text) throws Exception {
//        return Base64.getUrlDecoder().decode(text); // note: Java平台. 需与Python一样使用Url Safe(使用_-替换+/): 1. Base64.getUrlDecoder(); 2. Base64.getDecoder()
        return Base64.decode( text, Base64.NO_WRAP | Base64.URL_SAFE ); // note: Android平台.
    }

    /**
     * BASE64 加密
     * @param text 需要加密的字节数组
     */
    public static String b64encode(byte[] text) throws Exception {
//        return Base64.getUrlEncoder().encodeToString(text); // note: Java平台. 需与Python一样使用Url Safe(使用_-替换+/): 1. Base64.getUrlEncoder(); 2. Base64.getEncoder()
        return Base64.encodeToString( text, Base64.NO_WRAP | Base64.URL_SAFE ); // note: Android平台.
    }

}
