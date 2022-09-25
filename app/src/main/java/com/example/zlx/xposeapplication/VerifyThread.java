package com.example.zlx.xposeapplication;

import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.example.zlx.hardware.HardwareInfo;
import com.example.zlx.mybase.*;
import com.example.zlx.mynative.AuthArg;
import com.example.zlx.mynative.JNIUtils;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;

import static de.robv.android.xposed.XposedHelpers.callMethod;

public class VerifyThread extends Thread {
    static String gui_token = "";     // 并没有使用这个!
    static String lib_dir = "";
    //
    public static String wxid = null;
    public static AuthArg autharg = new AuthArg();
    private Logger log;

    VerifyThread(Logger log) {
        this.log = log;
    }
    @Override
    public void run() {
        try{
            while(true) {// note catch外层代码, 此线程会退出, 不过退出后会被TouchThread重新拉起

                if( AuthArg.next_auth_state == AuthArg.authentic_i)
                    VerifyThread.post_authentic_packet(AuthArg.authentic_i);
                else if( AuthArg.next_auth_state == AuthArg.authentic_u)
                    VerifyThread.post_authentic_packet(AuthArg.authentic_u);

                if( ! this.autharg.is_plugin_auth_success ){
                    WechatClass.currentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WechatClass.wechatContext, "插件认证失败! 请检查!", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                /** 睡眠 */
                log.d("VerifyThread sleep 5 minute");
                long sleep_millisecond = (5 * 60 * 1000);   // 休眠5分钟再检测. = 5 * 60 * 1000
                Thread.sleep(sleep_millisecond);     //单位: 毫秒.
            }
        } catch (Exception e) {
            log.e("VerifyThread error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    public static void post_authentic_packet(String i_u_t){
        // note: 鉴权I,U,T包
        try {
            XLog.d("post authentic_packet, type:%s", i_u_t);
//            String sting = JNIUtils.getString();
//            XLog.d("getString:%s", sting);
//            if(true) return;
            // note 读取文件中的user token
            VerifyThread.autharg.user_token = UserConfig.getString("token");
            XLog.d("VerifyThread.autharg.user_token:%s", VerifyThread.autharg.user_token);
            // note 赋值 AuthArg 结构体
            VerifyThread.autharg.auth_type = i_u_t;
            XLog.d("VerifyThread.autharg.auth_type:%s", VerifyThread.autharg.auth_type);
            XLog.d("VerifyThread.autharg.key:%s", VerifyThread.autharg.key);
            XLog.d("VerifyThread.autharg.last_token:%s", VerifyThread.autharg.last_token);
            // note: 加密
            String b64_encrypt_token = JNIUtils.CencryptToken( VerifyThread.autharg );
            XLog.d("key:%s, last_token:%s", VerifyThread.autharg.key, VerifyThread.autharg.last_token);
//            if(true) {
//                XLog.d("b64_encrypt_token:%s", b64_encrypt_token);
//                return;    // note DEBUG
//            }
            // TODO 发送Http请求
            JSONObject res_dict = new JSONObject();
            res_dict.put("msgtype", i_u_t);
            res_dict.put("key", JNIUtils.getKey());
            res_dict.put("last_token", VerifyThread.autharg.last_token);
            XLog.d("post json:%s", res_dict);
            HashMap<String, String> header = new HashMap<>();
            header.put("device-id", HardwareInfo.deviceId);
            header.put("token", VerifyThread.autharg.user_token);
            header.put("build-variant", BuildConfig.BUILD_VARIANT);
            String html = MyHttp.post_json(String.format("http://%s/wxapi/auth.json", UserConfig.getString("auth_ip")), res_dict, header, "utf-8");
            XLog.d("login req response:%s", html);
            // note: 解析应答内的Json报文
            JSONObject json_obj = new JSONObject(html);
            XLog.d("Web Response:%s", json_obj);
            if( !json_obj.has("token") || !json_obj.has("last_token")) {
                XLog.e("authentic req response has not token or last_token");
                autharg.is_plugin_auth_success = false;
                return;
            }
            String new_key = json_obj.getString("key");
            String b64_encrypt_user_token = json_obj.getString("token");
            String b64_encrypt_last_token = json_obj.getString("last_token");
            VerifyThread.autharg.key = new_key;
            // note: 解密
            XLog.d("b64_encrypt_user_token:%s", b64_encrypt_user_token);
            XLog.d("b64_encrypt_last_token:%s", b64_encrypt_last_token);
//            if( MyAES.currentTimeMillis <= 0 || MyAES.currentTimeMillis > 0 ){ // note 在Activity内操作Main类会导致崩溃
//                MyAES.currentTimeMillis += 0x99;
//            };
            XLog.d("before decrypt, MyAES.currentTimeMillis:%d", MySync.currentTimeMillis);
            XLog.d("Main.g_robot_wxid:%s", MySync.g_robot_wxid);
            String decrypt_token = JNIUtils.CdecryptToken( b64_encrypt_user_token, b64_encrypt_last_token, VerifyThread.autharg);
            XLog.d("decrypt_token:%s", decrypt_token);
            XLog.d("decrypt last token::%s", VerifyThread.autharg.last_token);
            XLog.d("after decrypt, MyAES.currentTimeMillis:%d", MySync.currentTimeMillis);

            // 下一阶段发送U包
            if(i_u_t == AuthArg.authentic_i){
                AuthArg.next_auth_state = AuthArg.authentic_u;
            }
        } catch (Exception e) {
            XLog.e("post_authentic error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** post_authentic() 函数结束 */

    public static void post_login_success(){
        // note: 登陆成功, 上传hardware信息
        Thread _login_success_task = new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    VerifyThread.autharg.user_token = UserConfig.getString("token");

                    JSONObject userinfo = new JSONObject();
                    userinfo.put("wxid", WechatHook.get_robot_info(MySync.g_robot_wxid));
                    userinfo.put("alias", WechatHook.get_robot_info("robot_alias"));
                    userinfo.put("nickname", WechatHook.get_robot_info("robot_nickname"));
                    userinfo.put("qq", WechatHook.get_robot_info("robot_qq"));
                    userinfo.put("email", WechatHook.get_robot_info("robot_email"));
                    userinfo.put("appversion", WechatHook.get_robot_info("robot_version"));

                    JSONObject hwinfo = new JSONObject();
                    hwinfo.put("IMEI", HardwareInfo.IMEI);
                    hwinfo.put("android_id", HardwareInfo.android_id);
                    hwinfo.put("Line1Number", HardwareInfo.Line1Number);
                    hwinfo.put("SimSerialNumber", HardwareInfo.SimSerialNumber);
                    hwinfo.put("IMSI", HardwareInfo.IMSI);
                    hwinfo.put("SimCountryIso", HardwareInfo.SimCountryIso);
                    hwinfo.put("SimOperator", HardwareInfo.SimOperator);
                    hwinfo.put("SimOperatorName", HardwareInfo.SimOperatorName);
                    hwinfo.put("NetworkCountryIso", HardwareInfo.NetworkCountryIso);
                    hwinfo.put("NetworkOperator", HardwareInfo.NetworkOperator);
                    hwinfo.put("NetworkOperatorName", HardwareInfo.NetworkOperatorName);
                    hwinfo.put("NetworkType", HardwareInfo.NetworkType);
                    hwinfo.put("PhoneType", HardwareInfo.PhoneType);
                    hwinfo.put("SimState", HardwareInfo.SimState);
                    hwinfo.put("MacAddress", HardwareInfo.MacAddress);
                    hwinfo.put("SSID", HardwareInfo.SSID);
                    hwinfo.put("BSSID", HardwareInfo.BSSID);

                    hwinfo.put("RELEASE", HardwareInfo.RELEASE);
                    hwinfo.put("SDK", HardwareInfo.SDK);
                    hwinfo.put("CPU_ABI", HardwareInfo.CPU_ABI);
                    hwinfo.put("CPU_ABI2", HardwareInfo.CPU_ABI2);
                    hwinfo.put("widthPixels", HardwareInfo.widthPixels);
                    hwinfo.put("heightPixels", HardwareInfo.heightPixels);
                    hwinfo.put("RadioVersion", HardwareInfo.RadioVersion);
                    hwinfo.put("BRAND", HardwareInfo.BRAND);
                    hwinfo.put("MODEL", HardwareInfo.MODEL);
                    hwinfo.put("PRODUCT", HardwareInfo.PRODUCT);
                    hwinfo.put("MANUFACTURER", HardwareInfo.MANUFACTURER);
                    hwinfo.put("cpuinfo", HardwareInfo.cpuinfo);
                    hwinfo.put("HARDWARE", HardwareInfo.HARDWARE);
                    hwinfo.put("FINGERPRINT", HardwareInfo.FINGERPRINT);
                    hwinfo.put("DISPLAY", HardwareInfo.DISPLAY);
                    hwinfo.put("INCREMENTAL", HardwareInfo.INCREMENTAL);
                    hwinfo.put("SERIAL", HardwareInfo.SERIAL);
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.login_success);
                    res_dict.put("hwinfo", hwinfo);
                    res_dict.put("userinfo", userinfo);
                    // note end
                    XLog.d("http post login_success, android_id:%s", HardwareInfo.android_id);
                    HashMap<String, String> header = new HashMap<>();
                    header.put("device-id", HardwareInfo.deviceId);
                    header.put("token", VerifyThread.autharg.user_token);
                    header.put("build-variant", BuildConfig.BUILD_VARIANT);
                    String html = MyHttp.post_json(String.format("http://%s/wxapi/hwinfo.json", UserConfig.getString("auth_ip")), res_dict, header, "utf-8");
                    XLog.d("login success response:%s", html);
                } catch (Exception e) {
                    XLog.e("post_login_success error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
        _login_success_task.start(); //启动线程
    }   /** post_login_success() 函数结束 */

    public static void post_login_new_device(){
        // note: 目前只有微信插件用上
        Thread _login_new_device_task = new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    VerifyThread.autharg.user_token = UserConfig.getString("token");

                    String user = WechatHook.get_robot_info("robot_user");
                    JSONObject userinfo = new JSONObject();
                    userinfo.put("user", user);
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.login_new_device);
                    res_dict.put("userinfo", userinfo);
                    XLog.i("rpush msgtype:login_new_device, user:%s", user);
                    HashMap<String, String> header = new HashMap<>();
                    header.put("device-id", HardwareInfo.deviceId);
                    header.put("token", VerifyThread.autharg.user_token);
                    header.put("build-variant", BuildConfig.BUILD_VARIANT);
                    String html = MyHttp.post_json(String.format("http://%s/wxapi/hwinfo.json", UserConfig.getString("auth_ip")), res_dict, header, "utf-8");
                    XLog.d("login req response:%s", html);
                    // note: 解析应答内的Json报文
                    JSONObject json_obj = new JSONObject(html);
                    XLog.d("Web Response:%s", json_obj);
                    if( !json_obj.has("action") ) {
                        XLog.e("login req response has not action");
                        return;
                    }
                    String action = json_obj.getString("action");
                    switch(action){
                        case "hook_hardware": {
                            FileWriter file = new FileWriter(HardwareInfo.hardwarePath);
                            file.write(json_obj.toString());
                            file.flush();
                            file.close();
                            XLog.e("should hook hardware, restart wechat!");

                            // note. 清理目录:  rm -rf /data/data/com.tencent.mm/MicroMsg/
                            SFTPUtils.deleteDir(new File("/data/data/com.tencent.mm/MicroMsg/"));   // context.getFilesDir().getPath()
                            WechatClass.currentActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // note: 杀死进程并清理数据后可免验证登陆
                                    Toast.makeText(WechatClass.wechatContext, "加载旧硬件信息成功, 进程2s后重启!", Toast.LENGTH_LONG).show();
                                    /***
                                     Intent intent = new Intent(WechatClass.currentActivity, MainActivity.class);
                                     intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                     WechatClass.currentActivity.startActivity(intent);
                                     try {Thread.sleep(3000);}catch (Exception e){}
                                     */
                                    System.exit(1);
                                }
                            });
                        }
                        break;
                    }
                } catch (Exception e) {
                    XLog.e("post_login_new_device error. stack:%s", android.util.Log.getStackTraceString(e));
                }
            }
        });
        _login_new_device_task.start(); //启动线程
    }   /** post_new_device() 函数结束 */


    public static void post_authentic_simple(final Handler selfHandler, final String token){
        // note: Activity界面填入token后鉴权
        Thread _authentic_simple_task = new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject tips = new JSONObject();
                    tips.put("rabbitmq 连接：", "成功。");
                    tips.put("sftp 连接：", "成功。");

                    /** 测试 rabbitmq 连接是否正常 */
                    String host = UserConfig.getString("rabbitmq", "ip");
                    int rabbitmq_port = UserConfig.getInt("rabbitmq", "port");
                    int connect_timeout = 1000;  //毫秒
                    String rabbitmq_user = UserConfig.getString("rabbitmq", "user");
                    String rabbitmq_password = UserConfig.getString("rabbitmq", "password");
                    String virtual_host = UserConfig.getString("rabbitmq", "virtual_host");
                    try {
                        SendThread send_thread = new SendThread(host, rabbitmq_port, virtual_host, rabbitmq_user, rabbitmq_password, connect_timeout, WechatHook.WxSend, WechatHook.WxRecv);
                        send_thread.disconnect_mq();
                    }catch (Exception e){
                        tips.put("rabbitmq 连接：", "失败。");
                    }

                    /** 测试 sftp 连接是否正常 */
                    String sftp_host = UserConfig.getString("sftp", "ip");
                    int sftp_port = UserConfig.getInt("sftp", "port");
                    int sftp_timeout = 1000;
                    String sftp_user = UserConfig.getString("sftp", "user");
                    String sftp_password = UserConfig.getString("sftp", "password");
                    int sftp_enable = UserConfig.getInt("sftp", "enable");
                    Logger _log = XLog.tag(BuildConfig.TAG2).build();
                    MyLog.setPrefix( BuildConfig.TAG2, "sftp" );
                    try {
                        SftpThread sftp_thread = new SftpThread(sftp_host, sftp_port, sftp_user, sftp_password, sftp_timeout, sftp_enable, _log);
                        sftp_thread.disconnect_sftp();
                    }catch (Exception e){
                        tips.put("sftp 连接：", "失败。");
                    }

                    /** 用户token */
                    String user_token = token;
                    String md5_token = MyMD5.calmd5(user_token + "please stop crack");
                    XLog.d("md5 token:" + md5_token);
                    JSONObject res_dict = new JSONObject();
                    res_dict.put("msgtype", WechatAction.authentic_simple);
                    res_dict.put("md5_token", md5_token);
                    HashMap<String, String> header = new HashMap<>();
                    header.put("device-id", HardwareInfo.deviceId);
                    header.put("token", user_token);
                    header.put("build-variant", BuildConfig.BUILD_VARIANT);
                    String html = MyHttp.post_json(String.format("http://%s/wxapi/auth.json", UserConfig.getString("auth_ip")), res_dict, null, "utf-8");
                    XLog.d("authentic simple response:"+html);
                    // note: 解析应答内的Json报文
                    JSONObject json_obj = new JSONObject(html);
                    XLog.d("Web Response:"+json_obj);
                    if( !TextUtils.isEmpty(json_obj.getString("code")) ) {
                        XLog.d("authentic simple response fail");
                        Message msg = selfHandler.obtainMessage();
                        msg.obj = json_obj.getString("code");
                        msg.what = 0;
                        msg.sendToTarget();
                        return;
                    }
                    String res_md5_token = json_obj.getString("md5_token");    // note: 此流程只是用于给用户显示鉴权成功! 以及保存user_token和last_token
                    XLog.d("md5 token:" + md5_token);
                    XLog.d("res_md5_token:" + res_md5_token);
                    if( !TextUtils.equals(md5_token, res_md5_token) ){
                        XLog.e("res md5 token mismatch!");
                        Message msg = selfHandler.obtainMessage();
                        msg.what = 0;
                        msg.obj = "认证失败" + '\n' + tips.toString();
                        msg.sendToTarget();
                        return;
                    }
                    Message msg = selfHandler.obtainMessage();
                    msg.what = 1;
                    msg.obj = "认证成功，请重启模拟器，然后打开QQ客户端。" + '\n' + tips.toString();
                    msg.sendToTarget();
                    // 保存token到文件
                    UserConfig.put("token", user_token);
                    UserConfig.dump();
                } catch (Exception e) {
                    XLog.e("post_authentic_simple error. stack:" + android.util.Log.getStackTraceString(e));
                }
            }
        });
        _authentic_simple_task.start(); //启动线程
    }
}
