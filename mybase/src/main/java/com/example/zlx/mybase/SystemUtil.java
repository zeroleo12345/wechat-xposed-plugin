package com.example.zlx.mybase;

/**
 * Created by zlx on 2017/3/22.
 * http://blog.csdn.net/zhuwentao2150/article/details/51946387#
 */

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 系统工具类
 * Created by zhuwentao on 2016-07-18.
 */
public class SystemUtil {

    public static String getLibPath(String package_name) {
        return String.format("/data/data/%s/lib", package_name);
    }
    public static String getSign(Context context, String package_name) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo( package_name, PackageManager.GET_SIGNATURES);
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.update(info.signatures[0].toByteArray());
        return MyString.bytesToHexString(md.digest());
    }
    /**
     * 获取当前手机系统语言。
     *
     * @return 返回当前系统语言。例如：当前设置的是“中文-中国”，则返回“zh-CN”
     */
    public static String getSystemLanguage() {
        return Locale.getDefault().getLanguage();
    }

    /**
     * 获取当前系统上的语言列表(Locale列表)
     *
     * @return  语言列表
     */
    public static Locale[] getSystemLanguageList() {
        return Locale.getAvailableLocales();
    }

    /**
     * 获取当前手机系统版本号
     *
     * @return  系统版本号
     */
    public static String getSystemVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    /**
     * 获取手机型号
     *
     * @return  手机型号
     */
    public static String getSystemModel() {
        return android.os.Build.MODEL;
    }

    /**
     * 获取手机厂商
     *
     * @return  手机厂商
     */
    public static String getDeviceBrand() {
        return android.os.Build.BRAND;
    }

    /**
     * 获取手机IMEI(需要“android.permission.READ_PHONE_STATE”权限)
     *
     * @return  手机IMEI
     */
    public static String getIMEI(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Activity.TELEPHONY_SERVICE);
        if (tm != null) {
            return tm.getDeviceId();
        }
        return null;
    }

    public static String readTextFile(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte buf[] = new byte [1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
        } catch (IOException e) {
            Log.e("zzz", String.format("[readTextFile] %s", Log.getStackTraceString(e)));
        } finally {
            if(outputStream != null) {
                outputStream.close();
            }
        }
        return outputStream.toString();
    }

    public static String long2ip(long ip){
        StringBuffer sb=new StringBuffer();
        sb.append(String.valueOf((int)(ip&0xff)));
        sb.append('.');
        sb.append(String.valueOf((int)((ip>>8)&0xff)));
        sb.append('.');
        sb.append(String.valueOf((int)((ip>>16)&0xff)));
        sb.append('.');
        sb.append(String.valueOf((int)((ip>>24)&0xff)));
        return sb.toString();
    }

    public static void writeByte2File(byte[] content, String filepath) throws java.io.IOException{
        FileOutputStream fos = new FileOutputStream( new File(filepath) );
        try {
            fos.write(content);
            fos.flush();
        }finally {
            if( fos != null) {
                fos.close();
            }
        }
    }

    public static String stringToHexString(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = (int) s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + "\\u" + String.format("%4s", s4).replace(' ','0');
        }
        return str;
    }


    public static int get_timestamp(){
        return (int) (System.currentTimeMillis()/1000);
    }
}