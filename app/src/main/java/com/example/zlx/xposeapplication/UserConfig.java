package com.example.zlx.xposeapplication;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.example.zlx.mybase.MyFile;
import com.example.zlx.mybase.MyPath;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class UserConfig {
    static String config_path = MyPath.join( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), BuildConfig.PROC_TYPE + ".config");
    public static JSONObject user_config = null;

    public static void init_from_config(String gateway) throws Exception{
        try {
            user_config = new JSONObject();
            user_config.put("token", "");

            {
                JSONObject rabbitmq = new JSONObject();
                rabbitmq.put("ip", "");
                rabbitmq.put("port", 5672);
                rabbitmq.put("user", "guest");
                rabbitmq.put("password", "guest");
                rabbitmq.put("virtual_host", "/");
                rabbitmq.put("connect_timeout", 2000);
                user_config.put("rabbitmq", rabbitmq);
            }
            {
                JSONObject sftp = new JSONObject();
                sftp.put("ip", "");
                sftp.put("port", 2222);
                sftp.put("user", "zlx");
                sftp.put("password", "password");
                sftp.put("enable", 1);
                user_config.put("sftp", sftp);
            }
            {
                JSONObject other = new JSONObject();
                other.put("http_ip", "192.168.1.6");
                other.put("http_port", 8080);
                user_config.put("other", other);
            }
        }catch (Exception e){
            XLog.e("UserConfig init error. stack:%s", android.util.Log.getStackTraceString(e));
        }

        InputStream inStream = null;
        try {
            // 读取配置文件: /sdcard/tencent/config
            /**
             * Environment.getExternalStorageDirectory().getAbsolutePath();    //  Environment.getExternalStorageDirectory().getAbsolutePath() => /storage/emulated/0
             * System.getenv("EXTERNAL_STORAGE");  // 路径:   /storage/emulated/legacy,  而/storage/sdcard0 也是指向这里!!!
             * System.getenv("SECONDARY_STORAGE");   //    null
             */
            JSONObject restore_config = MyFile.readUserConfig(config_path);
            if (restore_config != null){
                UserConfig.load(restore_config, user_config);
            } else {
                XLog.w("config file not exists.path:%s", config_path);
                if (BuildConfig.DEBUG) {
                    // note. 调试环境
                    UserConfig.put("sftp", "ip", gateway);
                    UserConfig.put("rabbitmq", "ip", gateway);
                } else {
                    if(TextUtils.equals(BuildConfig.FLAVOR, "taobaoke")) {
                        // note. 用于临时编译ServiceRelease版本的发单软件apk
                        UserConfig.put("sftp", "ip", gateway);
                        UserConfig.put("rabbitmq", "ip", gateway);
                    } else {
                        // note. 生产环境
                        UserConfig.put("sftp", "ip", "192.168.0.11");
                        UserConfig.put("rabbitmq", "ip", "192.168.0.11");
                    }
                }
            }
        } finally {
            if(inStream != null){
                inStream.close();
            }
        }
    }


    public static void load(JSONObject source, JSONObject target) throws Exception {
        Iterator iterator = source.keys();
        while(iterator.hasNext()){
            String key = (String) iterator.next();
            Object value = source.get(key);
            // note: 目标存在该key时, 才考虑替换
            if( ! target.has(key) ) {
                XLog.e("target not contain key: %s", key);
                continue;
            }

            if(value instanceof JSONObject){
                JSONObject source_child = (JSONObject)value;
                JSONObject target_child = target.getJSONObject(key);
                load(source_child, target_child);
            } else if(value instanceof String){
                if( TextUtils.isEmpty((String)value)) {
                    XLog.e("source key:%s, value is null", key);
                    continue;
                }
                XLog.w("set key: %s, value: %s", key, value);
                target.put(key, value);
            } else {
                XLog.w("set key: %s, value: %s", key, value);
                target.put(key, value);
            }
        }
    }


    public static int getInt(String key) throws Exception {
        return user_config.getInt(key);
    }


    public static int getInt(String section, String key) throws Exception {
        if(section == null) {
            throw new Exception("section must not null!");
        }

        JSONObject sub_json = user_config.getJSONObject(section);
        return sub_json.getInt(key);
    }


    public static String getString(String key) throws Exception {
        return user_config.getString(key);
    }


    /**
     *
     * @param section   值参考: "sftp",  "rabbitmq"
     * @param key
     * @return
     * @throws Exception
     */
    public static String getString(String section, String key) throws Exception {
        if(section == null) {
            throw new Exception("section must not null!");
        }

        JSONObject sub_json = user_config.getJSONObject(section);
        return sub_json.getString(key);
    }


    public static void put(String key, Object value) throws Exception {
        user_config.put(key, value);
    }


    public static void put(String section, String key, Object value) throws Exception {
        if(section == null) {
            throw new Exception("section must not null!");
        }

        JSONObject sub_json = user_config.getJSONObject(section);
        sub_json.put(key, value);
    }


    public static void dump() throws Exception{
        //String jsonString = user_config.toString();
        //XLog.e("save config: %s", jsonString.toString());
        MyFile.writeUserConfig(config_path, user_config);
    }
}
