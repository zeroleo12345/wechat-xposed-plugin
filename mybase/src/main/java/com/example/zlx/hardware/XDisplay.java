package com.example.zlx.hardware;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

/** 屏幕相关 */
public class XDisplay {
    public XDisplay(final ClassLoader classLoader){
        try {
            /**
            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getMetrics", DisplayMetrics.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST) {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("DPI") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.densityDpi = HardwareInfo.SharedPref.getInt("DPI");
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getRealMetrics", DisplayMetrics.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST) {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("DPI") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.densityDpi = HardwareInfo.SharedPref.getInt("DPI");
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getMetrics", DisplayMetrics.class, new XC_MethodHook()
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                    if( HardwareInfo.SharedPref.has("density") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.density = (float) HardwareInfo.SharedPref.getDouble("density");
                    }
                }

            });

            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getMetrics", DisplayMetrics.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                    if( HardwareInfo.SharedPref.has("xdpi") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.xdpi = (float) HardwareInfo.SharedPref.getDouble("xdpi");
                    }
                }

            });

            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getMetrics", DisplayMetrics.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                    if( HardwareInfo.SharedPref.has("ydpi") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.ydpi = (float) HardwareInfo.SharedPref.getDouble("ydpi");
                    }
                }

            });

            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getMetrics", DisplayMetrics.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                    if( HardwareInfo.SharedPref.has("scaledDensity") ) {
                        super.afterHookedMethod(param);
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.scaledDensity = (float) HardwareInfo.SharedPref.getDouble("scaledDensity");
                    }
                }

            });
            */

            //  已废弃的修改屏幕信息
            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getWidth", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("width") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("width"));
                    }
                }
            });

            // 高
            XposedHelpers.findAndHookMethod("android.view.Display", classLoader, "getHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("height") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("height"));
                    }
                }
            });

            // 宽
            XposedHelpers.findAndHookMethod(Display.class, "getMetrics", DisplayMetrics.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST) {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("width") ) {
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.widthPixels = HardwareInfo.SharedPref.getInt("width");
                    }
                }
            });
            // 高
            XposedHelpers.findAndHookMethod(Display.class, "getMetrics", DisplayMetrics.class, new XC_MethodHook(XCallback.PRIORITY_LOWEST) {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("height") ) {
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        metrics.heightPixels = HardwareInfo.SharedPref.getInt("height");
                    }
                }
            });
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }
}