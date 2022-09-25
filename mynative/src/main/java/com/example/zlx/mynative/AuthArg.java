package com.example.zlx.mynative;

public class AuthArg {
    public static String g_robot_wxid = "g_robot_wxid";     // 其实用的是 MySync.g_robot_wxid

    public boolean is_plugin_auth_success = false;       // note 该变量用于插件认证不通过时, 打印Toast.show()
    public String auth_type = "auth_type";  // WechatAction.authentic_i:    WechatAction.authentic_u;
    public String key = "key";
    public String user_token = "user_token";
    public String last_token = "last_token";

    public static final String authentic_i = "authentic_i";
    public static final String authentic_u = "authentic_u";
    public static String next_auth_state = "authentic_i";
    public boolean is_i(){
        switch(this.auth_type){
            case AuthArg.authentic_i:
                return true;
        }
        return false;
    }
}