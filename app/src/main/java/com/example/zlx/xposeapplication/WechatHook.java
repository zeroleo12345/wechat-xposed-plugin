package com.example.zlx.xposeapplication;

import android.app.Activity;
import android.app.Application;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.widget.Toast;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter2;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy;
import com.example.zlx.hardware.HardwareInfo;
import com.example.zlx.mybase.*;
import com.example.zlx.mynative.AuthArg;
import com.example.zlx.mynative.JNIUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

//import redis.clients.jedis.Jedis;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Text;

public class WechatHook {
    static BlockingQueue< List<String> > rpush_queue = new LinkedBlockingQueue<>(1000);
    static BlockingQueue< String > recv_queue = new LinkedBlockingQueue<>(1000);
    static TouchThread thread_touch;
    static HardwareInfo hw_info;
    static int last_send_time = -1;      // 上次rpush的时间戳, 单位秒, 使用int是为了线程安全!  也可以加关键字 volatile
    static int last_sync = -1;      // 上次微信同步心跳的时间戳, 单位秒
    private long login_time = -1;  // 成功登陆时间点
    private HashMap<String, String> room2memberlist = new HashMap<>();  //记录群成员memberlist是否有变动, 是否需要把更新成员信息给到后端
    private HashMap<String, String> room2displayname = new HashMap<>();  //记录群成员displayname是否有变动, 是否需要把更新成员信息给到后端
    private HashMap<String, String> room2roomowner = new HashMap<>();  //记录群成员roomowner是否有变动, 是否需要把更新成员信息给到后端
    private LinkedHashMap<Long, Boolean> msgSvrId_cache = new LinkedHashMap<Long, Boolean>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };
    private LinkedHashMap<Long, Boolean> done_msgSvrId_cache = new LinkedHashMap<Long, Boolean>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };
    private LinkedHashMap<String, String> wxid2headurl = new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };
    private LinkedHashMap<String, String> wxid2nickname = new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 100;
        }
    };
    private LinkedHashMap<String, String> wxid2v1_encryptUsername = new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 50;
        }
    };

    //public static ReadWriteLock rwl = new ReentrantReadWriteLock();
    //private HashMap<String, String> wxid2remark = new HashMap<>();
    static ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> wxid2rooms = new ConcurrentHashMap<>();  //记录哪个微信号需要更新alias, value为其所属群. 多线程读写, 需加锁
    static ConcurrentHashMap<String, Boolean> room_wxid2true = new ConcurrentHashMap<>(); //记录哪个成员需要被邀请入群. 多线程读写, 需加锁
    static ConcurrentHashMap<String, Boolean> wxid2addfriend = new ConcurrentHashMap<>(); //记录哪个wxid需要添加好友. 多线程读写, 需加锁
    static ConcurrentHashMap<String, LinkedList<String>> friendtest = new ConcurrentHashMap<>(); //记录哪个wxid需要做好友消息测试.
    static ConcurrentHashMap<String, Boolean> wxid2getcode = new ConcurrentHashMap<>();

    //unhook
    HashMap<String, String>  unhook_map = new HashMap<>();
    XC_MethodHook.Unhook unhook_loadClass = null;

    //
    XC_MethodHook.Unhook unhook_SyncService = null;

    // myself
    static String xLogDir = MyPath.join( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "/xlog"); //日志
    static String localTmpDir = MyPath.join( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "/tmp"); //临时目录
    static String remoteLogDir = "/MicroMsg2/log";
    static String remoteImageDir = "/MicroMsg/Image";
    static String localQrcodeDir = MyPath.join( Environment.getExternalStorageDirectory().getAbsolutePath(), "/tencent/MicroMsg/qrcode");
    static String remoteQrcodeDir = "/MicroMsg2/qrcode";
    static String localAttachDir = MyPath.join( Environment.getExternalStorageDirectory().getAbsolutePath(), "/tencent/MicroMsg/Download");
    static String remoteAttachDir = "/MicroMsg/Download";
    static String remoteVoiceDir = "/MicroMsg/Voice";
    static String remoteVideoDir = "/MicroMsg/Video";

    static boolean is_UI_launched = false;
    static boolean is_afterLogin = false;
    static String WxSend = null; //发送到apk端
    static String WxRecv = null;     //发送到python端
    static String ProcIdle = "ProcIdle";
    static String extStore = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
    static String sqlite_password = "";
    static int max_upload_voice_byte = 6000;

    static ConcurrentHashMap<String, String> _robot_info = new ConcurrentHashMap<>();
    static String get_robot_info(String key) throws NullPointerException{
        String value = WechatHook._robot_info.get(key);
        if( value == null){
            throw new NullPointerException(key + " is NULL");
        }else{
            return value;
        }
    }
    static void set_robot_info(String key, String value) {
        WechatHook._robot_info.put(key, value);
    }
    static boolean has_robot_info(String key) {
        return WechatHook._robot_info.containsKey(key);
    }

    WechatHook(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Exception{
        WechatClass._loader = loadPackageParam.classLoader;
        MyLog.init_xlog(WechatHook.xLogDir, BuildConfig.PROC_TYPE, BuildConfig.TAG, BuildConfig.LOG_LEVEL);

        XLog.d("appInfo.processName:%s, processName:%s, PID:%d", loadPackageParam.appInfo.processName, loadPackageParam.processName, android.os.Process.myPid());
        if( WechatClass.wechatContext == null) {
            //loadPackageParam.appInfo.uid
            Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
            WechatClass.wechatContext = (Context) callMethod(activityThread, "getSystemContext");
        }
        String versionName = WechatClass.wechatContext.getPackageManager().getPackageInfo(Main.WECHAT_PACKAGE_NAME, 0).versionName; // loadPackageParam.packageName
        Log.d(BuildConfig.TAG, String.format("Found wechat version:%s", versionName));

        if( TextUtils.isEmpty(VerifyThread.lib_dir) ) {
            VerifyThread.lib_dir = SystemUtil.getLibPath(BuildConfig.APPLICATION_ID);
            XLog.d("APPLICATION_ID:%s, lib_dir:%s", BuildConfig.APPLICATION_ID, VerifyThread.lib_dir); // APPLICATION_ID:com.example.zlx.wechatmanager.service, lib_dir:/data/app-lib/com.example.zlx.wechatmanager.service-1
            String sign = SystemUtil.getSign(WechatClass.wechatContext, BuildConfig.APPLICATION_ID);
            XLog.d("sign: %s", sign);
            JNIUtils.init_so(VerifyThread.lib_dir, sign);
        }

        if( !BuildConfig.cut ) {
            if (new File(HardwareInfo.hardwarePath).exists()) {
                JSONObject jsonObj = new JSONObject(MyFile.readAsString(HardwareInfo.hardwarePath));
                HardwareInfo.SharedPref = jsonObj.getJSONObject("hwinfo");
            } else {
                HardwareInfo.SharedPref = new JSONObject();
            }
            Log.i(BuildConfig.TAG, "hardware.json: "+HardwareInfo.SharedPref);
            //HardwareInfo.debug();     // note. 使用逍遥模拟器-door-原生机器信息, 用于逍遥模拟器-免验证测试-插件Hook及其信息
            HardwareInfo.replace(WechatClass._loader);
        }
        WechatHook.hw_info = new HardwareInfo(WechatClass.wechatContext);
        try {
            UserConfig.init_from_config(hw_info.gateway);
        }catch (Exception e){
            XLog.e("UserConfig.init error, gateway: %d", hw_info.gateway);
            return;
        }

        // note 新建线程, 但未start
        WechatHook.thread_touch = new TouchThread(WechatHook.hw_info.gateway);
        // 主工作线程
        WechatHook.thread_touch.thread_action = new WechatAction();
    }


    public void main() {
        /**
         * 登陆前  钩子
         */
        try {
            XLog.i("main, Hook Before Auth");
            WechatClass.findClass_before_auth(WechatClass._loader);
            hookDelay(WechatClass._loader);
        }catch (Throwable e) {
            XLog.e("main error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    static boolean already_hooked = false;
    void hookAfterAuth(String robot_wxid, String alias, String nickname, String qq, String email) {
        /**
         * 登陆后  钩子
         */
        try{
            XLog.i("Hook After Auth. WechatHook.already_hooked: %b", WechatHook.already_hooked);
            if( WechatHook.already_hooked ) {
                return;
            }
            WechatHook.already_hooked = true;

            for ( String class_name: unhook_map.keySet() ) {
                String function_name = unhook_map.get(class_name);
                XLog.e("function: %s, class_name: %s, not found !", function_name, class_name);
            }

            String appversion = WechatClass.wechatContext.getPackageManager().getPackageInfo(Main.WECHAT_PACKAGE_NAME, 0).versionName;
            XLog.d("app version:%s", appversion);

            // note 设置wxid
            WechatHook.set_robot_info("robot_wxid", robot_wxid);    // set时, 设置为 robot_wxid, 而get时, 使用 MySync.g_robot_wxid
            WechatHook.set_robot_info("robot_alias", alias);
            WechatHook.set_robot_info("robot_nickname", nickname);
            WechatHook.set_robot_info("robot_qq", qq);
            WechatHook.set_robot_info("robot_email", email);
            WechatHook.set_robot_info("robot_version", appversion);

            // note 重命名WxRecv
            WechatHook.WxRecv = robot_wxid + "_WxRecv";
            WechatHook.WxSend = robot_wxid + "_WxSend";
            MyLog.setDefaultPrefix( String.format("%s_%s", BuildConfig.PROC_TYPE, robot_wxid) );   //修改日志前缀, 加上机器人wxid

            create_dirs();

            // note: 登陆后, 钩住数据库和debug的函数
            hookDbInsert(WechatClass._loader);
            hookDbUpdate(WechatClass._loader);
            hookDbDelete(WechatClass._loader);
            // Debug
            hookDebug(WechatClass._loader);
            hookSendButton(WechatClass._loader);

            // note 初始化findClass
            WechatClass.findClass_after_auth(WechatClass._loader);

            // note 登陆后, 钩住所有需要的函数
            hookGetmsgimgResponse(WechatClass._loader);
            hookSyncChatroomMember(WechatClass._loader);
            hookInsertContact(WechatClass._loader);
            hookGetQrcodeResponse(WechatClass._loader);
            hookTaskResponse(WechatClass._loader);
            hookSearchcontactResponse(WechatClass._loader);
            hookVerifyResponse(WechatClass._loader);
            hookGet8keyResponse(WechatClass._loader);
            hookAddChatroomMemberResponse(WechatClass._loader);
            //
            hookDownloadAttachResponse(WechatClass._loader);
            hookDownloadOrUploadVideoResponse(WechatClass._loader);
            hookUploadVoiceResponse(WechatClass._loader);
            //if(BuildConfig.DEBUG) hookUploadImgResponse(WechatClass._loader);     // note 调用会崩溃!!

            hookReceivewxhbResponse(WechatClass._loader);
            hookOpenwxhbResponse(WechatClass._loader);
            hookQrydetailwxhbResponse(WechatClass._loader);
            // Web版微信
            hookWXLoginUICreate(WechatClass._loader);
            hookWXLoginResponse(WechatClass._loader);
            // 发红包
            hookRequestwxhbResponse(WechatClass._loader);
            hookBindQueryResponse(WechatClass._loader);
            /** Call */
            callGetQrcode(WechatClass._loader);
            hook_keep_onUploadSuccessed(WechatClass._loader);

            // TODO 放弃的更新
            //_hookNotification(WechatClass._loader);

            // 找出漏更新的点
            find_out_exception();
            /** 启动线程 */
            this.login_time = System.currentTimeMillis();
            WechatHook.thread_touch.start();        // 启动更新pid线程 note: 该线程维护所有线程
        } catch (Throwable e) {
            XLog.e("hookAfterAuth error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    private void create_dirs() {
        // note 建立目录
        {
            /** 本地xlog日志目录 */
            File xlog_dir = new File(WechatHook.xLogDir);
            if (!xlog_dir.exists()) {
                XLog.i("xlog mkdirs: ret:" + xlog_dir.mkdirs());
            } else {
                XLog.i("xlog dir exists, %s", xlog_dir.getAbsolutePath());
            }
        }
        {
            WechatHook.remoteImageDir = MyPath.join(WechatHook.remoteImageDir, WechatHook.get_robot_info(MySync.g_robot_wxid));
            /** 本地二维码目录 */
            File qrcode_dir = new File(WechatHook.localQrcodeDir);
            if (!qrcode_dir.exists()) {
                XLog.i("qrcode mkdirs: ret:" + qrcode_dir.mkdirs());
            } else {
                XLog.i("qrcode dir exists, %s", qrcode_dir.getAbsolutePath());
            }
        }
        {
            /** 本地附件目录 */
            File attach_dir = new File(WechatHook.localAttachDir);
            if (!attach_dir.exists()) {
                XLog.i("attach mkdirs: ret:" + attach_dir.mkdirs());
            } else {
                XLog.i("attach dir exists, %s", attach_dir.getAbsolutePath());
            }
        }
        {
            /** 图片和视频临时目录 */
            File tmp_dir = new File(WechatHook.localTmpDir);
            if (!tmp_dir.exists()) {
                XLog.i("tmp mkdirs: ret:" + tmp_dir.mkdirs());
            } else {
                XLog.i("tmp dir exists, %s", tmp_dir.getAbsolutePath());
            }
        }
    }


    private void find_out_exception(){
        /** note 用于测试更新版本代码时, 找出漏更新的地方
         */
        if( ! BuildConfig.DEBUG ) return;
        new ScanThread(XLog.tag(BuildConfig.TAG2).build()).work();  // 调试scan线程
        String attachment_dir = WechatClass.getAttachmentDir();
        XLog.d("attachment_dir: %s", attachment_dir);
    }


    public void hook_keep_onUploadSuccessed(final ClassLoader loader) {
        findAndHookMethod(WechatClass.get("com/tencent/mm/modelcdntran/b"), "keep_onUploadSuccessed", String.class, WechatClass.get("com/tencent/mm/modelcdntran/keep_SceneResult"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String p1 = (String) param.args[0];
                    Object p2 = param.args[1];
                    XLog.d("hook keep_onUploadSuccessed, p1:%s, p2:%s", p1, p2);
                    //
                    //setObjectField(p2, "field_toUser", "weixin12345");
                } catch (Throwable e) {
                    XLog.e("hook_keep_onUploadSuccessed error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    public void hookManualAuthResponse(final ClassLoader loader) {
        /** TODO: 仅识别到新手机登陆时, 才会触发!  没有被加载~~
         * 搜索:      "summerauth errType:%d, errCode:%d, errMsg:%s unifyAuthResp:%s, unifyFlag:%d, auth:%s, acct:%s, network:%s"
         * com/tencent/mm/modelsimple/u     =?      com/tencent/mm/modelsimple/q
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/modelsimple/q"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    int p1 = (int) param.args[0];
                    int p2 = (int) param.args[1];
                    int p3 = (int) param.args[2];
                    String p4 = (String) param.args[3];
                    Object p5 = param.args[4];
                    XLog.d("hook ManualAuth Response, p1:%d, p2:%d, p3:%d, p4:%s", p1, p2, p3, p4);
                    //
                    if ((p1 == 0 && p2 == 4 && p3 == -205) || (p1 == 1 && p2 == 4 && p3 == -205) || p4.contains("你在新手机登录微信，我们需要验证你的身份")) {
                        /** 参考 hardware.png
                         *  当前文件中搜索: "key_auth_update_version", 点击进入 com/tencent/mm/s/ap,  查找到 hxa, sJP, txk, jSr
                         invoke-static {}, Lcom/tencent/mm/s/ap;->yI()Landroid/content/SharedPreferences;
                         move-result-object v3
                         .line 119
                         const-string/jumbo v4, "key_auth_update_version"
                         ======================================================>
                         invoke-static {}, Lcom/tencent/mm/model/av;->Ib()Landroid/content/SharedPreferences;
                         move-result-object v3
                         .line 130
                         const-string/jumbo v4, "key_auth_update_version"
                         */
                        XLog.e("maybe new device login!");
                        Object hxa = getObjectField(p5, "dBN");
                        Object sJP = getObjectField(hxa, "qWk");
                        Object txk = getObjectField(sJP, "rUY");
                        String alias_qq_email = (String) getObjectField(txk, "hbL");
                        XLog.d("hook ManualAuth Response, robot_user %s", alias_qq_email);

                        WechatHook.set_robot_info("robot_user", alias_qq_email);
                        VerifyThread.post_login_new_device();
                    }
                } catch (Throwable e) {
                    XLog.e("hookManualAuthResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void _hookDeviceInfo(final ClassLoader loader) {
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ClassLoader cl = ((Context)param.args[0]).getClassLoader();
                try{
                    /** 搜索 "<softtype><lctmoc>"
                     *  com/tencent/mm/plugin/normsg/b      =>      com/tencent/mm/plugin/normsg/b
                     *  .method public final pY(I)Ljava/lang/String;        =>      .method public final ub(I)Ljava/lang/String;
                     */
                    findAndHookMethod(WechatClass.load("com/tencent/mm/plugin/normsg/b", cl), "ub", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
//                                        if( HardwareInfo.SharedPref.has("softtype") ){
//                                            param.setResult(HardwareInfo.SharedPref.getString("softtype"));
//                                            XLog.e("has softtype, fake:%s", HardwareInfo.SharedPref.getString("softtype"));
//                                            return;
//                                        }
                                int p1 = (int) param.args[0];
                                HardwareInfo.softtype = (String) param.getResult();
                                XLog.d("HardwareInfo.softtype:%s, p1:%d", HardwareInfo.softtype, p1);
                            } catch (Throwable e) {
                                XLog.e("_hookDeviceInfo error. stack:%s", android.util.Log.getStackTraceString(e));
                            }
                        }
                    });
                }catch (Exception e){}
            }
        });

        /** 搜索 "writeConfigToLocalFile, path: %s, info:%s"
         * com/tencent/mm/storage/bc        =>      com/tencent/mm/storage/bn
         * .method public static bLd()Ljava/lang/String;        =>      .method public static cmZ()Ljava/lang/String;
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/storage/bn"), "cmZ", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
//                            if( HardwareInfo.SharedPref.has("deviceinfo") ){
//                                param.setResult(HardwareInfo.SharedPref.getString("deviceinfo"));
//                                XLog.e("has deviceinfo, fake:%s", HardwareInfo.SharedPref.getString("deviceinfo"));
//                                return;
//                            }
                    //HardwareInfo.deviceinfo = (String) param.getResult();
                    //XLog.d("HardwareInfo.deviceinfo:%s", HardwareInfo.deviceinfo);
//                            // note. 获取hook硬件信息前的值
//                            HardwareInfo.info(WechatClass.wechatContext, Main.WECHAT_PACKAGE_NAME);
//                            if( new File(HardwareInfo.hardwarePath).exists() ){
//                                JSONObject jsonObj = new JSONObject(MyFile.readAsString(HardwareInfo.hardwarePath));
//                                //XLog.d("has hardware.json, jsonObj:%s", jsonObj);
//                                HardwareInfo.SharedPref = jsonObj.getJSONObject("hwinfo");
//                                //XLog.i("has hardware.json, hwinfo:%s", HardwareInfo.SharedPref);
//                                /**
//                                 if( !TextUtils.equals( HardwareInfo.SharedPref.getString("apkVersion"), HardwareInfo.apkVersion) ){// apk版本不一致
//                                 XLog.e("apkVersion mismatch, hook:%s, install:%s", HardwareInfo.SharedPref.getString("apkVersion"), HardwareInfo.apkVersion;
//                                 return;
//                                 }
//                                 */
//                            }
                    //HardwareInfo.info(WechatClass.wechatContext);
                } catch (Throwable e) {
                    XLog.e("_hookDeviceInfo error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** _hookDeviceInfo 结束 */


    public void hookHandleAuthResponse(final ClassLoader loader) {
        /**
         * 搜索: "summerauth updateProfile acctsect BindUin:%s, Status:%d, UserName:%s, NickName:%s, BindEmail:%s, BindMobile:%s, Alias:%s, PluginFlag:%d, RegType:%d, DeviceInfoXml:%s, SafeDevice:%d, OfficialUserName:%s, OfficialUserName:%s PushMailStatus:%d, FSURL:%s"
         * .method public static a(Lcom/tencent/mm/protocal/c/bdv;Z)V       =>      .method public static a(Lcom/tencent/mm/protocal/c/bup;Z)V
         */
        XLog.i("hook HandleAuthResponse");
        findAndHookMethod(WechatClass.get("com/tencent/mm/model/z"), "a", "com.tencent.mm.protocal.c.bup", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object p1 = param.args[0];
                    boolean p2 = (boolean)param.args[1];
                    XLog.d("hook Handle AuthResponse, p2:%b", p2);
                    /** 搜索 "Not all required fields were included: BaseResponse"
                     */
                    Object obj_eh = getObjectField(p1, "six");
                    Object obj_atu = getObjectField(obj_eh, "rgv");
                    String tips = (String)getObjectField(obj_atu, "siM");
                    if( !TextUtils.equals(tips, "Everything is ok") ){
                        XLog.e("hookHandleAuthResponse != Everything is ok");
                        return;
                    }
                    /**
                     * note 参考: E:\cygwin64\home\zlx\android-study\analyse6510\登陆鉴权返回
                     */
                    // 获取wxid, alias, nickname
                    Object obj_am = getObjectField(p1, "srO");
                    MySync.alias = (String)getObjectField(obj_am, "eJM");
                    MySync.wxid = (String)getObjectField(obj_am, "hbL");
                    MySync.nickname = (String)getObjectField(obj_am, "hcS");
                    if( MySync.alias == null ) MySync.alias = "";
                    if( getObjectField(obj_am, "raz") != null ) MySync.email = (String)getObjectField(obj_am, "raz");
                    if( getObjectField(obj_am, "ray") != null ) MySync.qq = (int)getObjectField(obj_am, "ray");

                    // FIXME: 会出现: HandleAuthResponse, wxid:null, alias:, nickname:null, email:, qq:0
                    XLog.i("HandleAuthResponse, wxid:%s, alias:%s, nickname:%s, email:%s, qq:%d", MySync.wxid, MySync.alias, MySync.nickname, MySync.email, MySync.qq);
                    // 最后: 启动Action线程
                    if(!is_afterLogin) {
                        is_afterLogin = true;

                        if( BuildConfig.cut ){
                            // note 需验证
                            WechatHook.thread_touch.new_verify_thread();
                        }else{
                            // note 不需验证
                            MySync.g_robot_wxid = "robot_wxid";
                            MySync.currentTimeMillis = System.currentTimeMillis();
                        }

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    int count = 0;
                                    while(true) {
                                        count++;
                                        if( MySync.currentTimeMillis == 0) {
                                            Thread.sleep(300); // 等待Http返回 g_robot_wxid 才开始 (不需验证的情况下, 会立即进行)
                                            if( count >= 30 ){ break; }
                                            continue;
                                        }
                                        if( TextUtils.isEmpty(MySync.wxid) ){
                                            // FIXME
                                            System.exit(1);
                                        }
                                        // note: 登陆后钩子
                                        hookAfterAuth(MySync.wxid, MySync.alias, MySync.nickname, String.valueOf(MySync.qq), MySync.email);
                                        break;
                                    }
                                } catch (Exception e) {
                                    Log.e(BuildConfig.TAG, "post_authentic_simple error. stack:" + android.util.Log.getStackTraceString(e));
                                }
                            }
                        }).start(); //启动线程
                    }
                } catch (Throwable e) {
                    XLog.e("hookHandleAuthResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookHandleAuthResponse 结束 */


    public void hookDebug(final ClassLoader loader) {
        if( ! BuildConfig.DEBUG )
            return;
        //hookSetSalt(loader);

//        findAndHookMethod(WechatClass.get("android/widget/Toast"), "show", new XC_MethodHook() {  //挂钩非构造函数, 且函数无参数
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                // note: Toast.makeText
//                try {
//                    //stackOverflow("eee");
//                } catch (Throwable e) {
//                    XLog.e("Toast error. stack:%s", android.util.Log.getStackTraceString(e));
//                }
//            }
//        });


        // note: 上传附件任务类
        findAndHookConstructor(WechatClass.get("com/tencent/mm/pluginsdk/model/app/al", "/cgi-bin/micromsg-bin/uploadappattach"), long.class, String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    long p1 = (long)param.args[0];
                    String p2 = (String)param.args[1];
                    String p3 = (String)param.args[2];
                    XLog.w("uploadappattach: p1: %d, p2: %s, p3: %s", p1, p2, p3);
                } catch (Throwable e) {
                    XLog.e("uploadappattach error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });


        // note: 发送app卡片任务类1
        findAndHookConstructor(WechatClass.get("com/tencent/mm/pluginsdk/model/app/ai", "/cgi-bin/micromsg-bin/sendappmsg"), long.class, String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    long p1 = (long)param.args[0];
                    String p2 = (String)param.args[1];
                    String p3 = (String)param.args[2];
                    XLog.w("sendappmsg1: p1: %d, p2: %s, p3: %s", p1, p2, p3);
                } catch (Throwable e) {
                    XLog.e("sendappmsg error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });

        // note: 发送app卡片任务类2
        findAndHookConstructor(WechatClass.get("com/tencent/mm/pluginsdk/model/app/aj", "/cgi-bin/micromsg-bin/sendappmsg"),
                long.class,
                boolean.class,
                WechatClass.get("com/tencent/mm/modelcdntran/keep_SceneResult"),
                WechatClass.get("com/tencent/mm/pluginsdk/model/app/aj$a"),
                String.class,
                WechatClass.get("com/tencent/mm/pluginsdk/model/app/b"),
                new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    long msgid = (long)param.args[0];      // message 表内的 msgid
                    boolean p2 = (boolean)param.args[1];       // true
                    Object keep_SceneResult = param.args[2];          // id:aupattach_9e6a379515fa4d3a_1536977756145_31 file:30590201000452305002010002043c8764ba02033d14b9020476fd03b702045b9c556f042b6175706174746163685f643865343531646566346664366161315f313533363937313731393838305f32310204010800050201000400 filelen:1631368 midlen:0 thlen:0 transInfo:0,5,[::ffff:183.3.229.89]:443|,1,aupattach_9e6a379515fa4d3a_1536977756145_31,0,1631376,17,1,9103502,9103486,3 retCode:0 toUser:weixin12345 arg:null videoFileId: argInfo:null hitcache:3 needsend:true msgid:0 convert2baseline:false thumbUrl: fileUrl: filemd5:8d09a067b0c2b92f61a3cb4e802082f0 thumbfilemd5:,mp4identifymd5:, exist_whencheck[true], aesKey[], crc[2011366671], safecdn:true
                    Object p4 = param.args[3];          //
                    String p5 = (String) param.args[4];     // null
                    Object p6 = param.args[5];          // appattach 表记录
                    XLog.d("sendappmsg2: msgid: %d, p2: %b, keep_SceneResult: %s, p4: %s, p5: %s, p6: %s", msgid, p2, keep_SceneResult, p4, p5, p6);
                } catch (Throwable e) {
                    XLog.e("sendappmsg error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });

        /**
        findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase", loader, "replace", String.class, String.class, ContentValues.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            String table = (String)param.args[0];
                            String p2 = (String)param.args[1];
                            ContentValues p3 = (ContentValues)param.args[2];
                            XLog.d("DbOperation.replace table: %s, p2:%s, cv:%s", table, p2, p3);
                            if( !is_afterLogin ){
                                return;
                            }
                            switch(table) {
                                case "chatroom":{
                                    stackOverflow("eee");
                                }
                                break;
                            }
                        } catch (Throwable e) {
                            XLog.e("DbOperation replace error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );
        findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase", loader, "execSQL", String.class, Object[].class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            String p1 = (String)param.args[0];
                            XLog.d("DbOperation.execSQL p1:%s", p1);
                            if( !is_afterLogin ){
                                return;
                            }
                            switch(p1) {
                            }
                        } catch (Throwable e) {
                            XLog.e("DbOperation delete error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );
        findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase", loader, "executeSql", String.class, Object[].class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            String p1 = (String)param.args[0];
                            XLog.d("DbOperation.executeSql p1:%s", p1);
                            if( !is_afterLogin ){
                                return;
                            }
                            switch(p1) {
                            }
                        } catch (Throwable e) {
                            XLog.e("DbOperation delete error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );
        */

        /**
        //挂钩构造函数, 注意是否需要参数!
        findAndHookConstructor(WechatClass.get("com/tencent/mm/modelvoice/f", "uploadvoice"), String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String filename = (String)param.args[0];
                    XLog.i("init object uploadvoice, filename:%s", filename);
                    if(BuildConfig.DEBUG) stackOverflow("zzz");
                } catch (Throwable e) {
                    XLog.e("[init com/tencent/mm/modelvoice/f] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
         */

        /*
        findAndHookConstructor(WechatClass.get("com/tencent/mm/modelvoice/f$1"), "com.tencent.mm.modelvoice.f", //挂钩构造函数, 注意是否需要参数!
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            stackOverflow("zzz");
                        } catch (Throwable e) {
                            XLog.e("[init com/tencent/mm/modelvoice/f$1] %s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );

        findAndHookConstructor("com.tencent.mm.network.ae", loader, //挂钩构造函数, 注意是否需要参数!
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            WechatClass.test_handler = param.args[0];
                            XLog.i("hookDebug, test_handler:%s", WechatClass.test_handler);
                        } catch (Throwable e) {
                            XLog.e("[hookDebug] %s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );
        */
    }   /** hookDebug 结束 */


    void hookInitUIObject(final ClassLoader loader) {
        /**
         findAndHookMethod("com.tencent.mm.ui.LauncherUI", loader, "onPause",
         new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        WechatHook.launcherUIActivity = (Activity) param.thisObject;
        XLog.i("onPause launcherUIActivity");
        }
        }
         );
         findAndHookMethod("com.tencent.mm.ui.LauncherUI", loader, "onResume",
         new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        WechatHook.launcherUIActivity = (Activity) param.thisObject;
        XLog.i("onResume launcherUIActivity");
        }
        }
         );
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/ui/LauncherUI"), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Activity launcherUIActivity = (Activity) param.thisObject;
                    XLog.i("hookUIObject. launcherUIActivity:%s", launcherUIActivity);
                    launcherUIActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(2000);
                            } catch (Exception e) {
                            }
                            if( WechatClass.currentActivity == null){
                                XLog.e("current Activity null !");
                                return;
                            }
                            Toast.makeText(WechatClass.wechatContext, "加载插件成功!", Toast.LENGTH_LONG).show();
                        }
                    });
                    /**
                     Intent intent = activity.getIntent();
                     if (intent != null) {
                     XLog.i("intent is null");
                     return;
                     }
                     String className = intent.getComponent().getClassName();
                     if ( !TextUtils.isEmpty(className) && className.equals("com.tencent.mm.ui.LauncherUI") && intent.hasExtra("donate")) {
                     Intent donateIntent = new Intent();
                     donateIntent.setClassName(activity, "com.tencent.mm.plugin.remittance.ui.RemittanceUI");
                     donateIntent.putExtra("scene", 1);
                     donateIntent.putExtra("pay_scene", 32);
                     donateIntent.putExtra("scan_remittance_id", "011259012001125901201468688368254");
                     donateIntent.putExtra("fee", 10.0d);
                     donateIntent.putExtra("pay_channel", 12);
                     donateIntent.putExtra("receiver_name", "yang_xiongwei");
                     donateIntent.removeExtra("donate");
                     activity.startActivity(donateIntent);
                     activity.finish();
                     }
                     */
                } catch (Throwable e) {
                    XLog.e("[hookUIObject] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookUIObject 结束 */


    void hookNewActivity(final ClassLoader loader) {
        hookAllMethods(XposedHelpers.findClass("android.app.Instrumentation", loader), "newActivity", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    Activity activity = (Activity) param.getResult();
                    String activity_name = activity.getClass().getName();
                    XLog.d("current activity:%s", activity_name);

                    if ( WechatHook.is_UI_launched == false && TextUtils.equals(activity_name, "com.tencent.mm.ui.LauncherUI") ){
                        hookInitUIObject(loader);
                    }
                    WechatClass.currentActivity = activity;
                } catch (Throwable e) {
                    XLog.e("[hookUIObject] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookInitDbObject(final ClassLoader loader) {
        /** 搜索: "-vfslo1"
         .method public static q(Ljava/lang/String;Ljava/lang/String;Z)Lcom/tencent/mm/bh/e;        =>      .method public static s(Ljava/lang/String;Ljava/lang/String;Z)Lcom/tencent/mm/bt/f;
         跳到该类 init 函数, 搜索:
         iput-object v0, p0, Lcom/tencent/mm/bh/e;->usT:Lcom/tencent/wcdb/database/SQLiteDatabase;       =>      iput-object v0, p0, Lcom/tencent/mm/bt/f;->tdt:Lcom/tencent/wcdb/database/SQLiteDatabase;
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/bt/f"), "s", String.class, String.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String p1 = (String) param.args[0];
                    String db_filepath = p1;
                    Object db_handler = param.getResult();
                    XLog.d("hookDbObject, db_filepath:%s, db_handler:%s", db_filepath, db_handler);
                    if (db_filepath.endsWith("EnMicroMsg.db")) {    //  param.thisObject != null ||
                        if (WechatClass.EnMicroMsg != null)
                            return;   //note: 当第一次启动的时候, 可能会卡死!!
                        Object obj_SQLiteDatabase = getObjectField(db_handler, "tdt");
                        WechatClass.EnMicroMsg = new Db(obj_SQLiteDatabase);   // note SQLiteDatabase类
                        WechatHook.sqlite_password = (String) param.args[1];
                        XLog.i("sqlite password:%s", WechatHook.sqlite_password);
                    } else if (db_filepath.endsWith("enFavorite.db")) {
                        if (WechatClass.enFavorite != null) return;
                        Object obj_SQLiteDatabase = getObjectField(db_handler, "tdt");
                        WechatClass.enFavorite = new Db(obj_SQLiteDatabase);   // note SQLiteDatabase类
                    }
                } catch (Throwable e) {
                    XLog.e("[hookDbObject] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
//        /**
//         // cd /home/zlx/unpack_weixin6311/weixin/smali/com/tencent/mm/storage; grep "CREATE INDEX IF NOT EXISTS serverChatRoomUserIndex ON" *
//         // cd /home/zlx/unpack_weixin6311/weixin/smali/com/tencent/mm/storage
//         Class = "com.tencent.mm.storage.f";        =>      "com.tencent.mm.storage.r"
//         Params = "com.tencent.mm.sdk.h.d";         =>      "com.tencent.mm.sdk.e.e"        设置断点查看密码: p1->khB -> buL
//         */
//        findAndHookConstructor("com.tencent.mm.storage.r", loader, "com.tencent.mm.sdk.e.e", //挂钩构造函数, 注意是否需要参数!
//                new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        try {
//                            WechatClass.db_handler = param.args[0];
//                            XLog.i("hookDbObject, db_handler:%s", WechatClass.db_handler);
//
//                            String password = (String) getObjectField(getObjectField(WechatClass.db_handler, "uti"), "arH");
//                            XLog.i("sqlite password:%s", password);  // 例如: "3ba1ff2"
//                        } catch (Throwable e) {
//                            XLog.e("[hookDbObject] %s", android.util.Log.getStackTraceString(e));
//                        }
//                    }
//                }
//        );
    }


    public void hookInitMtimerObject(final ClassLoader loader) {
        /** 搜索:     "pusherTry onTimerExpired tryStartNetscene"         667
         * com/tencent/mm/ac/c$1      =>      com/tencent/mm/ai/c$1
         * .method constructor <init>(Lcom/tencent/mm/ac/c;)V     =>      .method constructor <init>(Lcom/tencent/mm/ai/c;)V
         */
        //Hook  MtimerHandler
        //挂钩构造函数, 注意是否需要参数!
        findAndHookConstructor(WechatClass.get("com/tencent/mm/ai/c$1"), WechatClass.get("com/tencent/mm/ai/c"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    //MicroMsg.GetContactService
                    WechatClass.mtimer_handler = param.args[0];
                    XLog.i("hookDbObject, mtimer_handler: %s", WechatClass.mtimer_handler);
                } catch (Throwable e) {
                    XLog.e("[hookMtimerHandler] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    public void hookInitVoiceInfoObject(final ClassLoader loader) {
        /** 搜索: "info.getLastModifyTime()  "        2个结果中向上滚动寻找含有: "MicroMsg.SceneVoiceService"
         com/tencent/mm/c/b/i       =>      com/tencent/mm/e/b/i         // note 该类是: MicroMsg.SceneVoiceService
         ////   .method public final pl()V     =>      .method static synthetic h(Lcom/tencent/mm/e/b/i;)V
         */
        // note Hook voiceinfo Loop: SELECT FileName, User, MsgId, NetOffset, FileNowSize, TotalLen, Status, CreateTime, LastModifyTime, ClientId, VoiceLength, MsgLocalId, Human, reserved1, reserved2, MsgSource, MsgFlag, MsgSeq FROM voiceinfo WHERE FileName= ?
        //挂钩构造函数, 注意是否需要参数!
        findAndHookConstructor(WechatClass.get("com/tencent/mm/e/b/i"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    WechatClass.voiceinfo_handler = param.thisObject;
                    XLog.i("hookDbObject, voiceinfo_handler:%s", WechatClass.voiceinfo_handler);
                } catch (Throwable e) {
                    XLog.e("[hook voiceinfo Loop] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }



    public void hookInitVideoInfoObject(final ClassLoader loader) {
        /** 搜索: "sceneUp should null"
         com/tencent/mm/modelvideo/x$a      =>      com/tencent/mm/modelvideo/x$a
         */
        //Hook videoinfo2 Loop of: select videoinfo2.filename,videoinfo2.clientid,videoinfo2.msgsvrid,videoinfo2.netoffset,videoinfo2.filenowsize,videoinfo2.totallen,videoinfo2.thumbnetoffset,videoinfo2.thumblen,videoinfo2.status,videoinfo2.createtime,videoinfo2.lastmodifytime,videoinfo2.downloadtime,videoinfo2.videolength,videoinfo2.msglocalid,videoinfo2.nettimes,videoinfo2.cameratype,videoinfo2.user,videoinfo2.human,videoinfo2.reserved1,videoinfo2.reserved2,videoinfo2.reserved3,videoinfo2.reserved4,videoinfo2.videofuncflag,videoinfo2.masssendid,videoinfo2.masssendlist,videoinfo2.videomd5,videoinfo2.streamvideo from videoinfo2
        //挂钩构造函数, 注意是否需要参数!
        findAndHookConstructor(WechatClass.get("com/tencent/mm/modelvideo/x$a"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    // MicroMsg.VideoService, VideoService_runThread
                    WechatClass.videoinfo2_handler = param.thisObject;
                    XLog.i("hook constructor videoinfo2_handler:%s", WechatClass.videoinfo2_handler);
                } catch (Throwable e) {
                    XLog.e("[hook videoinfo2 Loop] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });

        /** 搜索: "select oplog2.id,oplog2.inserTime,oplog2.cmdId,oplog2.buffer,oplog2.reserved1,oplog2.reserved2,oplog2.reserved3,oplog2.reserved4 from oplog2  order by inserTime asc limit"
         com/tencent/mm/ag/c$1      =>      com/tencent/mm/al/r$1
         com/tencent/mm/ag/c     =>      com/tencent/mm/al/r
         */
//        //Hook oplog2 Loop: select oplog2.id,oplog2.inserTime,oplog2.cmdId,oplog2.buffer,oplog2.reserved1,oplog2.reserved2,oplog2.reserved3,oplog2.reserved4 from oplog2  order by inserTime asc limit ?
//        findAndHookConstructor("com.tencent.mm.al.r$1", loader, "com.tencent.mm.al.r",//挂钩构造函数, 注意是否需要参数!
//                new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                try {
//                    WechatClass.oplog2_handler = param.thisObject;
//                    XLog.i("hookDbObject, oplog2_handler:%s", WechatClass.oplog2_handler);
//                } catch (Throwable e) {
//                    XLog.e("[hook oplog2 Loop] %s", android.util.Log.getStackTraceString(e));
//                }
//            }
//        });
        /** end of hookDbObject */
    }


    public void hookInitImgInfo2Object(final ClassLoader loader) {
        /** 搜索:
         com/tencent/mm/ak/i
         */
        //Hook ImgInfo2 Loop of: select * FROM ImgInfo2 WHERE iscomplete= 0 AND totalLen != 0
        //挂钩构造函数, 注意是否需要参数!
        findAndHookConstructor(WechatClass.get("com/tencent/mm/ak/i"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    WechatClass.ImgInfo2_handler = param.thisObject;
                    XLog.i("hook constructor ImgInfo2_handler:%s", WechatClass.ImgInfo2_handler);
                } catch (Throwable e) {
                    XLog.e("[hook ImgInfo2 Loop] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void _hookNotification(final ClassLoader loader) {
        /** APP 推送通知
         com/tencent/mm/booter/notification/b$1        =>      com/tencent/mm/booter/notification/b$1
         .method public final handleMessage(Landroid/os/Message;)V      =>      .method public final handleMessage(Landroid/os/Message;)V
         */
        //挂钩非构造函数, 且函数有1个参数
        findAndHookMethod(WechatClass.get("com/tencent/mm/booter/notification/b$1"), "handleMessage", Message.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Message message = (Message) param.args[0];
                int type = message.getData().getInt("notification.show.message.type");
                String talker = message.getData().getString("notification.show.talker");
                String content = message.getData().getString("notification.show.message.content");
                XLog.d("hookNotification, type:%d, talker:%s, content:%s", type, talker, content);
                switch(talker){ //过滤新闻消息, 等
                    case "newsapp":
                        return;
                }
                if (!(type == 1 || type == 3 || type == 34 || type == 47 || type == 62)) return;

                Cursor cursor = WechatClass._getLastMsg(talker);
                try {
                    if (cursor == null){
                        XLog.e("_getLastMsg failed ret: null");
                        return;
                    }
                    if(!cursor.moveToFirst()){
                        XLog.e("_getLastMsg moveToFirst fail");
                        return;
                    }

                    switch (type) {
                        case 1:  //text
                        {
                            content = cursor.getString(cursor.getColumnIndex("content"));
                            XLog.i("talker:%s, content:%s", talker, content);
                            if (talker.contains("@chatroom")) { // from chatroom
                                ;
                            }
                            break;
                        }
                        case 3:  //image
                        {
                            break;
                        }
                        case 34:  // audio
                        {
                            break;
                        }
                        case 47:  // emoji
                        {
                            break;
                        }
                        case 62:  //video
                        {
                            break;
                        }
                    }
                } catch (Throwable e) {
                    XLog.e("hookNotification error. stack:%s", android.util.Log.getStackTraceString(e));
                } finally{
                    cursor.close();
                }
            }
        });
    }  /** hookNotification 结束 */


    void hookWXLoginUICreate(final ClassLoader loader) {
        /** hook  WXLoginUI 创建界面函数,  用于: 二维码扫描后点击确认登陆 */
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/webwx/ui/ExtDeviceWXLoginUI"), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    int type = (int)getObjectField(param.thisObject, "type");
                    XLog.i("WXLoginUI Create, type: %d", type);
                    if( type == 0 ) {
                        /** 进入类中, 搜索:   "intent.key.login.url"      向下寻找
                         * iput-object v0, p0, Lcom/tencent/mm/plugin/webwx/ui/ExtDeviceWXLoginUI;->aHe:Ljava/lang/String;      =>      iput-object v0, p0, Lcom/tencent/mm/plugin/webwx/ui/ExtDeviceWXLoginUI;->bZD:Ljava/lang/String;
                         */
                        String url = (String) getObjectField(param.thisObject, "bZD");

                        /** 进入类中, 搜索:   "intent.key.ok.session.list"  向下寻找
                         * iput-object v0, p0, Lcom/tencent/mm/plugin/webwx/ui/ExtDeviceWXLoginUI;->ivx:Ljava/lang/String;      =>      iput-object v0, p0, Lcom/tencent/mm/plugin/webwx/ui/ExtDeviceWXLoginUI;->qmk:Ljava/lang/String;
                         */
                        String rconversation = (String) getObjectField(param.thisObject, "qmk");
                        if ( WechatClass.webwx_a_c == null ){
                            /** 搜索:     "/cgi-bin/micromsg-bin/extdeviceloginconfirmok"     6311    =>  667
                             * com/tencent/mm/plugin/webwx/a/c       =>      com/tencent/mm/plugin/webwx/a/e
                             * .method public constructor <init>(Ljava/lang/String;Ljava/lang/String;)V       =>      .method public constructor <init>(Ljava/lang/String;Ljava/lang/String;Z)V
                             */
                            WechatClass.webwx_a_c = WechatClass.get("com/tencent/mm/plugin/webwx/a/e");     // note 该类是 /cgi-bin/micromsg-bin/extdeviceloginconfirmok
                        }
                        Object obj_webwx_a_c = newInstance(WechatClass.webwx_a_c, url, rconversation, true);
                        WechatClass.postTask(obj_webwx_a_c);
                    }else{
                        XLog.e("WXLogin fail.type != 0");
                    }
                } catch (Throwable e) {
                    XLog.e("[hookWXLoginUI Create] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookWXLoginUICreate 结束 */


    void hookWXLoginResponse(final ClassLoader loader) {
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/webwx/a/e", "/cgi-bin/micromsg-bin/extdeviceloginconfirmok"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    int p1 = (int)param.args[0];// 0
                    int p2 = (int)param.args[1];// 0
                    int p3 = (int)param.args[2];// 0
                    String p4 = (String)param.args[3];// null
                    Object p5 =  param.args[4];
                    XLog.i("wxlogin callback, p1:%d, p2:%d, p3:%d, p4:%s", p1, p2, p3, p4);
                    if( WechatClass.currentActivity != null){
                        WechatClass.currentActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(2000);
                                } catch (Exception e) {
                                }
                                Toast.makeText(WechatClass.wechatContext, "扫码登陆成功!", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } catch (Throwable e) {
                    XLog.e("[hookWXLogin Response] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookWXLoginResponse 结束 */


    public void hookWebwxObject(final ClassLoader loader) {
        //挂钩 微信登陆 <init> 构造函数
        /** 搜索:     , 0x3cb     6311 => 667
         * com/tencent/mm/plugin/webwx/a/e$3    =>  com/tencent/mm/plugin/webwx/a/g$3
         * 类中搜索:     constructor <init>
         * .method constructor <init>(Lcom/tencent/mm/plugin/webwx/a/e;)V       =>      .method constructor <init>(Lcom/tencent/mm/plugin/webwx/a/g;)V
         */
        findAndHookConstructor(WechatClass.get("com/tencent/mm/plugin/webwx/a/g$3"), WechatClass.replace_slash("com/tencent/mm/plugin/webwx/a/g"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    WechatClass.webwx_handler = param.thisObject;
                    XLog.i("hook constructor webwx_handler:%s", WechatClass.webwx_handler);
                } catch (Throwable e) {
                    XLog.e("[hookWebwxObject] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    public void hookAppPanel(final ClassLoader loader) {
        /** note: 在delay内调用 unhook_map.put(WechatClass.replace_slash("com/tencent/mm/pluginsdk/ui/chat/AppPanel"), "hookAppPanel");
         * 登陆后, App界面全部出现后, 刷新一次
         * 搜索:      "app panel refleshed"
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/pluginsdk/ui/chat/AppPanel"), "refresh", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    XLog.i("hookAppPanelRefresh, is_plugin_auth_success: %b", VerifyThread.autharg.is_plugin_auth_success);
                } catch (Throwable e) {
                    XLog.e("[hookAppPanelRefresh] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookAppPanel 结束 */


    public void hookMenuOnClick(final ClassLoader loader) {
        /**
         * 搜索:      "Switch to MonkeyEnv now."
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/ui/HomeUI$25"), "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    XLog.d("hookMenuOnClick, 2222222222");
                } catch (Throwable e) {
                    XLog.e("[hookMenuOnClick] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookMenuOnClick 结束 */


    protected void hookDelay(final ClassLoader loader) {
        /** 因延迟加载 delay load.dex.jar文件, 加载.dex.jar 后才能挂钩构造函数
         */
        hookNewActivity(WechatClass._loader);
        hookInitDbObject(WechatClass._loader);
        hookGUID(WechatClass._loader);
        // 加载类后的callback. note: 这里的函数名不能混淆! 且是 public !
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/ai/c"), "hookInitMtimerObject");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/e/b/i"), "hookInitVoiceInfoObject");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/modelvideo/x$a"), "hookInitVideoInfoObject");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/ak/i"), "hookInitImgInfo2Object");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/model/z"), "hookHandleAuthResponse");
        //unhook_map.put(WechatClass.replace_slash("com/tencent/mm/modelsimple/q"), "hookManualAuthResponse");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/plugin/webwx/a/g$3"), "hookWebwxObject");
        unhook_map.put(WechatClass.replace_slash("com/tencent/mm/ui/HomeUI$25"), "hookMenuOnClick");

        //挂钩非构造函数, 且函数有1个参数
        unhook_loadClass = findAndHookMethod("java.lang.ClassLoader", loader, "loadClass", String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    if(unhook_map.size() == 0) {
                        XLog.i("unhook loadClass");
                        unhook_loadClass.unhook();  // // 当数量递减到0时, 所需hook已经全部找到, 卸载 loadClass 钩子
                        return;
                    }
                    String class_name = (String) param.args[0];

                    //XLog.i("after load dex.jar, class_name:%s", class_name);   //不能打印, 因为太多次调用!!!
                    if ( unhook_map.containsKey(class_name) ) {
                        XLog.i("loadClass:%s", class_name);
                        String function_name = unhook_map.get(class_name);
                        unhook_map.remove(class_name);
                        // note: 反射回调
                        Class[] parameterTypes = new Class[1];
                        parameterTypes[0] = ClassLoader.class;
                        Method method = WechatHook.class.getMethod(function_name, parameterTypes);
                        method.invoke(Main.wechathook, WechatClass._loader);
                    }
                } catch (Throwable e) {
                    XLog.e("[hookDelay] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookLoadClass 结束 */


    void hookAddChatroomMemberResponse(final ClassLoader loader) {
        /** 搜索: "/cgi-bin/micromsg-bin/addchatroommember"
         * com/tencent/mm/plugin/chatroom/d/d       =>      com/tencent/mm/plugin/chatroom/d/d           // note 该类是 addchatroommember
         * 进入类中, 搜索: III
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         * 进入类中, 文件开头查看类成员字段,
         * .field public chatroomName:Ljava/lang/String;
         * .field public final gcD:Ljava/util/List;
         * =========>
         * .field public chatroomName:Ljava/lang/String;
         * .field public final hKU:Ljava/util/List;
         */
        // 发送群加人后的响应函数, 触发自动发送群邀请url
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/chatroom/d/d", "addchatroommember"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    String room = (String)getObjectField(param.thisObject, "chatroomName");
                    ArrayList<String> wxid_list = (ArrayList)getObjectField(param.thisObject, "hKU");
                    String p4 = (String)param.args[3];
                    XLog.i("hookAddChatroomMemberResponse, room:%s, wxid_list:%s, tips:%s", room, wxid_list, p4);
                    if ( TextUtils.equals(p4, "Everything is OK") ){
                        for(String wxid: wxid_list) {
                            WechatHook.room_wxid2true.remove(room+wxid);
                        }
                    } else if( TextUtils.equals(p4, "Need invite") ){
                        for(String wxid: wxid_list) {
                            String room_wxid = room + wxid;
                            if( WechatHook.room_wxid2true.containsKey(room_wxid)) {
                                XLog.i("send invite card. room:%s, wxid:%s", room, wxid);
                                WechatHook.room_wxid2true.remove(room_wxid);
                                // 发送群邀请卡片
                                ArrayList _list = new ArrayList();
                                _list.add(wxid);
                                /** 搜索: "/cgi-bin/micromsg-bin/invitechatroommember"
                                 * com/tencent/mm/plugin/chatroom/d/k       =>      com/tencent/mm/plugin/chatroom/d/k      // note 该类是 invitechatroommember
                                 */
                                Object obj_ahctroom_a_i = newInstance(WechatClass.get("com/tencent/mm/plugin/chatroom/d/k", "invitechatroommember"), room, _list);
                                WechatClass.postTask(obj_ahctroom_a_i);
                            }
                        }
                    } else{
                        XLog.e("hookAddChatroomMemberResponse unknow p4:%s", p4);
                    }
                } catch (Throwable e) {
                    XLog.e("hookAddChatroomMemberResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookAddChatroomMemberResponse 结束 */

    /**
    protected void hookNotifyNewFriendRequest(final ClassLoader loader) {
        // 收到好友申请通知
     findAndHookMethod("com.tencent.mm.am.c", loader, "a", String.class, findClass("com.tencent.mm.sdk.h.i", loader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            String rowid = (String)param.args[0];
                            XLog.i("hookNotifyNewFriendRequest rowid:%s", rowid);
                            String wxid = WechatClass.getTalkerFromFmessagemsginfoByRowid(rowid);
                            if(wxid == null){
                                XLog.e("hookNotifyNewFriendRequest rowid is null!");
                                return;
                            }
                            private HashMap<String, Long> wxid2timestamp = new HashMap<>();  //记录好友申请时间是否在3秒内, 大于3秒则给到后端. 3秒内不传.
                            Long timestamp = wxid2timestamp.get(wxid);
                            if(timestamp != null){
                                // 少于3秒则不传给后端
                                if( System.currentTimeMillis() - timestamp < 3000 ) {
                                    XLog.w("NotifyNewFriendRequest interval less than 3 seconds!");
                                    return;
                                }
                            }
                        } catch (Throwable e) {
                            XLog.e("hookNotifyNewFriendRequest error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                }
        );
    }
    */


    void hookGet8keyResponse(final ClassLoader loader) {
        if(BuildConfig.DEBUG){
            /** 搜索:     "getDynamicConfigValue, accHasReady = "         667
             * 类中搜索:     "geta8key_session_id"
             * invoke-direct/range {v0 .. v7}, Lcom/tencent/mm/modelsimple/l;-><init>(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;I)V
             * =>   invoke-direct {v0, v1, v2, v3, v4}, Lcom/tencent/mm/modelsimple/h;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V
             */
            findAndHookConstructor(WechatClass.get("com/tencent/mm/modelsimple/h", "get8key"), String.class, String.class, String.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try{
                        String p1 = (String)param.args[0];
                        String p2 = (String)param.args[1];
                        String p3 = (String)param.args[2];
                        int p4 = (int)param.args[3];
                        XLog.d("get8key Constructor, p1:%s, p2:%s, p3:%s, p4:%d", p1, p2, p3, p4);
                    } catch (Throwable e) {
                        XLog.e("hookGet8keyResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                    }
                }
            });
        }

        /** 进入类中, 搜索: III           667
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/modelsimple/h", "get8key"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    XLog.i("get8key Response, type=233");
                    /** 流程: 模拟点击群邀请卡片(通过发送获取群邀请链接的任务), 响应函数返回"确认"按钮所指示的url值     667
                     * note: 投机取巧方式, 搜索:    "dkwt geta8key onGYNetEnd:[%d,%d] url:[%s]  a8key:[%s]"
                     * invoke-virtual {p0}, Lcom/tencent/mm/modelsimple/l;->It()Ljava/lang/String;      =>      invoke-virtual {p0}, Lcom/tencent/mm/modelsimple/h;->QL()Ljava/lang/String;
                     * */
                    String url = (String)callMethod(param.thisObject, "QL");
                    XLog.i("enter room confirm url:%s", url);

                    /** 搜索:     "get a8key appid=%s requestId=%d"       向上寻找
                     * iget-object v0, p0, Lcom/tencent/mm/modelsimple/l;->hgi:Lcom/tencent/mm/w/b;
                     * iget-object v0, v0, Lcom/tencent/mm/w/b;->hDb:Lcom/tencent/mm/w/b$b;
                     * iget-object v0, v0, Lcom/tencent/mm/w/b$b;->hDj:Lcom/tencent/mm/bb/a;
                     * check-cast v0, Lcom/tencent/mm/protocal/c/st;
                     * =======================>
                     * iget-object v0, p0, Lcom/tencent/mm/modelsimple/h;->diG:Lcom/tencent/mm/ab/b;
                     * iget-object v0, v0, Lcom/tencent/mm/ab/b;->dID:Lcom/tencent/mm/ab/b$b;
                     * iget-object v0, v0, Lcom/tencent/mm/ab/b$b;->dIL:Lcom/tencent/mm/bk/a;
                     * check-cast v0, Lcom/tencent/mm/protocal/c/yo;
                     */
                    Object obj_r_a = getObjectField(param.thisObject, "diG");
                    Object obj_r_a$b = getObjectField(obj_r_a, "dID");
                    Object obj_at_a = getObjectField(obj_r_a$b, "dIL");

                    /** 进入类     check-cast v0, Lcom/tencent/mm/protocal/c/st;       =>      check-cast v0, Lcom/tencent/mm/protocal/c/yo;
                     * .field public jSr:Ljava/lang/String;     =>      .field public hbL:Ljava/lang/String;
                     */
                    String inviter_wxid = (String)getObjectField(obj_at_a, "hbL");
                    if( inviter_wxid == null){
                        XLog.w("inviter_wxid is null");
                        return;
                    }
                    if( TextUtils.equals(inviter_wxid, "newsapp") ){
                        XLog.w("ignroe inviter_wxid:%s", inviter_wxid);
                        return;
                    }
                    XLog.i("inviter_wxid:%s", inviter_wxid);
                    // 返回应答: { "type":1815, "url":"", "wxid":"" }
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.chatroom_invite_res);
                    res_dict.put("url", url);
                    res_dict.put("wxid", inviter_wxid);

                    XLog.i("rpush msgtype:chatroom_invite_res, url:%s, inviter_wxid:%s", url, inviter_wxid);
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                } catch (Throwable e) {
                    XLog.e("hookGet8keyResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }  /** hookGet8keyResponse 结束 */


    void hookGetQrcodeResponse(final ClassLoader loader) {
        /** 流程分析: ./个人二维码/个人二维码图片.txt
         * note 不同于旧版本的原因: 6311是在保存图片时下钩住; 6510是在任务响应时钩住
         * 搜索:　"/cgi-bin/micromsg-bin/getqrcode", 进入类中, 搜索:     "onGYNetEnd errType:"       677
         * com/tencent/mm/an/a      =>      com/tencent/mm/as/a       // note 该类是 getqrcode
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         */
        // getqrcode后响应函数中的保存二维码图片函数, 包括: (个人二维码, 群二维码)
        findAndHookMethod(WechatClass.get("com/tencent/mm/as/a", "getqrcode"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    XLog.i("GetQrcode Response");
                    /** 解析wxid
                     * 进入类中, 搜索:   " errCode"       向下寻找
                     * iget-object v0, p0, Lcom/tencent/mm/an/a;->hgi:Lcom/tencent/mm/w/b;
                     * iget-object v0, v0, Lcom/tencent/mm/w/b;->hDb:Lcom/tencent/mm/w/b$b;
                     * iget-object v0, v0, Lcom/tencent/mm/w/b$b;->hDj:Lcom/tencent/mm/bb/a;
                     * iget-object v0, v0, Lcom/tencent/mm/protocal/c/zq;->tdh:Lcom/tencent/mm/protocal/c/atu;
                     * ==============>
                     * iget-object v0, p0, Lcom/tencent/mm/as/a;->diG:Lcom/tencent/mm/ab/b;
                     * iget-object v0, v0, Lcom/tencent/mm/ab/b;->dID:Lcom/tencent/mm/ab/b$b;
                     * iget-object v0, v0, Lcom/tencent/mm/ab/b$b;->dIL:Lcom/tencent/mm/bk/a;
                     * iget-object v0, v0, Lcom/tencent/mm/protocal/c/agy;->rvi:Lcom/tencent/mm/protocal/c/bhz;
                     */
                    Object obj_protocal_c_atu = getObjectField(getObjectField(getObjectField(getObjectField(param.thisObject, "diG"), "dID"), "dIL"), "rvi");
                    String wxid = (String)callStaticMethod(WechatClass.platformtools_n, "a", obj_protocal_c_atu);

                    /** 解析imgbuf
                     * 进入类中, 搜索:   " errCode"       向下寻找
                     * iget-object v1, p0, Lcom/tencent/mm/an/a;->hgi:Lcom/tencent/mm/w/b;
                     * iget-object v1, v1, Lcom/tencent/mm/w/b;->hDc:Lcom/tencent/mm/w/b$c;
                     * iget-object v1, v1, Lcom/tencent/mm/w/b$c;->hDj:Lcom/tencent/mm/bb/a;
                     * iget-object v2, v1, Lcom/tencent/mm/protocal/c/zr;->tpB:Lcom/tencent/mm/protocal/c/att;
                     * =============>
                     * iget-object v1, p0, Lcom/tencent/mm/as/a;->diG:Lcom/tencent/mm/ab/b;
                     * iget-object v1, v1, Lcom/tencent/mm/ab/b;->dIE:Lcom/tencent/mm/ab/b$c;
                     * iget-object v1, v1, Lcom/tencent/mm/ab/b$c;->dIL:Lcom/tencent/mm/bk/a;
                     * * iget-object v2, v1, Lcom/tencent/mm/protocal/c/agz;->rKm:Lcom/tencent/mm/protocal/c/bhy;
                     *  */
                    Object obj_protocal_c_zr = getObjectField(getObjectField(getObjectField(getObjectField(param.thisObject, "diG"), "dIE"), "dIL"), "rKm");
                    byte[] imgbuf = new byte[0];
                    imgbuf = (byte[])callStaticMethod(WechatClass.platformtools_n, "a", obj_protocal_c_zr, imgbuf);
                    XLog.i("getqrcode Response, wxid:%s", wxid);

                    if( WechatHook.wxid2getcode.containsKey(wxid) ) {
                        WechatHook.wxid2getcode.remove(wxid);
                        /** 保存到文件 */
                        File qrcode_file = new File(WechatHook.localQrcodeDir, wxid + ".jpg");
                        SystemUtil.writeByte2File(imgbuf, qrcode_file.getAbsolutePath());
                        /** sftp 二维码文件到远端 */
                        // note 文件格式如: /MicroMsg/qrcode/[wxid].jpg
                        WechatHook.thread_touch.sftp_queue.put( Arrays.asList( qrcode_file.getParent(), qrcode_file.getName(), WechatHook.remoteQrcodeDir, qrcode_file.getName(), "put", "0" ) );

                        if ( TextUtils.equals(wxid, WechatHook.get_robot_info(MySync.g_robot_wxid) ) ) {
                            //String qrcode = Base64.encodeToString(imgbuf, Base64.DEFAULT);  //与Python通信时建议用default
                            //WechatHook.robot_info.put("robot_qrcode", qrcode);
                            /** 解析二维码图片文件为Url字符串 */
                            String qrcode_url  = WechatClass.parseQrcode( qrcode_file.getAbsolutePath() );
                            XLog.i("robot_qrcode_url:%s", qrcode_url);
                            WechatHook.set_robot_info("robot_qrcode_url", qrcode_url);
                        }
                    }
                } catch (Throwable e) {
                    XLog.e("hookGetQrcodeResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
        /**
        // 6.3.11 版本使用
         findAndHookMethod("com.tencent.mm.ai.b", loader, "k", String.class, byte[].class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            XLog.i("hookSaveQrcode");
                            String wxid = (String)param.args[0];
                            byte[] imgbuf = (byte[])param.args[1];
                            if( wxid.equals(WechatHook.get_robot_info(MySync.g_robot_wxid)) ){
                                // 判断wxid是否与要hook的相等
                                XLog.i("wxid match. wxid:%s", wxid);
                                String qrcode = Base64.encodeToString(imgbuf, Base64.DEFAULT);  //与Python通信时建议用default
                                WechatHook.set_robot_info("robot_qrcode", qrcode);
                                try {
                                    WechatAction.writeByte2File(imgbuf, WechatHook.localQrcodeDir);
                                    // 解析二维码url
                                    String qrcode_url = WechatClass.parseQrcode(WechatHook.localQrcodeDir);
                                    XLog.i("qrcode_url:%s", qrcode_url);
                                    WechatHook.set_robot_info("robot_qrcode_url", qrcode_url);
                                } catch (IOException e) {
                                    XLog.e("hookSaveQrcode writeByte2File error. stack:%s", e.getMessage());
                                }
                            }
                        } catch (Throwable e) {
                            XLog.e("hookSaveQrcode error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                });
         */
    }   /** hookSaveQrcode 结束 */


    void hookInsertContact(final ClassLoader loader) {
        /** 流程分析: ./点开群成员更新微信号/点开群成员微信号调用流程.txt,  更新步骤: 版本更新.txt, 点开群成员微信号调用流程
         * note: 投机取巧更新方法: 搜索       "Insert Contact %s"
         * com/tencent/mm/plugin/search/a/b/a/a$i     =>      com/tencent/mm/plugin/fts/b/a$e
         * .method public final execute()Z        =>      .method public final execute()Z
         * iget-object v4, p0, Lcom/tencent/mm/plugin/search/a/b/a/a$i;->gXN:Ljava/lang/String;     =>      iget-object v4, p0, Lcom/tencent/mm/plugin/fts/b/a$e;->cYO:Ljava/lang/String;
         */
        // 更新alias后的响应函数
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/fts/b/a$e"), "execute", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    //stackOverflow("eee");
                    String wxid = (String) getObjectField(param.thisObject, "cYO");
                    XLog.i("hookInsertContact, wxid:%s", wxid);
                    ConcurrentHashMap<String, Boolean> submap = WechatHook.wxid2rooms.get(wxid);
                    if( submap == null ){
                        XLog.w("wxid2rooms not get wxid:%s", wxid);
                        return;
                    }
                    String alias, nickname;
                    ContentValues rcontact_row = WechatClass.Rcontact.getContactByWxid2(wxid);
                    if( rcontact_row == null){
                        XLog.e("not exist rcontact row, wxid:%d", wxid);
                        return;
                    }
                    alias = rcontact_row.getAsString("alias");
                    nickname = rcontact_row.getAsString("nickname");
                    XLog.i("SaveAlias wxid:%s, alias:%s, nickname:%s", wxid, alias, nickname);

                    if( ( TextUtils.isEmpty(alias) && TextUtils.isEmpty(nickname) )
                            || ( TextUtils.equals(wxid, nickname) ) )
                    {
                        XLog.w("nickname equal wxid in hookInsertContact");
                        return;   //直接返回, 不remove key, 以使rcontact update的时候, 重新触发
                    }
                    // 返回应答
                    //    { "msgtype":"update_alias_res", "data":[ {"chatroom":"1", "member":"wxid_1", "alias":"1", "name":"1"} ] }
                    JSONObject res_dict = new JSONObject();
                    JSONArray data_array = new JSONArray();
                    JSONObject info_dict = new JSONObject();
                    for( ConcurrentHashMap.Entry<String, Boolean> entry : submap.entrySet()) {
                        info_dict.put("chatroom", entry.getKey());
                        info_dict.put("member", wxid);
                        info_dict.put("alias", alias);
                        info_dict.put("name", nickname);
                        data_array.put(0, info_dict);
                    }
                    WechatHook.wxid2rooms.remove(wxid);
                    res_dict.put("msgtype", WechatAction.update_alias_res);
                    res_dict.put("data", data_array);
                    XLog.i("rpush msgtype:update_alias_res, data:%s", data_array);
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                } catch (Throwable e) {
                    XLog.e("hookInsertContact error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookInsertContact 结束 */


    public void rpush_nickname(ContentValues contentvalues) throws Throwable{
        String wxid = contentvalues.getAsString("username");
        String nickname = contentvalues.getAsString("nickname");
        if( TextUtils.isEmpty(nickname) ) {
            XLog.d("nickname is NULL, wxid:%s, nickname:%s", wxid, nickname);
            return;
        }
        if( TextUtils.equals(nickname, wxid2nickname.get(wxid)) ){
            XLog.d("nickname not modify, wxid:%s", wxid);
            return;
        }
        JSONObject jsonobj = new JSONObject();
        jsonobj.put(wxid, nickname);
        // 返回应答
        //     {
        //          "msgtype":'update_nickname_res,
        //          "data": {"wxid_1":"url1", "wxid_2":"url2"}
        //      }
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.update_nickname_res);
        res_dict.put("data", jsonobj);
        XLog.i("rpush msgtype:update_nickname_res, wxid:%s, headurl:%s", wxid, nickname);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
        wxid2headurl.put(wxid, nickname);
    }


    public void rpush_header(ContentValues contentvalues) throws Throwable{
        String wxid = contentvalues.getAsString("username");
        String headurl = contentvalues.getAsString("reserved2");   // reserved2为小头像; reserved1为大头像
        if( TextUtils.isEmpty(headurl) || wxid.endsWith("@stranger") ) {
            XLog.d("headurl is NULL, wxid:%s, headurl:%s", wxid, headurl);
            return;
        }
        if( TextUtils.equals(headurl, wxid2headurl.get(wxid)) ){
            XLog.w("headurl not modify, wxid:%s", wxid);
            return;
        }
        JSONObject jsonobj = new JSONObject();
        jsonobj.put(wxid, headurl);
        // 返回应答
        //     {
        //          "msgtype":'update_header_res,
        //          "data": {"wxid_1":"url1", "wxid_2":"url2"}
        //      }
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.update_header_res);
        res_dict.put("data", jsonobj);
        XLog.i("rpush msgtype:update_header_res, wxid:%s, headurl:%s", wxid, headurl);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
        wxid2headurl.put(wxid, headurl);
    }


    private boolean isSendMessage(int field_isSend) {
        return field_isSend == 1;
    }

    void hookDbInsert(final ClassLoader loader) {
        /** hook数据库: 增
         * com/tencent/wcdb/database/SQLiteDatabase
         * .method public final insert(Ljava/lang/String;Ljava/lang/String;Landroid/content/ContentValues;)J
         */
        findAndHookMethod(WechatClass.get("com/tencent/wcdb/database/SQLiteDatabase"), "insert", String.class, String.class, ContentValues.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // 此insert函数可以直接下断点!
                String table = "";      /** 这里初始化用于异常时打印这3个变量 */
                String primarykey = "";
                ContentValues contentvalues = null;
                try{
                    table = (String)param.args[0];
                    primarykey = (String)param.args[1];
                    contentvalues = (ContentValues)param.args[2];
                    XLog.d("DbOperation insert table: %s", table);
                    if( !is_afterLogin ){
                        return;
                    }
                    switch(table){
                        case "message": {   // 收到消息
                            stackOverflow("ddd");
                            if( !contentvalues.containsKey("isSend") ){
                                return;
                            }
                            int isSend = contentvalues.getAsInteger("isSend");
                            if( isSendMessage(isSend) ){
                                /** insert message时, 同时处理: 接收消息, 发送消息 */
                                rpush_send_message(DbOperation.INSERT, contentvalues);
                            }else {
                                rpush_recv_message(DbOperation.INSERT, contentvalues);
                            }
                        }break;

                        case "ImgInfo2": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            stackOverflow("ddd");
                        }break;

                        case "appattach": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            stackOverflow("ddd");
                        }break;

                        case "WxFileIndex2": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("ddd");
                        }break;

                        case "AppMessage": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            stackOverflow("ddd");
                        }break;

                        case "img_flag": {   // 头像
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            rpush_header(contentvalues);
                        }break;

                        case "rcontact": {
                            rpush_nickname(contentvalues);
                        }break;

                        case "fmessage_msginfo":{   // 收到好友申请通知
                            int isSend = contentvalues.getAsInteger("isSend");
                            if( isSendMessage(isSend) ){
                            }else {
                                rpush_recv_fmessage_msginfo(DbOperation.INSERT, contentvalues);
                            }
                        }break;

                        case "FavItemInfo": { // note. 重要! 点击收藏server.log时, 查看详细流程
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "FavCdnInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "FavConfigInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "oplog2": {
                            //stackOverflow("eee");
                        }break;

                        case "chatroom": {
                            //stackOverflow("eee");
                        }break;

                        case "voiceinfo":{
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "EmojiInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;
                    } /** switch end */
                } catch (Throwable e) {
                    XLog.e("insert table: %s, contentvalues:%s", table, contentvalues);
                    XLog.e("DbOperation insert error, stack:%s", android.util.Log.getStackTraceString(e));
                }
            } /** beforeHookedMethod end */
        }); /** findAndHookMethod end */
    }  /** hookDbInsert 结束 */


    void hookDbDelete(final ClassLoader loader) {
        /** hook数据库: 删 */
        findAndHookMethod(WechatClass.get("com/tencent/wcdb/database/SQLiteDatabase"), "delete", String.class, String.class, String[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    String table = (String)param.args[0];
                    XLog.d("DbOperation.delete table: %s", table);
                    if( !is_afterLogin ){
                        return;
                    }
                    switch(table) {
                    }
                } catch (Throwable e) {
                    XLog.e("DbOperation delete error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookDbDelete 结束 */


    void hookDbUpdate(final ClassLoader loader) {
        /** hook数据库: 改 */
        findAndHookMethod(WechatClass.get("com/tencent/wcdb/database/SQLiteDatabase"), "update", String.class, ContentValues.class, String.class, String[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String table = "";      /** 这里初始化用于异常时打印这3个变量 */
                ContentValues contentvalues = null;
                try{
                    table = (String)param.args[0];
                    contentvalues = (ContentValues)param.args[1];
                    if( !is_afterLogin && !TextUtils.equals(table, "netstat") ){
                        return;
                    }
                    switch(table){
                        case "rconversation":{
                            if( contentvalues.containsKey("unReadCount") ){ contentvalues.put("unReadCount", 0); }
                        }
                        break;
                    }   /** switch结束 */
                } catch (Throwable e) {
                    XLog.e("update table: %s, contentvalues:%s", table, contentvalues);
                    XLog.e("DbOperation before update error, stack:%s", android.util.Log.getStackTraceString(e));
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String table = "";      /** 这里初始化用于异常时打印这3个变量 */
                ContentValues contentvalues = null;
                try{
                    table = (String)param.args[0];
                    contentvalues = (ContentValues)param.args[1];
                    XLog.d("DbOperation update table: %s", table);
                    if( !is_afterLogin && !TextUtils.equals(table, "netstat") ){
                        switch (table) {
                            case "voiceinfo": {
                                if (BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                                //stackOverflow("eee");
                            }
                        }
                        return;
                    }
                    switch(table){
                        case "netstat":{
                            //ContentValues contentvalues = (ContentValues)param.args[1];
                            //XLog.d("contentvalues:%s", contentvalues);
                        }break;

                        // TODO delete something
                        case "rcontact":{
                            rpush_nickname(contentvalues);
                            /** 成功成为好友后通知 */
                            String where = (String)param.args[2];
                            String wxid = contentvalues.getAsString("username");
                            String alias = contentvalues.getAsString("alias");
                            String nickname = contentvalues.getAsString("nickname");
                            int type = contentvalues.getAsInteger("type");
                            if(where=="username=?" && WechatHook.wxid2addfriend.containsKey(wxid)){  // type == 3 or type == 7
                                WechatHook.wxid2addfriend.remove(wxid);
                                // 发送消息测试
                                LinkedList<String> linkelist = new LinkedList<>();
                                linkelist.add(0, alias);
                                linkelist.add(1, nickname);
                                linkelist.add(2, String.valueOf(type));
                                WechatHook.friendtest.put(wxid, linkelist);
                                JSONObject res_dict = new JSONObject();
                                res_dict.put("wxid", wxid);
                                res_dict.put("content", "get_contact_by_wxid");
                                WechatHook.thread_touch.thread_action.send_text(res_dict);
                                XLog.i("send_text to friendtest relation, wxid:%s, content:get_contact_by_wxid", wxid);
                            }
                            /** 好友别名更新 */
                            if( ! wxid.endsWith("@chatroom") && ! TextUtils.equals(nickname, wxid) ){
                                ConcurrentHashMap<String, Boolean> submap = WechatHook.wxid2rooms.get(wxid);
                                if( submap != null ){
                                    XLog.i("rcontact update, wxid:%s, alias:%s, name:%s", wxid, alias, nickname);
                                    // 返回应答
                                    JSONObject res_dict = new JSONObject();
                                    JSONArray data_array = new JSONArray();
                                    JSONObject info_dict = new JSONObject();
                                    for( ConcurrentHashMap.Entry<String, Boolean> entry : submap.entrySet()) {
                                        info_dict.put("chatroom", entry.getKey());
                                        info_dict.put("member", wxid);
                                        info_dict.put("alias", alias);
                                        info_dict.put("name", nickname);
                                        data_array.put(0, info_dict);
                                    }
                                    WechatHook.wxid2rooms.remove(wxid);
                                    res_dict.put("msgtype", WechatAction.update_alias_res);
                                    res_dict.put("data", data_array);
                                    XLog.i("rpush msgtype:update_alias_res, data:%s", data_array);
                                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                                }
                            }
                        } break;

                        case "ImgInfo2": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            stackOverflow("ddd");
                        }break;

                        case "img_flag": {   // 头像
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            rpush_header(contentvalues);
                        } break;

                        case "appattach": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                        } break;

                        case "AppMessage": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("ddd");
                        }break;

                        case "message": {   // 收到消息
                            //stackOverflow("zzz");
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            long msgId = contentvalues.getAsLong("msgId");
                            if( !contentvalues.containsKey("status") ) {
                                // note 下载完附件后, 会有一个update message动作, 在下面查表的时候触发异常:
                                // note java.lang.IllegalStateException: Couldn't read row 0, col 0 from CursorWindow.  Make sure the Cursor is initialized correctly before accessing data from it.
                                if(BuildConfig.DEBUG) XLog.d("message not contain status, return. msgid:%d", msgId);
                                return;
                            }
                            ContentValues message_row = WechatClass.getMessageByMsgId(msgId);
                            if( message_row == null ){
                                return;
                            }
                            int isSend = message_row.getAsInteger("isSend");
                            if( isSendMessage(isSend) ){
                                /** update message时, 只处理发送的消息 */
                                rpush_send_message(DbOperation.UPDATE, message_row);
                                transmit_card_if_need(message_row);
                            }else{
                                // 不关心以下情况:  收到的消息有更新
                                XLog.d("update message ignore Recving. msgid:%d, type:%d", message_row.getAsInteger("msgId"), message_row.getAsInteger("type"));
                            }
                        } break;

                        case "fmessage_msginfo":{   // 收到好友申请通知
                            if(BuildConfig.DEBUG) XLog.d("update fmessage_msginfo, contentvalues:%s", contentvalues);
                            /**
                            int isSend = contentvalues.getAsInteger("isSend");
                            if( isSendMessage(isSend) ){
                            }else {
                                rpush_recv_fmessage_msginfo(DbOperation.UPDATE, contentvalues);
                            }
                            */
                        }break;

                        case "FavItemInfo": { // note. 重要! 点击收藏server.log时, 查看详细流程
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }
                        break;

                        case "FavCdnInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "FavConfigInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "chatroom":{
                            //stackOverflow("eee");
                        }break;

                        case "videoinfo2":{
                            //stackOverflow("eee");
                        }
                        break;
                        case "voiceinfo":{
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;

                        case "EmojiInfo": {
                            if(BuildConfig.DEBUG) XLog.d("%s contentvalues:%s", table, contentvalues);
                            //stackOverflow("eee");
                        }break;
                    }   /** switch结束 */
                } catch (Throwable e) {
                    XLog.e("update table: %s, contentvalues:%s", table, contentvalues);
                    XLog.e("DbOperation update error, stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookDbUpdate 结束 */


    void transmit_card_if_need(final ContentValues message_row) throws Exception {
        long msgId = message_row.getAsLong("msgId");
        if( ! MySync.msgid2card_cc.containsKey(msgId) ) {
            return;
        }
        String wxid_list = (String) MySync.msgid2card_cc.get(msgId);
        MySync.msgid2card_cc.remove(msgId);

        // 从 message 表记录获取需要的字段
        int status = message_row.getAsInteger("status");
        int type = message_row.getAsInteger("type");
        if( status != Define.MESSAGE_STATUS_SEND_SUCCESS && type != Define.MESSAGE_TYPE_CARD_MSG ){
            XLog.e("transmit_card_if_need not match, status: %s, type: %s", status, type);
            return;
        }
        String content = message_row.getAsString("content");
        Map message_appmsg = WechatClass.parseChatroomMsgsource(content, "msg");


        // 从 AppMessage 表记录获取需要的字段
        ContentValues appmessage_row = WechatClass.EnMicroMsg.select( "select xml from AppMessage where msgId=?", new String[]{String.valueOf(msgId)} );
        if( appmessage_row == null ){
            XLog.e("select error, contentValues is null");
            return;
        }
        String xml = appmessage_row.getAsString("xml");
        Map appmsg = WechatClass.parseChatroomMsgsource(xml, "msg");
        XLog.d("sendMsgXml: %s", appmsg);
        {
            String title = (String) appmsg.get(".msg.appmsg.title");   // "test_attachment2.exe";
            String extention = (String) appmsg.get(".msg.appmsg.appattach.fileext");       // "exe";
            String description = (String) appmsg.get(".msg.appmsg.des");      // "8.2 KB";
            String totalLen = (String) appmsg.get(".msg.appmsg.appattach.totallen");       // 1631368
            String cdnattachurl = (String) appmsg.get(".msg.appmsg.appattach.cdnattachurl");  // "30590201000452305002010002043c8764ba02033d14b9020476fd03b702045b9c556f042b6175706174746163685f643865343531646566346664366161315f313533363937313731393838305f32310204010800050201000400";
            String aeskey = (String) message_appmsg.get(".msg.appmsg.appattach.aeskey");        //  "b07908cafddf4d76aca90897f6662799";
            String md5 = "8d09a067b0c2123f61a3cb4e804562f0";    // 不确定是否有什么影响
            String filekey = "wxid_w6f7i8zvvtbc12140_" + System.currentTimeMillis()/1000;   // 不确定是否有什么影响
            String attachid = String.format("@cdn_%s_%s_1", cdnattachurl, aeskey);
            String new_content = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>"
                    + title + "</title><des>"
                    + description + "</des><action>view</action><type>6</type><showtype>0</showtype><soundtype>0</soundtype><mediatagname></mediatagname><messageext></messageext><messageaction></messageaction><content></content><contentattr>0</contentattr><url></url><lowurl></lowurl><dataurl></dataurl><lowdataurl></lowdataurl><appattach><totallen>"
                    + totalLen + "</totallen><attachid>"
                    + attachid + "</attachid><emoticonmd5></emoticonmd5><fileext>"
                    + extention + "</fileext><cdnattachurl>"
                    + cdnattachurl + "</cdnattachurl><cdnthumbaeskey></cdnthumbaeskey><aeskey>"
                    + aeskey + "</aeskey><encryver>0</encryver><filekey>"
                    + filekey + "</filekey></appattach><extinfo></extinfo><sourceusername></sourceusername><sourcedisplayname></sourcedisplayname><thumburl></thumburl><md5>"
                    + md5 + "</md5><statextstr></statextstr><webviewshared><jsAppId></jsAppId></webviewshared></appmsg><fromusername>weixin12345</fromusername><scene>0</scene><appinfo><version>1</version><appname></appname></appinfo><commenturl></commenturl></msg>";
            // 抄送卡片
            JSONObject res_dict = new JSONObject();
            res_dict.put("wxid_list", wxid_list);
            res_dict.put("content", new_content);
            WechatHook.thread_touch.thread_action.transmit_card(res_dict);
        }
    }


    static final Handler handler = new Handler();  // note Handler机制会有消息发到当前线程处理
    void hookUploadVoiceResponse(final ClassLoader loader) {
        // note: 流程参考: 多次上传流程分析.txt
        /** 搜索:     "dkmsgid UpdateAfterSend file:["       667
         * com/tencent/mm/modelvoice/q      // note 该类是 "/cgi-bin/micromsg-bin/uploadvoice"
         * 进入类中, 搜索:    III
         * .method public static a(Ljava/lang/String;IJLjava/lang/String;II)I
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/modelvoice/q"), "a", String.class, int.class, long.class, String.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    // note: 此调用的上层代码逻辑, 流程参考: 多次上传流程分析.txt
                    /** 搜索:     "dkmsgid onGYNetEnd updateAfterSend:"       667
                     * com/tencent/mm/modelvoice/f      =>      com/tencent/mm/modelvoice/f     // note 该类是 "/cgi-bin/micromsg-bin/uploadvoice"
                     * 进入类中, 搜索:    III
                     * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
                     */
                    String filename = (String) param.args[0];     // filename, 例如: 491620081918d25c875113c104
                    int NetOffset = (int) param.args[1];           // 当前发送到语音文件的偏移量 (单位:字节), 例如: 10752
                    long msgSvrId = (long) param.args[2];         // msgSvrId,  例如: 199333176462726501
                    String p4 = (String) param.args[3];     // filename, 例如: 491620081918d25c875113c104
                    int is_last = (int) param.args[4];           // 0-非最后一个包; 1-最后一个包
                    int p6 = (int) param.args[5];           // 0
                    XLog.d("UpdateAfterSend Voice, filename: %s, NetOffset: %d, msgSvrId: %d, p4: %s, is_last: %d, p6: %d", filename, NetOffset, msgSvrId, p4, is_last, p6);

                    if( MySync.filename2uploadvoice.containsKey( String.format(Locale.ENGLISH, "%s.voice.totallen", filename) ) ){
                        int totallen = (int)MySync.filename2uploadvoice.get( String.format(Locale.ENGLISH, "%s.voice.totallen", filename));
                        XLog.d("uploadvoice callback, recall task, totallen:%d", totallen);
                        if( totallen - NetOffset > 0 ){
                            WechatHook.handler.postDelayed(new VoiceRunnable(), 3000);     // 延迟执行
                            /*
                            XLog.d("manally update voiceinfo_status=3, insert message row");
                            //1. update voiceinfo status = 3
                            ContentValues contentValues = new ContentValues();
                            contentValues.put("Status", 3);
                            int ret = WechatClass.EnMicroMsg.rawUpdate("voiceinfo", contentValues, "FileName=?", new String[]{filename});
                            //2. insert message
                            ContentValues message_row = (ContentValues) MySync.filename2uploadvoice.get( String.format(Locale.ENGLISH, "%s.voice.message_row", filename));
                            WechatClass.EnMicroMsg.rawInsert("message", "", message_row);
                            */
                        }
                    }
                    // note 更新此次 upload 语音时间; 当文件上传完成时, 从任务队列中移除
                    WechatAction.send_voice_last_time.set(SystemUtil.get_timestamp());
                    if(is_last == 1) {
                        XLog.d("remove filename: %s", filename);
                        MySync.filename2uploadvoice_task.remove(filename);
                    }
                } catch (Throwable e) {
                    XLog.e("hookUploadVoiceResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
        /*
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/modelvoice/f"), "tP",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            // note 单次上传: 6000 字节, 最大上传次数: 60, 最大NetOffset大小: 360000 字节
                            XLog.d("return max times 100");
                            //stackOverflow("zzz");
                            return 100;
                        } catch (Throwable e) {
                            XLog.e("hookDownloadAttach error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                        return 0x3c; // 60
                    }
                }
        );
        */
        /*
                                MySync.filename2uploadvoice.put( String.format(Locale.ENGLISH, "%s.voice.offset", filename), NetOffset );
                                String fullpath = WechatClass.voiceFullpath(filename);
                                File voice_file = new File(fullpath);
                                long now = System.currentTimeMillis();
                                voice_file.setLastModified(now);

                                int last_offset = (int)MySync.filename2uploadvoice.get( String.format(Locale.ENGLISH, "%s.voice.offset", filename));
                                if( last_offset == NetOffset){
                                    XLog.e("offset not change, last_offset:%d, netoffset:%d", last_offset, NetOffset);
                                    return;
                                }
         */
    }


    void hookDownloadOrUploadVideoResponse(final ClassLoader loader) {
        /** 搜索: "download video finish, but file size is not equals db size[%d, %d]"
         * com/tencent/mm/modelvideo/t      =>      com/tencent/mm/modelvideo/t
         * 进入类中, 搜索:    Ljava/lang/String;->length()I
         * .method public static e(Lcom/tencent/mm/modelvideo/r;)Z      =>      .method public static e(Lcom/tencent/mm/modelvideo/r;)Z
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/modelvideo/t"), "e", WechatClass.get("com/tencent/mm/modelvideo/r"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    Object videoinfo2_row = param.args[0];
                    /** 进入类中, 搜索:   " status: "
                     * iget v0, v0, Lcom/tencent/mm/modelvideo/r;->status:I     =>      iget v0, v0, Lcom/tencent/mm/modelvideo/r;->status:I
                     */
                    int status = (int)getObjectField(videoinfo2_row, "status");
                    XLog.d("download or upload video callback, status:%d", status);

                    /** 进入类中, 搜索:     "do prepare, but toUser is null, type %d"       向下寻找
                     * iput-object v0, v3, Lcom/tencent/mm/modelvideo/r;->enF:Ljava/lang/String;
                     */
                    String human = (String)getObjectField(videoinfo2_row, "enF");
                    String robot_wxid = WechatHook.get_robot_info(MySync.g_robot_wxid);
                    if( TextUtils.equals(human, robot_wxid)){
                        _UploadVideoResponse(status, videoinfo2_row);
                    } else{
                        _DownloadVideoResponse(status, videoinfo2_row);
                    }
                } catch (Throwable e) {
                    XLog.e("hookDownloadVideo error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }

    void _UploadVideoResponse(int status, Object videoinfo2_row) throws Exception {
        // note 更新此次 upload 视频时间; 当文件上传完成时, 从任务队列中移除
        WechatAction.send_video_last_time = SystemUtil.get_timestamp();
        if( status == 199 ){
            String filename = (String)callMethod(videoinfo2_row, "getFileName");
            //XLog.i("upload video done, filename: %s, before: %s", filename,  MySync.filename2uploadvideo_task.values());
            MySync.filename2uploadvideo_task.remove(filename);
        }
    }

    void _DownloadVideoResponse(int status, Object videoinfo2_row) throws Exception {
        if( status != 199) { // note: 199 - 完成, 0xc7
            return;
        }
        /** videoinfo2表记录
         * 进入类中, 搜索:    " total:"
         * iget v4, v1, Lcom/tencent/mm/modelvideo/r;->hCk:I        =>      iget v4, v1, Lcom/tencent/mm/modelvideo/r;->dHI:I
         */
        int totalLen = (int)getObjectField(videoinfo2_row, "dHI");
        /** 进入类中, 搜索:   "it error short video can not retransmit. file size[%d], video info size[%d]"
         * invoke-virtual {v2}, Lcom/tencent/mm/modelvideo/r;->getFileName()Ljava/lang/String;      =>  invoke-virtual {v0}, Lcom/tencent/mm/modelvideo/r;->getFileName()Ljava/lang/String;
         */
        String filename = (String)callMethod(videoinfo2_row, "getFileName");
        String mp4FullPath = WechatClass.videoMp4Fullpath(filename);
        XLog.i("videoinfo2_row, totalLen:%d, mp4FullPath:%s", totalLen, mp4FullPath);
        File _video_file = new File(mp4FullPath);
        if( ! _video_file.exists() ) {
            XLog.e("attach not exists, path:%s", mp4FullPath);
            return;
        }
        // 查询message表
        /** message表记录
         * 进入类中, 搜索:    " msgid:"
         * iget v2, v10, Lcom/tencent/mm/modelvideo/r;->ilr:I       =>      iget v4, v10, Lcom/tencent/mm/modelvideo/r;->enN:I
         */
        long msgid = (int)getObjectField(videoinfo2_row, "enN");
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select msgSvrId, talker, content, status, type, isSend, createTime from message where msgid=?", new String[]{String.valueOf(msgid)} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }
        String field_talker = contentValues.getAsString("talker");
        String field_content = contentValues.getAsString("content");
        int field_isSend = contentValues.getAsInteger("isSend");
        if( field_isSend == 1 ){
            return;
        }
        long lMsgSvrid = contentValues.getAsLong("msgSvrId");       // 发送视频后
        int field_status = contentValues.getAsInteger("status");
        int field_type = contentValues.getAsInteger("type");
        long lCreateTime = contentValues.getAsLong("isSend");

        /** 组装消息 */
        if( done_msgSvrId_cache.containsKey(lMsgSvrid) ) {return;}      // 防重复
        else {done_msgSvrId_cache.put(lMsgSvrid, true);}

        JSONObject res_dict = new JSONObject();
        res_dict.put("type", field_type);
        res_dict.put("msgtype", WechatAction.video_msg_done);
        if (field_talker.endsWith("@chatroom")) {
            /** 群消息 */
            res_dict.put("room", field_talker);
            HashMap hashmap  = WechatAction.content_split(field_content, WechatAction.video_msg_done);
            res_dict.put("talker", hashmap.get("talker"));
            res_dict.put("content", hashmap.get("content"));
            if( hashmap.containsKey("duration")){
                res_dict.put("duration", hashmap.get("duration"));
            }
        } else {
            /** 好友消息 */
            res_dict.put("room", "");
            res_dict.put("talker", field_talker);
            res_dict.put("content", "");
        }
        res_dict.put("status", field_status);
        res_dict.put("isSend",field_isSend);
        res_dict.put("createTime", lCreateTime);

        File remoteFile = new File( WechatHook.remoteVideoDir, _video_file.getName() ) ; // 传送到远端 /MicroMsg 目录
        res_dict.put("fileFullPath", remoteFile.getAbsoluteFile() );
        res_dict.put("totalLen", totalLen);

        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%s.video.live", filename) ) ) {
            res_dict.put("live", true);
        }
        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%s.video.needbuf", filename) ) ) {
            // base64编码文件
            File file = new File(mp4FullPath);
            int size = (int) file.length();
            byte[] bytes = new byte[size];
            BufferedInputStream stream = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(file));
                stream.read(bytes, 0, bytes.length);
            }finally {
                if(stream != null) {
                    stream.close();
                }
            }
            String imgbuf = Base64.encodeToString(bytes, Base64.DEFAULT);  //与Python通信时建议用default
            res_dict.put("filebuf", imgbuf);
        }

        XLog.i("rpush msgtype:video_msg_done, fileFullPath:%s", mp4FullPath);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
        /** sftp 视频 */
        WechatHook.thread_touch.sftp_queue.put( Arrays.asList( _video_file.getParent(), _video_file.getName(), remoteFile.getParent(), remoteFile.getName(), "put", "1" ) );
    }

    //.method public static bc(Z)Ljava/lang/String;     com/tencent/mm/compatible/e/q
    void hookGUID(final ClassLoader loader) {
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/compatible/e/q"), "bc", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    boolean p1 = (boolean) param.args[0];
                    String result = (String) param.getResult();
                    XLog.d("hookGUID, p1: %b, result: %s", p1, result);
                } catch (Throwable e) {
                    XLog.e("hookGUID error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }

    void hookDownloadAttachResponse(final ClassLoader loader) {
        /** 打开: com/tencent/mm/ui/chatting/AppAttachDownloadUI$4
         * 进入类中, 搜索: IILcom
         * .method public final a(IILcom/tencent/mm/w/k;)V      =>      .method public final a(IILcom/tencent/mm/ab/l;)V
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/ui/chatting/AppAttachDownloadUI$4"), "a", int.class, int.class, "com.tencent.mm.ab.l", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    // note: p3 就是task类, 6311版本是 com/tencent/mm/pluginsdk/model/app/x
                    /** TaskResponse, type:162 */
                    int p1 = (int)param.args[0];
                    int totalLen = (int)param.args[1];
                    Object p3 = param.args[2];
                    if( p1 != totalLen ) return null;
                    XLog.i("downloadappattach UI callback, p1:%d, p2:%d", p1, totalLen);

                    /** message表记录
                     * 进入类中搜索:  " get msginfo failed mediaId:%s  msgId:%d"    向上寻找
                     * iget-object v0, p0, Lcom/tencent/mm/pluginsdk/model/app/ab;->ggq:Lcom/tencent/mm/storage/aw;     =>      iget-object v0, p0, Lcom/tencent/mm/pluginsdk/model/app/ac;->bXQ:Lcom/tencent/mm/storage/bd;
                     * 进入类中搜索:    "dancy download full xml succed! xml: [%s]"     向上寻找
                     * iget-object v4, p0, Lcom/tencent/mm/pluginsdk/model/app/ab;->smA:Lcom/tencent/mm/pluginsdk/model/app/b;      =>      iget-object v4, p0, Lcom/tencent/mm/pluginsdk/model/app/ac;->qAe:Lcom/tencent/mm/pluginsdk/model/app/b;
                     */
                    Object message_row = getObjectField(p3, "bXQ");
                    String field_talker, field_content;
                    int field_isSend=-1, field_status=-1, field_type=-1;
                    long lmsgid=-1, lMsgSvrid=-1, lCreateTime=-1;
                    lmsgid = (long) XposedHelpers.getObjectField(message_row, "field_msgId");
                    field_isSend = (int) XposedHelpers.getObjectField(message_row, "field_isSend");
                    field_status = (int) XposedHelpers.getObjectField(message_row, "field_status");
                    field_type = (int) XposedHelpers.getObjectField(message_row, "field_type");
                    lCreateTime = (long) XposedHelpers.getObjectField(message_row, "field_createTime");
                    field_talker = (String) XposedHelpers.getObjectField(message_row, "field_talker");
                    field_content = (String) XposedHelpers.getObjectField(message_row, "field_content");
                    if( XposedHelpers.getObjectField(message_row, "field_msgSvrId") != null)
                        lMsgSvrid = (long) XposedHelpers.getObjectField(message_row, "field_msgSvrId");

                    /** appattach表记录 */
                    Object appattach_row = getObjectField(p3, "qAe");
                    long status = (long) XposedHelpers.getObjectField(appattach_row, "field_status");
                    String fileFullPath = (String) XposedHelpers.getObjectField(appattach_row, "field_fileFullPath");
                    XLog.i("appattach_row, status:%d, fileFullPath:%s", status, fileFullPath);
                    File _attach_file = new File(fileFullPath);
                    if( ! _attach_file.exists() ) {
                        XLog.e("attach not exists, path:%s", fileFullPath);
                        return null;
                    }

                    /** 组装消息 */
                    if( done_msgSvrId_cache.containsKey(lMsgSvrid) ) {return null;}      // 防重复
                    else {done_msgSvrId_cache.put(lMsgSvrid, true);}
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("type", field_type);
                    res_dict.put("msgtype", WechatAction.attach_msg_done);
                    if (field_talker.endsWith("@chatroom")) {
                        /** 群消息 */
                        res_dict.put("room", field_talker);
                        HashMap hashmap = WechatAction.content_split(field_content, WechatAction.attach_msg_done);
                        res_dict.put("talker", hashmap.get("talker"));
                        res_dict.put("content", hashmap.get("content"));
                    } else {
                        /** 好友消息 */
                        res_dict.put("room", "");
                        res_dict.put("talker", field_talker);
                        res_dict.put("content", "");
                    }
                    res_dict.put("status", field_status);
                    res_dict.put("isSend",field_isSend);
                    res_dict.put("createTime", lCreateTime);

                    File remoteFile = new File( WechatHook.remoteAttachDir, _attach_file.getName() ) ; // 传送到远端 /MicroMsg 目录
                    res_dict.put("fileFullPath", remoteFile.getAbsoluteFile() );
                    res_dict.put("totalLen", totalLen);

                    Map appmsg = WechatClass.parseMessageContent(field_content, null);
                    if(BuildConfig.DEBUG) {
                        XLog.d("appmsg: %s", appmsg);
                    }
                    String title = (String)appmsg.get(".msg.appmsg.title");
                    String description = (String)appmsg.get(".msg.appmsg.des");
                    res_dict.put("title", title);
                    res_dict.put("description", description);

                    if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.attach.live", lmsgid) ) ) {
                        res_dict.put("live", true);
                    }
                    if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.attach.needbuf", lmsgid) ) ) {
                        // base64编码文件
                        File file = new File(fileFullPath);
                        int size = (int) file.length();
                        byte[] bytes = new byte[size];
                        BufferedInputStream stream = null;
                        try {
                            stream = new BufferedInputStream(new FileInputStream(file));
                            stream.read(bytes, 0, bytes.length);
                        }finally {
                            if(stream != null) {
                                stream.close();
                            }
                        }
                        String imgbuf = Base64.encodeToString(bytes, Base64.DEFAULT);  //与Python通信时建议用default
                        res_dict.put("filebuf", imgbuf);
                    }

                    XLog.i("rpush msgtype:attach_msg_done, fileFullPath:%s", fileFullPath);
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                    /** sftp 附件 */
                    WechatHook.thread_touch.sftp_queue.put( Arrays.asList( _attach_file.getParent(), _attach_file.getName(), remoteFile.getParent(), remoteFile.getName(), "put", "1" ) );
                } catch (Throwable e) {
                    XLog.e("hookDownloadAttach error. stack:%s", android.util.Log.getStackTraceString(e));
                }
                return null;
            }
        });
//        /** 流程分析: ./下载附件/收到文件-下载文件流程分析.txt
//         * 搜索: "/cgi-bin/micromsg-bin/downloadappattach"
//         * com/tencent/mm/pluginsdk/model/app/x     =>      com/tencent/mm/pluginsdk/model/app/ab
//         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/o;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V
//         * Field
//         * iBg      =>      smA         搜索"argument is not consistent", 定位上下文
//         */
//        findAndHookMethod(WechatClass.get("com/tencent/mm/pluginsdk/model/app/ab", "downloadappattach"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.p", byte[].class,
//                new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        try{
//                            /**  /downattach 162  */
//                            /** appattach表记录 */
//                            int p1 = (int)param.args[0];
//                            int p2 = (int)param.args[1];
//                            Object appattach_row = getObjectField(param.thisObject, "smA");
//                            long status = (long) getObjectField(appattach_row, "field_status");
//                            if( status != 199){    // == 0xc   note. 下载完成时, 状态为 0xc
//                                return;
//                            }
//                            XLog.i("downloadappattach without UI callback, status:%d, p1:%d, p2:%d", status, p1, p2);
//                            if(true) return;    // note 6510会崩溃
//                            long field_msgInfoId = (long) getObjectField(appattach_row, "field_msgInfoId");
//                            long field_totalLen = (long) getObjectField(appattach_row, "field_totalLen");
//                            String fileFullPath = (String) getObjectField(appattach_row, "field_fileFullPath");
//                            XLog.i("appattach_row, field_msgInfoId:%d, fileFullPath:%s, field_totalLen:%d", field_msgInfoId, fileFullPath, field_totalLen);
//                            File _attach_file = new File(fileFullPath);
//                            if( ! _attach_file.exists() ) {
//                                XLog.e("attach not exists, path:%s", fileFullPath);
//                                return;
//                            }
//                            /** message表记录 */
//                            ContentValues message_row = Db.getMessageByMsgId(field_msgInfoId);
//                            String field_talker, field_content;
//                            int field_isSend=-1, field_status=-1, field_type=-1;
//                            long lMsgSvrid=-1, lCreateTime=-1;
//                            field_isSend = message_row.getAsInteger("isSend");
//                            field_status = message_row.getAsInteger("status");
//                            field_talker = message_row.getAsString("talker");
//                            field_content = message_row.getAsString("content");
//                            field_type = message_row.getAsInteger("type");
//                            lCreateTime = message_row.getAsLong("createTime");
//                            if(message_row.containsKey("msgSvrId"))
//                                lMsgSvrid = message_row.getAsLong("msgSvrId");
//
//                            /** 组装消息 */
//                            JSONObject res_dict = new JSONObject();
//                            res_dict.put("type", field_type);
//                            res_dict.put("msgtype", WechatAction.attach_msg_done);
//                            if (field_talker.endsWith("@chatroom")) {
//                                /** 群消息 */
//                                res_dict.put("room", field_talker);
//                                HashMap hashmap = WechatAction.content_split(field_content, WechatAction.attach_msg_done);
//                                res_dict.put("talker", hashmap.get("talker"));
//                                res_dict.put("content", hashmap.get("content"));
//                            } else {
//                                /** 好友消息 */
//                                res_dict.put("room", "");
//                                res_dict.put("talker", field_talker);
//                                res_dict.put("content", "");
//                            }
//                            res_dict.put("status", field_status);
//                            res_dict.put("isSend",field_isSend);
//                            res_dict.put("createTime", lCreateTime);
//
//                            File remoteFile = new File( WechatHook.remoteAttachDir, _attach_file.getName() ) ; // 传送到远端 /MicroMsg 目录
//                            res_dict.put("fileFullPath", remoteFile.getAbsoluteFile() );
//                            res_dict.put("totalLen", field_totalLen);
//                            if( done_msgSvrId_cache.containsKey(lMsgSvrid) ) {return;}      // 防重复
//                            else {done_msgSvrId_cache.put(lMsgSvrid, true);}
//                            XLog.i("rpush msgtype:attach_msg_done, fileFullPath:%s", fileFullPath);
//                            WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
//                            /** sftp 附件 */
//                            WechatHook.thread_touch.sftp_queue.put( Arrays.asList( _attach_file.getParent(), _attach_file.getName(), remoteFile.getParent(), remoteFile.getName(), "put", "1" ) );
//                        } catch (Throwable e) {
//                            XLog.e("hookDownloadAttach error. stack:%s", android.util.Log.getStackTraceString(e));
//                        }
//                    }
//                }
//        );
    }   /** hookDownloadAttachResponse 结束 */


    void hookUploadImgResponse(final ClassLoader loader) {
        /** 搜索:     /cgi-bin/micromsg-bin/uploadmsgimg
         * 进入类中向下搜索:       "onGYNetEnd invalid server return value : startPos = "      定位函数
         * com/tencent/mm/ak/l
         * .method private a(Lcom/tencent/mm/ak/e;IJILcom/tencent/mm/modelcdntran/keep_SceneResult;)Z
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/ak/l"), "a",
                WechatClass.get("com/tencent/mm/ak/e"), int.class, long.class, int.class, WechatClass.get("com/tencent/mm/modelcdntran/keep_SceneResult"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    // note: 并不是所有发送图片都会触发, 只有没有缓存的图片才触发
                    Object ImgInfo2_row = param.args[0];
                    int offset = (int) param.args[1];
                    long msgSvrId = (long) param.args[2];
                    int timestamp = (int) param.args[3];
                    XLog.i("uploadmsgimg response, ImgInfo2_row: %s, offset: %d, msgSvrId: %d, timestamp: %d", ImgInfo2_row, offset, msgSvrId, timestamp);
                    //
                    if( msgSvrId != 0 ){
                        // note: 上传图片完毕
                    }
                } catch (Throwable e) {
                    XLog.e("hookUploadImgResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });

        /**
         * .method public final a(Ljava/lang/String;ILcom/tencent/mm/modelcdntran/keep_ProgressInfo;Lcom/tencent/mm/modelcdntran/keep_SceneResult;Z)I
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/ak/l$4"), "a",
                String.class, int.class, WechatClass.get("com/tencent/mm/modelcdntran/keep_ProgressInfo"),WechatClass.get("com/tencent/mm/modelcdntran/keep_SceneResult"), boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try{
                            String p1 = (String) param.args[0];     // aupimg_d01616e4091a0a98_1537692782147
                            int p2 = (int) param.args[1];
                            Object keep_ProgressInfo = param.args[2];
                            Object keep_SceneResult = param.args[3];
                            boolean p5 = (boolean) param.args[4];
                            XLog.i("upload img response, p1: %s, p2: %d, keep_ProgressInfo: %s, p5: %b", p1, p2, keep_ProgressInfo, p5);
                            if( keep_SceneResult != null){
                                XLog.d("keep_SceneResult: %s", keep_SceneResult);
                            }
                            //
                            if( keep_ProgressInfo != null ) {
                                boolean isUploadTask = (boolean) getObjectField(keep_ProgressInfo, "field_isUploadTask");
                                int status = (int) getObjectField(keep_ProgressInfo, "field_status");
                                int totalLength = (int) getObjectField(keep_ProgressInfo, "field_toltalLength");
                                int finishedLength = (int) getObjectField(keep_ProgressInfo, "field_finishedLength");
                                XLog.i("isUploadTask: %b, status: %d, totalLength: %d, finishedLength: %d", isUploadTask, status, totalLength, finishedLength);
                            }
                        } catch (Throwable e) {
                            XLog.e("hookUploadImgResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                        }
                    }
                });
    }


    void hookReceivewxhbResponse(final ClassLoader loader) {
        /** 打开红包第一步, 封面
         * 搜索:  "/cgi-bin/mmpay-bin/receivewxhb"
         * com/tencent/mm/plugin/luckymoney/c/ae        =>      com/tencent/mm/plugin/luckymoney/b/ag
         * 类中搜索:    "timingIdentifier"
         * .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V        =>      .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ag", "/cgi-bin/mmpay-bin/receivewxhb"), "a", int.class, String.class, JSONObject.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    /** TaskResponse, type: 1581 */
                    int p1 = (int) param.args[0];           // 0
                    String p2 = (String) param.args[1];     // 字符串: "ok"
                    JSONObject p3 = (JSONObject)param.args[2];
                    XLog.i("receivewxhb callback, p1:%d, p2:%s, p3:%s", p1, p2, p3);

                    /** 下面的一般不会变化
                     *  "timingIdentifier"
                     *  "sendId"
                     *  "msgtype"
                     *  "channelid"
                     */
                    String timingIdentifier = p3.getString("timingIdentifier");
                    if (TextUtils.isEmpty(timingIdentifier)) {
                        XLog.d("timingIdentifier null");
                        return;
                    }
                    String sendid = p3.getString("sendId");
                    if (TextUtils.isEmpty(sendid)) {
                        XLog.d("sendId null");
                        return;
                    }
                    if( ! MySync.sendid2luckymoney.containsKey(String.format(Locale.ENGLISH, "%s.luckymoney.nativeurl", sendid) ) ){
                        XLog.d("not contain sendid:%s", sendid);
                        return;
                    }

                    String nativeurl = (String)MySync.sendid2luckymoney.get(String.format(Locale.ENGLISH, "%s.luckymoney.nativeurl", sendid));
                    String talker = (String)MySync.sendid2luckymoney.get(String.format(Locale.ENGLISH, "%s.luckymoney.talker", sendid));    // note: 因为有可能是roomid, 而sendUserName是个人
                    Uri uri = Uri.parse(nativeurl);
                    int msgtype = Integer.parseInt(uri.getQueryParameter("msgtype"));
                    int channelid = Integer.parseInt(uri.getQueryParameter("channelid"));

                    /** 搜索:     "start to open lucky"
                     * invoke-direct/range {v0 .. v9}, Lcom/tencent/mm/plugin/luckymoney/c/ab;-><init>(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
                     * =>   invoke-direct/range {v0 .. v9}, Lcom/tencent/mm/plugin/luckymoney/b/ad;-><init>(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
                     */
                    Object luckyMoneyRequest = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ad", "/cgi-bin/mmpay-bin/openwxhb"), msgtype, channelid, sendid, nativeurl, "", "", talker, "v1.0", timingIdentifier);
                    WechatClass.postTask(luckyMoneyRequest);
                } catch (Throwable e) {
                    XLog.e("hookReceivewxhbResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookOpenwxhbResponse(final ClassLoader loader) {
        /** 打开红包第二步, 开
         * 搜索:  "/cgi-bin/mmpay-bin/openwxhb"
         * com/tencent/mm/plugin/luckymoney/c/ab        =>      com/tencent/mm/plugin/luckymoney/b/ad
         * 类中搜索:    "real_name_info"
         * .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V        =>      .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ad", "/cgi-bin/mmpay-bin/receivewxhb"), "a", int.class, String.class, JSONObject.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    /** TaskResponse, type: 1581 */
                    int p1 = (int) param.args[0];           // 0
                    String p2 = (String) param.args[1];     // note: "ok"
                    JSONObject p3 = (JSONObject)param.args[2];
                    XLog.i("openwxhb callback, p1:%d, p2:%s, p3:%s", p1, p2, p3);
                    //
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.luckymoney_detail);
                    res_dict.put("data", p3);

                    XLog.i("rpush msgtype:luckymoney_detail");
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                } catch (Throwable e) {
                    XLog.e("hookOpenwxhbResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookQrydetailwxhbResponse(final ClassLoader loader) {
        /** 红包领取详细信息
         * 搜索:      "/cgi-bin/mmpay-bin/qrydetailwxhb"
         * com/tencent/mm/plugin/luckymoney/c/u     =>      com/tencent/mm/plugin/luckymoney/b/w
         * 类中搜索:        "parse luckyMoneyDetail fail: "
         * .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V        =>      .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V
         */
        XposedHelpers.findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/w", "/cgi-bin/mmpay-bin/qrydetailwxhb"), "a", int.class, String.class, JSONObject.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    /** TaskResponse, type: 1581 */
                    int p1 = (int) param.args[0];           // 0
                    String p2 = (String) param.args[1];     // note: "ok"
                    JSONObject p3 = (JSONObject)param.args[2];
                    XLog.i("qrydetailwxhb callback, p1:%d, p2:%s, p3:%s", p1, p2, p3);
                    //
//                            String sendUserName = p3.getString("sendUserName"); // 发红包的人
//                            int isContinue = p3.getInt("isContinue"); // 领取完没有?
//                            int totalAmount = p3.getInt("totalAmount"); // 总金额
//                            int amount = p3.getInt("amount"); // 领取金额
//                            int isSender = p3.getInt("isSender"); // 是否发红包之人
//                            int recAmount = p3.getInt("recAmount"); // record数量?
//                            JSONArray record = p3.getJSONArray("record"); // record详情
                    // note: luckymoney_detail
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.luckymoney_detail);
                    res_dict.put("data", p3);

                    XLog.i("rpush msgtype:luckymoney_detail");
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                } catch (Throwable e) {
                    XLog.e("hookQrydetailwxhbResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookSendButton(final ClassLoader loader) {
        /** 发送消息按钮
         // 搜索:     "send msg onClick"
         // 更新步骤: 版本更新.txt
         * com/tencent/mm/pluginsdk/ui/chat/ChatFooter$2      =>      com/tencent/mm/pluginsdk/ui/chat/ChatFooter$3
         * .method public final declared-synchronized onClick(Landroid/view/View;)V     =>      .method public final declared-synchronized onClick(Landroid/view/View;)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/pluginsdk/ui/chat/ChatFooter$3"), "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try{
                    XLog.i("SendButton click");
                    // 获取文本框内容
                    /** 更新步骤: 6510版本更新.txt
                     * iget-object v0, p0, Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter$2;->sAv:Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;      =>      iget-object v0, p0, Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter$3;->qMv:Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;
                     * * invoke-static {v0}, Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;->a(Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;)Lcom/tencent/mm/ui/widget/MMEditText;       =>          invoke-static {v0}, Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;->a(Lcom/tencent/mm/pluginsdk/ui/chat/ChatFooter;)Lcom/tencent/mm/ui/widget/MMEditText;
                     */
                    Object _chatfooter = getObjectField(param.thisObject, "qMv");
                    Object _mmedittext = callStaticMethod(WechatClass.get("com/tencent/mm/pluginsdk/ui/chat/ChatFooter"), "a", _chatfooter);
                    Object _editable = callMethod(_mmedittext, "getText");
                    String text = _editable.toString();
                    String[] text_list = text.split("\\|");
                    XLog.i("raw Text:%s, split length:%d", text, text_list.length);
                    if( text_list.length == 0 ) {
                        XLog.e("text_list is empty");
                        return;
                    }
                    if (BuildConfig.DEBUG) {
                        if( WechatHook.thread_touch.thread_action._button_click(text_list, loader) ) {
                            param.setResult(null);
                        }else{
                            XLog.w("not match, text_list[0]:%s", text_list[0]);
                        }
                    }
                } catch (Throwable e) {
                    XLog.e("hookSendButton error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookVerifyResponse(final ClassLoader loader) {
        /** 搜索:"/cgi-bin/micromsg-bin/verifyuser"
         * com/tencent/mm/pluginsdk/model/m     =>      com/tencent/mm/pluginsdk/model/m
         * 然后搜索 III
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/pluginsdk/model/m", "verifyuser"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String p4 = (String)param.args[3];
                    XLog.i("VerifyResponse, type:137, tips:%s", p4);
                    if( ! TextUtils.equals(p4, "user need verify") ) {
                        // 不需要验证时, 返回
                        return;
                    }
                    /** 搜索:     "This NetSceneVerifyUser init NEVER use opcode == MM_VERIFYUSER_VERIFYOK"       向下寻找
                     * iput-object p2, p0, Lcom/tencent/mm/pluginsdk/model/m;->slm:Ljava/util/List;     =>      iput-object p2, p0, Lcom/tencent/mm/pluginsdk/model/m;->qyZ:Ljava/util/List;
                     */
                    LinkedList<String> wxid_list = (LinkedList)getObjectField(param.thisObject, "qyZ");
                    String wxid = wxid_list.get(0);
                    if( ! WechatHook.wxid2addfriend.containsKey(wxid) ){
                        XLog.e("wxid2addfriend not contain wxid:%s", wxid);
                        return;
                    }
                    if( ! wxid2v1_encryptUsername.containsKey(wxid) ){
                        XLog.e("wxid2v1_encryptUsername not contain wxid:%s", wxid);
                        return;
                    }
                    String v1_encryptUsername = wxid2v1_encryptUsername.get(wxid);
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("wxid", wxid);
                    res_dict.put("v1_encryptUsername", v1_encryptUsername);
                    WechatHook.thread_touch.thread_action._sayhi_with_snspermission(res_dict);
                } catch (Throwable e) {
                    XLog.e("VerifyResponse Constructor error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookSearchcontactResponse(final ClassLoader loader) {
        if(BuildConfig.DEBUG){
            /** 搜索:     "/cgi-bin/micromsg-bin/searchcontact"       向上得到
             * com/tencent/mm/modelsimple/aa        =>      com/tencent/mm/plugin/messenger/a/f
             * .method public constructor <init>(Ljava/lang/String;IIZ)V        =>      .method public constructor <init>(Ljava/lang/String;IIZ)V
             */
            findAndHookConstructor(WechatClass.get("com/tencent/mm/plugin/messenger/a/f", "searchcontact"), String.class, int.class, int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        // new instance searchcontact, 用来Debug
                        String p1 = (String)param.args[0];
                        int p2 = (int)param.args[1];
                        int p3 = (int)param.args[2];
                        boolean p4 = (boolean)param.args[3];
                        XLog.d("Searchcontact Constructor, p1:%s, p2:%d, p3:%d, p4:%b", p1, p2, p3, p4);
                    } catch (Throwable e) {
                        XLog.e("searchcontact Constructor error. stack:%s", android.util.Log.getStackTraceString(e));
                    }
                }
            });
        }

        /** 子任务.    类中搜索:   "search RES username [%s]"      677
         * com/tencent/mm/modelsimple/aa       =>      com/tencent/mm/plugin/messenger/a/f
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/messenger/a/f", "searchcontact"), "a", int.class, int.class, int.class, String.class, "com.tencent.mm.network.q", byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String p4 = (String)param.args[3];
                    XLog.d("Searchcontact Response, type:106, tips:%s", p4);
                    if( !TextUtils.equals(p4, "Everything is OK") ) {
                        XLog.e("Searchcontact Response fail. tips:%s", p4);
                        return;
                    }
                    // 搜索微信号响应, 得到v1参数, 用于添加好友
                    /** 类中正则搜索:     .method.*com/tencent/mm/protocal        677
                     * .method public final IZ()Lcom/tencent/mm/protocal/c/auj;     =>     .method public final bcS()Lcom/tencent/mm/protocal/c/bja;
                     * 进入函数返回类, 搜索:     pswitch_e
                     * iput-object v0, v1, Lcom/tencent/mm/protocal/c/auj;->hLy:Ljava/lang/String;      =>      iput-object v0, v1, Lcom/tencent/mm/protocal/c/bja;->eJM:Ljava/lang/String;
                     */
                    Object obj_protocal_b_amk = callMethod(param.thisObject, "bcS");
                    String alias = (String)getObjectField(obj_protocal_b_amk, "eJM");

                    /** 通过断点得到的变量v0图, 才能看清楚
                     * note: 投机取巧方式, 搜索:  "search RES username [%s]"
                     * iget-object v3, v0, Lcom/tencent/mm/protocal/c/auh;->tdh:Lcom/tencent/mm/protocal/c/atu;
                     * invoke-static {v3}, Lcom/tencent/mm/platformtools/n;->a(Lcom/tencent/mm/protocal/c/atu;)Ljava/lang/String;
                     * =======================>
                     * iget-object v4, v1, Lcom/tencent/mm/protocal/c/biy;->rvi:Lcom/tencent/mm/protocal/c/bhz;
                     * invoke-static {v4}, Lcom/tencent/mm/platformtools/ab;->a(Lcom/tencent/mm/protocal/c/bhz;)Ljava/lang/String;
                     */
                    Object v1_encryptUsername_raw = getObjectField(obj_protocal_b_amk, "rvi");
                    String v1_encryptUsername = (String)callStaticMethod(WechatClass.platformtools_n, "a", new Class[]{WechatClass.protocal_b_aly}, v1_encryptUsername_raw);

                    /**
                     * 进入类中, com/tencent/mm/protocal/c/auh      =>      com/tencent/mm/protocal/c/biy,  搜索:   "Not all required fields were included: UserName"
                     * iget-object v1, p0, Lcom/tencent/mm/protocal/c/auh;->ttW:Lcom/tencent/mm/protocal/c/atu;     =>  iget-object v1, p0, Lcom/tencent/mm/protocal/c/biy;->rQz:Lcom/tencent/mm/protocal/c/bhz;
                     */
                    Object nickname_raw = getObjectField(obj_protocal_b_amk, "rQz");
                    String nickname = (String)callStaticMethod(WechatClass.platformtools_n, "a", new Class[]{WechatClass.protocal_b_aly}, nickname_raw);

                    /**
                     * 进入类中, com/tencent/mm/protocal/c/auh      =>      com/tencent/mm/protocal/c/biy,  搜索:   "Not all required fields were included: PYInitial"
                     * iget-object v1, p0, Lcom/tencent/mm/protocal/c/auh;->tcY:Lcom/tencent/mm/protocal/c/atu;     =>      iget-object v1, p0, Lcom/tencent/mm/protocal/c/biy;->ruU:Lcom/tencent/mm/protocal/c/bhz;
                     */
                    Object wxid_raw = getObjectField(obj_protocal_b_amk, "ruU"); //使用主动搜索功能时, 真实值是wxid, 而Intent;->putExtra对应名"Contact_QuanPin"
                    String wxid = (String)callStaticMethod(WechatClass.platformtools_n, "a", new Class[]{WechatClass.protocal_b_aly}, wxid_raw); // 返回 Lcom/tencent/mm/protocal/b/aly;->jHw:Ljava/lang/String; 字段
                    if(wxid.length()==18 && wxid.startsWith("wxid")){
                        wxid = "wxid_" + wxid.substring(4);
                    }

                    /**
                     * 进入类中, com/tencent/mm/protocal/c/auh      =>      com/tencent/mm/protocal/c/biy,  搜索:   pswitch_19
                     * iput-object v0, v1, Lcom/tencent/mm/protocal/c/auh;->tkV:Ljava/lang/String;      =>      iput-object v0, v1, Lcom/tencent/mm/protocal/c/biy;->rEJ:Ljava/lang/String;
                     */
                    String v2_ticket = (String)getObjectField(obj_protocal_b_amk, "rEJ");
                    XLog.d("Searchcontact Response wxid:%s, alias:%s, nickname:%s, v1_encryptUsername:%s, v2_ticket:%s", wxid, alias, nickname, v1_encryptUsername, v2_ticket);
                    if( !WechatHook.wxid2addfriend.containsKey(wxid) ) {
                        XLog.d("searchcontact not contain wxid:%s", wxid);
                        return;
                    }
                    // TODO 当自己把对方删除, 对方未把自己删除的情况. 仍然可以直接就发送消息
                    // 上面已经确定"Everything is OK", 调用/cgi-bin/micromsg-bin/verifyuser 验证对方用户, 包括( 1.免验证返回ok; 2.开验证返回需验证 )
                    wxid2v1_encryptUsername.put(wxid, v1_encryptUsername);
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("wxid", wxid);
                    res_dict.put("ticket", v2_ticket);
                    WechatHook.thread_touch.thread_action._verifyuser(res_dict);
                } catch (Throwable e) {
                    XLog.e("Searchcontact Response error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    void hookTaskResponse(final ClassLoader loader) {
        if (BuildConfig.DEBUG) {
            /** 主任务:, 更新步骤: 6510更新.txt,  搜索:    "doscene mmcgi Failed type:%d hash[%d,%d] cancel[%b] run:%d wait:%d ret:%d autoauth:%d"
             * com/tencent/mm/w/n$5     =>      com/tencent/mm/ab/o$5
             * .method public final run()V      =>      .method public final run()V
             */
            // 任务分发函数, 获取搜索任务返回结果. 因为后台调用Call, 返回时不唤起UI界面, 导致不能进入子函数, 所以hook该层函数
            findAndHookMethod(WechatClass.get("com/tencent/mm/ab/o$5"), "run", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        // response_handler 就是内层真实的处理类
                        /** 类中, 向下寻找
                         * iget-object v5, p0, Lcom/tencent/mm/w/n$5;->fOH:Lcom/tencent/mm/w/k;     =>      iget-object v5, p0, Lcom/tencent/mm/ab/o$5;->bFp:Lcom/tencent/mm/ab/l;
                         * invoke-virtual {v5}, Lcom/tencent/mm/w/k;->getType()I        =>      invoke-virtual {v5}, Lcom/tencent/mm/ab/l;->getType()I
                         */
                        Object response_handler = getObjectField(param.thisObject, "bFp");
                        if (response_handler == null) {
                            XLog.w("response_handler is null!");
                            return;
                        }
                        int type = (int) callMethod(response_handler, "getType");
                        //String tips = (String)getObjectField(param.thisObject, "bGb");
                        if (type != 0) {
                            XLog.i("TaskResponse, type: %d", type);
                            //XLog.i("TaskResponse type:%d, tips:%s", type, tips);
                        }

                        if (!is_afterLogin) {
                            return;
                        }
                        /**
                         * 搜索:      "/cgi-bin/micromsg-bin/searchcontact"       677
                         * com/tencent/mm/modelsimple/aa     =>      com/tencent/mm/plugin/messenger/a/f
                         */
                        if (response_handler.getClass() == WechatClass.get("com/tencent/mm/plugin/messenger/a/f", "searchcontact") ) {
                            //XLog.d("111111111, type:%d", type);     // note type = 106
                        } else if (response_handler.getClass() == WechatClass.NetSceneVerifyUser_dkverify) {
                            //XLog.d("222222222, type:%d", type);   // note type = 30
                        }
                    } catch (Throwable e) {
                        XLog.e("hookTaskResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                    }
                }
            });
        }
    }   /** hookTaskResponse 结束 */

    /**
    protected boolean remarkMember(int field_type, String wxid, String field_nickname) {
        if (!wxid2remark.containsKey(wxid)) {
            //TODO 通过修改rcontact表的remarkName字段实现
            if (field_type == 3) {
                //好友
                String remarkName = field_nickname + WechatAction.DB_F + wxid;
                wxid2remark.put(wxid, remarkName);
            } else if (field_type == 4) {
                //群友, 非好友关系
                String remarkName = field_nickname + WechatAction.DB_F + wxid;
                wxid2remark.put(wxid, remarkName);
                String wxid_v2 = "wxid_jy8batoqm0so12";
                String remarkName_v1 = wxid_v2;
                Object v0 = newInstance(WechatHook.storage_an, wxid_v2, remarkName_v1);
                Object obj_storage_ao = callMethod(callStaticMethod(MMCore, "tD"), "rr");
                callMethod(obj_storage_ao, "c", new Class[]{WechatHook.r_j}, v0);
            } else {
                //impossible
                XLog.e("wxid2remark impossible.field_type:%s", field_type);
            }
        }
        return true;
    }
    */


    void hookSetSalt(final ClassLoader loader) {
        findAndHookMethod(WechatClass.get("com/tenpay/android/wechat/TenpaySecureEditText"), "setSalt", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String p1 = (String) param.args[0];
                    XLog.d("setSalt, p1: %s", p1);
                } catch (Throwable e) {
                    XLog.e("hookSetSalt error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookSetSalt 结束 */


    String reqkey = null;
    void hookRequestwxhbResponse(final ClassLoader loader) {
        // 微信红包订单请求返回
        /** 搜索:    "/cgi-bin/mmpay-bin/requestwxhb"     667
         * =>       com/tencent/mm/plugin/luckymoney/b/ae
         * =>       .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ae", "/cgi-bin/mmpay-bin/requestwxhb"), "a", int.class, String.class, JSONObject.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    int p1 = (int) param.args[0];
                    String p2 = (String) param.args[1];
                    JSONObject p3 = (JSONObject) param.args[2];
                    XLog.d("requestwxhb response, p1: %d, p2: %s, p3: %s", p1, p2, p3);

                    /**
                     *
                     */
                    String sendMsgXml = p3.getString("sendMsgXml");
                    Map appmsg = WechatClass.parseChatroomMsgsource(sendMsgXml, "msg");
                    //XLog.d("sendMsgXml: %s", appmsg);
                    // 判断 title
                    String title_raw = (String) appmsg.get(".msg.appmsg.wcpayinfo.receivertitle");
                    String[] titles = title_raw.split(WechatAction.DB_F, 2);
                    if( titles.length != 2 ){
                        return;     // 不是插件自动发送的红包
                    }

                    // PayInfo
                    reqkey = p3.getString("reqkey");
                    Object PayInfo = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/wallet/PayInfo"));

                    setObjectField(PayInfo, "bOd", reqkey);
                    setObjectField(PayInfo, "bVY", 0x25);   // 代码固定值
                    setObjectField(PayInfo, "bVU", 14);     // "pay_channel"

                    Object sns_qrcodeusebindquery = newInstance(WechatClass.get("com/tencent/mm/plugin/wallet/pay/a/c/f", "/cgi-bin/mmpay-bin/tenpay/sns_qrcodeusebindquery"), PayInfo, 0x2);
                    WechatClass.postTask(sns_qrcodeusebindquery);
                } catch (Throwable e) {
                    XLog.e("hookRequestwxhbResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookRequestwxhbResponse 结束 */


    void hookBindQueryResponse(final ClassLoader loader) {
        // 绑定的银行卡信息返回
        /** 搜索:    "/cgi-bin/mmpay-bin/tenpay/qrcodeusebindquery"     667
         * =>       com/tencent/mm/plugin/wallet/pay/a/c/e
         * =>       .method public final a(ILjava/lang/String;Lorg/json/JSONObject;)V
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/plugin/wallet/pay/a/c/e", "/cgi-bin/mmpay-bin/tenpay/qrcodeusebindquery"), "a", int.class, String.class, JSONObject.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    int p1 = (int) param.args[0];
                    String p2 = (String) param.args[1];
                    JSONObject p3 = (JSONObject) param.args[2];
                    XLog.d("qrcodeusebindquery response, p1: %d, p2: %s, p3: %s", p1, p2, p3);

                    JSONObject bindqueryresp = p3.getJSONObject("bindqueryresp");
                    long time_stamp = bindqueryresp.getLong("time_stamp");
                    /**
                     *
                     */
                    // UI password
                    Object TenpaySecureEditText = newInstance(WechatClass.get("com/tenpay/android/wechat/TenpaySecureEditText"), WechatClass.wechatContext);
                    callMethod(TenpaySecureEditText, "setSalt", String.valueOf(time_stamp));
                    callMethod(TenpaySecureEditText, "setText", MySync.hb_password);
                    String encrypt_password = (String) callMethod(TenpaySecureEditText, "getEncryptDataWithHash", true, false);
                    XLog.d("encrypt password: %s", encrypt_password);

                    // Orders
                    Object Orders = callStaticMethod(WechatClass.get("com/tencent/mm/plugin/wallet_core/model/Orders"), "ah", p3);

                    // PayInfo
                    Object PayInfo = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/wallet/PayInfo"));
                    setObjectField(PayInfo, "bOd", reqkey);
                    setObjectField(PayInfo, "bVY", 0x25);
                    setObjectField(PayInfo, "bVU", 14);     // pay_channel

                    // Authen
                    Object Authen = newInstance(WechatClass.get("com/tencent/mm/plugin/wallet_core/model/Authen"));
                    setObjectField(Authen, "bWA", 0x3);
                    setObjectField(Authen, "pli", encrypt_password);
                    setObjectField(Authen, "lMW", "CFT");
                    setObjectField(Authen, "lMV", "CFT");
                    setObjectField(Authen, "plu", null);
                    setObjectField(Authen, "plt", null);
                    setObjectField(Authen, "mpb", PayInfo);

                    // 发送请求: "/cgi-bin/mmpay-bin/tenpay/authen"
                    Object authen_task = callStaticMethod(WechatClass.get("com/tencent/mm/plugin/wallet/pay/a/a"), "a", Authen, Orders, false);
                    WechatClass.postTask(authen_task);
                } catch (Throwable e) {
                    XLog.e("hookBindQueryResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }   /** hookBindQueryResponse 结束 */


    boolean needUpdateMemberWxid(String roomid, String field_memberlist) {
        if(room2memberlist.containsKey(roomid)){
            String memberlist = room2memberlist.get(roomid);
            if( TextUtils.equals(memberlist, field_memberlist) ){
                XLog.i("no needUpdateMemberWxid");
                return false;
            }else{
                XLog.i("needUpdateMemberWxid");
                return true;
            }
        }else{
            XLog.i("needUpdateMemberWxid");
            return true;
        }
    }


    boolean needUpdateMemberName(String roomid, String new_displayname) {
        if(room2displayname.containsKey(roomid)){
            String memberlist = room2displayname.get(roomid);
            if( TextUtils.equals(memberlist, new_displayname) ){
                XLog.i("no needUpdateMemberName");
                return false;
            }else{
                XLog.i("needUpdateMemberName");
                return true;
            }
        }else{
            XLog.i("needUpdateMemberName");
            return true;
        }
    }


    boolean needUpdateRoomowner(String roomid, String new_roomowner) {
        if(room2roomowner.containsKey(roomid)){
            String memberlist = room2roomowner.get(roomid);
            if( TextUtils.equals(memberlist, new_roomowner) ){
                XLog.i("no needUpdateRoomowner");
                return false;
            }else{
                XLog.i("needUpdateRoomowner");
                return true;
            }
        }else{
            XLog.i("needUpdateRoomowner");
            return true;
        }
    }


    void hookSyncChatroomMember(final ClassLoader loader) {
        /** 群成员变动通知. (Hook住获取成员信息的对象, 返回群组织结构体, 具体查看 SynChatroomMember.txt)
         * 触发场景： 群员增减, 更新群成员自改别名, 群主变更
         * 先定位调用函数的上层,  搜索 "syncAddChatroomMember OldVer:%d"
         * com/tencent/mm/s/j
         * .method public static a(Ljava/lang/String;Ljava/lang/String;Lcom/tencent/mm/protocal/c/jn;Ljava/lang/String;Lcom/tencent/mm/g/a/a/a;Lcom/tencent/mm/sdk/b/b;)Z
         * 定位第二层调用方法, 通过 "syncAddChatroomMember OldVer:%d" 定位上下文
         * invoke-virtual {v6, v0, v1, v3}, Lcom/tencent/mm/storage/q;->a(Ljava/lang/String;Lcom/tencent/mm/g/a/a/a;Z)Lcom/tencent/mm/storage/q;
         * ========================================================================================================================================
         * note: 投机搜索方法, 搜索:  "syncAddChatroomMember replaceChatroomMember ret : %s , during : %s"        向上找到
         * invoke-virtual {v6, v0, v1, v3}, Lcom/tencent/mm/storage/q;->a(Ljava/lang/String;Lcom/tencent/mm/g/a/a/a;Z)Lcom/tencent/mm/storage/q;
         * =>      invoke-virtual {v6, v0, v1, v3}, Lcom/tencent/mm/storage/u;->a(Ljava/lang/String;Lcom/tencent/mm/i/a/a/a;Z)Lcom/tencent/mm/storage/u;
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/storage/u"), "a", String.class, "com.tencent.mm.i.a.a.a", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                /** 返回应答
                    {
                        "msgtype":chatroom_info_res,
                        "data":[
                            {
                                "room":"123@chatroom", "roomowner":"wxid_1", "chatroomnick":"群名",
                                "members":{
                                    "wxid_1":{'id':1, 'name': '昵称', 'alias':'weixin12345',}
                                },
                            },
                        ]
                    }
                 */
                try {
                    String print_field_displayname = (String) getObjectField(param.thisObject, "field_displayname");
                    boolean p3 = (boolean)param.args[2];
                    XLog.i("hookSyncChatroomMember, print_field_displayname:%s, p3:%b", print_field_displayname, p3);
                    if( print_field_displayname == null){
                        print_field_displayname = "";
                    }
                    String field_roomowner = (String) getObjectField(param.thisObject, "field_roomowner"); //群主
                    String roomid = (String) getObjectField(param.thisObject, "field_chatroomname"); //群id
                    String field_memberlist = (String) getObjectField(param.thisObject, "field_memberlist"); //群成员wxid列表, 分号分隔
                    XLog.i("field_roomowner:%s, roomid:%s, field_memberlist:%s", field_roomowner, roomid, field_memberlist);
                    if( field_memberlist == null ){
                        return;
                    }

                    StringBuffer displayname = new StringBuffer();
                    String[] wxids = field_memberlist.split(WechatAction.FenHao);
                    /** 定位LinkedList中的昵称字段, 全局搜索关键词: ".RoomData.Member.DisplayName"
                     * 进入类中, 查找 Map 类型的类成员变量
                     * .field private hHA:Ljava/util/Map;       =>      .field public dNh:Ljava/util/Map;
                     */
                    HashMap hashmap = (HashMap)getObjectField(param.thisObject, "dNh");

                    /** 查询rcontact获取群名 */
                    String roomnick = "";
                    Cursor cursor = WechatClass.Rcontact.getContactByWxid(roomid);
                    try{
                        if (cursor != null && cursor.moveToFirst() ){
                            roomnick = cursor.getString(cursor.getColumnIndex("nickname"));     /** rcontact表中字段nickname: 是群改过名后的群名 */
                            XLog.i("getRoomnick:%s, roomid:%s", roomnick, roomid);
                        }
                    }finally {
                        cursor.close();
                    }
                    if( TextUtils.isEmpty(roomnick) ) {
                        if( print_field_displayname.length() > 30) {
                            roomnick = print_field_displayname.substring(0, 30);    /** 群未改过名, 以群员昵称组合成群名 */
                        } else {
                            roomnick = print_field_displayname;     /** 群未改过名, 以群员昵称组合成群名 */
                        }
                    }
                    /**  遍历wxids, 如果有群成员自改别名, 则 nicknamelist 中显示自改别名. */
                    JSONObject members = new JSONObject();
                    int id = 1;
                    String _name = "", _alias ="";
                    for(String wxid: wxids) {
                        try {
                            // TODO
                            _name = "";
                            _alias = "";
                            String memberAlias = null;
                            Object nameObj = hashmap.get(wxid);
                            if( nameObj == null ) {
                                XLog.e("objcet from arraylist null, wxid:%s, obj:%s", wxid, nameObj); //自己加人入群的时候, 有时会空
                            }else{
                                /** 类中搜索:    "displayName[%s] roomFlag[%d] flag[%d]"    向上寻找
                                 * iget-object v0, v1, Lcom/tencent/mm/g/a/a/b;->gZg:Ljava/lang/String;     =>      iget-object v0, v1, Lcom/tencent/mm/i/a/a/b;->daA:Ljava/lang/String;
                                 */
                                memberAlias = (String) getObjectField(nameObj, "daA");//群成员自改别名
                            }
                            if ( ! TextUtils.isEmpty(memberAlias) ) {
                                if (displayname.length() == 0) {
                                    displayname.append(memberAlias);
                                    _name = memberAlias;
                                    if(BuildConfig.DEBUG) XLog.d("syncName1, wxid:%s, alias:%s", wxid, _name);
                                } else {
                                    displayname.append(WechatAction.DB_F + memberAlias);
                                    _name = memberAlias;
                                    if(BuildConfig.DEBUG) XLog.d("syncName2, wxid:%s, alias:%s", wxid, _name);
                                }
                                continue;
                            } else {
                                /** 查询rcontact获取成员名 */
                                String nickname;
                                Cursor _cursor = WechatClass.Rcontact.getContactByWxid(wxid);
                                try{
                                    // 这里和旧代码不同, 是因为做了优化, 删减了一些代码分支
                                    if (_cursor != null && _cursor.moveToFirst() ){
                                        nickname = _cursor.getString(_cursor.getColumnIndex("nickname"));     /** rcontact表中字段nickname: 是群改过名后的群名 */
                                        XLog.i("getRoomnick, roomid:%s, nickname:%s", roomid, nickname);
                                    }else{
                                        nickname = null;
                                        XLog.e("get rcontact fail.wxid:%s", wxid);
                                    }
                                }finally {
                                    _cursor.close();
                                }
                                if (TextUtils.isEmpty(nickname)) {
                                    nickname = wxid;
                                }
                                //remarkMember(field_type, wxid, nickname);   // 群成员备注
                                if (displayname.length() == 0) {
                                    displayname.append(nickname);
                                    _name = nickname;
                                    if(BuildConfig.DEBUG) XLog.d("syncName3, wxid:%s, alias:%s", wxid, _name);
                                } else {
                                    displayname.append(WechatAction.DB_F + nickname);
                                    _name = nickname;
                                    if(BuildConfig.DEBUG) XLog.d("syncName4, wxid:%s, alias:%s", wxid, _name);
                                }
                                continue;
                            }
                        }finally{
                            JSONObject _member = new JSONObject();
                            _member.put("id", id);
                            _member.put("name", _name);
                            _member.put("alias", _alias);
                            members.put(wxid, _member);
                            id++;
                        }
                    }
                    String field_displayname = displayname.toString();  // 群成员昵称
                    if(  needUpdateMemberWxid(roomid, field_memberlist) ||
                            needUpdateMemberName(roomid, field_displayname) ||
                            needUpdateRoomowner(roomid, field_roomowner))
                    {
                        room2memberlist.put(roomid, field_memberlist);
                        room2displayname.put(roomid, field_displayname);
                        room2roomowner.put(roomid, field_roomowner);
                        JSONArray data_array = new JSONArray();
                        JSONObject chatroom_info_dict = new JSONObject();
                        chatroom_info_dict.put("room", roomid);
                        chatroom_info_dict.put("roomowner", field_roomowner);
                        chatroom_info_dict.put("chatroomnick", roomnick);
                        chatroom_info_dict.put("members", members);
                        data_array.put(0, chatroom_info_dict);
                        JSONObject res_dict = new JSONObject();
                        res_dict.put("msgtype", WechatAction.chatroom_info_res);
                        res_dict.put("data", data_array);
                        XLog.i("rpush msgtype:chatroom_info_res, data:%s", data_array);
                        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                    }
                } catch (Throwable e) {
                    XLog.e("[hookSyncChatroomMember] %s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }


    protected void hookGetmsgimgResponse(final ClassLoader loader) {
        /** 搜索: "/cgi-bin/micromsg-bin/getmsgimg"               667
         * 进入类中, 搜索:    "onGYNetEnd : offset = "
         * com/tencent/mm/af/j      =>      com/tencent/mm/ak/k
         * .method public final a(Lcom/tencent/mm/af/d;III[B)Z      =>      .method private a(Lcom/tencent/mm/ak/e;III[B)Z
         */
        findAndHookMethod(WechatClass.get("com/tencent/mm/ak/k", "getmsgimg"), "a", "com.tencent.mm.ak.e", int.class, int.class, int.class, byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object p1 = param.args[0];
                    /**搜索当前类成员变量
                     * .field private fUb:J     =>      .field private bJC:J
                     * 进入p1类文件中, 搜索: "msgSvrId"
                     * iget-wide v2, p0, Lcom/tencent/mm/af/d;->ggW:J       =>      iget-wide v2, p0, Lcom/tencent/mm/ak/e;->bYu:J
                     */
                    long msgid = (long)getObjectField(param.thisObject, "bJC");
                    long msgsvrid = (long)getObjectField(p1, "bYu");
                    boolean hasMore = (boolean)param.getResult();
                    XLog.d("hookGetmsgimgResponse, msgid:%d, msgSvrid:%d, ret:%b", msgid, msgsvrid, hasMore);
                    if( !hasMore ) {
                        // 获取sqlite表中大图路径
                        String bigImgPath = WechatClass.get_ImgInfo2bigImgPath_ByMsgSrvId(msgsvrid);
                        XLog.i("ImgInfo2 bigImgPath:%s, msgsvrid:%d", bigImgPath, msgsvrid);
                        // TODO: 查看原图会报错, 因为 bigImgPath 为 null
                        if(bigImgPath.startsWith("SERVERID")){
                            // 跳过异常情况
                            XLog.i("bigImgPath not match");
                            return;
                        }
                        // 获取大图路径.
                        String picture_path = WechatClass.getBigImgFullPath(bigImgPath);
                        if ( TextUtils.isEmpty(picture_path) ) {
                            XLog.e("picture_path is null");
                            return;
                        }
                        File _pic_file = new File(picture_path);
                        if( ! _pic_file.exists() ) {
                            XLog.e("picture_path not exists, path:%s", picture_path);
                            return;
                        }
                        XLog.i("picture_path:%s", picture_path);

                        ContentValues message_row = WechatClass.getMessageByMsgId(msgid);
                        if (message_row == null) {
                            XLog.e("not get row in table:message, msgid:%d", msgid);
                            return;
                        }
                        // 组装消息给py侧
                        if( done_msgSvrId_cache.containsKey(msgsvrid) ) {return;}      // 防重复
                        else {done_msgSvrId_cache.put(msgsvrid, true);}
                        JSONObject res_dict = new JSONObject();
                        res_dict.put("msgtype", WechatAction.image_msg_done);
                        res_dict.put("msgId", msgid);
                        res_dict.put("msgSvrId", msgsvrid);
                        res_dict.put("type", message_row.getAsInteger("type"));
                        res_dict.put("status", message_row.getAsInteger("status"));
                        res_dict.put("createTime", message_row.getAsLong("createTime"));
                        res_dict.put("isSend", message_row.getAsInteger("isSend"));     // 自己发的不入redis
                        String talker = message_row.getAsString("talker");
                        String content = message_row.getAsString("content");
                        int type = message_row.getAsInteger("type");
                        if( talker.endsWith("@chatroom") ){
                            /** 群消息 */
                            res_dict.put("room", talker);
                            HashMap hashmap = WechatAction.content_split(content, WechatAction.image_msg_done);
                            res_dict.put("talker", hashmap.get("talker"));
                            res_dict.put("content", hashmap.get("content"));
                        }else{
                            /** 好友消息 */
                            res_dict.put("room", "");
                            res_dict.put("talker", talker);
                            res_dict.put("content", content);
                        }

                        /**
                         /storage/emulated/0/tencent/MicroMsg/8e3bc7b84cc00c02c2f6bb6aee6e9f69/image2/a6/b3/a6b3acaea994f0369b5e7288a9354f96.jpg 截断成
                         /MicroMsg/8e3bc7b84cc00c02c2f6bb6aee6e9f69/image2/a6/b3/a6b3acaea994f0369b5e7288a9354f96.jpg
                         */
                        File remoteFile = new File( WechatHook.remoteImageDir, _pic_file.getName() );  // 传送图片到远端 /MicroMsg 目录
                        res_dict.put("fileFullPath", remoteFile.getAbsoluteFile() );
                        res_dict.put("totalLen", _pic_file.length());
                        res_dict.put("content", ""); // note. image_msg_done 消息的content如下: 留空传输给py侧
                        /*
                        <?xml version="1.0"?>
                        <msg>
                        <img aeskey="63be2a855e91496ba5c91104d760a96f" encryver="0" cdnthumbaeskey="63be2a855e91496ba5c91104d760a96f" cdnthumburl="3052020100044b30490201000204978b062e02032df53e0204ae21067b02045a13dca40424373032333437363834384063686174726f6f6d3138313738375f313531313235313130370204010800010201000400" cdnthumblength="1810" cdnthumbheight="120" cdnthumbwidth="67" cdnmidheight="0" cdnmidwidth="0" cdnhdheight="0" cdnhdwidth="0" cdnmidimgurl="3052020100044b30490201000204978b062e02032df53e0204ae21067b02045a13dca40424373032333437363834384063686174726f6f6d3138313738375f313531313235313130370204010800010201000400" length="18802" cdnbigimgurl="3052020100044b30490201000204978b062e02032df53e0204ae21067b02045a13dca40424373032333437363834384063686174726f6f6d3138313738375f313531313235313130370204010800010201000400" hdlength="252999" md5="e6be3dad989e968d632a99d8a66c5fb3" />
                        </msg>
                        */

                        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.img.parseQR", msgid) ) ) {
                            // 解析二维码url
                            String imgurl = WechatClass.parseQrcode(picture_path);
                            res_dict.put("imgurl", imgurl);
                        }

                        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.img.live", msgid) ) ) {
                            res_dict.put("live", true);
                        }
                        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.img.needbuf", msgid) ) ) {
                            // base64编码文件
                            File file = new File(picture_path);
                            int size = (int) file.length();
                            byte[] bytes = new byte[size];
                            BufferedInputStream stream = null;
                            try {
                                stream = new BufferedInputStream(new FileInputStream(file));
                                stream.read(bytes, 0, bytes.length);
                            }finally {
                                if(stream != null) {
                                    stream.close();
                                }
                            }
                            String imgbuf = Base64.encodeToString(bytes, Base64.DEFAULT);  //与Python通信时建议用default
                            res_dict.put("filebuf", imgbuf);
                        }

                        XLog.i("rpush msgtype:image_msg_done, picture_path:%s", picture_path);
                        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                        /** sftp 图片 */
                        WechatHook.thread_touch.sftp_queue.put( Arrays.asList( _pic_file.getParent(), _pic_file.getName(), remoteFile.getParent(), remoteFile.getName(), "put", "1" ) );
                    }
                } catch (Throwable e) {
                    XLog.e("hookGetmsgimgResponse error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
    }  /** hookGetmsgimgResponse end */


    private void printVariable(Object obj, String name) {
        if (obj instanceof Integer) {
            XLog.i("%s:%d", name, obj);
        }else if(obj instanceof Long) {
            XLog.i("%s:%d", name, obj);
        }else if(obj instanceof String) {
            XLog.i("%s:%s", name, obj);
        }
    }


    public void rpush_send_message(DbOperation op, ContentValues row) throws Throwable {
        try {
            XLog.d("rpush_send_message, op:%s, row:%s", op, row);
            String field_talker="", field_content="";
            int field_isSend = -1, field_status = -1, field_type = -1;
            long lMsgid = -1, lMsgSvrid = -1, lCreateTime = -1;
            field_isSend = row.getAsInteger("isSend");
            if ( row.get("field_status") != null )
                field_status = row.getAsInteger("status");
            field_talker = row.getAsString("talker");
            field_content = row.getAsString("content");
            field_type = row.getAsInteger("type");
            lMsgid = row.getAsLong("msgId");
            lCreateTime = row.getAsLong("createTime");
            if ( row.get("msgSvrId") != null )
                lMsgSvrid = row.getAsLong("msgSvrId");

            switch(op){
                case UPDATE: {
                    if ( WechatHook.friendtest.containsKey(field_talker) ) {  // 必须保证update的操作才进到内层
                        LinkedList<String> linkelist = WechatHook.friendtest.get(field_talker);
                        String alias = linkelist.get(0);
                        String nickname = linkelist.get(1);
                        int type = Integer.parseInt(linkelist.get(2));
                        WechatHook.friendtest.remove(field_talker);     // 这里先remove掉, 因为下面会根据需要再put
                        XLog.i("remove friendtest, wxid:%s", field_talker);
                        if (field_status == 5) {  //失败
                            switch (field_content) {
                                case "add_friend_to_contact_by_qrid": {  // 发送"add_friend_to_contact_by_qrid"时, 正常对方会发送"add_friend_to_contact_by_qrid"回来, 而遇到发送失败status=5时, 则改为发送通过扫描qrid加好友申请
                                    String qrid = linkelist.get(3);
                                    JSONObject res_dict = new JSONObject();
                                    res_dict.put("wxid", field_talker);
                                    res_dict.put("qrid", qrid);
                                    WechatHook.thread_touch.thread_action._searchcontact(res_dict);
                                    XLog.i("send request searchcontact, wxid:%s, qrid:%s, force:true, ret:%d", field_talker, qrid);
                                    break;
                                }
                                case "add_friend_to_contact_by_wxid": {  // 发送"add_friend_to_contact_by_wxid"时, 遇到status=5,发送失败, 强制发送verifyuser加好友申请
                                    JSONObject res_dict = new JSONObject();
                                    res_dict.put("wxid", field_talker);
                                    res_dict.put("force", true);    // 是否先判断好友关系, 再发送加好友请求
                                    WechatHook.thread_touch.thread_action._verifyuser(res_dict);
                                    XLog.i("send request _verifyuser, wxid:%s, force:true", field_talker);
                                    break;
                                }
                                case "get_contact_by_wxid": {
                                    break;
                                }
                            }
                        } else if (field_status == 2 || field_status == 3) {    //成功
                            JSONArray data_array = new JSONArray();
                            JSONObject contact_info_dict = new JSONObject();
                            contact_info_dict.put("wxid", field_talker);
                            contact_info_dict.put("alias", alias);
                            contact_info_dict.put("nickname", nickname);
                            contact_info_dict.put("type", type);
                            data_array.put(0, contact_info_dict);

                            JSONObject res_dict = new JSONObject();
                            res_dict.put("msgtype", WechatAction.update_friend_res);
                            res_dict.put("data", data_array);

                            XLog.i("rpush msgtype:update_friend_res, wxid:%s, alias:%s, nickname:%s, type:%d", field_talker, alias, nickname, type);
                            WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                        }
                    } else {
                        XLog.i("friendtest not contain wxid:%s", field_talker);
                    }
                }
                break;
                case INSERT: {
                    String msgtype = WechatAction.wxtype2msgtype(field_type, "");
                    _save_hwinfo(op, msgtype, field_isSend, field_status);
                    if (field_type == 1 && field_status == 4 && field_isSend == 1) {
                        // 这种情况为群公告
                    } else {
                        return; // 不保存自己发的消息到redis
                    }
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", msgtype);
                    res_dict.put("type", field_type);
                    res_dict.put("status", field_status);
                    res_dict.put("createTime", lCreateTime);
                    res_dict.put("msgId", lMsgid);
                    res_dict.put("msgSvrId", lMsgSvrid);
                    if (field_talker.endsWith("@chatroom")) {
                        /** 群消息 */
                        res_dict.put("room", field_talker);
                        HashMap hashmap = WechatAction.content_split(field_content, msgtype);
                        res_dict.put("talker", hashmap.get("talker"));
                        res_dict.put("content", hashmap.get("content"));
                    } else {
                        /** 好友消息 */
                        res_dict.put("room", "");
                        res_dict.put("talker", field_talker);
                        res_dict.put("content", field_content);
                    }
                    res_dict.put("isSend", field_isSend);
                    if (this.msgSvrId_cache.containsKey(lMsgSvrid)) {
                        return;
                    } else {
                        this.msgSvrId_cache.put(lMsgSvrid, true);
                    }
                    XLog.i("rpush send msg, msgtype:%s, field_talker:%s, content:%s, type:%d", msgtype, field_talker, field_content, field_type);
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                }
                break;
            }
        }catch (Throwable e) {
            XLog.e("rpush_send_message error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    public void rpush_recv_message(DbOperation op, ContentValues row) throws Throwable{
        /** 1. SQLiteDatabase insert 处理 所有消息
         */
        try{
            XLog.d("rpush recv_message, op:%s, row:%s", op, row);
            //String field_bizChatUserId, field_bizClientMsgId, field_transBrandWording, field_transContent;
            String field_imgPath="", field_talker="", field_content="", field_reserved="";
            int field_isSend=-1, field_status=-1, field_type=-1; //, field_flag=-1, field_isShowTimer=-1, field_talkerId=-1;
            long lMsgid=-1, lMsgSvrid=-1, lCreateTime=-1; // lMsgSeq=-1,
            byte[] lvbuffer=null;
            field_isSend = row.getAsInteger("isSend");
            if( row.get("status") != null )
                field_status = row.getAsInteger("status");
            field_talker = row.getAsString("talker");
            field_content = row.getAsString("content");
            field_type = row.getAsInteger("type");
            lMsgid = row.getAsLong("msgId");
            lCreateTime = row.getAsLong("createTime");
            if( row.get("msgSvrId") != null )
                lMsgSvrid = row.getAsLong("msgSvrId");
            if( row.get("lvbuffer") != null )
                lvbuffer = row.getAsByteArray("lvbuffer");
            if( row.get("imgPath") != null )
                field_imgPath = row.getAsString("imgPath");
            //        if(row.containsKey("field_msgSeq"))
            //            lMsgSeq = row.getAsLong("msgSeq");
            //        field_talkerId = row.getAsInteger("talkerId");
            //        if(row.containsKey("flag"))
            //            field_flag = row.getAsInteger("flag");

            switch (op){
                case INSERT:{
                    String msgtype = WechatAction.wxtype2msgtype(field_type, "");
                    XLog.i("recv message, talker:%s, isSend:%d, type:%d, status:%d, msgtype:%s, msgid:|msgsrvid: %d|%d", field_talker, field_isSend, field_type, field_status, msgtype, lMsgid, lMsgSvrid);
                    _save_hwinfo(op, msgtype, field_isSend, field_status);

                    // 处理别人发过来的消息
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", msgtype);
                    res_dict.put("type", field_type);
                    res_dict.put("status", field_status);
                    res_dict.put("createTime", lCreateTime);
                    res_dict.put("msgId", lMsgid);
                    res_dict.put("msgSvrId", lMsgSvrid);
                    if( field_talker.endsWith("@chatroom") ){
                        /** 群消息 */
                        res_dict.put("room", field_talker);
                        HashMap hashmap = WechatAction.content_split(field_content, msgtype);
                        res_dict.put("talker", hashmap.get("talker"));
                        res_dict.put("content", hashmap.get("content"));
                    }else{
                        /** 好友消息 */
                        res_dict.put("room", "");
                        res_dict.put("talker", field_talker);
                        res_dict.put("content", field_content);
                    }
                    res_dict.put("isSend", field_isSend);
                    switch (msgtype)
                    {
                        case WechatAction.text_msg:{// 文字消息. type = 1
                            if( lvbuffer != null && lvbuffer.length > 0 ){
                                if(BuildConfig.DEBUG) {
                                    String strOfbuffer = MyString.bytesToHexString( lvbuffer );
                                    XLog.d("string of lvbuffer:%s", strOfbuffer);
                                    strOfbuffer = MyString.stringToHexString( res_dict.getString("content") );
                                    XLog.d("string of content:%s", strOfbuffer);
                                }
                                // parse lvbuffer
                                String msgsource = WechatClass.parseMessageLvbuffer( lvbuffer );
                                if(BuildConfig.DEBUG) XLog.d("msgsource:%s", msgsource);
                                if( !TextUtils.isEmpty(msgsource) ) {
                                    /**
                                     <msgsource>
                                     <atuserlist>wxid_u9d5k0sn17xo12,wxid_2</atuserlist>
                                     <silence>0</silence>
                                     <membercount>8</membercount>
                                     </msgsource>
                                     */
                                    Map appmsg = WechatClass.parseChatroomMsgsource(msgsource, "msgsource");
                                    //for (Object key : appmsg.keySet()) { XLog.d("key:|%s|", key);}
                                    if(BuildConfig.DEBUG) XLog.d("appmsg222:%s", appmsg);
                                    if (appmsg != null) {
                                        String atuserlist = (String) appmsg.get(".msgsource.atuserlist");
                                        if ( !TextUtils.isEmpty(atuserlist) ) {
                                            res_dict.put("atuserlist", atuserlist);
                                        }
                                    }
                                }
                            }
                        }
                        break;
                        case WechatAction.image_msg: {   // 图片消息. type = 3
                            res_dict.put( "content", field_content.substring( 0, field_content.indexOf(":")+1 ) );   //截断 ":\n" 后面内容
                        }break;
                        case WechatAction.voice_msg:{// 语音消息. type = 34
                        }break;
                        case WechatAction.video_msg:{// 视频消息. type = 43
                            res_dict.put("imgPath", field_imgPath);
                        }break;
                        case WechatAction.emoji_msg:{// Emoji消息. type = 47
                            Map appmsg = WechatClass.parseMessageContent( field_content, null);
                            if(BuildConfig.DEBUG){
                                XLog.d("appmsg: %s", appmsg);
                            }
                            res_dict.put("cdnurl", appmsg.get(".msg.emoji.$cdnurl"));
                        }break;
                        case WechatAction.card_msg: {  // 卡片消息. type = 49
                            // 表message里面的reserved字段内容, 6.3.11版本因解析函数内部不使用p2参数field_reserved， 所以可控
                            Map appmsg = WechatClass.parseMessageContent( field_content, null);
                            String subtype = (String)appmsg.get(".msg.appmsg.type");
                            if(BuildConfig.DEBUG){
                                XLog.d("appmsg: %s", appmsg);
                                XLog.w("subtype: %s", subtype);
                            }
                            //note: 转换子类型
                            msgtype = WechatAction.wxtype2msgtype(field_type, subtype);
                            switch(msgtype){
                                case WechatAction.weblink_card_msg: {// note 网站链接卡片. type = 49, subtype = 5
                                    String geta8key_data_req_url = appmsg.get(".msg.appmsg.url") + "&from=singlemessage&isappinstalled=0";  // &isappinstalled=0 可以去掉
                                    if (geta8key_data_req_url.indexOf("cgi-bin/mmsupport-bin/addchatroombyinvite") == -1) {
                                        // 群邀请链接, 需再次核对<.msg.appmsg.url>格式如:  http://support.weixin.qq.com/cgi-bin/mmsupport-bin/addchatroombyinvite?ticket=AZDZ3nQkAEiFX9V0m%2FmgOg%3D%3D
                                        res_dict.put("msgtype", msgtype);
                                        res_dict.put("url", appmsg.get(".msg.appmsg.url"));
                                        res_dict.put("title", appmsg.get(".msg.appmsg.title"));
                                        res_dict.put("description", appmsg.get(".msg.appmsg.des"));
                                        res_dict.put("thumburl", appmsg.get(".msg.appmsg.thumburl"));
                                    } else {
                                        msgtype = WechatAction.chatroom_invite_msg;// note 群邀请卡片. type = 49, subtype = 5
                                        XLog.i("geta8key_data_req_url: " + geta8key_data_req_url);
                                        res_dict.put("geta8key_data_req_url", geta8key_data_req_url);
                                        res_dict.put("msgtype", msgtype);
                                        res_dict.put("content", field_content.substring(0, field_content.indexOf(":") + 1));   //截断 ":\n" 后面内容
                                    }
                                }break;
                                case WechatAction.attach_card_msg:{// 附件卡片. type = 49, subtype = 6
                                    if( field_status == 5 ){
                                        XLog.e("attach_card_msg download fail, status:5, clean key");
                                        return;
                                    }
                                    String filename = (String)appmsg.get(".msg.appmsg.title");  // 文件名
                                    String fileext = (String)appmsg.get(".msg.appmsg.appattach.fileext");   //后缀, 例如: doc
                                    String totallen = (String)appmsg.get(".msg.appmsg.appattach.totallen"); //字节
                                    String attachid = (String)appmsg.get(".msg.appmsg.appattach.attachid"); // @cdn_304b020100044430420201000204cef7b12f02033d14b9020474
                                    res_dict.put("filename", filename);
                                    res_dict.put("fileext", fileext);
                                    res_dict.put("totallen", totallen);
                                    res_dict.put("msgtype", msgtype);
                                    res_dict.put( "content", field_content.substring( 0, field_content.indexOf(":")+1 ) );   //截断 ":\n" 后面内容
                                }break;
                            }
                        }break;
                        case WechatAction.system_msg:{// 系统消息. type = 10000
                            if(field_content.matches("^你被.*移出群聊$")){
                                XLog.i("exit chatroom! delete relate table");
                                WechatClass.EnMicroMsg.rawDelete("rconversation", "username=?", new String[]{field_talker});
                                WechatClass.EnMicroMsg.rawDelete("chatroom", "chatroomname=?", new String[]{field_talker});
                                WechatClass.EnMicroMsg.rawDelete("message", "talker=?", new String[]{field_talker});
                            }
                        }break;
                    } /** switch end */
                    if( this.msgSvrId_cache.containsKey(lMsgSvrid) ) {
                        XLog.e("duplicate recv messag, msgSvrId_cache exist:%d", lMsgSvrid);
                        return;
                    } else {
                        this.msgSvrId_cache.put(lMsgSvrid, true);
                    }
                    XLog.i("rpush recv msg, msgtype:%s, field_talker:%s, content:%s, type:%d", msgtype, field_talker, field_content, field_type);
                    if( BuildConfig.DEBUG ) XLog.i("rpush dict: %s", res_dict.toString());
                    WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                }
                break;
                case UPDATE:{}
                break;
            }
        }catch (Throwable e) {
            XLog.e("rpush recv_message error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    private void _save_hwinfo(DbOperation op, String msgtype, int field_isSend, int field_status) throws JSONException, InterruptedException{
        /** 收到消息时, INSERT message status=3
         * 发送消息时, UPDATE message,  由status=1 更新为 status=2
         */
        if( HardwareInfo.isSave ) return;
        // note: 只有被动接收到特定消息, 才上报硬件信息
        if( field_isSend == 0 && field_status == 3 && op == DbOperation.INSERT) {// 收到
            switch(msgtype){
                case WechatAction.text_msg:
                case WechatAction.image_msg:
                case WechatAction.voice_msg:
                case WechatAction.video_msg:
                    break;
                default:
                    return;
            }
        }else if( field_isSend == 1 && field_status == 2 && op == DbOperation.UPDATE) {// 发送
            switch(msgtype){
                case WechatAction.text_msg:
                case WechatAction.image_msg:
                case WechatAction.voice_msg:
                case WechatAction.video_msg:
                    break;
                default:
                    return;
            }
        }
        if( System.currentTimeMillis() - this.login_time > 4 * 60 * 1000) {// 登陆成功后4分钟
//        if( true ) {
            HardwareInfo.isSave = true;
            VerifyThread.post_login_success();
        }
    }


    public void rpush_recv_fmessage_msginfo(DbOperation op, ContentValues row) throws Throwable {
        try {
            XLog.d("rpush_recv_fmessage_msginfo op:%s, row:%s", op, row);
            String msgContent = "", talker = "", v1_encryptUsername = "";
            int type = -1;
            long createTime = -1;
            msgContent = row.getAsString("msgContent");
            createTime = row.getAsLong("createTime");
            talker = row.getAsString("talker");
            type = row.getAsInteger("type");
            if (row.get("encryptTalker") != null)
                v1_encryptUsername = row.getAsString("encryptTalker");

            switch (op) {
                case INSERT: {
                    if (type == 1) {
                        // note 解析fmessage_msginfo表的msgContent字段. (这里偷懒, 并没有使用真实微信内调用的函数, 测试过用解析message表的field_reserved字段的也正常)
                        Map apmsg = WechatClass.parseMessageContent( msgContent, null );
                        String ticket = (String) apmsg.get(".msg.$ticket");
                        int scene = Integer.parseInt((String) apmsg.get(".msg.$scene"));
                        String bigheadimgurl = (String) apmsg.get(".msg.$bigheadimgurl");
                        String fromnickname = (String) apmsg.get(".msg.$fromnickname");
                        String province = (String) apmsg.get(".msg.$province");
                        String city = (String) apmsg.get(".msg.$city");
                        String country = (String) apmsg.get(".msg.$country");
                        String sex = (String) apmsg.get(".msg.$sex");
                        XLog.i("notify_new_friend_req. talker:%s, ticket:%s, scene:%d", talker, ticket, scene);
                        // 返回应答
                        JSONObject res_dict = new JSONObject();
                        res_dict.put("msgtype", WechatAction.notify_new_friend_req);
                        res_dict.put("wxid", talker);
                        res_dict.put("ticket", ticket);
                        res_dict.put("scene", scene);
                        res_dict.put("headimgurl", bigheadimgurl);
                        res_dict.put("nickname", fromnickname);
                        res_dict.put("province", province);
                        res_dict.put("city", city);
                        res_dict.put("country", country);
                        res_dict.put("sex", sex);
                        XLog.i("rpush msgtype:notify_new_friend_req, wxid:%s, ticket:%s, scene:%d, nickname:%s, headimgurl:%s", talker, ticket, scene, fromnickname, bigheadimgurl);
                        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                    } else {
                        XLog.e("notify_new_friend_req type != 1");
                    }
                }
                break;
                case UPDATE: {
                }
                break;
            }
        } catch (Throwable e) {
            XLog.e("rpush_recv_fmessage_msginfo error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    public void callGetQrcode(final ClassLoader loader) {
        try{
            /** 生成机器人自身的二维码图片 */
            WechatHook.thread_touch.thread_action._recreate_robot_qrcode(false);
        } catch (Throwable e) {
            XLog.e("callGetQrcode error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    public void stackOverflow(String tag){
        if(BuildConfig.DEBUG) {
            new Exception(tag).printStackTrace();  // 查看所有消息调用堆栈
        }
    }

} /** WechatHook 类结束 */

/**
    private String getFromXml(String xmlmsg, String node) throws XmlPullParserException, IOException {
        String xl = xmlmsg.substring(xmlmsg.indexOf("<msg>"));
        //nativeurl
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser pz = factory.newPullParser();
        pz.setInput(new StringReader(xl));
        int v = pz.getEventType();
        String result = "";
        while (v != XmlPullParser.END_DOCUMENT) {
            if (v == XmlPullParser.START_TAG) {
                if (pz.getName().equals(node)) {
                    pz.nextToken();
                    result = pz.getText();
                    break;
                }
            }
            v = pz.next();
        }
        return result;
    }

    private int getRandom(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
*/

//void hookGetMessage(final ClassLoader loader) {
//    /**　更新message表前, 应该也会调用这个函数, 使数据库记录抽象成对象, 然后才update
//     grep "create a temp session conversation" *
//     Class:     "com.tencent.mm.storage.s"      =>      "com.tencent.mm.storage.p"
//     Method:    "a"        =>      不变
//     Params1:   "com.tencent.mm.storage.ah"     =>      "com.tencent.mm.storage.aw"
//     Params2:   com.tencent.mm.storage.ah$c"        =>  "com.tencent.mm.storage.ae"
//     Params3:   无       =>      boolean.class
//     Params4:   无       =>      "com.tencent.mm.plugin.messenger.foundation.a.a.c$c"
//     Object:    "kgp"       =>      "nwN"
//     Object:    "kgq"       =>      "nwO"
//     * */
//    findAndHookMethod("com.tencent.mm.storage.p", loader, "a", "com.tencent.mm.storage.aw", "com.tencent.mm.storage.ae", boolean.class, "com.tencent.mm.plugin.messenger.foundation.a.a.c$c",
//            new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    try {
//                        // param.thisObject => this指针
//                        /**
//                         Field field = findField(param.args[1].getClass(), "apb");
//                         field.setAccessible(true);
//                         String str1 = (String) (field.get(param.args[1]));    //方法1: java 反射技术.  方法2 : getObjectField(obj, name)
//                         XLog.i("str1:" + str1);
//                         */
//                        XLog.i("hookGetMessage");
//                        Object p4 = param.args[3];
//                        String operation = (String)getObjectField(p4, "nwN");
//                        switch(operation){
//                            case "insert":
//                            case "update":
//                            {
//                                // select * from
//                                ArrayList arraylist = (ArrayList)getObjectField(p4, "nwO");
//                                XLog.i("arraylist size:%d", arraylist.size());
//                                for(int i=0; i<arraylist.size(); i++){
//                                    Object obj_storage_ag = arraylist.get(0);
//                                    int field_isSend = (int) getObjectField(obj_storage_ag, "field_isSend");
//                                    int field_status = (int) getObjectField(obj_storage_ag, "field_status");
//                                    int field_type = (int) getObjectField(obj_storage_ag, "field_type");
//                                    if( field_isSend == 1 && field_status == 1){
//                                        // 发送消息, status为1的不入redis.  (status值更新顺序: 先1, 成功后2, 失败后5)
//                                        continue;
//                                    }
//                                    if( field_type == 10000) {
//                                        // 如果是 10000:系统消息, 则跳过
//                                        continue;
//                                    }
//                                    /** 只处理  非10000:系统消息 */
//                                    String msgtype = WechatAction.wxtype2msgtype(field_type, "");
//                                    if( !BuildConfig.DEBUG && TextUtils.isEmpty(msgtype)) {
//                                        // release版本针对不需处理的消息过滤掉
//                                        continue;
//                                    }
//                                    rpush_recv_message(null, obj_storage_ag);
//                                }
//                            }
//                        }
//                    } catch (Throwable e) {
//                        XLog.e("hookGetMessage error. stack:%s", android.util.Log.getStackTraceString(e));
//                    }
//                }
//            }
//    );
//}  /** hookGetMessage end */

/*
    // base64编码文件
    File file = new File(picture_path);
    int size = (int) file.length();
    byte[] bytes = new byte[size];
    BufferedInputStream stream = null;
    try {
    stream = new BufferedInputStream(new FileInputStream(file));
    stream.read(bytes, 0, bytes.length);
    }finally {
    if(stream != null) {
    stream.close();
    }
    }
    String imgbuf = Base64.encodeToString(bytes, Base64.DEFAULT);  //与Python通信时建议用default
 */

/*
    Class<?> model_h = null;
    model_h = findClass("com.tencent.mm.model.h", loader);
    if(model_h == null) {
        XLog.e("model_h == null!");
        return;
    }
    String robot_wxid = (String)callStaticMethod(model_h, "sc");
    WechatHook.filenameGenarotor.prefix = String.format("%s_%s", BuildConfig.PROC_TYPE, robot_wxid);   //修改日志前缀, 加上机器人wxid
    Cursor cursor = WechatClass.getContactByWxid2(robot_wxid);
    try{
        if (cursor == null){
            XLog.e("getContactByWxid2 failed ret: null");
            return;
        }
        if(!cursor.moveToFirst()){
            XLog.e("getContactByWxid2 moveToFirst fail.");
            return;
        }
        String alias = cursor.getString(cursor.getColumnIndex("alias"));
        String nickname = cursor.getString(cursor.getColumnIndex("nickname"));
        WechatHook.set_robot_info(MySync.g_robot_wxid, robot_wxid);
        WechatHook.set_robot_info("robot_alias", alias);
        WechatHook.set_robot_info("robot_nickname", nickname);
        XLog.i("robot_wxid:%s. robot_alias:%s, robot_nickname:%s", robot_wxid, alias, nickname);
    }finally {
        cursor.close();
    }
*/