package com.example.zlx.hardware;

import android.os.Environment;
import android.util.Log;

import com.example.zlx.mybase.MyFile;
import com.example.zlx.mybase.MyPath;
import java.io.DataOutputStream;
import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class XCpu {
    private ClassLoader classLoader = null;
    public XCpu(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        FakeCPUFile();
    }

    public void FakeCPUFile() {
        /** 通过重定向cpuinfo文件 改变其内容 */
        try {
            if( HardwareInfo.SharedPref.has("cpuinfo") ) {
                String filepath = MyPath.join(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "cpuinfo" );
                MyFile.writeAsString(filepath, HardwareInfo.SharedPref.getString("cpuinfo"));
                movefile(filepath);
            }else{
                Log.w("xxx", "has not cpuinfo in SharePref");
                return;
            }

            Log.w("xxx", "faking CPU:"+HardwareInfo.SharedPref.getString("cpuinfo"));
            XposedBridge.hookAllConstructors(File.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (param.args.length == 1) {
                        if ( param.args[0].equals("/proc/cpuinfo") || param.args[0].equals("/system/lib/arm/cpuinfo") ) {
                            param.args[0] = HardwareInfo.FAKE_CPUINFO;
                        }
                    } else if (param.args.length == 2 && !File.class.isInstance(param.args[0])) {
                        int i = 0;
                        String str = "";
                        while (i < 2) {
                            String stringBuilder;
                            if (param.args[i] != null) {
                                if ( param.args[i].equals("/proc/cpuinfo") || param.args[i].equals("/system/lib/arm/cpuinfo") ) {
                                    param.args[i] = HardwareInfo.FAKE_CPUINFO;
                                }
                                stringBuilder = new StringBuilder(String.valueOf(str)).append(param.args[i]).append(":").toString();
                            } else {
                                stringBuilder = str;
                            }
                            i++;
                            str = stringBuilder;
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod("java.lang.Runtime", this.classLoader, "exec", String[].class, String[].class, File.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (param.args.length == 1) {
                        if ( param.args[0].equals("/proc/cpuinfo") || param.args[0].equals("/system/lib/arm/cpuinfo") ) {
                            param.args[0] = HardwareInfo.FAKE_CPUINFO;
                        }
                    } else if (param.args.length == 2 && !File.class.isInstance(param.args[0])) {
                        int i = 0;
                        String str = "";
                        while (i < 2) {
                            String stringBuilder;
                            if (param.args[i] != null) {
                                if ( param.args[i].equals("/proc/cpuinfo") || param.args[i].equals("/system/lib/arm/cpuinfo") ) {
                                    param.args[i] = HardwareInfo.FAKE_CPUINFO;
                                }
                                stringBuilder = new StringBuilder(String.valueOf(str)).append(param.args[i]).append(":").toString();
                            } else {
                                stringBuilder = str;
                            }
                            i++;
                            str = stringBuilder;
                        }
                    }
                }
            });
            // 2.
            XposedBridge.hookMethod(XposedHelpers.findConstructorExact(ProcessBuilder.class, new Class[] { String[].class }), new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (param.args[0] != null) {
                        String[] strArr = (String[]) param.args[0];
                        String str = "";
                        for (String str2 : strArr) {
                            str = new StringBuilder(String.valueOf(str)).append(str2).append(":").toString();
                            if ( str2 == "/proc/cpuinfo" || str2 == "/system/lib/arm/cpuinfo" ) {
                                strArr[1] = HardwareInfo.FAKE_CPUINFO;
                            }
                        }
                        param.args[0] = strArr;
                    }
                }
            });
            // 3.
            XposedHelpers.findAndHookMethod("java.util.regex.Pattern", this.classLoader, "matcher", CharSequence.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (param.args.length == 1) {
                        if ( param.args[0].equals("/proc/cpuinfo") || param.args[0].equals("/system/lib/arm/cpuinfo") ) {
                            param.args[0] = HardwareInfo.FAKE_CPUINFO;
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e("xxx", "FakeCPUFile error. stack:%s"+android.util.Log.getStackTraceString(e));
        }
    }

    private static void movefile(String filepath) throws Exception {
        if( !new File(filepath).exists() ){
            Log.e("xxx", "move cpuinfo fail, file not exist, path:"+filepath);
            return;
        }
        Process process = null;
        DataOutputStream dataOutputStream = null;
        try {
            /**
            process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());
//            dataOutputStream.writeBytes("mkdir /sdcard/Test/\n");
//            dataOutputStream.writeBytes("chmod 777 /sdcard/Test/\n");
//            dataOutputStream.writeBytes( String.format("cp %s %s\n", filepath, HardwareInfo.CPUINFO_PATH) );
            dataOutputStream.writeBytes(String.format("chmod 444 %s\n", HardwareInfo.CPUINFO_PATH) );
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();
            process.waitFor();
             */
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (dataOutputStream != null) dataOutputStream.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
            }
        }
        File cpuinfo = new File(HardwareInfo.FAKE_CPUINFO);
        if ( !cpuinfo.exists()) {
            Log.e("xxx", "fake cpuinfo file not exist, filepath:" + cpuinfo.getAbsolutePath());
            throw new Exception();
        }
    }
}