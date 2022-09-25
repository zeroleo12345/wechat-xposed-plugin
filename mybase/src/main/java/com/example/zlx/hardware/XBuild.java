package com.example.zlx.hardware;

import android.content.ContentResolver;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XBuild {
    private ClassLoader classLoader = null;
    public XBuild(final ClassLoader loader){
        this.classLoader = loader;
        AndroidSerial();
        BaseBand();
        BuildProp();
    }

    public void AndroidSerial(){
        try {
            if( HardwareInfo.SharedPref.has("SERIAL")){
                XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", this.classLoader), "SERIAL", HardwareInfo.SharedPref.getString("SERIAL")); // 串口序列号
            }

            /**
            XposedHelpers.findAndHookMethod(Class.forName("android.os.SystemProperties"), "get", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("getBaseband") ){
                        super.afterHookedMethod(param);
                        String serialno = (String) param.args[0];
                        if ( serialno.equals("gsm.version.baseband") || serialno.equals("no message") ) {
                            param.setResult(HardwareInfo.SharedPref.getString("getBaseband"));
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(Class.forName("android.os.SystemProperties"), "get", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("getBaseband") ) {
                        super.afterHookedMethod(param);
                        String serialno = (String) param.args[0];
                        if (serialno.equals("gsm.version.baseband") || serialno.equals("no message")) {
                            param.setResult(HardwareInfo.SharedPref.getString("getBaseband"));
                        }
                    }
                }
            });
             */
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }

    public void BaseBand() {
        try {
            XposedHelpers.findAndHookMethod("android.os.Build", this.classLoader, "getRadioVersion", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable { //固件版本
                    if(HardwareInfo.SharedPref.has("RadioVersion")) {
                        param.setResult(HardwareInfo.SharedPref.getString("RadioVersion"));
                    }
                }
            });
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }

    public void BuildProp(){
        try {
//            if( HardwareInfo.SharedPref.has("board") ) XposedHelpers.findField(Build.class, "BOARD").set(null, HardwareInfo.SharedPref.getString("board"));
            if( HardwareInfo.SharedPref.has("BRAND") ) XposedHelpers.findField(Build.class, "BRAND").set(null, HardwareInfo.SharedPref.getString("BRAND"));
            if( HardwareInfo.SharedPref.has("CPU_ABI") ) XposedHelpers.findField(Build.class, "CPU_ABI").set(null, HardwareInfo.SharedPref.getString("CPU_ABI"));
            if( HardwareInfo.SharedPref.has("CPU_ABI2") ) XposedHelpers.findField(Build.class, "CPU_ABI2").set(null, HardwareInfo.SharedPref.getString("CPU_ABI2"));
//            if( HardwareInfo.SharedPref.has("device") ) XposedHelpers.findField(Build.class, "DEVICE").set(null, HardwareInfo.SharedPref.getString("device"));
            if( HardwareInfo.SharedPref.has("DISPLAY") ) XposedHelpers.findField(Build.class, "DISPLAY").set(null, HardwareInfo.SharedPref.getString("DISPLAY"));
            if( HardwareInfo.SharedPref.has("FINGERPRINT") ) XposedHelpers.findField(Build.class, "FINGERPRINT").set(null, HardwareInfo.SharedPref.getString("FINGERPRINT"));
            if( HardwareInfo.SharedPref.has("HARDWARE") ) XposedHelpers.findField(Build.class, "HARDWARE").set(null, HardwareInfo.SharedPref.getString("HARDWARE"));
//            if( HardwareInfo.SharedPref.has("ID") ) XposedHelpers.findField(Build.class, "ID").set(null, HardwareInfo.SharedPref.getString("ID"));
            if( HardwareInfo.SharedPref.has("MANUFACTURER") ) XposedHelpers.findField(Build.class, "MANUFACTURER").set(null, HardwareInfo.SharedPref.getString("MANUFACTURER"));
            if( HardwareInfo.SharedPref.has("MODEL") ) XposedHelpers.findField(Build.class, "MODEL").set(null, HardwareInfo.SharedPref.getString("MODEL"));
            if( HardwareInfo.SharedPref.has("PRODUCT") ) XposedHelpers.findField(Build.class, "PRODUCT").set(null, HardwareInfo.SharedPref.getString("PRODUCT"));
//            if( HardwareInfo.SharedPref.has("booltloader") ) XposedHelpers.findField(Build.class, "BOOTLOADER").set(null, HardwareInfo.SharedPref.getString("booltloader")); //主板引导程序
//            if( HardwareInfo.SharedPref.has("host") ) XposedHelpers.findField(Build.class, "HOST").set(null, HardwareInfo.SharedPref.getString("host"));  // 设备主机地址
//            if( HardwareInfo.SharedPref.has("tags") ) XposedHelpers.findField(Build.class, "TAGS").set(null, HardwareInfo.SharedPref.getString("tags"));  //描述build的标签
//            if( HardwareInfo.SharedPref.has("type") ) XposedHelpers.findField(Build.class, "TYPE").set(null, HardwareInfo.SharedPref.getString("type")); //设备版本类型
            if( HardwareInfo.SharedPref.has("INCREMENTAL") ) XposedHelpers.findField(Build.VERSION.class, "INCREMENTAL").set(null, HardwareInfo.SharedPref.getString("INCREMENTAL")); //源码控制版本号
            if( HardwareInfo.SharedPref.has("RELEASE") ) XposedHelpers.findField(android.os.Build.VERSION.class, "RELEASE").set(null, HardwareInfo.SharedPref.getString("RELEASE"));
            if( HardwareInfo.SharedPref.has("SDK") ) XposedHelpers.findField(android.os.Build.VERSION.class, "SDK").set(null, HardwareInfo.SharedPref.getString("SDK"));
//            XposedHelpers.findField(android.os.Build.VERSION.class, "CODENAME").set(null, "REL"); //写死就行 这个值为固定
//            if( HardwareInfo.SharedPref.has("time") ) XposedHelpers.findField(Build.class, "TIME").set(null,HardwareInfo.SharedPref.getString("time"));  // 固件时间build
            // 2.
            XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", this.classLoader, "getString", ContentResolver.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("android_id") ) {
                        if ( param.args[1].equals(Settings.Secure.ANDROID_ID) ) {
                            param.setResult(HardwareInfo.SharedPref.getString("android_id"));
                        }
                    }
                }
            });
            // 3.
            /*
            Class<?> cls = Class.forName("android.os.SystemProperties");
            if(cls != null){
                for (Member mem : cls.getDeclaredMethods()) {
                    XposedBridge.hookMethod(mem, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            // 用户的KEY
                            if (param.args.length > 0 && param.args[0] != null && param.args[0].equals("ro.build.description")) {
                                param.setResult(HardwareInfo.SharedPref.getString("DESCRIPTION"));
                            }
                        }
                    });
                }
            }
             */
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }
}

