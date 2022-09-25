package com.example.zlx.xposeapplication;

import android.content.Context;
import android.util.Log;

import com.elvishew.xlog.XLog;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookLoadPackage {
    public static String WECHAT_PACKAGE_NAME = "com.tencent.mm";
    public static String WECHAT_PROCESS_NAME = "com.tencent.mm";
    public static WechatHook wechathook = null;

    @Override
    public void handleLoadPackage(final LoadPackageParam loadPackageParam) throws Throwable {
        //note.
        if ( ! loadPackageParam.packageName.equals( Main.WECHAT_PACKAGE_NAME ) ) return;
        if ( ! loadPackageParam.processName.equals( Main.WECHAT_PROCESS_NAME ) ) return;

        try {
            // 开始 Hook
            wechathook = new WechatHook(loadPackageParam);
            wechathook.main();
            // 隐藏插件进程
            MonitorModule monitor = new MonitorModule();
            monitor.hide(loadPackageParam);
//            monitor.keepalived();
        } catch (Exception e) {
            Log.e(BuildConfig.TAG, String.format("[handleLoadPackage] %s", Log.getStackTraceString(e)));
        }
    }
}
//Context wechatContext = (Context) callMethod(callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread", new Object[0]), "getSystemContext", new Object[0]);
    /**
    public static Context applicationContext  = null; //applicationContext
    private XC_MethodHook.Unhook unhook_getApplicationContext = null;
    public static  Activity currentActivity  = null;
    public static  XC_MethodHook.Unhook unhook_newActivity = null;
    */

    /**
    //  https://forum.xda-developers.com/xposed/access-resources-module-t2805276
    //  IXposedHookZygoteInit, IXposedHookInitPackageResources
    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        String MODULE_PATH = startupParam.modulePath;
    }
    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
    }
    */
            /**
            if (Main.applicationContext == null) {
                Class<?> contextWrapper = findClass("android.content.ContextWrapper", loadPackageParam.classLoader);
                unhook_getApplicationContext = findAndHookMethod(contextWrapper, "getApplicationContext",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                Main.applicationContext = (Context) param.getResult();
                                if (unhook_getApplicationContext != null) {
                                    unhook_getApplicationContext.unhook();
                                }
                                Log.d("zzz", "getApplicationContext success");
                            }
                        });
            }
            if (Main.currentActivity == null) {
                Class<?> instrumentation = findClass("android.app.Instrumentation", loadPackageParam.classLoader);
                unhook_newActivity = findAndHookMethod(instrumentation, "newActivity", ClassLoader.class, String.class, Intent.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Main.currentActivity = (Activity) param.getResult();
                            }
                });
            }
            */
/**
    AssetManager assetManager = Resources.getSystem().getAssets();
    String[] files = assetManager.list();
    assetManager.open("client");
*/
/**
    assetManager = WechatClass.wechatContext.getAssets();
    inputStream = assetManager.open("client");
    if(inputStream != null) privateKey = readTextFile(inputStream);
    inputStream.close();
    Log.d("zzz", "privateKey3333:"+privateKey);
*/