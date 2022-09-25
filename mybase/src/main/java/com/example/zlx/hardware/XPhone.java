package com.example.zlx.hardware;

import android.os.Build;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XPhone {
    private ClassLoader classLoader = null;
    public XPhone(final ClassLoader loader){
        this.classLoader = loader;
        Telephony();
        Wifi();
    }

    public void Telephony() {
        try {
            if( HardwareInfo.SharedPref.has("IMEI") ) {
                XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getDeviceId", XC_MethodReplacement.returnConstant(HardwareInfo.SharedPref.getString("IMEI")));
                XposedHelpers.findAndHookMethod("com.android.internal.telephony.PhoneSubInfo", this.classLoader, "getDeviceId", XC_MethodReplacement.returnConstant(HardwareInfo.SharedPref.getString("IMEI")));
                if(Build.VERSION.SDK_INT < 22){
                    XposedHelpers.findAndHookMethod("com.android.internal.telephony.gsm.GSMPhone", this.classLoader, "getDeviceId", XC_MethodReplacement.returnConstant(HardwareInfo.SharedPref.getString("IMEI")));
                    XposedHelpers.findAndHookMethod("com.android.internal.telephony.PhoneProxy", this.classLoader, "getDeviceId", XC_MethodReplacement.returnConstant(HardwareInfo.SharedPref.getString("IMEI")));
                }
            }

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSimSerialNumber", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("simSerialNumber") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("simSerialNumber"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSubscriberId", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("IMSI") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("IMSI"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getLine1Number", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("Line1Number") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("Line1Number"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getNetworkOperatorName", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("NetworkOperatorName") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("NetworkOperatorName"));    // 网络类型名
                    }
                }
            });


            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getNetworkOperator", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("NetworkOperator") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("NetworkOperator"));    // 网络运营商类型
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getNetworkType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("NetworkType") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("NetworkType"));    //      网络类型
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getPhoneType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("PhoneType") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("PhoneType"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSimState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("SimState") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("SimState"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getNetworkCountryIso", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("NetworkCountryIso") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("NetworkCountryIso"));    // 国家iso代码
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSimCountryIso", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("SimCountryIso") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("SimCountryIso"));    // 手机卡国家
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSimOperator", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("SimOperator") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("SimOperator"));    // 运营商
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getSimOperatorName", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("SimOperatorName") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("SimOperatorName"));    // 运营商名字
                    }
                }
            });
            /**
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", this.classLoader, "getDeviceSoftwareVersion", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("deviceversion") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("deviceversion"));    // 返系统版本
                    }
                }
            });
             */
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }

    public void Wifi() {
        try {
            XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", this.classLoader, "getMacAddress", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("MacAddress") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("MacAddress"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", this.classLoader, "getSSID", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("SSID") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("SSID"));
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", this.classLoader, "getBSSID", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("BSSID") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getString("BSSID"));
                    }
                }
            });

            /**
            // 内网IP
            XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", this.classLoader, "getIpAddress", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if( HardwareInfo.SharedPref.has("ipAddress") ) {
                        super.afterHookedMethod(param);
                        param.setResult(HardwareInfo.SharedPref.getInt("ipAddress"));
                    }
                }
            });
            */
        } catch (Throwable e) {
            Log.e("xxx", "AndroidSerial error. stack:" + android.util.Log.getStackTraceString(e));
        }
    }
}
