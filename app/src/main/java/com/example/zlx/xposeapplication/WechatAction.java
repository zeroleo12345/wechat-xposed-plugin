package com.example.zlx.xposeapplication;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;

import com.elvishew.xlog.XLog;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

//import redis.clients.jedis.Jedis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.example.zlx.mybase.*;

/* 代码风格:
 * note 当post Task的时需要传入参数, 而参数是从微信收取消息时, 入到sqlite 2张表以上的! 或者需要字段参数个数超过2个以上的! 或者需要调用app parse的!
 * note 则使用msgid透传给python侧! 调用的时候, App侧再从sqlite查回来! 因为本身到py侧也要查多一次sqlite! By zlx, At:2017-12-28
 * note 而单张表的, 则解析需要的所有参数, 再透传给py侧, py侧拿到所有参数再回调App! 因为使用msgid, App侧要查多一次sqlite! 效率低一些!
 * TODO: geta8key_data_req_url, geta8key_data_username 改回使用传msgid, 查sqlite方式.
 */
//@SuppressWarnings("WeakerAccess");
public class WechatAction extends Thread {
    static final String DB_F = "\u007f";
    //note. 内部消息
    static final String text_msg = "text_msg";
    static final String image_msg = "image_msg";
    static final String voice_msg = "voice_msg";
    static final String video_msg = "video_msg";
    static final String system_msg = "system_msg";
    static final String card_msg = "card_msg";
    static final String attach_card_msg = "attach_msg";
    static final String weblink_card_msg = "weblink_card_msg";      // 网站链接卡片
    static final String miniapp_card_msg = "miniapp_card_msg";      // 小程序卡片
    static final String chatroom_invite_msg = "chatroom_invite_msg";    // 群邀请网站链接卡片
    static final String luckymoney_msg = "luckymoney_msg";
    static final String emoji_msg = "emoji_msg";
    static final String namecard_msg = "namecard_msg";
    //note. end
    private final String heartbeat_i = "heartbeat_i";
    private final String heartbeat_u = "heartbeat_u";
    private final String heartbeat_t = "heartbeat_t";
    static final String chatroom_info_res = "chatroom_info_res";
    static final String all_chatroom_info_res = "all_chatroom_info_res";
    static final String update_alias_res = "update_alias_res";
    static final String update_header_res = "update_header_res";
    static final String update_nickname_res = "update_nickname_res";
    static final String latest_header_res = "latest_header_res";
    static final String latest_nickname_res = "latest_nickname_res";
    static final String get_roomdata_res = "get_roomdata_res";
    static final String get_newbie_res = "get_newbie_res";
    static final String chatroom_invite_res = "chatroom_invite_res";
    static final String update_friend_res = "update_friend_res";
    static final String notify_new_friend_req = "notify_new_friend_req";
    static final String image_msg_done = "image_msg_done";
    static final String voice_msg_done = "voice_msg_done";
    static final String attach_msg_done = "attach_msg_done";
    static final String video_msg_done = "video_msg_done";
    static final String ignore_msg = "ignore_msg";
    static final String luckymoney_detail = "luckymoney_detail";
    static final String login_success = "login_success";
    static final String login_new_device = "login_new_device";
    static final String authentic_simple = "authentic_simple";


    // chatroom表memberlist微信ID分隔符
    static String FenHao = ";";
    static String MaoHao = ":";
    static HashMap content_split(String content, String msgtype){
        HashMap hashmap = new HashMap();
        switch(msgtype){
            case WechatAction.voice_msg:
            case WechatAction.video_msg:
            case WechatAction.video_msg_done:
            case WechatAction.voice_msg_done: {
                String[] content_list = content.substring(0, content.length() - "\n".length()).split(":", 3);
                if (content_list.length == 3) {
                    hashmap.put("talker", content_list[0]);
                    hashmap.put("duration", Integer.parseInt(content_list[1]));     // 语音, 视频时长
                    hashmap.put("content", "");
                } else {
                    hashmap.put("talker", "");
                    hashmap.put("content", "");
                }
            }break;
            case WechatAction.system_msg: {
                // content = "周礼欣"邀请你加入了群聊，群聊参与人还有：June@Fibonacci、【券狗优惠】导购070、宁宁、朵朵助理
                hashmap.put("talker", "");
                hashmap.put("content", content);
            }break;
            case WechatAction.emoji_msg: {
                String[] content_list = content.split(":", 2);
                hashmap.put("talker", content_list[0]);
                hashmap.put("content", content_list[1]);
            }break;
            // 其他
            default: {
                String[] content_list = content.split(":\n", 2);
                if( content_list.length == 2 ) {
                    hashmap.put("talker", content_list[0]);
                    hashmap.put("content", content_list[1]);
                }else{
                    hashmap.put("talker", "");
                    hashmap.put("content", "");
                }
            }break;
        }
        // 修正不用的字段
        switch (msgtype){
            case WechatAction.attach_msg_done:
            case WechatAction.video_msg_done:
            case WechatAction.voice_msg_done:
                hashmap.put("content", "");
        }
        return hashmap;
    }


    // 其他变量
    private final int HEARTBEAT_TIMEOUT = 8;
    private int accept_backend_pid = -1;
    private int last_req_msg_time = -1; // 上一次从queue接收消息时间点
    private int last_idle_time = -1; // 上一次设置空闲时间
    private boolean idle = true;  //用于控制能否被接管


    static String wxtype2msgtype(int type, String subtype){
        /** 根据微信消息type值 转换成 对应字符串 */
        switch(type){
            case 1: return WechatAction.text_msg;   // 文字
            case 3: return WechatAction.image_msg;  // 图片
            case 34: return WechatAction.voice_msg; // 语音
            case 42: return WechatAction.namecard_msg; // 个人名片消息
            case 43: return WechatAction.video_msg; // 视频
            case 47: return WechatAction.emoji_msg; // Emoji
            case 49:
                switch (subtype){
                    case "4":   // 网页链接卡片
                    case "5": return WechatAction.weblink_card_msg;   // 网站链接卡片, 也包含: 群邀请卡片 chatroom_invite_msg
                    case "6": return WechatAction.attach_card_msg;   // 附件卡片
                    case "33":  return WechatAction.miniapp_card_msg;   // 小程序卡片
                    case "19":  // TODO 未知
                    case "":
                    default:
                        return WechatAction.card_msg;   //卡片消息
                }
            case 10000: return WechatAction.system_msg;
            case 10002: return ignore_msg;    // 你邀请"小7管家"加入了群聊  撤销提示
            case 1048625: return "";  // TODO 未知什么消息
            case 469762097:
            case 436207665: // 大多数是这个
                return WechatAction.luckymoney_msg;
        }
        return "";
    }


    // 构造函数
    private ImgThread img_thread = null;
    private VoiceThread voice_thread = null;
    WechatAction() { }


    String topic = null;
    private void idle_true(int now) throws Exception{
        if(now==0) {
            now = (int)(System.currentTimeMillis() / 1000);
        }
        this.idle = true;
        // TODO 改进为: 使用发文字消息给好友, 检测app侧微信是否僵死,断网等状态
//        String str_backend_pid = "";
//        str_backend_pid = String.valueOf(this.accept_backend_pid);
//        if( _now - WechatHook.last_sync > 20 * 60){
//            /** 如果心跳包sync超时大于20分钟 */
//            str_backend_pid = "not sync";
//            this.idle = false;
//        }else{
//            str_backend_pid = String.valueOf(this.accept_backend_pid);
//            this.idle = true;
//        }
        this._send_proc_idle();
        this.last_idle_time = now;
    }
    private void idle_false(){
        idle = false;
    }


    private void _send_proc_idle() throws Exception{
        // note 发送目标exchange=ProcIdle, routing_key=robot_wxid, message=res_dict, timeout=5.  (每8s发送一次idle消息)
        String robot_wxid = WechatHook.get_robot_info(MySync.g_robot_wxid);
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", "idle");
        res_dict.put("wxid", robot_wxid);
        res_dict.put("alias", WechatHook.get_robot_info("robot_alias"));
        res_dict.put("nickname", WechatHook.get_robot_info("robot_nickname"));
        res_dict.put("queue_name", WechatHook.WxSend);
        if (this.topic == null) {
            this.topic = BuildConfig.PROC_TYPE + "." + robot_wxid;
        }
        WechatHook.rpush_queue.put(Arrays.asList(WechatHook.ProcIdle, this.topic, res_dict.toString(), "7000"));
        //if( BuildConfig.DEBUG ) XLog.d("send ProcIdle, topic:%s, data:%s", this.topic, res_dict );      // note: 版本更新时, 可打开这个日志
    }


    private void py_idle(final JSONObject req_dict) throws Exception{
        if( this.idle){
            this._send_proc_idle();
        }
    }


    private void heartbeat_i(final JSONObject req_dict) throws Exception{
        /** py侧发过来的i包 (接管请求),  App侧返回应答包含是否接受控制. 参数:{
         * "pid": self.pid,
         * "queue_name": ''
         * }
         */
        int pid = req_dict.getInt("pid");
        String queue_name = req_dict.getString("queue_name");
        //
        if( idle ){
            // note 发回 init 包
            JSONObject res_dict = new JSONObject();
            res_dict.put("msgtype", this.heartbeat_i);
            res_dict.put("pid", pid);
            res_dict.put("queue_name", WechatHook.WxSend);
            WechatHook.rpush_queue.put( Arrays.asList("", queue_name, res_dict.toString(), "0") );
            if(BuildConfig.DEBUG) XLog.d(">>>publish heartbeat_i, pid:%d.", pid);
            this.accept_backend_pid = pid;
            this.idle_false();
        }else{
            // note 发回 term 包
            JSONObject res_dict = new JSONObject();
            res_dict.put("msgtype", this.heartbeat_t);
            res_dict.put("pid", pid);
            res_dict.put("queue_name", WechatHook.WxSend);
            WechatHook.rpush_queue.put( Arrays.asList("", queue_name, res_dict.toString(), "0") );
            if(BuildConfig.DEBUG) XLog.d(">>>publish heartbeat_t, pid:%d.", pid);
        }
    }

    private void heartbeat_u(final JSONObject req_dict) throws Exception{
        /** py侧发过来的心跳,  apk侧返回是否接受控制. 参数:{
         * "pid": self.pid,
         * "queue_name": ''
         * }
         */
        int pid = req_dict.getInt("pid");
        String queue_name = req_dict.getString("queue_name");
        //
        if( this.accept_backend_pid != pid ){
            // 当pid不同时, 发送T包
            JSONObject res_dict = new JSONObject();
            res_dict.put("msgtype", this.heartbeat_t);
            res_dict.put("pid", pid);
            res_dict.put("queue_name", WechatHook.WxSend);
            WechatHook.rpush_queue.put( Arrays.asList("", queue_name, res_dict.toString(), "0") );
            XLog.w("publish heartbeat_t, accept_backend_pid != pid. %d != %d", this.accept_backend_pid, pid);
        }
    }

    private void heartbeat_t(final JSONObject req_dict) throws Exception{
        /** py侧发过来的t包 (退出接管). 参数:{
         * "pid": self.pid,
         * "queue_name": ''
         * }
         */
        int pid = req_dict.getInt("pid");
        String queue_name = req_dict.getString("queue_name");
        //
        if( this.accept_backend_pid == pid ){
            this.last_req_msg_time = -1;
            this.accept_backend_pid = -1;
            idle_true(0);
        }
    }


    public void run() {
        String action;
        String jsonString;
        XLog.i("WechatAction thread start!");
        //Thread.sleep(5000);  //throw InterruptedException
        int now;
        Looper.prepare();
        while(true) {
            try {
                now = (int)(System.currentTimeMillis() / 1000);
                if( now - this.last_req_msg_time > 2*HEARTBEAT_TIMEOUT
                        && now - this.last_idle_time > HEARTBEAT_TIMEOUT)  // 防止过多hset操作
                {
                    // 当没收到任何时间超过n秒时, 设置为空闲
                    this.idle_true(now);
                }
                if(now - WechatHook.last_send_time >= HEARTBEAT_TIMEOUT){
                    send_heartbeat();
                }
                jsonString = WechatHook.recv_queue.poll(10, TimeUnit.SECONDS);       // note: 阻塞读
                if( jsonString == null ){
                    continue;
                }
                this.last_req_msg_time = now;
                /*
                java.util.List<String> rows = send_jedis.blpop(10, WechatHook.WxSend);  //暂设timeout为2秒.  0:阻塞
                if(rows == null || rows.size() == 0){
                    continue;
                }
                this.last_req_msg_time = now;
                jsonString = rows.get(1);
                */
                if( BuildConfig.cut ){ // note 需验证
                    int last_verify = (int)(MySync.currentTimeMillis / 1000);
                    if( now - last_verify > 30 * 60 ){
                        XLog.e("verify timeout");
                        continue;
                    }
                }
                JSONTokener jsonParser = new JSONTokener(jsonString);
                JSONObject req_dict = (JSONObject) jsonParser.nextValue();
                if( !req_dict.has("action") ){
                    XLog.e("msg miss action, req_dict:%s", req_dict);
                    continue;
                }
                action = req_dict.getString("action");
                if( !BuildConfig.DEBUG && action.equals("heartbeat_u") ) {
                    ;// release版本, 且是心跳包, 则不打印
                }else{
                    String filebuf = "";
                    if( req_dict.has("filebuf") ){
                        filebuf = req_dict.getString("filebuf");
                        req_dict.put("filebuf", "IGNORE");
                    }
                    XLog.i(">>>blpop %s. %s", WechatHook.WxSend, req_dict);
                    if( req_dict.has("filebuf") ){
                        req_dict.put("filebuf", filebuf);
                    }
                }
                switch(action){
                    case "py_idle":
                        py_idle(req_dict);
                        break;
                    case "heartbeat_i":
                        heartbeat_i(req_dict);
                        break;
                    case "heartbeat_u":
                        heartbeat_u(req_dict);
                        break;
                    case "heartbeat_t":
                        heartbeat_t(req_dict);
                        break;
                    case "chatroom_invite_url_req":
                        chatroom_invite_url_req(req_dict);
                        break;
                    case "web_login":
                        web_login(req_dict);
                        break;
                    case "settings_need_verify":
                        settings_need_verify(req_dict);
                        break;
                    case "set_selfname_inroom":
                        set_selfname_inroom(req_dict);
                        break;
                    case "get_contact_by_wxid":
                        get_contact_by_wxid(req_dict);
                        break;
                    case "add_friend_to_contact_by_wxid":
                        add_friend_to_contact_by_wxid(req_dict);
                        break;
                    case "add_friend_to_contact_by_qrid":
                        add_friend_to_contact_by_qrid(req_dict);
                        break;
                    case "accept_friend":
                        accept_friend(req_dict);
                        break;
                    case "get_qrcode":
                        get_qrcode(req_dict);
                        break;
                    case "send_text":
                        send_text(req_dict);
                        break;
                    case "send_attachment":
                        send_attachment(req_dict);
                        break;
                    case "transmit_voice":
                        transmit_voice(req_dict);
                        break;
                    case "transmit_video":
                        transmit_video(req_dict);
                        break;
                    case "send_voice":
                        send_voice(req_dict);
                        break;
                    case "invoke_send_voice":
                        invoke_send_voice(req_dict);
                        break;
                    case "sftp_get":
                        sftp_get(req_dict);
                        break;
                    case "send_video":
                        send_video(req_dict);
                        break;
                    case "send_local_video":
                        send_local_video(req_dict);
                        break;
                    case "send_img":
                        send_img(req_dict);
                        break;
                    case "invoke_send_img":
                        invoke_send_img(req_dict);
                        break;
                    case "send_gif":
                        send_gif(req_dict);
                        break;
                    case "send_card":
                        send_card(req_dict);
                        break;
                    case "transmit_card":
                        transmit_card(req_dict);
                        break;
                    case "transmit_card2":
                        transmit_card2(req_dict);
                        break;
                    case "change_roomnotice":
                        change_roomnotice(req_dict);
                        break;
                    case "kick_member":
                        kick_member(req_dict);
                        break;
                    case "add_member":
                        add_member(req_dict);
                        break;
                    case "change_roomowner":
                        change_roomowner(req_dict);
                        break;
                    case "del_chatroom":
                        del_chatroom(req_dict);
                        break;
                    case "quit_chatroom":
                        quit_chatroom(req_dict);
                        break;
                    case "save_room_to_contact":
                        save_room_to_contact(req_dict);
                        break;
                    case "all_chatroom_info_req":
                        all_chatroom_info_req(req_dict);
                        break;
                    case "update_alias":
                        update_alias(req_dict);
                        break;
                    case "get_roomdata":
                        get_roomdata(req_dict);
                        break;
                    case "get_newbie":
                        get_newbie(req_dict);
                        break;
                    case "latest_header":
                        latest_header(req_dict);
                        break;
                    case "latest_nickname":
                        latest_nickname(req_dict);
                        break;
                    case "download_bigimg":
                        download_bigimg(req_dict);
                        break;
                    case "download_video":
                        download_video(req_dict);
                        break;
                    case "download_voice":
                        download_voice(req_dict);
                        break;
                    case "download_attachment":
                        download_attachment(req_dict);
                        break;
                    case "open_luckymoney":
                        open_luckymoney(req_dict);
                        break;
                    case "get_luckymoney_detail":
                        get_luckymoney_detail(req_dict);
                        break;
                    case "sqlite_execute":
                        sqlite_execute(req_dict);
                        break;
                    case "sqlite_update":
                        sqlite_update(req_dict);
                        break;
                    case "sqlite_delete":
                        sqlite_delete(req_dict);
                        break;
                    case "sqlite_select":
                        sqlite_select(req_dict);
                        break;
                    default:
                        XLog.e("unknow action:"+action);
                }
            } catch (Throwable e) {
                XLog.e("[while loop] %s", android.util.Log.getStackTraceString(e));
                continue;
            }
        }
    }


    private void send_heartbeat() throws Exception{
        // App测主动发送心跳
        if( this.accept_backend_pid == -1) return;
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", this.heartbeat_u);
        res_dict.put("pid", this.accept_backend_pid);
        res_dict.put("queue_name", WechatHook.WxSend);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
        XLog.v(">>>publish heartbeat_u, pid:%d", this.accept_backend_pid);
    }


    /**
     * sqlite_execute
     * @param req_dict
     * "sql" : " CREATE INDEX IF NOT EXISTS img_flag_lastupdatetime_index ON img_flag ( lastupdatetime ) ",
     */
    void sqlite_execute(final JSONObject req_dict) throws Exception{
        String sql = req_dict.getString("sql");
        XLog.i("sqlite_execute, sql:%s", sql);

        try {
            WechatClass.EnMicroMsg.execSQL(sql);
        }catch (Throwable e) {
            if( !req_dict.has("ignore_error") ){
                XLog.e("sqlite_execute error, stack: %s", android.util.Log.getStackTraceString(e));
            }
        }
    }


    /**
     * sqlite_update
     * @param req_dict
     * "table" : "rconversation",
     * "set"   :  {""name": "value"}
     * "where" : "username=?",
     * "args" : ["wxid_1", ]
     */
    void sqlite_update(final JSONObject req_dict) throws Exception{
        String table = req_dict.getString("table");
        JSONObject set = new JSONObject( req_dict.getString("set") );
        String where = req_dict.getString("where");
        JSONArray args = req_dict.getJSONArray("args");
        XLog.i("sqlite_update, table:%s, set:%s, where:%s, args:%s", table, set, where, args);

        ContentValues contentValues = new ContentValues();
        String key, value;
        Iterator iterator = set.keys();
        while(iterator.hasNext()){
            key = (String) iterator.next();
            value = set.getString(key);
            contentValues.put(key, value);
        }

        int args_len = args.length();
        String[] params = new String[ args_len ];
        for(int i=0; i < args_len; i++){
            params[i] = (String)args.get(i);
        }

        XLog.i("contentValues:%s, params:%s", contentValues, params);
        int ret = WechatClass.EnMicroMsg.rawUpdate(table, contentValues, where, params);    // db_handler.rawDelete("rconversation", "username=?", new String[]{field_talker});
        XLog.i("Update ret:%d", ret);
    }


    /**
     * sqlite_delete
     * @param req_dict
     * "table" : "rconversation",
     * "where" : "username=?",
     * "args" : ["wxid_1", ]
     */
    void sqlite_delete(final JSONObject req_dict) throws Exception{
        String table = req_dict.getString("table");
        String where = req_dict.getString("where");
        JSONArray args = req_dict.getJSONArray("args");
        XLog.i("sqlite_delete, table:%s, where:%s, args:%s", table, where, args);

        int args_len = args.length();
        String[] params = new String[ args_len ];
        for(int i=0; i < args_len; i++){
            params[i] = (String)args.get(i);
        }
        int ret = WechatClass.EnMicroMsg.rawDelete(table, where, params);    // db_handler.rawDelete("rconversation", "username=?", new String[]{field_talker});
        XLog.i("Delete ret:%d", ret);
    }


    /**
     * sqlite_select
     * @param req_dict
     * {      </br>
     * "sql" : "select * from rcontact where wxid='123'",      </br>
     * "response" : true,      // 是否返回      </br>
     * }
     */
    void sqlite_select(final JSONObject req_dict) throws Exception{
        String sql = req_dict.getString("sql");
        boolean response = req_dict.getBoolean("response");;
        XLog.i("sqlite_select, sql:%s, response:%b", sql, response);

        HashMap<String, String> table_to_db = new HashMap<>();
        table_to_db.put("from FavItemInfo", "enFavorite");
        Db database = WechatClass.EnMicroMsg;
        Cursor cursor = null;
        try {
            for (String table_name: table_to_db.keySet()) {
                Pattern p = Pattern.compile(table_name);
                Matcher m = p.matcher(sql);
                if ( m.find() ) {
                    String database_name = table_to_db.get(table_name);
                    XLog.w("database: %s", database_name);
                    database = WechatClass.enFavorite;
                }
            }
            cursor = database.rawQuery(sql, null);
            if (cursor == null) {
                XLog.e("sqlite_select rawQuery failed ret: null");
                return;
            }
            if (!cursor.moveToFirst()) {
                XLog.e("sqlite_select rawQuery empty.");
                return;
            }
            boolean headerExist = false;
            StringBuffer sss = new StringBuffer();
            while (cursor.isAfterLast() == false) {
                if( !headerExist ){
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        sss.append(cursor.getColumnName(i));
                        sss.append("|");
                    }
                    sss.append( "\n" );
                    headerExist = true;
                }
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    switch (cursor.getType(i)){
                        case Cursor.FIELD_TYPE_BLOB:
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            sss.append( cursor.getFloat(i) );
                            sss.append("|");
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            sss.append( cursor.getLong(i) );
                            sss.append("|");
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            sss.append( "" );
                            sss.append("|");
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            sss.append( cursor.getString(i) );
                            sss.append("|");
                            break;
                    }
                }
                sss.append( "\n" );
                cursor.moveToNext();
            }
            String data = sss.toString();
            XLog.i("SELECT rows:%s", data);
            if(!response) return;
            JSONObject res_dict = new JSONObject();
            res_dict.put("msgtype", WechatAction.ignore_msg);
            res_dict.put("data", data);
            // 插入redis
            XLog.i("rpush msgtype:ignore_msg, data:%s", data);
            WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
        } catch (Throwable e) {
            if( !req_dict.has("ignore_error") ){
                XLog.e("sqlite_select error, stack: %s", android.util.Log.getStackTraceString(e));
            }
        }finally {
            if (cursor != null) cursor.close();
        }
    }


    /**
     * 群邀请自动点击确认入群接口.
     * @param req_dict
     * {      </br>
     * geta8key_data_req_url : "http://support.weixin.qq.com/cgi-bin/mmsupport-bin/addchatroombyinvite?ticket=AW%2BDPIGMvUd0G0P7v99S5g%3D%3D&from=singlemessage&isappinstalled=0",      </br>
     * geta8key_data_username : "发送邀请链接人的wxid"      </br>
     * }
     */
    void chatroom_invite_url_req(final JSONObject req_dict) throws Exception{
        String geta8key_data_req_url = req_dict.getString("geta8key_data_req_url");
        String geta8key_data_username = req_dict.getString("geta8key_data_username");
        int geta8key_data_scene = 1;    //req_dict.getInt("geta8key_data_scene");
        int geta8key_data_reason = 0;    //req_dict.getInt("geta8key_data_reason");
        int geta8key_data_flag = 0;     //req_dict.getInt("geta8key_data_flag");
        String geta8key_data_net_type = "WIFI";     //req_dict.getString("geta8key_data_net_type");
        int geta8key_session_id = (int)System.currentTimeMillis();   //req_dict.getInt("geta8key_session_id");
        /** 更新步骤: ./群邀请确认/群邀请确认.txt
         * 定位到函数调用上一层, 搜索:   "reading_mode_data_useragent"
         * 进入类中, 搜索:    "geta8key_session_id"
         * invoke-direct/range {v0 .. v7}, Lcom/tencent/mm/modelsimple/l;-><init>(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;I)V
         * => invoke-direct {v0, v1, v2, v3, v4}, Lcom/tencent/mm/modelsimple/h;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V
         * => invoke-direct/range {v0 .. v11}, Lcom/tencent/mm/modelsimple/h;-><init>(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;ILjava/lang/String;Ljava/lang/String;I[B)V
         */
        // Call
        Object obj_modelsimple_m = newInstance(WechatClass.get("com/tencent/mm/modelsimple/h", "get8key"),     // note 该类是: /cgi-bin/micromsg-bin/geta8key
                geta8key_data_req_url, geta8key_data_username, geta8key_data_scene, geta8key_data_reason, geta8key_data_flag, geta8key_data_net_type, geta8key_session_id,
                null, null, 0, null
        );
        WechatClass.postTask(obj_modelsimple_m);
    }


    /**
     * 下载语音.(微信接收到语音后, 会自动下载好, 不需要hook).
     * @param req_dict
     * {      </br>
     * "msgid" : 123456,    //long类型.      </br>
     * "needbuf" : true or false,   //是否返回语音文件      </br>
     * "live" : true or false,   //是否多群直播, 直接回传      </br>
     * }
     */
    void download_voice(final JSONObject req_dict) throws Exception{
        long msgid = req_dict.getLong("msgid");
        boolean needbuf = false;
        if( req_dict.has("needbuf") ) needbuf = req_dict.getBoolean("needbuf");
        if( needbuf ) MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.voice.needbuf", msgid), true);
        boolean live = false;
        if( req_dict.has("live") ) live = req_dict.getBoolean("live");
        if( live )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.voice.live", msgid), true);
        XLog.i("download voice, msgid:%d, needbuf:%b, live:%b", msgid, needbuf, live);

        // Call
        ContentValues contentValues = WechatClass.EnMicroMsg.select("select type, status, createTime, isSend, talker, content, imgPath from message where msgid=?", new String[]{String.valueOf(msgid)});
        if (contentValues == null) {
            XLog.e("select error, contentValues is null");
            return;
        }

        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.voice_msg_done);
        res_dict.put("msgId", msgid);
        res_dict.put("type", contentValues.getAsInteger("type"));
        res_dict.put("status", contentValues.getAsInteger("status"));
        res_dict.put("createTime", contentValues.getAsLong("createTime"));
        res_dict.put("isSend", contentValues.getAsInteger("isSend"));     // 自己发的不入redis
        String talker = contentValues.getAsString("talker");
        String content = contentValues.getAsString("content");
        if (talker.endsWith("@chatroom")) {
            /** 群消息 */
            res_dict.put("room", talker);
            HashMap hashmap  = WechatAction.content_split(content, WechatAction.voice_msg_done);      // note: content实例 "wxid_w6f7i8zvvtbc12:2220:0\n"
            res_dict.put("talker", hashmap.get("talker"));
            res_dict.put("content", hashmap.get("content"));
            if( hashmap.containsKey("duration")){
                res_dict.put("duration", hashmap.get("duration"));
            }
        } else {
            /** 好友消息 */
            res_dict.put("room", "");
            res_dict.put("talker", talker);
            res_dict.put("content", content);
        }

        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.voice.live", msgid) ) ) {
            res_dict.put("live", true);
        }

        if( MySync.msg2downlaod.containsKey( String.format(Locale.ENGLISH, "%d.voice.needbuf", msgid) ) ) {
            String filename = contentValues.getAsString("imgPath");
            String voice_path = WechatClass.voiceFullpath(filename);
            // base64编码文件
            File voice_file = new File(voice_path);
            int size = (int) voice_file.length();
            byte[] bytes = new byte[size];
            BufferedInputStream stream = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(voice_file));
                stream.read(bytes, 0, bytes.length);
            } finally {
                if (stream != null) {
                    stream.close();
                }
            }
            String filebuf = Base64.encodeToString(bytes, Base64.DEFAULT);  //与Python通信时建议用default
            res_dict.put("filebuf", filebuf);
            res_dict.put("totalLen", bytes.length);

            File remoteFile = new File( WechatHook.remoteVoiceDir, voice_file.getName() ) ; // 传送到远端 /MicroMsg 目录
            res_dict.put("fileFullPath", remoteFile);
            XLog.i("rpush msgtype:voice_msg_done, fileFullPath:%s", remoteFile);
        }
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    /**
     * 下载大图.
     * @param req_dict
     * {      </br>
     * "msgId" : 123456,        //long类型.      </br>
     * "msgSvrId" : 456789,      </br>
     * "parseQR" : true or false,   //是否需要解析二维码      </br>
     * "needbuf" : true or false,   //是否返回图片文件      </br>
     * "live" : true or false,   //是否多群直播, 直接回传      </br>
     * }
     */
    void download_bigimg(final JSONObject req_dict) throws Exception{
        long msgId = req_dict.getLong("msgId");
        long msgSvrId = req_dict.getLong("msgSvrId");
        boolean need_parseQR = false;
        if( req_dict.has("parseQR") ) need_parseQR =  req_dict.getBoolean("parseQR");
        if( need_parseQR ) MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.img.parseQR", msgId), true);

        boolean needbuf = false;
        if( req_dict.has("needbuf") ) needbuf = req_dict.getBoolean("needbuf");
        if( needbuf ) MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.img.needbuf", msgId), true);
        boolean live = false;
        if( req_dict.has("live") ) live = req_dict.getBoolean("live");
        if( live )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.img.live", msgId), true);
        /** 判断记录是否存在 */
        long imgid = WechatClass.get_ImgInfo2Id_ByMsgSrvId(msgSvrId);
        if( imgid < 0 ){
            XLog.e("ImgInfo2 not find record, msgid:%d, msgsrvid:%d, need_parseQR:%b, needbuf:%b", msgId, msgSvrId, need_parseQR, needbuf);
            return;
        }
        XLog.i("ImgInfo2 imgid:%d, msgid:%d, msgsrvid:%d, need_parseQR:%b, needbuf:%b", imgid, msgId, msgSvrId, need_parseQR, needbuf);

        /** Call */
        /** note 先忽略这一段, 更新较下面内容.       667
         * 进入搜索: "task callback list is null" 的类中, 搜索:  "] add failed, task already done"   向下寻找
         * invoke-virtual {v2, v0, p6}, Lcom/tencent/mm/af/c$b;->a(Lcom/tencent/mm/af/c$a;Ljava/lang/Object;)Z      =>      invoke-virtual {v2, v0, p6}, Lcom/tencent/mm/ak/d$b;->a(Lcom/tencent/mm/ak/d$a;Ljava/lang/Object;)Z
         */
        LinkedList linklist = new LinkedList();
        Object obj_ab_c$b = newInstance(WechatClass.get("com/tencent/mm/ak/d$b"), imgid, msgId, 0);
        setObjectField(obj_ab_c$b, "dTG", 2130970396);      // note 这个数值需要断点

        /** 进入搜索结果的类, 搜索:   "task item already exists"       向上寻找
         * iput-object v0, p0, Lcom/tencent/mm/af/c$b;->hPJ:Ljava/util/List;        =>      iput-object v0, p0, Lcom/tencent/mm/ak/d$b;->dTH:Ljava/util/List;
         */
        setObjectField(obj_ab_c$b, "dTH", linklist);

        /** 全局搜索:    "filling image : %s position : %s"
         * 进入类中, 搜索:   "[oreh download_and_save] startScene, msgLoacalID:%d"
         * invoke-static {}, Lcom/tencent/mm/af/n;->Gx()Lcom/tencent/mm/af/c;       =>      invoke-static {}, Lcom/tencent/mm/ak/o;->Pg()Lcom/tencent/mm/ak/d;
         */
        Object obj_ab_c = callStaticMethod(WechatClass.ImgInfoStorage, "Pg");
        setObjectField(obj_ab_c, "dTA", obj_ab_c$b);

        /** 更新步骤: ./接收图片/接收图片.txt
         * 先定位调用函数的上层, 搜索: "task callback list is null"
         * 进入类中搜索:   "do scene, ("      向上寻找
         * invoke-direct/range {v1 .. v8}, Lcom/tencent/mm/af/j;-><init>(JJILcom/tencent/mm/w/f;I)V
         * iput-object v1, p0, Lcom/tencent/mm/af/c;->hPD:Lcom/tencent/mm/af/j;
         * iget-object v0, p0, Lcom/tencent/mm/af/c;->hPD:Lcom/tencent/mm/af/j;
         * iget-object v1, p0, Lcom/tencent/mm/af/c;->hPC:Lcom/tencent/mm/af/c$b;
         * iget v1, v1, Lcom/tencent/mm/af/c$b;->hPI:I
         * iput v1, v0, Lcom/tencent/mm/af/j;->hRq:I
         * ==================>
         * invoke-direct/range {v1 .. v8}, Lcom/tencent/mm/ak/k;-><init>(JJILcom/tencent/mm/ab/f;I)V
         * iput-object v1, p0, Lcom/tencent/mm/ak/d;->dTB:Lcom/tencent/mm/ak/k;
         * iget-object v0, p0, Lcom/tencent/mm/ak/d;->dTB:Lcom/tencent/mm/ak/k;
         * iget-object v1, p0, Lcom/tencent/mm/ak/d;->dTA:Lcom/tencent/mm/ak/d$b;
         * iget v1, v1, Lcom/tencent/mm/ak/d$b;->dTG:I
         * iput v1, v0, Lcom/tencent/mm/ak/k;->dVo:I
         */
        Object obj_ab_j = newInstance(WechatClass.get("com/tencent/mm/ak/k"), imgid, msgId, 0, obj_ab_c, 0);  //note: 该类是: ModelImage.DownloadImgService

        Object ov1 = getObjectField(obj_ab_c, "dTA");
        if( ov1 == null){
            XLog.e("ov1 is null");
            return;
        }
        Object oov1 = getObjectField(ov1, "dTG");
        if( oov1 == null){
            XLog.e("oov1 is null");
            return;
        }
        setObjectField(obj_ab_c, "dTB", obj_ab_j);
        setObjectField(obj_ab_j, "dVo", oov1);  // note 该类是 "/cgi-bin/micromsg-bin/getmsgimg"

        Object obj_r_j = getObjectField(obj_ab_c, "dTB");       // note 该类是 "/cgi-bin/micromsg-bin/getmsgimg"
        WechatClass.postTask(obj_r_j);
    }


    /**
     * 下载视频.
     * @param req_dict
     * {      </br>
     * "filename" : "204026130417d25c87561727",      </br>
     * "needbuf" : true or false,   //是否返回视频文件      </br>
     * "live" : true or false,   //是否多群直播, 直接回传      </br>
     * }
     */
    void download_video(final JSONObject req_dict) throws Exception{
        String filename = req_dict.getString("filename");
        boolean needbuf = false;
        if( req_dict.has("needbuf") ) needbuf = req_dict.getBoolean("needbuf");
        if( needbuf )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%s.video.needbuf", filename), true);
        boolean live = false;
        if( req_dict.has("live") ) live = req_dict.getBoolean("live");
        if( live )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%s.video.live", filename), true);
        XLog.i("download video, filename:%s, needbuf:%b, live:%b", filename, needbuf, live);

        /** Call */
        /** 搜索:   "/cgi-bin/micromsg-bin/downloadvideo"     667
         * com/tencent/mm/modelvideo/d      =>      Lcom/tencent/mm/modelvideo/d
         */
        Object obj_app_x = newInstance(WechatClass.get("com/tencent/mm/modelvideo/d", "downloadvideo"), filename);
        WechatClass.postTask(obj_app_x);
    }


    /**
     * 下载附件.
     * @param req_dict
     * {      </br>
     * "msgid" : 123456,    // long类型.      </br>
     * "needbuf" : true or false,   //是否返回视频文件      </br>
     * "live" : true or false,   //是否多群直播, 直接回传      </br>
     * }
     */
    void download_attachment(final JSONObject req_dict) throws Exception{
        long msgid = req_dict.getLong("msgid");
        boolean needbuf = false;
        if( req_dict.has("needbuf") ) needbuf = req_dict.getBoolean("needbuf");
        if( needbuf )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.attach.needbuf", msgid), true);
        boolean live = false;
        if( req_dict.has("live") ) live = req_dict.getBoolean("live");
        if( live )MySync.msg2downlaod.put(String.format(Locale.ENGLISH, "%d.attach.live", msgid), true);
        XLog.i("download attach, msgid:%d, needbuf:%b, live:%b", msgid, needbuf, live);

        /** 先入表 appattach */
        ContentValues contentValues = WechatClass.getMessageByMsgId(msgid);
        HashMap appmsg = WechatClass.parseMessageContent(contentValues.getAsString("content"), null);
        //XLog.d("appmsg:%s", appmsg);
        String cdn = (String)appmsg.get(".msg.appmsg.appattach.attachid");
        ContentValues v = new ContentValues();
        v.put("sdkVer", 0);
        v.put("mediaSvrId", (String)appmsg.get(".msg.appmsg.appattach.attachid"));
        v.put("type", 0);
        v.put("totalLen", (String)appmsg.get(".msg.appmsg.appattach.totallen") );
        v.put("offset", 0);
        v.put("status", 101);
        v.put("isUpload", 0);
        v.put("createTime", System.currentTimeMillis() );
        v.put("lastModifyTime", System.currentTimeMillis()/1000);
        v.put("fileFullPath", MyPath.join( WechatHook.localAttachDir, (String)appmsg.get(".msg.appmsg.title") ) );    // 设置好下载文件路径
        v.put("msgInfoId", msgid);
        v.put("netTimes", 0);
        v.put("isUseCdn", 0);
        WechatClass.EnMicroMsg.rawInsert("appattach", "", v);

        // 方法1:
        // note:  触发流程:  PC版微信登陆weixin, 发送附件, 然后Logcat查看msgid, 使用Debug Command测试.   附件下载后目录:  /sdcard/tencent/MicroMsg/Download/
        /** 搜索:     "summerapp progressCallBack[%s], isDownloadFinished[%b], isDownloadStarted[%b]", 向上寻找       667
         * invoke-direct {v0, p0}, Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI$4;-><init>(Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI;)V       =>       invoke-direct {v0, p0}, Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI$4;-><init>(Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI;)V
         * 进入类中, 搜索:      "summerapp onSceneEnd reset downloadAppAttachScene[%s] by mediaSvrId[%s]"
         * iget-object v4, p0, Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI;->sms:Lcom/tencent/mm/pluginsdk/model/app/ab;        =>      iget-object v5, p0, Lcom/tencent/mm/ui/chatting/AppAttachDownloadUI;->qzW:Lcom/tencent/mm/pluginsdk/model/app/ac;
         */
        Object obj_AppAttachDownloadUI = null;  //newInstance(WechatClass.get("com/tencent/mm/ui/chatting/AppAttachDownloadUI"));
        Object obj_AppAttachDownloadUI$4 = newInstance(WechatClass.get("com/tencent/mm/ui/chatting/AppAttachDownloadUI$4"), obj_AppAttachDownloadUI);
        Object obj_app_x = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/model/app/ac"), msgid, cdn, obj_AppAttachDownloadUI$4);

        // 方法2:
//        Object obj_app_x = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/model/app/ab"), cdn);

        /** Call */
        WechatClass.postTask(obj_app_x);
    }


    /**
     * 转发语音.
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "msgid" : 1,  // long类型. 语音消息 msgid      </br>
     * }
     */
    void transmit_voice(final JSONObject req_dict) throws Exception {
        // note 模拟器插入耳机后才能发送语音, 转发语音, 否则发送状态一直转圈圈
        String wxid_list = req_dict.getString("wxid_list");
        long msgid = req_dict.getLong("msgid");     // 语音amr文件大小, 单位:字节
        XLog.i("transmit voice, wxid_list:%s, msgid:%d", wxid_list, msgid);
        //
        ContentValues contentValues = WechatClass.EnMicroMsg.select("select FileName, TotalLen, VoiceLength from voiceinfo where MsgLocalId=?", new String[]{String.valueOf(msgid)});
        if (contentValues == null) {
            XLog.e("select error, contentValues is null");
            return;
        }

        if (BuildConfig.DEBUG) XLog.d("voiceinfo rows:%s", contentValues);
        String filename = contentValues.getAsString("FileName");
        if (TextUtils.isEmpty(filename)) {
            XLog.e("filename is null");
            return;
        }
        int totallen = contentValues.getAsInteger("TotalLen");
        if (totallen == 0) {
            XLog.e("totallen is null");
            return;
        }
        int voicelength = contentValues.getAsInteger("VoiceLength");
        if (voicelength == 0) {
            XLog.e("voicelength is null");
            return;
        }

        // note. 读取原amr文件为byte[]
        String fullpath = WechatClass.voiceFullpath(filename);
        File voice = new File(fullpath);
        if (!voice.exists()) {
            XLog.e("voice file not exists, fullpath:%s", fullpath);
            return;
        }
        int size = (int) voice.length();
        byte[] amr_byte_array = new byte[size];
        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(voice));
        buf.read(amr_byte_array, 0, amr_byte_array.length);
        buf.close();

        for (String wxid : wxid_list.split(";")) {
            // 调用内部接口
            JSONObject params = new JSONObject();
            params.put("wxid", wxid);
            params.put("filename", filename);
            params.put("voicelength", voicelength);
            params.put("totallen", totallen);
            //this.invoke_send_voice(params);
            this.voice_thread.add_task(params.toString());
        }
    }


    /**
     * 发送语音.
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "voicelength" : 2000,  // 语音时长, 单位: 毫秒      </br>
     *      "filebuf" : "b64 binary buffer",  // 文件二进制字符串, base64编码      </br>
     *      "max_concurrent" : 5, // (可选参数, 默认为5）每次发送图片最大并发数     </br>
     * }
     */
    void send_voice(final JSONObject req_dict) throws Exception{
        if(this.voice_thread == null || !this.voice_thread.isAlive()) {
            this.voice_thread = new VoiceThread();
            this.voice_thread.start();
        }

        // note 发送语音时, 如果一直转圈圈, 可以尝试插入耳机后, 重启模拟器
        String wxid_list = req_dict.getString("wxid_list");
        int voicelength = req_dict.getInt("voicelength");  // 单位: 毫秒, 如:2000
        String filebuf = req_dict.getString("filebuf");
        int concurrent = 5;
        if (req_dict.has("max_concurrent")){
            concurrent = req_dict.getInt("max_concurrent");
            if(this.voice_thread != null ) this.voice_thread.set_max_concurrent(concurrent);
        }
        XLog.i("send voice, wxid_list: %s, voicelength: %d, max_concurrent: %d", wxid_list, voicelength, concurrent);
        //
        byte[] amr_byte_array = Base64.decode(filebuf, Base64.DEFAULT);

        for (String wxid: wxid_list.split(";")) {
            // note. 根据传入的filebuf重新生成新文件
            int totallen = amr_byte_array.length;     // 语音amr文件大小, 单位:字节
            String filename = WechatClass.voiceFilename();
            XLog.i("voice filename: %s", filename);
            String to = WechatClass.voiceFullpath(filename);
            String from = MyPath.join(WechatHook.localTmpDir, filename);
            // 把byte[]写入成文件
            SystemUtil.writeByte2File(amr_byte_array, from);

            // 调用内部接口
            JSONObject params = new JSONObject();
            params.put("wxid", wxid);
            params.put("from", from);
            params.put("to", to);
            params.put("filename", filename);
            params.put("voicelength", voicelength);
            params.put("totallen", totallen);
            //this.invoke_send_voice(params);
            this.voice_thread.add_task(params.toString());
        }
    }


    void invoke_send_voice(final JSONObject req_dict) throws Exception{
        /**
         * 发送语音消息接口
         * @param req_dict
         * {      </br>
         *      "wxid" : "wxid_1"        // 发送目标      </br>
         *      "voicelength" : 2000,  // 语音时长, 单位: 毫秒      </br>
         *      "totallen" : 2000,  // 语音文件大小, 单位: 字节      </br>
         * }
         */
        String wxid = req_dict.getString("wxid");
        String from = req_dict.getString("from");
        String to = req_dict.getString("to");
        String filename = req_dict.getString("filename");
        int voicelength = req_dict.getInt("voicelength");  // 单位: 毫秒, 如:2000
        int totallen = req_dict.getInt("totallen");  // 单位: 字节, 如:2000
        XLog.i("invoke_send_voice, wxid: %s, from: %s, to: %s, filename: %s, voicelength: %d, totallen: %d", wxid, from, to, filename, voicelength, totallen);

        FileUtils.moveFile(new File(from), new File(to));
        {
            // Call
            String ClientId = filename;
            String User = wxid;       // 发送对象, voiceinfo表User字段 - 对应message表talker字段
            long createTime = System.currentTimeMillis();       // 单位: 毫秒
            String Human = WechatHook.get_robot_info(MySync.g_robot_wxid);     // 自己
            // voiceinfo 存在唯一索引, 发送前先删掉原记录
            //WechatClass.Db.rawDelete("voiceinfo", "FileName=?", new String[]{new_filename});
            /** 搜索:     "[oneliang] fix send msg create time, before return, msg id:%s, now is :%s"         667
             * 进入类中, 搜索:    "bottlemessage"     向上寻找
             * invoke-static {v0}, Lcom/tencent/mm/kernel/h;->k(Ljava/lang/Class;)Lcom/tencent/mm/kernel/b/a;
             * invoke-interface {v0}, Lcom/tencent/mm/plugin/messenger/foundation/a/h;->aHD()Lcom/tencent/mm/plugin/messenger/foundation/a/a/c;
             * ===========>
             * invoke-static {v0}, Lcom/tencent/mm/kernel/g;->l(Ljava/lang/Class;)Lcom/tencent/mm/kernel/c/a;
             * invoke-interface {v0}, Lcom/tencent/mm/plugin/messenger/foundation/a/i;->bcY()Lcom/tencent/mm/plugin/messenger/foundation/a/a/f;
             */
            Object obj_foundation_a_h = callStaticMethod(WechatClass.get("com/tencent/mm/kernel/g"), "l", WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/i"));
            Object obj_message_row = callMethod(obj_foundation_a_h, "bcY");

            // 自增 msgid
            /** 搜索: "protect:c2c msg should not here"        向下寻找       667
             * invoke-direct {p0, v0}, Lcom/tencent/mm/storage/ax;->Rk(Ljava/lang/String;)Lcom/tencent/mm/plugin/messenger/foundation/a/a/c$b;        =>      invoke-direct {p0, v0}, Lcom/tencent/mm/storage/be;->Ze(Ljava/lang/String;)Lcom/tencent/mm/plugin/messenger/foundation/a/a/f$b;
             */
            Object obj_storage_ah$b = callMethod(obj_message_row, "Ze", User);

            /** 搜索: "check table name from id:%d table:%s getTableNameByLocalId:%s"     向上寻找        667
             * invoke-virtual {v2}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/c$b;->aHP()V
             * iget-wide v4, v2, Lcom/tencent/mm/plugin/messenger/foundation/a/a/c$b;->hPV:J
             * ================>
             * invoke-virtual {v2}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/f$b;->bdn()V
             * iget-wide v4, v2, Lcom/tencent/mm/plugin/messenger/foundation/a/a/f$b;->dTS:J
             */
            callMethod(obj_storage_ah$b, "bdn");  // incMsgLocalId 方法自增bQj, 而 msgid 与此关联
            long msgid = (long) getObjectField(obj_storage_ah$b, "dTS");

            /** 根据字符串 "insert:%d talker:%s id:%d type:%d svrid:%d msgseq:%d flag:%d create:%d issend:%d"     使用前半段搜索, 向上寻找          667
             * invoke-direct {p0, v0}, Lcom/tencent/mm/storage/ax;->Rh(Ljava/lang/String;)J     =>      invoke-direct {p0, v0}, Lcom/tencent/mm/storage/be;->Za(Ljava/lang/String;)J
             */
            int field_talkerId = (int) (long) callMethod(obj_message_row, "Za", User);

            //
            ContentValues message_row = new ContentValues();
            message_row.put("msgId", msgid);
            message_row.put("type", 34);
            message_row.put("status", 1);
            message_row.put("isSend", 1);
            message_row.put("createTime", createTime);
            message_row.put("talker", User);
            message_row.put("content", String.format(Locale.ENGLISH, "%s:%d:0\n", Human, voicelength, ":0"));   // 格式如: "wxid_w6f7i8zvvtbc12:2220:0\n"
            message_row.put("imgPath", filename);
            message_row.put("talkerId", field_talkerId);
            message_row.put("bizChatId", -1);
            WechatClass.EnMicroMsg.rawInsert("message", "", message_row);

            ContentValues voiceinfo_row = new ContentValues();
            voiceinfo_row.put("FileName", filename);
            voiceinfo_row.put("User", User);
            voiceinfo_row.put("MsgId", 0);
            voiceinfo_row.put("NetOffset", 0);
            voiceinfo_row.put("FileNowSize", 0);
            voiceinfo_row.put("TotalLen", totallen);
            if (totallen > WechatHook.max_upload_voice_byte) {
                voiceinfo_row.put("Status", 3); // note 上传多次 status:2
                MySync.filename2uploadvoice.put(String.format(Locale.ENGLISH, "%s.voice.totallen", filename), totallen);
            } else {
                voiceinfo_row.put("Status", 3); // note 单次上传 status:3
            }
            voiceinfo_row.put("CreateTime", createTime / 1000);
            voiceinfo_row.put("LastModifyTime", createTime / 1000);
            voiceinfo_row.put("ClientId", ClientId);
            voiceinfo_row.put("VoiceLength", voicelength);
            voiceinfo_row.put("MsgLocalId", msgid);
            voiceinfo_row.put("Human", Human);
            voiceinfo_row.put("reserved1", 0);
            voiceinfo_row.put("reserved2", "");
            voiceinfo_row.put("MsgSource", "");
            voiceinfo_row.put("MsgFlag", 0);
            voiceinfo_row.put("MsgSeq", 0);
            WechatClass.EnMicroMsg.rawInsert("voiceinfo", "", voiceinfo_row);

            // 方法1: 直接发送任务 (可能会只看到消息提醒, 没有上传语音到对方微信, 2018-08-19时使用已经解决此问题)
            /** 搜索: "/cgi-bin/micromsg-bin/uploadvoice"         667
             * com/tencent/mm/modelvoice/f      =>      com/tencent/mm/modelvoice/f
             */
            //Object obj_modelvoice_f = newInstance(WechatClass.get("com/tencent/mm/modelvoice/f", "/cgi-bin/micromsg-bin/uploadvoice"), filename);     // note 该类是: /cgi-bin/micromsg-bin/uploadvoice
            //WechatClass.postTask(obj_modelvoice_f);

            // 方法2: 内层调用方法2 (会查重, 防止任务重叠运行), 可能的报错: Can't create handler inside thread that has not called Looper.prepare(), http://blog.51cto.com/11838641/1842810
            WechatHook.handler.postDelayed(new VoiceRunnable(), 3000);

            XLog.i("send voice, msgid:%d, talkerid:%d", msgid, field_talkerId);
            MySync.filename2uploadvoice_task.put(String.format(Locale.ENGLISH, "%s", filename), filename);
        }
        // note 更新上次发送时间; 开始监视未上传完成语音
        WechatAction.send_voice_last_time.set(SystemUtil.get_timestamp());
        WechatAction.send_voice_create_time = WechatAction.send_voice_last_time.get();
        if(this._voice_task_thread == null || !this._voice_task_thread.isAlive()) {
            this._voice_task_thread = new VoiceTaskLoop();
            this._voice_task_thread.start();
        }
    }


    static AtomicInteger send_voice_last_time = new AtomicInteger(0);
//    static int send_voice_last_time = 0;
    static int send_voice_create_time = 0;
    private Thread _voice_task_thread = null;
    public class VoiceTaskLoop extends Thread {
        @Override
        public void run() {
            try {
                while(true) {
                    int last_time = WechatAction.send_voice_last_time.get();
                    int now = SystemUtil.get_timestamp();
                    if (now - last_time > 1) {
                        // 没有upload了
                        boolean isEmpty = MySync.filename2uploadvoice_task.isEmpty();
                        if (now - WechatAction.send_voice_create_time > 60 * 3 || isEmpty) {
                            // 大于 3 分钟, 退出线程
                            MySync.filename2uploadvoice_task.clear();
                            XLog.i("exit voice task thread");
                            return;
                        }
                        XLog.w("start voice not done thread, voice monitor loop, isEmpty: %b", isEmpty);
                        //callMethod(WechatClass.voiceinfo_handler, "h", WechatClass.voiceinfo_handler);
                        WechatHook.handler.postDelayed(new VoiceRunnable(), 0);     // 延迟执行
                        WechatAction.send_voice_last_time.set(now);
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                XLog.e("voice not done thread sleep error. stack:%s", android.util.Log.getStackTraceString(e));
            }
        }
    }


    /**
     * sftp 拉取大文件.
     * @param req_dict
     * {      </br>
     *      "directory" : "/MicroMsg/Video"        // 远程目录      </br>
     *      "filename" : "test.mp4"           // 文件名      </br>
     *      "session_id": "abcdefghijklmnopqrstuvwxyz123456"    // 会话ID, 会以此作为文件名  </br>
     * }
     * @param 返回
     *   {      </br>
     *       "msgtype":    'sftp_geted',      </br>
     *       "filepath":  "/sdcard/DCIM/tmp/test.mp4",      </br>
     *       "filename":    "test.mp4"      </br>
     *   }
     */
    void sftp_get(final JSONObject req_dict) throws Exception{
        String directory = req_dict.getString("directory");
        String filename = req_dict.getString("filename");
        String session_id = req_dict.getString("session_id");
        XLog.i("sftp get, directory: %s, filename: %s, session_id: %s", directory, filename, session_id);

        //
        String extention = FilenameUtils.getExtension(filename);
        String localFileName = String.format("%s.%s", session_id, extention);
        XLog.i("local filename: %s", localFileName);
        WechatHook.thread_touch.sftp_queue.put( Arrays.asList( WechatHook.localTmpDir, localFileName, directory, filename, "get", "0" ) );
    }


    /**
     * 发送本地视频文件.
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "videolength" : 1           // 视频长度, 单位: 秒      </br>
     *      "filepath" : "/storage/emulated/0/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/video/test.mp4",  // 视频文件全路径, 存在时, filebuf可不传      </br>
     * }
     */
    void send_local_video(final JSONObject req_dict) throws Exception{
        String wxid_list = req_dict.getString("wxid_list");
        String filepath = req_dict.getString("filepath");
        int videolength = req_dict.getInt("videolength");
        XLog.i("send video, wxid_list:%s", wxid_list);
        // note. 读取filebuf为byte[]
        File video_file = new File( filepath );
        if( !video_file.exists() ){
            XLog.e("video file not exists, filepath:%s", filepath);
            return;
        }
        int size = (int) video_file.length();
        byte[] mp4_byte_array = new byte[size];
        BufferedInputStream buf = new BufferedInputStream( new FileInputStream( video_file ) );
        buf.read(mp4_byte_array, 0, mp4_byte_array.length);
        // 调用内部接口
        this.call_sendVideo(wxid_list, videolength, mp4_byte_array);
    }


    /**
     * 发送视频.
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "videolength" : 1           // 视频长度, 单位: 秒      </br>
     *      "filebuf" : "b64 binary buffer",  // 文件二进制字符串, base64编码      </br>
     * }
     */
    void send_video(final JSONObject req_dict) throws Exception{
        String wxid_list = req_dict.getString("wxid_list");
        String filebuf = req_dict.getString("filebuf");
        int videolength = req_dict.getInt("videolength");
        XLog.i("send video, wxid_list:%s", wxid_list);
        // note. 读取filebuf为byte[]
        byte[] mp4_byte_array = Base64.decode(filebuf, Base64.DEFAULT);
        // 调用内部接口
        this.call_sendVideo(wxid_list, videolength, mp4_byte_array);
    }


    /**
     * 根据msgid, 转发视频.
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "msgid" : 1,  // long类型. 语音消息 msgid      </br>
     * }
     */
    void transmit_video(final JSONObject req_dict) throws Exception{
        String wxid_list = req_dict.getString("wxid_list");
        long msgid = req_dict.getLong("msgid");
        XLog.i("transmit video, wxid_list:%s, msgid:%d", wxid_list, msgid);
        //
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select filename, totallen, videolength from videoinfo2 where msglocalid=?", new String[]{String.valueOf(msgid)} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        if(BuildConfig.DEBUG) XLog.d("videoinfo2 rows:%s", contentValues);
        String filename = contentValues.getAsString("filename");
        if( TextUtils.isEmpty(filename) ){
            XLog.e("filename is null");
            return;
        }
        int totallen = contentValues.getAsInteger("totallen");
        if( totallen == 0 ){
            XLog.e("totallen is null");
            return;
        }
        int videolength = contentValues.getAsInteger("videolength");
        if( videolength == 0 ){
            XLog.e("videolength is null");
            return;
        }

        // note. 读取原amr文件为byte[]
        String fullpath = WechatClass.videoMp4Fullpath(filename);
        File video_file = new File( fullpath );
        if( !video_file.exists() ){
            XLog.e("video file not exists, fullpath:%s", fullpath);
            return;
        }
        int size = (int) video_file.length();
        byte[] mp4_byte_array = new byte[size];
        BufferedInputStream buf = new BufferedInputStream( new FileInputStream( video_file ) );
        buf.read(mp4_byte_array, 0, mp4_byte_array.length);
        buf.close();
        // 调用内部接口
        this.call_sendVideo(wxid_list, videolength, mp4_byte_array);
    }


    void call_sendVideo(String wxid_list, int videolength, byte[] mp4_byte_array) throws Exception {
        for (String User: wxid_list.split(";")) {
            long time_ms = System.currentTimeMillis();      //1491924801000    13位
            // note. 根据传入的buf重新生成新文件
            String human = WechatHook.get_robot_info(MySync.g_robot_wxid);     // 自己
            String new_filename = WechatClass.videoFilename();       //"204026130417d25c87561727";
            String mp4FullPath = WechatClass.videoMp4Fullpath(new_filename);      // 例如:  /storage/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/video/204026130417d25c87561727.mp4
            String jpgFullPath = WechatClass.videoJpgFullpath(new_filename);      // 例如:  /storage/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/video/204026130417d25c87561727.jpg

            // 把byte[]写入成文件
            SystemUtil.writeByte2File(mp4_byte_array, mp4FullPath);
            XLog.i("mp4FullPath:%s, jpgFullPath:%s", mp4FullPath, jpgFullPath);
            //Bitmap
            //String mp4Src = "/storage/sdcard/tencent/MicroMsg/WeiXin/1491914696058.mp4";
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(mp4FullPath, MediaStore.Images.Thumbnails.MINI_KIND);

            // 生成jpg
            /** 先定位调用函数的上一层, 搜索:    "report video file error reportId : "       667
             * com/tencent/mm/pluginsdk/model/h     =>      com/tencent/mm/pluginsdk/model/j
             * 进入类中, 搜索倒数第二个: ->JPEG     向下定位
             * invoke-static {v7, v10, v11, v0, v12}, Lcom/tencent/mm/sdk/platformtools/d;->a(Landroid/graphics/Bitmap;ILandroid/graphics/Bitmap$CompressFormat;Ljava/lang/String;Z)V       =>      invoke-static {v7, v10, v11, v0, v12}, Lcom/tencent/mm/sdk/platformtools/c;->a(Landroid/graphics/Bitmap;ILandroid/graphics/Bitmap$CompressFormat;Ljava/lang/String;Z)V
             */
            callStaticMethod(WechatClass.get("com/tencent/mm/sdk/platformtools/c"), "a", bitmap, 0x3c, Bitmap.CompressFormat.JPEG, jpgFullPath, true);      // note. 该类是:　MicroMsg.ImportMultiVideo

            // 获取生成图片的大小
            long thumblen = new File(jpgFullPath).length();     // 视频文件大小, 单位:字节
            //long totallen =  new File(mp4FullPath).length();       // 视频文件大小, 单位:字节
            long totallen = mp4_byte_array.length;
            //Duration
            if (videolength == 0) {
                MediaMetadataRetriever retr = new MediaMetadataRetriever();
                retr.setDataSource(mp4FullPath);
                String height = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT); // 视频高度
                String width = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); // 视频宽度
                String duration = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); // 视频长度
                String rotation = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION); // 视频旋转方向
                long durationMillisec = Long.parseLong(duration);
                XLog.i("mp4 duration:%d, height:%s, width:%s", durationMillisec, height, width);
                videolength = (int) (durationMillisec / 1000);    // 视频时长, 单位: 秒
            }
            // 发送对象, videoinfo2 表 User 字段 - 对应 message 表 talker 字段
            /** note: 下面这几个方法与发送语音的一样!!!
             * 本java文件中搜索:  [oneliang] fix send msg create time, before return, msg id:%s, now is :%s"      直接拷贝下来
             */
            Object obj_foundation_a_h = callStaticMethod(WechatClass.get("com/tencent/mm/kernel/g"), "l", WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/i"));
            Object obj_message_row = callMethod(obj_foundation_a_h, "bcY");

            /** 本java文件中搜索:  : "protect:c2c msg should not here"        直接拷贝下  */
            Object obj_storage_ah$b = callMethod(obj_message_row, "Ze", User);

            /** 本java文件中搜索: "check table name from id:%d table:%s getTableNameByLocalId:%s"        直接拷贝下  */
            callMethod(obj_storage_ah$b, "bdn");  // incMsgLocalId 方法自增bQj, 而 msgid 与此关联
            long msgid = (long) getObjectField(obj_storage_ah$b, "dTS");

            /** 本java文件中搜索: "insert:%d talker:%s id:%d type:%d svrid:%d msgseq:%d flag:%d create:%d issend:%d"      直接拷贝下  */
            int field_talkerId = (int) (long) callMethod(obj_message_row, "Za", User);

            ContentValues message_values = new ContentValues();
            message_values.put("msgId", msgid);
            message_values.put("type", 43);
            message_values.put("status", 1);
            message_values.put("isSend", 1);
            message_values.put("createTime", time_ms);
            message_values.put("talker", User);
            message_values.put("imgPath", new_filename);
            message_values.put("talkerId", field_talkerId);
            message_values.put("bizChatId", -1);
            WechatClass.EnMicroMsg.rawInsert("message", "", message_values);

            ContentValues videoinfo2_values = new ContentValues();
            videoinfo2_values.put("filename", new_filename);
            videoinfo2_values.put("clientid", "");
            videoinfo2_values.put("msgsvrid", 0);
            videoinfo2_values.put("netoffset", 0);
            videoinfo2_values.put("filenowsize", 0);
            videoinfo2_values.put("totallen", totallen);
            videoinfo2_values.put("thumbnetoffset", 0);
            videoinfo2_values.put("thumblen", thumblen);
            videoinfo2_values.put("status", 103);
            videoinfo2_values.put("createtime", time_ms / 1000);
            videoinfo2_values.put("lastmodifytime", time_ms / 1000);
            videoinfo2_values.put("downloadtime", time_ms / 1000);
            videoinfo2_values.put("videolength", videolength);
            videoinfo2_values.put("msglocalid", msgid);
            videoinfo2_values.put("nettimes", 0);
            videoinfo2_values.put("cameratype", 0);
            videoinfo2_values.put("user", User);
            videoinfo2_values.put("human", human);
            videoinfo2_values.put("reserved1", 1);
            videoinfo2_values.put("reserved2", 0);
            videoinfo2_values.put("reserved3", mp4FullPath);
            videoinfo2_values.put("reserved4", "");
            videoinfo2_values.put("videofuncflag", 1);
            videoinfo2_values.put("masssendid", 0);
            videoinfo2_values.put("masssendlist", "");
            videoinfo2_values.put("videomd5", "");
            videoinfo2_values.put("streamvideo", "");
            WechatClass.EnMicroMsg.rawInsert("videoinfo2", "", videoinfo2_values);
            XLog.i("will send video, wxid: %s, msgid:%d, field_talkerId:%d, totallen:%d", User, msgid, field_talkerId, totallen);
            //
            MySync.filename2uploadvideo_task.put(String.format(Locale.ENGLISH, "%s", new_filename), new_filename);
            //XLog.i("filename2uploadvideo put: %s", new_filename);
        }
        // 方法2:
        //Object obj_an_d = newInstance(WechatHook.an_d, new_filename);
        //callMethod(callStaticMethod(WechatHook.model_ah, "tE"), "d", new Class[]{WechatHook.r_j}, obj_an_d);
        /** 搜索: "No Data Any More , Stop Service"
         * com/tencent/mm/an/q$a        =>      com/tencent/mm/modelvideo/x$a
         * .method static synthetic j(Lcom/tencent/mm/an/q$a;)V     =>      .method public final pl()V      // note. 所有结果的函数名都一样
         */

        // 方法1:
        /** 搜索: "sceneUp should null"
         * com/tencent/mm/modelvideo/x$a      =>      com/tencent/mm/modelvideo/x$a
         * .method public final pl()V     =>      .method static synthetic a(Lcom/tencent/mm/modelvideo/x$a;)V
         */
        WechatHook.handler.postDelayed(new VideoRunnable(), 0);     // 延迟执行
        // note 更新上次发送时间; 开始监视未上传完成视频
        WechatAction.send_video_last_time = SystemUtil.get_timestamp();
        WechatAction.send_video_create_time = WechatAction.send_video_last_time;
        if(this._video_task_thread == null || !this._video_task_thread.isAlive()) {
            this._video_task_thread = new VideoTaskLoop();
            this._video_task_thread.start();
        }
    }


    static int send_video_last_time = 0;
    static int send_video_create_time = 0;
    private Thread _video_task_thread = null;
    public class VideoTaskLoop extends Thread {
        @Override
        public void run() {
            try {
                while(true) {
                    int last_time = WechatAction.send_video_last_time;
                    int now = SystemUtil.get_timestamp();
                    if (now - last_time > 2) {
                        // 没有upload了
                        boolean isEmpty = MySync.filename2uploadvideo_task.isEmpty();
                        if (now - WechatAction.send_video_create_time > 60 * 3 || isEmpty) {
                            // 大于 3 分钟, 退出线程
                            MySync.filename2uploadvideo_task.clear();
                            XLog.i("exit video task thread");
                            return;
                        }
                        XLog.w("start video  not done thread, video monitor loop, isEmpty: %b", isEmpty);
                        WechatHook.handler.postDelayed(new VideoRunnable(), 0);     // 延迟执行
                        WechatAction.send_video_last_time = now;
                    }
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                XLog.e("video not done thread sleep error. stack:%s", android.util.Log.getStackTraceString(e));
            }
        }
    }


    /**
     * 发送图片消息接口
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "filename" : "文件名"       // 例如: test.jpg      </br>
     *      "filebuf" : "b64 binary buffer"  // 文件二进制字符串, base64编码      </br>
     *      "hdthumb" : true or false, // (可选参数, 默认为false）是否使用hd图代替缩略图      </br>
     *      "max_concurrent" : 5, // (可选参数, 默认为5）每次发送图片最大并发数     </br>
     * }
     */
    void send_img(final JSONObject req_dict) throws Exception {
        if(this.img_thread == null || !this.img_thread.isAlive()) {
            this.img_thread = new ImgThread();
            this.img_thread.start();
        }

        // TODO 此Apk函数目前只支持一次只发送一张图片, 而Wechat侧Call是可以支持一次发送多张的
        // 触发方式: 拍摄 -> 发送
        String wxid_list = req_dict.getString("wxid_list");
        String filename = req_dict.getString("filename");
        String filebuf = req_dict.getString("filebuf");
        boolean hdthumb = false;
        if (req_dict.has("hdthumb")) hdthumb = req_dict.getBoolean("hdthumb");
        int concurrent = 5;
        if (req_dict.has("max_concurrent")){
            concurrent = req_dict.getInt("max_concurrent");
            if(this.img_thread != null ) this.img_thread.set_max_concurrent(concurrent);
        }
        XLog.i("send img, wxid_list: %s, filename: %s, hdthumb: %b, max_concurrent: %d", wxid_list, filename, hdthumb, concurrent);

        // note: 图片另存为临时文件
        String filepath = MyPath.join(WechatHook.localTmpDir, filename);    // note 默认图片保存位置, mmqrcode1489882267156.png";
        byte[] img_byte_array = Base64.decode(filebuf, Base64.DEFAULT);
        SystemUtil.writeByte2File(img_byte_array, filepath);

        for (String wxid: wxid_list.split(";")) {
            JSONObject params = new JSONObject();
            params.put("wxid", wxid);
            params.put("filepath", filepath);
            params.put("hdthumb", hdthumb);
            //this.invoke_send_img(params);
            this.img_thread.add_task(params.toString());
        }
    }

    void invoke_send_img(final JSONObject req_dict) throws Exception{
        /**
         * 发送图片消息接口
         * @param req_dict
         * {      </br>
         *      "wxid" : "wxid_1"        // 发送目标      </br>
         *      "filepath" : "b64 binary buffer"  // 文件二进制字符串, base64编码      </br>
         *      "hdthumb" : true or false, // 是否使用hd图代替缩略图      </br>
         * }
         */
        String wxid = req_dict.getString("wxid");
        String filepath = req_dict.getString("filepath");
        boolean hdthumb = req_dict.getBoolean("hdthumb");
        XLog.i("invoke_send_img, wxid: %s, filepath: %s, hdthumb: %b", wxid, filepath, hdthumb);

        //
        ArrayList filepath_arraylist = new ArrayList();
        filepath_arraylist.add(filepath);
        // 1. 插入 message 表. (通过遍历 filepath_arraylist) 然后1. ConcurrentHashMap: p0->bSd.put(key = msgId_Long, value=结构体); 2. ArrayList: p0->bSf.put(结构体); //note 插入后, msgId会保存在此结构内
        /** 搜索:    "overSizeList size:%s!"     向下寻找     667
         * invoke-static {}, Lcom/tencent/mm/af/m;->Gn()Lcom/tencent/mm/af/m;
         * sget v6, Lcom/tencent/mm/R$g;->bei:I
         * invoke-virtual/range {v0 .. v6}, Lcom/tencent/mm/af/m;->a(Ljava/util/ArrayList;ZIILjava/lang/String;I)V
         * ========>
         * invoke-static {}, Lcom/tencent/mm/ak/n;->OW()Lcom/tencent/mm/ak/n;
         * sget v6, Lcom/tencent/mm/R$g;->chat_img_template:I
         * invoke-virtual/range {v0 .. v6}, Lcom/tencent/mm/ak/n;->a(Ljava/util/ArrayList;ZIILjava/lang/String;I)V
         */
        Object concurrent_obj = callStaticMethod(WechatClass.get("com/tencent/mm/ak/n"), "OW");
        int magic_num = getStaticIntField(WechatClass.get("com/tencent/mm/R$g"), "chat_img_template");
        callMethod(concurrent_obj, "a", filepath_arraylist, true, 0, 0, wxid, magic_num);

        // 2 插入 ImgInfo2 表, 创建大图与小图
        /**
         * 进入上面的    com/tencent/mm/af/m     =>      com/tencent/mm/ak/n
         * 搜索:      "syncImgData, id size %d"       向上寻找
         * iget-object v2, v0, Lcom/tencent/mm/af/m;->hRX:Ljava/util/concurrent/ConcurrentHashMap;      =>       iget-object v2, v0, Lcom/tencent/mm/ak/n;->dVW:Ljava/util/concurrent/ConcurrentHashMap;
         */
        ConcurrentHashMap concurrentHashMap = (ConcurrentHashMap) getObjectField(concurrent_obj, "dVW");
        // 是否使用hd替换缩略图
        String thumb_path = "", hdthumb_path = "";
        if( hdthumb ) {
            for (Object key : concurrentHashMap.keySet()) {
                Object value = concurrentHashMap.get(key);      // value 为对象:   check-cast v17, Lcom/tencent/mm/ak/n$e;     667
                /** 搜索:     "[cpan] is gif coutinue. did not add to msg table"      向上寻找
                 * iget-object v4, v2, Lcom/tencent/mm/af/m$e;->hSn:Ljava/lang/String;      =>      iget-object v4, v2, Lcom/tencent/mm/ak/n$e;->dWk:Ljava/lang/String;
                 */
                String _fullpath = (String) getObjectField(value, "dWk");
                if (TextUtils.equals(filepath, _fullpath)) {
                    /** 搜索:     "file not exit:%s"      向下寻找
                     * iput-object v0, v2, Lcom/tencent/mm/af/m$e;->hSo:Ljava/lang/String;      =>      iput-object v0, v2, Lcom/tencent/mm/ak/n$e;->dWl:Ljava/lang/String;
                     */
                    String hash = (String) getObjectField(value, "dWl");
                    String image2 = WechatClass.getPersonalDir("image2");
                    thumb_path = MyPath.join(image2, hash.substring(0,2), hash.substring(2,4), "th_" + hash);
                    //hdthumb_path = MyPath.join(image2, hash.substring(0,2), hash.substring(2,4), "th_" + hash + "hd");
                    //XLog.d("hdthumb_path:%s, thumb_path:%s", hdthumb_path, thumb_path);
                    if( new File(filepath).exists() ){
                        XLog.d("overwrite thumb_path");
                        FileUtils.copyFile( new File(filepath), new File(thumb_path) );
                    }
                    break;
                }
            }
        }

        /** 搜索:     "fatal!! Send user mis-match, want:%s, fact:%s"     定位当前函数      667
         * .method public final jl(Ljava/lang/String;)Ljava/util/ArrayList;     =>      .method public final lQ(Ljava/lang/String;)Ljava/util/ArrayList;
         */
        ArrayList v3_arraylist = (ArrayList)callMethod(concurrent_obj, "lQ", wxid);

        /** 搜索:     "is_long_click"     向下寻找        667
         * invoke-static {}, Lcom/tencent/mm/af/n;->Gu()Lcom/tencent/mm/af/h;
         * invoke-virtual/range {v2 .. v9}, Lcom/tencent/mm/af/h;->a(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;IZI)V
         * =============>
         * invoke-static {}, Lcom/tencent/mm/ak/o;->Pd()Lcom/tencent/mm/ak/i;
         * invoke-virtual/range {v2 .. v9}, Lcom/tencent/mm/ak/i;->a(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;IZI)V
         */
        Object af_h = callStaticMethod(WechatClass.get("com/tencent/mm/ak/o"), "Pd");
        callMethod(af_h, "a", v3_arraylist, WechatHook.get_robot_info(MySync.g_robot_wxid), wxid, filepath_arraylist, 0, true, 2130837934);     // note: 内层调用 /cgi-bin/micromsg-bin/uploadmsgimg

//        // 3. Call 发送多张图片
//        // 锁定当前最大msgid+1
//        ArrayList integer_list = new ArrayList();
//        ArrayList bSe = (ArrayList) XposedHelpers.getObjectField(obj_ab_m, "bSe");
//        for(int i=0; i<bSe.size(); i++) {
//            Object obj = bSe.get(0);
//            Object obj_ab_m$e = bSd.get(obj); // cast to com/tencent/mm/ab/m$e
//            long bSu = (long) XposedHelpers.getObjectField(obj_ab_m$e, "bSu");
//            Integer integer = (int) bSu;
//            integer_list.add(integer);
//        }
//        bSe.clear();
//        bSd.clear();
//
//        // 方法:2
//        for(int i=0; i<integer_list.size(); i++) {
//            int field_msgid = (int)integer_list.get(i);
//            XLog.i("send img, msgid:%d", field_msgid);
//            Object obj_ab_h = callStaticMethod(WechatHook.ab_n, "Am");
//            Object v3 = newInstance(WechatHook.ab_k, new Class[]{Integer.class, Integer.class, String.class, String.class, String.class, Integer.class, WechatHook.r_e, Integer.class, String.class, String.class, Integer.class},
//                    field_msgid, 0, WechatHook.robot_info.get(MySync.g_robot_wxid), wxid, filepath, 1, obj_ab_h, 0, "", "", 2130970378);
//            callMethod(callStaticMethod(WechatHook.model_ah, "tE"), "d", new Class[]{WechatHook.r_j}, v3);
//        }
    }


    /**
     * 发送gif
     * @param req_dict
     * {      </br>
     *      "wxid" : "发送目标wxid",      </br>
     *      "filename" : "文件名"       // 例如: test.gif      </br>
     *      "filebuf" : "b64 binary buffer"  // 文件二进制字符串, base64编码      </br>
     * }
     */
    void send_gif(final JSONObject req_dict) throws Exception {
        String wxid = req_dict.getString("wxid");
        String filename = req_dict.getString("filename");
        String filebuf = req_dict.getString("filebuf");
        XLog.i("send_gif. wxid:%s, filename:%s", wxid, filename);

        // note: 图片另存为
        //String filepath = "/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/emoji/b3cb33732d5e525fd3717b1998e8aeba";
        String filepath = MyPath.join(WechatHook.localTmpDir, filename);    // note 默认图片保存位置, mmqrcode1489882267156.gif";
        ArrayList filepath_arraylist = new ArrayList();
        filepath_arraylist.add( filepath );
        byte[] img_byte_array = Base64.decode(filebuf, Base64.DEFAULT);
        SystemUtil.writeByte2File(img_byte_array, filepath);

        // Call
        /** 搜索:     "cannot get location"       向下寻找        667
         * const-class v3, Lcom/tencent/mm/plugin/emoji/b/b;
         * invoke-static {v3}, Lcom/tencent/mm/kernel/h;->h(Ljava/lang/Class;)Lcom/tencent/mm/kernel/plugin/b;
         * invoke-interface {v3}, Lcom/tencent/mm/plugin/emoji/b/b;->getEmojiMgr()Lcom/tencent/mm/pluginsdk/c/d;
         * invoke-interface {v3, v2}, Lcom/tencent/mm/pluginsdk/c/d;->sZ(Ljava/lang/String;)Ljava/lang/String;
         * =======>
         * const-class v4, Lcom/tencent/mm/plugin/emoji/b/c;
         * invoke-static {v4}, Lcom/tencent/mm/kernel/g;->n(Ljava/lang/Class;)Lcom/tencent/mm/kernel/b/a;
         * invoke-interface {v4}, Lcom/tencent/mm/plugin/emoji/b/c;->getEmojiMgr()Lcom/tencent/mm/pluginsdk/a/d;
         * invoke-interface {v4, v2}, Lcom/tencent/mm/pluginsdk/a/d;->zj(Ljava/lang/String;)Ljava/lang/String;
         */
        Object plugin_emoji = callStaticMethod(WechatClass.get("com/tencent/mm/kernel/g"), "n", WechatClass.get("com/tencent/mm/plugin/emoji/b/c"));
        Object EmojiMgrImpl = callMethod(plugin_emoji, "getEmojiMgr");
        callMethod(EmojiMgrImpl, "zj", filepath);   // 创建图片到emoji目录

        // 根据md5文件名, 获取数据库对象
        /** 搜索:     "cannot get location"       向下寻找        667
         *  invoke-static {v2}, Lcom/tencent/mm/a/g;->aU(Ljava/lang/String;)Ljava/lang/String;
         *  invoke-interface {v3, v10}, Lcom/tencent/mm/pluginsdk/c/d;->sY(Ljava/lang/String;)Lcom/tencent/mm/storage/a/c;
         *  =======>
         *  invoke-static {v2}, Lcom/tencent/mm/a/g;->cu(Ljava/lang/String;)Ljava/lang/String;
         *  invoke-interface {v4, v9}, Lcom/tencent/mm/pluginsdk/a/d;->zi(Ljava/lang/String;)Lcom/tencent/mm/storage/emotion/EmojiInfo;
         */
        String emoji_md5 = (String) callStaticMethod(WechatClass.get("com/tencent/mm/a/g"), "cu", filepath);
        Object EmojiInfo_row = callMethod(EmojiMgrImpl, "zi", emoji_md5);

        // 发送
        /** 搜索:     "sendAppMsgEmoji Fail cause there is no thumb"      向上有 "MicroMsg.ChattingSmileyPanelImpl"      667
         * com/tencent/mm/ui/chatting/db        =>      com/tencent/mm/ui/chatting/w
         * 进入文件中, 搜索:   "medianote"     向下寻找
         * invoke-interface {v1, v0, p1, v2}, Lcom/tencent/mm/pluginsdk/c/d;->a(Ljava/lang/String;Lcom/tencent/mm/storage/a/c;Lcom/tencent/mm/storage/aw;)V     =>      invoke-interface {v1, v0, p1, v2}, Lcom/tencent/mm/pluginsdk/a/d;->a(Ljava/lang/String;Lcom/tencent/mm/storage/emotion/EmojiInfo;Lcom/tencent/mm/storage/bd;)V
         */
        callMethod(EmojiMgrImpl, "a", wxid, EmojiInfo_row, null);
    }


    /**
     * 修改自己在群内的昵称
     * @param req_dict
     * {      </br>
     *      "room" : "1@chatroom",      </br>
     *      "name" : "new name", # 要修改成的名字      </br>
     * }
     */
    void set_selfname_inroom(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String name = req_dict.getString("name");
        XLog.i("set_selfname_inroom, room:%s, name:%s", room, name);

        // Call 修改群内自己的昵称
        /** 通过oplog2爆栈找log.         667
         * 全局搜索:  "readFromFile error, path is null or empty"
         * com/tencent/mm/sdk/platformtools/bg      =>      com/tencent/mm/sdk/platformtools/b
         * 进入类中, 查看类函数, 分辨出逻辑最简单的函数
         * .method public static mu(Ljava/lang/String;)Ljava/lang/String;       =>      .method public static oV(Ljava/lang/String;)Ljava/lang/String;
         *
         * 全局搜索:      "contact == null !!!"
         * com/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI     =>      com/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI
         * 进入类中搜索:  mu(Ljava/lang/String;)Ljava/lang/String;    =>      oV(Ljava/lang/String;)Ljava/lang/String;
         *
         * iput-object v2, v1, Lcom/tencent/mm/protocal/c/akq;->tdi:Ljava/lang/String;
         * iput-object v0, v1, Lcom/tencent/mm/protocal/c/akq;->jSr:Ljava/lang/String;
         * invoke-static {p1}, Lcom/tencent/mm/sdk/platformtools/bg;->mu(Ljava/lang/String;)Ljava/lang/String;
         * move-result-object v0
         * iput-object v0, v1, Lcom/tencent/mm/protocal/c/akq;->sZX:Ljava/lang/String;
         * invoke-direct {v0, v9, v1}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/e$a;-><init>(ILcom/tencent/mm/bb/a;)V
         * =======>
         * iput-object v2, v1, Lcom/tencent/mm/protocal/c/aua;->rvj:Ljava/lang/String;
         * iput-object v0, v1, Lcom/tencent/mm/protocal/c/aua;->hbL:Ljava/lang/String;
         * invoke-static {p1}, Lcom/tencent/mm/sdk/platformtools/bi;->oV(Ljava/lang/String;)Ljava/lang/String;
         * move-result-object v0
         * iput-object v0, v1, Lcom/tencent/mm/protocal/c/aua;->rqY:Ljava/lang/String;
         * invoke-direct {v0, v9, v1}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/h$a;-><init>(ILcom/tencent/mm/bk/a;)V
         */
        Object obj_protocal_b_adp = newInstance(WechatClass.get("com/tencent/mm/protocal/c/aua") );
        setObjectField(obj_protocal_b_adp, "rvj", room);
        setObjectField(obj_protocal_b_adp, "hbL", WechatHook.get_robot_info(MySync.g_robot_wxid));        // wxid_w6f7i8zvvtbc12  # 自己的 wxid
        setObjectField(obj_protocal_b_adp, "rqY", name);
        Object oplog2_row_obj = newInstance(WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/a/h$a"), 0x30, obj_protocal_b_adp);
        WechatClass.Oplog2.call_insert(oplog2_row_obj);
        //callMethod(WechatClass.oplog2_handler, "lj");  //循环查询表oplog2
    }


    /**
     * 加我为好友时需要验证(隐私选项)
     * @param req_dict
     * {      </br>
     *      "need_verify" : false,   // true-需要验证; false-不需要验证      </br>
     * }
     */
    void settings_need_verify(final JSONObject req_dict) throws Exception{
        Boolean need_verify = req_dict.getBoolean("need_verify");
        XLog.i("settings_need_verify, need_verify:%s", need_verify);
        int verify_flag = 2;    // 1-开启验证; 2-关闭验证
        if ( need_verify ){
            verify_flag = 1;
        }

        /** 搜索:   "sns Notify "         667
         * com/tencent/mm/plugin/setting/ui/setting/SettingsPrivacyUI       =>      com/tencent/mm/plugin/setting/ui/setting/SettingsPrivacyUI
         * 进入类中, 搜索:    "switch  "   向上寻找
         * invoke-direct {v3}, Lcom/tencent/mm/protocal/c/sb;-><init>()V
         * iput v1, v3, Lcom/tencent/mm/protocal/c/sb;->tjU:I
         * iput v0, v3, Lcom/tencent/mm/protocal/c/sb;->tjV:I
         * =========>
         * invoke-direct {v4}, Lcom/tencent/mm/protocal/c/xt;-><init>()V
         * iput v1, v4, Lcom/tencent/mm/protocal/c/xt;->rDz:I
         * iput v3, v4, Lcom/tencent/mm/protocal/c/xt;->rDA:I
         */
        // Call 隐私选项
        Object obj_protocal_b_ok = newInstance(WechatClass.get("com/tencent/mm/protocal/c/xt"));
        setObjectField(obj_protocal_b_ok, "rDz", 4);
        setObjectField(obj_protocal_b_ok, "rDA", verify_flag);    // 1-开启验证; 2-关闭验证

        /** 类中向下搜索:   0x17          667
         *  const/16 v6, 0x17
         * invoke-direct {v5, v6, v3}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/e$a;-><init>(ILcom/tencent/mm/bb/a;)V
         * ======>
         * const/16 v6, 0x17
         * invoke-direct {v5, v6, v4}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/h$a;-><init>(ILcom/tencent/mm/bk/a;)V
         */
        Object oplog2_row_obj = newInstance(WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/a/h$a"), 0x17, obj_protocal_b_ok);
        WechatClass.Oplog2.call_insert(oplog2_row_obj);
        //callMethod(WechatClass.oplog2_handler, "lj");  //循环查询表oplog2
    }


    /**
     * web 扫描登陆确认接口
     * @param req_dict
     * {      </br>
     *      "url : "http://weixin.qq.com/x/A_ZTz8I2mQ1EdQQ9LJlp",   二维码图片的url      </br>
     * }
     */
    void web_login(final JSONObject req_dict) throws Exception{
        String url = req_dict.getString("url");
        int type = 0;
        XLog.i("web_login.url:%s", url);

        // Call
        /** 搜索:  "getA8key-emoticon full url: "     向下寻找        6510  =>  667
         * iget-object v1, v0, Lcom/tencent/mm/d/a/kr;->aHd:Lcom/tencent/mm/d/a/kr$a;
         * iput-object p3, v1, Lcom/tencent/mm/d/a/kr$a;->aHe:Ljava/lang/String;
         * ==============>
         * iget-object v3, v2, Lcom/tencent/mm/g/a/or;->bZC:Lcom/tencent/mm/g/a/or$a;
         * iput-object v0, v3, Lcom/tencent/mm/g/a/or$a;->bZD:Ljava/lang/String;
         */
        Object obj_d_a_kr = newInstance(WechatClass.get("com/tencent/mm/g/a/or"));
        Object kr$a = getObjectField(obj_d_a_kr, "bZC");
        setObjectField(kr$a, "bZD", url);
        /** 搜索:     "pushloginurl is null"      向下寻找        6510  =>  667
         * const/4 v2, 0x1
         * iput v2, v0, Lcom/tencent/mm/d/a/kr$a;->type:I       =>      iput v2, v0, Lcom/tencent/mm/g/a/or$a;->type:I
         */
        setObjectField(kr$a, "type", type);     // 这里设置是 0

        /** 搜索 "/cgi-bin/micromsg-bin/extdeviceloginconfirmget"       6510  =>  667
         com.tencent.mm.plugin.webwx.a.b      =>      com/tencent/mm/plugin/webwx/a/d
         */
        Object obj_webwx_a_b = newInstance(WechatClass.get("com/tencent/mm/plugin/webwx/a/d"), url);
        /** 搜索:  "[oneliang]get session list error."     6510  =>  667
             com.tencent.mm.plugin.webwx.a.e$3$1     =>    com/tencent/mm/plugin/webwx/a/g$3$1
         */
        Object obj_webwx_a_e$3$1 = newInstance(WechatClass.get("com/tencent/mm/plugin/webwx/a/g$3$1"), WechatClass.webwx_handler, obj_webwx_a_b, obj_d_a_kr);

        /** 搜索:     0x3cb       6311    =>  667
         * Lcom/tencent/mm/plugin/webwx/a/e$3       =>      com/tencent/mm/plugin/webwx/a/g$3
         * 进入类中, 向下寻找
         * invoke-static {}, Lcom/tencent/mm/model/ah;->tE()Lcom/tencent/mm/r/m;
         * move-result-object v2
         *const/16 v3, 0x3cb
         * invoke-virtual {v2, v3, v1}, Lcom/tencent/mm/r/m;->a(ILcom/tencent/mm/r/d;)V
         * ===========>
         * invoke-static {}, Lcom/tencent/mm/model/au;->DF()Lcom/tencent/mm/ab/o;
         * move-result-object v2
         * const/16 v3, 0x3cb
         * invoke-virtual {v2, v3, v1}, Lcom/tencent/mm/ab/o;->a(ILcom/tencent/mm/ab/e;)V
         */
        callMethod(callStaticMethod(WechatClass.MMCore, WechatClass.tE), "a", 0x3cb, obj_webwx_a_e$3$1);
        WechatClass.postTask(obj_webwx_a_b);
    }


    /**
     * 接受好友申请(开启验证)接口
     * @param req_dict
     * {      </br>
     *     "wxid":"1",      </br>
     *     "ticket": "",      </br>
     *     "scene": 17      </br>
     * }
     */
    void accept_friend(final JSONObject req_dict) throws Exception{
        String wxid = req_dict.getString("wxid");
        String ticket = req_dict.getString("ticket");
        int scene = req_dict.getInt("scene");
        XLog.i("accept_friend. wxid:%s, ticket:%s, scene:%d", wxid, ticket, scene);

        // Call
        Object obj_pluginsdk_model_l = newInstance(WechatClass.NetSceneVerifyUser_dkverify, wxid, ticket, scene);   // note 该类是  /cgi-bin/micromsg-bin/verifyuser
        WechatClass.postTask(obj_pluginsdk_model_l);
    }


    void _sayhi_with_snspermission(final JSONObject req_dict) throws Exception{
        /** 请求参数:{
         *     "wxid": "1",
         *     "v1_encryptUsername": "v1_4c5c94d8515daaec6963c9a53b11129d9c0260c6aa0114a83bf49c92eb3b2ca3@stranger",
         * }
         */
        String wxid = req_dict.getString("wxid");
        String v1_encryptUsername = req_dict.getString("v1_encryptUsername");
        if(v1_encryptUsername.length() == 0){
            XLog.e("sayhi_with_snspermission v1_encryptUsername len == 0");
            return;
        }
        XLog.i("sayhi_with_snspermission. wxid: %s, v1_encryptUsername: %s", wxid, v1_encryptUsername);

        // Call
        LinkedList v1_encryptUsername_list = new LinkedList();
        v1_encryptUsername_list.add(wxid);
        LinkedList type_list = new LinkedList();
        type_list.add(Integer.valueOf(30));
        HashMap<String, Integer> snspermission_map = new HashMap<>();
        snspermission_map.put(wxid, 0);

        /** 搜索:     "/cgi-bin/micromsg-bin/verifyuser"       667
         * com/tencent/mm/pluginsdk/model/m     =>      com/tencent/mm/pluginsdk/model/m
         * 类中搜索:   (Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)V
         * .method public constructor <init>(Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)V
         * =>   .method public constructor <init>(Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)V
         */
        // 6311版本: Object obj_NetSceneVerifyUser_dkverify = newInstance(WechatClass.NetSceneVerifyUser_dkverify, 0x2, v1_encryptUsername_list, type_list, "我的专属好友申请语", "", snspermission_map, null);
        Object netSceneVerifyUser_dkverify = newInstance(WechatClass.NetSceneVerifyUser_dkverify, v1_encryptUsername_list, type_list, "我的专属好友申请语", "", snspermission_map, null);    // note: /cgi-bin/micromsg-bin/verifyuser
        WechatClass.postTask(netSceneVerifyUser_dkverify);
    }


    void _verifyuser(final JSONObject req_dict) throws Exception{
        // 表: fmessage_conversation 和 fmessage_msginfo
        /**
         * note 通过wxid加人 案例1) 互删好友, Door免验证, Service点击button(触发verifyuser_<0x1>添加), Door再点击添加对方, 至此双方完成好友添加      /4|wxid_jy8batoqm0so12
         * note 通过wxid加人 案例2) 互删好友, Service免验证, Door先添加Service, Service点击button(触发verifyuser_<0x1>添加), 至此双方完成好友添加           /4|wxid_jy8batoqm0so12
         * 请求参数:{
         *     "wxid": "1",
         *     "ticket": "v2_6a77c5feba57b57e7efc76a749f24cf3523fee9b3172f8b5ed83db8119eae33190b56a61eb97dddffe9f60614ad948ba@stranger",    // 可选参数. (主动通过search添加时, 需要)
         * }
         */
        // true: 直接请求通过wxid加好友
        String wxid = req_dict.getString("wxid");
        String ticket = "";
        if( req_dict.has("ticket")) {
            ticket = req_dict.getString("ticket");
        }
        XLog.i("_verifyuser. wxid: %s, ticket: %s", wxid, ticket);

        // Call
        /** 搜索:      "This NetSceneVerifyUser init NEVER use opcode == MM_VERIFYUSER_VERIFYOK"      定位当前函数      667
         * .method public constructor <init>(ILjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;)V
         */
        LinkedList v2_list = new LinkedList();
        v2_list.add(wxid);
        LinkedList v3_list = new LinkedList();
        v3_list.add(Integer.valueOf(30));       // 14 - 通过群内加好友;    30 - 扫描个人二维码加好友
        LinkedList v4_list = new LinkedList();
        if ( ! TextUtils.isEmpty(ticket) ) {
            v4_list.add(ticket);
        }
        String chatroomName = "";
        Object obj_pluginsdk_model_l = newInstance(WechatClass.NetSceneVerifyUser_dkverify, 0x1, v2_list, v3_list, v4_list, "", "", null, chatroomName, "");      //note 该类是 /cgi-bin/micromsg-bin/verifyuser
        WechatClass.postTask(obj_pluginsdk_model_l);
    }


    /**
     * 添加好友到通讯录接口.      </br>
     * 应用场景:      </br>
     *     1) 对方已经把我添加到好友, 而我未添加对方, 此时调用此接口此时调用函数verifyuser_<0x1>, 双方完成好友添加      </br>
     *     2) 知道对方免验证前提下, 直接通过此接口主动添加对方.  例如: Service加Door
     * @param req_dict
     * {      </br>
     *    wxid : "wxid_1",      </br>
     * }
     * @param 返回
     *   {      </br>
     *        "msgtype":    'update_friend_res',      </br>
     *       "data":    [ {"wxid":"1", "alias":"1", "nickname":"1", "type":1, } ]      </br>
     *   }
     */
    void add_friend_to_contact_by_wxid(final JSONObject req_dict) throws Exception{
        // TODO 替换名字为:　verifyuser_after_check_rcontact
        String wxid = req_dict.getString("wxid");
        XLog.i("add_friend_to_contact_by_wxid. wxid:%s", wxid);
        while(true) {
            /** 查contack表, 判断是否已经是好友,
             * 1) 如果是, 发送消息测试对方是否已经添加自己;
             * 2) 如果不是, 则强制发送添加好友申请
             * */
            Cursor cursor = WechatClass.Rcontact.getContactByWxid(wxid);
            try {
                if (cursor == null) {
                    XLog.e("getContact failed ret: null");
                    break;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("getContact empty.");
                    break;
                }
                String alias = cursor.getString(cursor.getColumnIndex("alias"));
                String nickname = cursor.getString(cursor.getColumnIndex("nickname"));
                int type = cursor.getInt(cursor.getColumnIndex("type"));
                XLog.i("getContactByWxid:%s. alias:%s, nickname:%s, type:%d", wxid, alias, nickname, type); // type:3-好友关系; type:4-群友关系; type:0-删除了对方; type:1-自己微信号的记录
                if( type == 0){
                    break;
                }
                // 发送消息测试
                LinkedList<String> linkelist = new LinkedList<>();
                linkelist.add(0, alias);
                linkelist.add(1, nickname);
                linkelist.add(2, String.valueOf(type));
                WechatHook.friendtest.put(wxid, linkelist);  // 用于记录那些wxid需要检测好友关系是否能正常发送消息
                JSONObject obj = new JSONObject();
                obj.put("wxid", wxid);
                obj.put("content", "add_friend_to_contact_by_wxid");
                this.send_text(obj);
                XLog.i("send msg to friendtest relation, wxid:%s", wxid);
                return;
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        this._verifyuser(req_dict);
    }


    /**
     * 添加好友到通讯录接口.      </br>
     * 主要应用场景是:      </br>
     *    使用二维码中的qrid, 向微信发送searchcontact查询对方联系方式, 微信响应时取出v1_encryptUsername和ticket,      </br>
     * 1) 若对方免验证, 返回tips为"Everything is OK", 此时调用函数verifyuser_<0x1>添加对方      </br>
     * 2) 若对方开验证, 则verifyuser响应时, 返回tips为"user need verify", 然后使用之前保存的v1_encryptUsername和ticket, 再次发送verifyuser_<0x2>请求验证      </br>
     * 内部实现细节: 先查rontact表判断现时非好友关系, 下一步是发消息测试, 发送失败时才请求通过扫描qrid加好友;
     * @param req_dict
     * {              </br>
     *    wxid : "wxid_xl35ygbrha7s22",       </br>
     *    qrid : "lpvawtPEtI7LrZeh986E@qr",      </br>
     * }
     */
    void add_friend_to_contact_by_qrid(final JSONObject req_dict) throws Exception{
        // TODO 替换名字为: searchcontact_after_check_rcontact
        String wxid = req_dict.getString("wxid");
        String qrid = req_dict.getString("qrid");

        XLog.i("add_friend_to_contact_by_qrid. qrid:%s, wxid:%s", qrid, wxid);
        while(true) {
            /** 查contack表, 判断是否已经是好友,
             * 1) 如果存在记录, 发送文字消息测试对方是否已经添加自己;
             * 2) 如果不存在记录, 则强制发送添加好友申请
             * */
            Cursor cursor = WechatClass.Rcontact.getContactByWxid(wxid);
            try {
                if (cursor == null) {
                    XLog.e("getContact failed ret: null");
                    break;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("getContact empty.");
                    break;
                }
                String alias = cursor.getString(cursor.getColumnIndex("alias"));
                String nickname = cursor.getString(cursor.getColumnIndex("nickname"));
                int type = cursor.getInt(cursor.getColumnIndex("type"));
                XLog.i("getContactByWxid:%s. alias:%s, nickname:%s, type:%d", wxid, alias, nickname, type); // type:3-好友关系; type:4-群友关系; type:0-删除了对方; type:1-自己微信号的记录
                if (type == 0) {
                    break;
                }
                // 发送消息测试
                LinkedList<String> linkelist = new LinkedList<>();
                linkelist.add(0, alias);
                linkelist.add(1, nickname);
                linkelist.add(2, String.valueOf(type));
                linkelist.add(3, qrid);
                WechatHook.friendtest.put(wxid, linkelist);
                JSONObject obj = new JSONObject();
                obj.put("wxid", wxid);
                obj.put("content", "add_friend_to_contact_by_qrid");
                this.send_text(obj);
                XLog.i("send msg to friendtest relation, wxid:%s", wxid);
                return;
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        this._searchcontact(req_dict);
    }


    void _searchcontact(final JSONObject req_dict) throws Exception {
        /**
         * 通过扫描qrid加好友接口
         * @param req_dict
         * {      </br>
         *     wxid : "wxid_xl35ygbrha7s22",      </br>
         *     qrid : "lpvawtPEtI7LrZeh986E@qr",      </br>
         * }
         */
        String wxid = req_dict.getString("wxid");
        String qrid = req_dict.getString("qrid");
        XLog.i("_searchcontact. qrid:%s, wxid:%s", qrid, wxid);

        if( !qrid.endsWith("@qr") ){
            XLog.e("qrid:%s not endwith '@qr', wxid:%s", qrid, wxid);
            return;
        }
        if( WechatHook.wxid2addfriend.containsKey((wxid))){
            XLog.e("already exisit in _searchcontact, qrid:%s, wxid:%s", qrid, wxid);
        }else{
            WechatHook.wxid2addfriend.put(wxid, true);
        }

        // Call /cgi-bin/micromsg-bin/searchcontact
        XLog.i("Call searchcontact, wxid:%s, qrid:%s", wxid, qrid);

        // 参考  搜索微信号.txt
        /** 搜索:     "start search contact %s"       向下寻找        667
         * invoke-direct {v2, p3, v0, v3, p4}, Lcom/tencent/mm/modelsimple/aa;-><init>(Ljava/lang/String;IIZ)V      =>      invoke-direct {v2, p3, v0, v3, p4}, Lcom/tencent/mm/plugin/messenger/a/f;-><init>(Ljava/lang/String;IIZ)V
         */
        Object obj_modelsimple_z = newInstance(WechatClass.get("com/tencent/mm/plugin/messenger/a/f", "searchcontact"), qrid, 0x2, 0x5, true);    // note 该类是 searchcontact
        WechatClass.postTask(obj_modelsimple_z);
    }


    /**
     * 发送文字测试, 触发py侧联系人变量更新      </br>
     *  note 流程: 当机器人被添加后, 会收到系统消息: 你已添加了XXXX，现在可以开始聊天了, 然后py侧判断到该用户是service机器人, 则会触发此接口发送文字消息"get_contact_by_wxid"给对方, 触发自己和对方机器人py侧更新contact_of_friend变量
     * @param req_dict
     * {      </br>
     *    wxid : "wxid",      </br>
     * }
     * @param 返回
     *     {      </br>
     *          "msgtype":  'update_friend_res,      </br>
     *          "data": [ {"wxid":"1", "alias":"1", "nickname":"1", "type":1, } ]      </br>
     *      }
     */
    void get_contact_by_wxid(final JSONObject req_dict) throws Exception {
        String wxid = req_dict.getString("wxid");
        XLog.i("get_contact_by_wxid. wxid:%s", wxid);
        Cursor cursor = WechatClass.Rcontact.getContactByWxid(wxid);
        try{
            if (cursor == null){
                XLog.e("getContact failed ret: null");
                return;
            }
            if(!cursor.moveToFirst()){
                XLog.e("getContact empty.");
                return;
            }
            String alias = cursor.getString(cursor.getColumnIndex("alias"));
            String nickname = cursor.getString(cursor.getColumnIndex("nickname"));
            int type = cursor.getInt(cursor.getColumnIndex("type"));
            XLog.i("getContactByWxid:%s. alias:%s, nickname:%s, type:%d", wxid, alias, nickname, type);
            // 发送消息测试
            LinkedList<String> linkelist = new LinkedList<>();
            linkelist.add(0, alias);
            linkelist.add(1, nickname);
            linkelist.add(2, String.valueOf(type));
            WechatHook.friendtest.put(wxid, linkelist);
            JSONObject obj = new JSONObject();
            obj.put("wxid", wxid);
            obj.put("content", "get_contact_by_wxid");
            this.send_text(obj);
            XLog.i("send msg to friendtest relation, wxid:%s", wxid);
        }finally {
            if(cursor!=null) cursor.close();
        }
    }


    /**
     * 发送网站链接卡片
     * @param req_dict
     * {      </br>
     *      "wxid" : "发送目标wxid",      </br>
     *      "url" : "http://www.baidu.com",      </br>
     *      "title" : "标题",      </br>
     *      "description" : "概要",      </br>
     *      "thumburl" : 'https://salescdn.pa18.com/salesinfo/eLifeAssist/201705/image-_-_-1495870268757.jpg'  // 图标路径      </br>
     * }
     */
    void send_card(final JSONObject req_dict) throws Exception {
        String wxid = req_dict.getString("wxid");
        String url = req_dict.getString("url");
        String title = req_dict.getString("title");
        String description = req_dict.getString("description");
        String thumburl = req_dict.getString("thumburl");
        XLog.i("send_card. wxid:%s, url:%s, title:%s, description:%s, thumburl:%s", wxid, url, title, description, thumburl);

        // Call
        /** note: 触发方法:  在较下方的调用打断点. (收到好友发送的链接卡片后, 长按卡片, 弹出菜单, 点击发送给好友)
         * 搜索:     "summerbig makecontent content md5[%s]"            667
         * com/tencent/mm/r/f$a     =>      com/tencent/mm/y/g$a
         * 进入类中, 搜索:    ".msg.fromusername"     向下寻找
         * iput-object v0, p0, Lcom/tencent/mm/r/f$a;->gbN:Ljava/lang/String;       =>      iput-object v0, p0, Lcom/tencent/mm/y/g$a;->bSS:Ljava/lang/String;
         */
        Object appMessage = newInstance(WechatClass.get("com/tencent/mm/y/g$a", "AppMessage"));     // note 该类是: AppMessage
        setObjectField(appMessage, "url", url);
        setObjectField(appMessage, "appId", "");
        setObjectField(appMessage, "title", title);
        setObjectField(appMessage, "description", description);
        setObjectField(appMessage, "content", "");
        setObjectField(appMessage, "thumburl", thumburl);
        setObjectField(appMessage, "bSS", wxid);

        setObjectField(appMessage, "dyJ", "");
        setObjectField(appMessage, "dyL", "");
        setObjectField(appMessage, "dyM", "");
        setObjectField(appMessage, "dyN", "");
        setObjectField(appMessage, "dyY", "");
        setObjectField(appMessage, "dxG", 0);
        setObjectField(appMessage, "dxH", 0);
        setObjectField(appMessage, "dxQ", 0);
        setObjectField(appMessage, "dwG", 0);
        setObjectField(appMessage, "dwF", 0);
        setObjectField(appMessage, "cbu", 1);
        setObjectField(appMessage, "dwt", 0);
        setObjectField(appMessage, "dwr", 3);
        setObjectField(appMessage, "dxa", 0);
        setObjectField(appMessage, "dwX", 0);
        setObjectField(appMessage, "dwU", 0);
        setObjectField(appMessage, "dwR", 0);
        setObjectField(appMessage, "dwQ", 0);
        setObjectField(appMessage, "sdkVer", 0);
        setObjectField(appMessage, "dwP", 0);
        setObjectField(appMessage, "showType", 0);
        setObjectField(appMessage, "type", 5);
        setObjectField(appMessage, "dwo", 0);

        String[] byte_array = null;
        Random random = new Random();
        long field_msgSvrId = random.nextLong();
        String session_id = String.format(Locale.US, "SessionId@%d#%d", field_msgSvrId, java.lang.System.currentTimeMillis());
        //        WechatClass.pluginsdk_model_app_l = findClass("com.tencent.mm.pluginsdk.model.app.l", loader);    // 发送视频

        /** 参考  转发链接.txt
         * 搜索:      "summerbig send attachPath is null islargefilemsg[%d], attachlen[%d]"           667
         * 进入类中, 搜索:    "chatroom"      向上寻找
         * com/tencent/mm/ui/transmit/MsgRetransmitUI       =>      com/tencent/mm/ui/transmit/MsgRetransmitUI
         * invoke-static/range {v3 .. v9}, Lcom/tencent/mm/pluginsdk/model/app/l;->a(Lcom/tencent/mm/r/f$a;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BLjava/lang/String;)I
         * =>  invoke-static/range {v9 .. v15}, Lcom/tencent/mm/pluginsdk/model/app/l;->a(Lcom/tencent/mm/y/g$a;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BLjava/lang/String;)I
         */
        callStaticMethod(WechatClass.get("com/tencent/mm/pluginsdk/model/app/l"), "a", appMessage, "", "", wxid, "", byte_array, session_id);
    }


    /** 转发卡片.(含附件卡片; 小程序卡片; 网站链接卡片).
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "content" : "&lt;msg&gt;...&lt;/msg&gt;",     // card_msg 中的content, 原样返回. (来自message表)       </br>
     * }
     */
    void transmit_card(final JSONObject req_dict) throws Exception{
        String wxid_list = req_dict.getString("wxid_list");
        String content = req_dict.getString("content");
        XLog.i("transmit card. wxid_list: %s, content: %s", wxid_list, content);

        for (String wxid: wxid_list.split(";")) {
            //
            HashMap appmsg = WechatClass.parseMessageContent(content, null);
            String action = (String) appmsg.get(".msg.appmsg.action");
            String appId = (String) appmsg.get(".msg.appmsg.$appid");
            String appName = (String) appmsg.get(".msg.appinfo.appname");
            Object sourceusername = appmsg.get(".msg.appmsg.sourceusername");
            Object sourcedisplayname = appmsg.get(".msg.appmsg.sourcedisplayname");
            String url = (String) appmsg.get(".msg.appmsg.url");
            String description = (String) appmsg.get(".msg.appmsg.des");
            String aeskey = (String) appmsg.get(".msg.appmsg.appattach.aeskey");
            String title = (String) appmsg.get(".msg.appmsg.title");
            Object weappiconurl = appmsg.get(".msg.appmsg.weappinfo.weappiconurl");
            Object weappinfo_pagepath = appmsg.get(".msg.appmsg.weappinfo.pagepath");
            Object weappinfo_appid = appmsg.get(".msg.appmsg.weappinfo.appid");
            Object weappinfo_shareId = appmsg.get(".msg.appmsg.weappinfo.shareId");
            int weappinfo_version = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.weappinfo.version", "0"));
            int weappinfo_type = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.weappinfo.type", "0"));
            String filemd5 = (String) appmsg.get(".msg.appmsg.md5");
            int totallen = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.appattach.totallen", "0"));
            String attachid = (String) appmsg.get(".msg.appmsg.appattach.attachid");
            String cdnattachurl = (String) appmsg.get(".msg.appmsg.appattach.cdnattachurl");
            String fileext = (String) appmsg.get(".msg.appmsg.appattach.fileext");
            int type = Integer.parseInt((String) appmsg.get(".msg.appmsg.type"));
            //XLog.d("appmsg: %s", appmsg);

            // Call
            String User = wxid;       // 发送对象, voiceinfo表User字段 - 对应message表talker字段
            long createTime = System.currentTimeMillis();       // 单位: 毫秒
            String Human = WechatHook.get_robot_info(MySync.g_robot_wxid);     // 自己
            /** 搜索:     "[oneliang] fix send msg create time, before return, msg id:%s, now is :%s"         667
             * 进入类中, 搜索:    "bottlemessage"     向上寻找
             * invoke-static {v0}, Lcom/tencent/mm/kernel/h;->k(Ljava/lang/Class;)Lcom/tencent/mm/kernel/b/a;
             * invoke-interface {v0}, Lcom/tencent/mm/plugin/messenger/foundation/a/h;->aHD()Lcom/tencent/mm/plugin/messenger/foundation/a/a/c;
             * ===========>
             * invoke-static {v0}, Lcom/tencent/mm/kernel/g;->l(Ljava/lang/Class;)Lcom/tencent/mm/kernel/c/a;
             * invoke-interface {v0}, Lcom/tencent/mm/plugin/messenger/foundation/a/i;->bcY()Lcom/tencent/mm/plugin/messenger/foundation/a/a/f;
             */
            Object obj_foundation_a_h = callStaticMethod(WechatClass.get("com/tencent/mm/kernel/g"), "l", WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/i"));
            Object obj_message_row = callMethod(obj_foundation_a_h, "bcY");

            // 自增 msgid
            /** 搜索: "protect:c2c msg should not here"
             */
            Object obj_storage_ah$b = callMethod(obj_message_row, "Ze", User);

            /** 搜索: "check table name from id:%d table:%s getTableNameByLocalId:%s"
             */
            callMethod(obj_storage_ah$b, "bdn");  // incMsgLocalId 方法自增bQj, 而 msgid 与此关联
            long msgid = (long) getObjectField(obj_storage_ah$b, "dTS");

            /** 根据字符串 "insert:%d talker:%s id:%d type:%d svrid:%d msgseq:%d flag:%d create:%d issend:%d"
             */
            int field_talkerId = (int) (long) callMethod(obj_message_row, "Za", User);

            //
            ContentValues message_row = new ContentValues();
            message_row.put("msgId", msgid);
            message_row.put("type", 49);
            message_row.put("status", 1);
            message_row.put("bizChatId", 1);
            message_row.put("isSend", 1);
            message_row.put("createTime", createTime);
            message_row.put("talker", User);
            message_row.put("content", content);
            message_row.put("imgPath", "");
            message_row.put("talkerId", field_talkerId);
            message_row.put("bizChatId", -1);
            WechatClass.EnMicroMsg.rawInsert("message", "", message_row);

            ContentValues appmessage_row = new ContentValues();
            appmessage_row.put("msgId", msgid);
            appmessage_row.put("title", title);
            appmessage_row.put("xml", content);
            appmessage_row.put("appId", "");
            appmessage_row.put("type", type);   // 子类型, 大类型是 49 - 卡片消息
            appmessage_row.put("description", description);
            WechatClass.EnMicroMsg.rawInsert("AppMessage", "", appmessage_row);

            /** 搜索: "/cgi-bin/micromsg-bin/sendappmsg"         667
             * com/tencent/mm/pluginsdk/model/app/ai
             */
            Object sendappmsg_task = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/model/app/ai", "/cgi-bin/micromsg-bin/sendappmsg"), msgid, null, null);     // note 该类是: /cgi-bin/micromsg-bin/sendappmsg
            WechatClass.postTask(sendappmsg_task);
        }
    }


    /** 转发卡片.(含附件卡片; 小程序卡片; 网站链接卡片).
     * @param req_dict
     * {      </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "content" : "&lt;msg&gt;...&lt;/msg&gt;",     // card_msg 中的content, 原样返回.       </br>
     * }
     */
    void transmit_card2(final JSONObject req_dict) throws Exception{
        String wxid_list = req_dict.getString("wxid_list");
        String content = req_dict.getString("content");
        XLog.i("transmit card. wxid_list: %s, content: %s", wxid_list, content);

        for (String wxid: wxid_list.split(";")) {
            //
            HashMap appmsg = WechatClass.parseMessageContent(content, null);
            String action = (String) appmsg.get(".msg.appmsg.action");
            String appId = (String) appmsg.get(".msg.appmsg.$appid");
            String appName = (String) appmsg.get(".msg.appinfo.appname");
            Object sourceusername = appmsg.get(".msg.appmsg.sourceusername");
            Object sourcedisplayname = appmsg.get(".msg.appmsg.sourcedisplayname");
            String url = (String) appmsg.get(".msg.appmsg.url");
            String description = (String) appmsg.get(".msg.appmsg.des");
            String aeskey = (String) appmsg.get(".msg.appmsg.appattach.aeskey");
            String title = (String) appmsg.get(".msg.appmsg.title");
            Object weappiconurl = appmsg.get(".msg.appmsg.weappinfo.weappiconurl");
            Object weappinfo_pagepath = appmsg.get(".msg.appmsg.weappinfo.pagepath");
            Object weappinfo_appid = appmsg.get(".msg.appmsg.weappinfo.appid");
            Object weappinfo_shareId = appmsg.get(".msg.appmsg.weappinfo.shareId");
            int weappinfo_version = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.weappinfo.version", "0"));
            int weappinfo_type = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.weappinfo.type", "0"));
            String filemd5 = (String) appmsg.get(".msg.appmsg.md5");
            int totallen = Integer.parseInt((String) MyHashMap.get_or_default(appmsg, ".msg.appmsg.appattach.totallen", "0"));
            String attachid = (String) appmsg.get(".msg.appmsg.appattach.attachid");
            String cdnattachurl = (String) appmsg.get(".msg.appmsg.appattach.cdnattachurl");
            String fileext = (String) appmsg.get(".msg.appmsg.appattach.fileext");
            int type = Integer.parseInt((String) appmsg.get(".msg.appmsg.type"));
            //XLog.d("appmsg: %s", appmsg);

            // Call
            // note 与发送链接一致
            /** 本java文件中搜索:      "summerbig makecontent content md5[%s]"            */
            Object appMessage = newInstance(WechatClass.get("com/tencent/mm/y/g$a", "AppMessage"));     // note 该类是: AppMessage
            setObjectField(appMessage, "action", action);
            setObjectField(appMessage, "appId", appId);      // 微信电脑版  对应 appid 为: wx6618f1cfc6c132f8
            setObjectField(appMessage, "appName", appName);
            setObjectField(appMessage, "bSS", wxid);
            setObjectField(appMessage, "bZG", sourceusername);
            setObjectField(appMessage, "bZH", sourcedisplayname);
            setObjectField(appMessage, "url", url);
            setObjectField(appMessage, "description", description);
            setObjectField(appMessage, "dwo", totallen);
            setObjectField(appMessage, "bGP", attachid);
            setObjectField(appMessage, "dwD", cdnattachurl);
            setObjectField(appMessage, "title", title);
            setObjectField(appMessage, "thumburl", "");
            setObjectField(appMessage, "secondUrl", "");
            setObjectField(appMessage, "dwK", aeskey);
            setObjectField(appMessage, "dwN", "");
            setObjectField(appMessage, "dwO", "");
            setObjectField(appMessage, "filemd5", filemd5);
            setObjectField(appMessage, "extInfo", "");
            setObjectField(appMessage, "dwW", "");
            setObjectField(appMessage, "dzh", content); // TODO: content截断第一个\n后的内容, 如 weixin12345:\n<msg><appmsg appid=""></msg>, 截断后为: <msg>...</msg>
            setObjectField(appMessage, "dwn", "");
            setObjectField(appMessage, "dzg", content);
            setObjectField(appMessage, "dzb", weappiconurl);
            setObjectField(appMessage, "dyR", weappinfo_pagepath);
            setObjectField(appMessage, "dyS", sourceusername);
            setObjectField(appMessage, "dyT", weappinfo_appid);
            setObjectField(appMessage, "dyX", weappinfo_shareId);
            setObjectField(appMessage, "dwp", fileext);
            setObjectField(appMessage, "dwq", "");
            setObjectField(appMessage, "dxx", "");
            setObjectField(appMessage, "dyJ", "");
            setObjectField(appMessage, "dyL", "");
            setObjectField(appMessage, "dyM", "");
            setObjectField(appMessage, "dyN", "");
            setObjectField(appMessage, "dyO", "");
            setObjectField(appMessage, "dyP", "");
            setObjectField(appMessage, "dyQ", "");
            setObjectField(appMessage, "dyZ", 0);
            setObjectField(appMessage, "dyd", 0);
            setObjectField(appMessage, "dyW", 0);
            setObjectField(appMessage, "dyU", weappinfo_type);
            setObjectField(appMessage, "dyK", 0);
            setObjectField(appMessage, "dyh", 0);
            setObjectField(appMessage, "dyi", false);
            setObjectField(appMessage, "dyj", 0);
            setObjectField(appMessage, "dyG", 0);
            setObjectField(appMessage, "dyl", 0);
            setObjectField(appMessage, "dxy", 0);
            setObjectField(appMessage, "dxs", 0);
            setObjectField(appMessage, "dxr", 0);
            setObjectField(appMessage, "dxq", 0);
            setObjectField(appMessage, "dxa", 0);
            setObjectField(appMessage, "dxQ", 0);
            setObjectField(appMessage, "dxI", 0);
            setObjectField(appMessage, "dyt", 0);
            setObjectField(appMessage, "dxH", 0);
            setObjectField(appMessage, "dxG", 0);
            setObjectField(appMessage, "dxD", 0);
            setObjectField(appMessage, "dxC", 0);
            setObjectField(appMessage, "dwy", 0);
            setObjectField(appMessage, "dwx", 0);
            setObjectField(appMessage, "dza", weappinfo_version);
            setObjectField(appMessage, "dww", 0);
            setObjectField(appMessage, "dwt", 0);
            setObjectField(appMessage, "dzd", 0);
            setObjectField(appMessage, "dws", 0);
            setObjectField(appMessage, "dwr", 3);
            setObjectField(appMessage, "dwX", 0);
            setObjectField(appMessage, "dwU", 0);
            setObjectField(appMessage, "dwR", 0);
            setObjectField(appMessage, "dwQ", 0);
            setObjectField(appMessage, "dwP", 0);
            setObjectField(appMessage, "dwM", 0);
            setObjectField(appMessage, "dwJ", 0);
            setObjectField(appMessage, "dwI", 0);
            setObjectField(appMessage, "pageType", 0);
            setObjectField(appMessage, "sdkVer", 0);
            setObjectField(appMessage, "dwH", 0);
            setObjectField(appMessage, "showType", 0);
            setObjectField(appMessage, "dwG", 0);
            setObjectField(appMessage, "tid", 0);
            setObjectField(appMessage, "dwF", 0);
            setObjectField(appMessage, "type", type);
            setObjectField(appMessage, "cbu", 1);       // 7 - 附件卡片; 1- 网站链接卡片, 以及小程序卡片;
            setObjectField(appMessage, "bYg", 0);

            String[] byte_array = null;
            Random random = new Random();
            long field_msgSvrId = random.nextLong();
            String session_id = String.format(Locale.US, "SessionId@%d#%d", field_msgSvrId, java.lang.System.currentTimeMillis());
            //        WechatClass.pluginsdk_model_app_l = findClass("com.tencent.mm.pluginsdk.model.app.l", loader);    // 发送视频

            // note 与发送链接一致
            /** 本java文件中搜索:      "summerbig send attachPath is null islargefilemsg[%d], attachlen[%d]"  */
            callStaticMethod(WechatClass.get("com/tencent/mm/pluginsdk/model/app/l"), "a", appMessage, "", "", wxid, "", byte_array, session_id);
        }
    }


    private Object get_favitem(String filepath, String inner_title, String inner_content, String inner_extenion){
        Object favitem;
        // "do WNNoteBase.ConvertNote2FavProtoItem"
        // note: 触发断点流程:  我 -> 收藏 -> 右上角+号 -> 完成编辑后, 返回
        // 流程参考: 6510/发送附件/插入favItemInfo表.txt
        /** 搜索:      "/note_fav_not_support.png"           667
         * com/tencent/mm/plugin/wenote/model/b        =>      com/tencent/mm/plugin/wenote/model/d
         * 进入类文件, 搜索:  substring(II)Ljava/lang/String;
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/qu;->Ng(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;       =>      invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/vx;->UC(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         */
        Object v10 = newInstance(WechatClass.get("com/tencent/mm/protocal/c/vx"));

        /** 搜索:      ".htm"        向下寻找      667
         * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/qv;-><init>()V
         * invoke-direct {v1}, Lcom/tencent/mm/protocal/c/qw;-><init>()V
         * =====>
         * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/vy;-><init>()V
         * invoke-direct {v1}, Lcom/tencent/mm/protocal/c/vz;-><init>()V
         */
        Object v0 = newInstance(WechatClass.get("com/tencent/mm/protocal/c/vy"));
        Object v1 = newInstance(WechatClass.get("com/tencent/mm/protocal/c/vz"));

        /** 向下寻找    const/4 v2, 0x6
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/qw;->xM(I)Lcom/tencent/mm/protocal/c/qw;
         * invoke-virtual {v0, v1}, Lcom/tencent/mm/protocal/c/qv;->c(Lcom/tencent/mm/protocal/c/qw;)Lcom/tencent/mm/protocal/c/qv;
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/qu;->a(Lcom/tencent/mm/protocal/c/qv;)Lcom/tencent/mm/protocal/c/qu;
         * ==========>
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/vz;->CJ(I)Lcom/tencent/mm/protocal/c/vz;
         * invoke-virtual {v0, v1}, Lcom/tencent/mm/protocal/c/vy;->c(Lcom/tencent/mm/protocal/c/vz;)Lcom/tencent/mm/protocal/c/vy;
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/vx;->a(Lcom/tencent/mm/protocal/c/vy;)Lcom/tencent/mm/protocal/c/vx;
         */
        callMethod(v1, "CJ", 0x6);
        callMethod(v0, "c", v1);
        callMethod(v10, "a", v0);// note: #1
        // FIXME
        /** 搜索:     "WeNoteHtmlFile"        向下寻找
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/qu;->xJ(I)Lcom/tencent/mm/protocal/c/qu;
         * const-string/jumbo v2, "WeNoteHtmlFile"
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/qu;->NC(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;
         * invoke-virtual {v1, v0}, Lcom/tencent/mm/protocal/c/qu;->Nt(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/qu;->jr(Z)Lcom/tencent/mm/protocal/c/qu;
         * ========>
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/vx;->CF(I)Lcom/tencent/mm/protocal/c/vx;
         * const-string/jumbo v2, "WeNoteHtmlFile"*
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/vx;->UY(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/vx;->kY(Z)Lcom/tencent/mm/protocal/c/vx;
         * invoke-virtual {v1, v2}, Lcom/tencent/mm/protocal/c/vx;->kY(Z)Lcom/tencent/mm/protocal/c/vx;
         */
        callMethod(v10, "CF", 0x8);// note: #2
        //callMethod(v10, "NC", "WeNote_1");// note: #9
        callMethod(v10, "UY", filepath); // note: #4 文件路径, 例如: /storage/emulated/0/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/favorite/220/server.log
        callMethod(v10, "kY", true);// note: #5

        /** 搜索:     ->JPEG      向上寻找
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/qu;->Ns(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;       =>      invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/vx;->UO(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         */
        callMethod(v10, "UO", "");  // note: #3 favCdnInfo 表中的 dataId, 兼且是path字段里的文件名; favItemInfo 表 xml字段中的 dataid 标签, 例如: "23f778e5287ad8ae87e1e2d14e6d7748"

        /** 搜索:     , 0x8       向下寻找
         * iget-object v1, v0, Lcom/tencent/mm/plugin/wenote/model/a/p;->title:Ljava/lang/String;
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/qu;->Nf(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;
         * iget-object v1, v0, Lcom/tencent/mm/plugin/wenote/model/a/p;->content:Ljava/lang/String;
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/qu;->Ng(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/qu;->Np(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;
         * ======>
         * iget-object v1, v0, Lcom/tencent/mm/plugin/wenote/model/a/t;->title:Ljava/lang/String;
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/vx;->UB(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         * iget-object v1, v0, Lcom/tencent/mm/plugin/wenote/model/a/t;->content:Ljava/lang/String;
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/vx;->UC(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         * invoke-virtual {v10, v0}, Lcom/tencent/mm/protocal/c/vx;->UL(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/vx;
         */
        callMethod(v10, "UB", inner_title);// note: #6
        callMethod(v10, "UC", inner_content);// note: #7
        callMethod(v10, "UL", inner_extenion);// note: #8

        LinkedList v7 = new LinkedList();
        v7.add(v10);//note: #add

        /** 搜索:     "do WNNoteBase.ConvertNote2FavProtoItem"
         * com/tencent/mm/plugin/wenote/model/a/h
         */
        Object wenote_1 = newInstance(WechatClass.get("com/tencent/mm/plugin/wenote/model/a/h"));
        setObjectField(wenote_1, "content", "<br/>");
        setObjectField(wenote_1, "jdM", "38bd34554dd3d0888aad66aaaab2c407");    // note 参考:  添加文件/2.png
        setObjectField(wenote_1, "qpc", "-1");
        setObjectField(wenote_1, "type", 1);
        setObjectField(wenote_1, "qpb", null);

        /**
         * com/tencent/mm/plugin/wenote/model/a/c
         */
        Object wenote_2 = newInstance(WechatClass.get("com/tencent/mm/plugin/wenote/model/a/c"));
        setObjectField(wenote_2, "title", "server.log");
        setObjectField(wenote_2, "content", "196.0B");
        setObjectField(wenote_2, "fTs", "/data/data/com.tencent.mm/files/public/wenote/res/fav_fileicon_unknow.png");
        setObjectField(wenote_2, "qpc", "WeNote_1");
        setObjectField(wenote_2, "type", 0x5);
        setObjectField(wenote_2, "qpd", true);
        setObjectField(wenote_2, "qoT", "log");
        setObjectField(wenote_2, "jdM", "76c6bedba41055a744c6470907c0632d");
        setObjectField(wenote_2, "bVd", filepath);    // 文件路径, 例如: /storage/emulated/0/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/favorite/220/server.log

        LinkedList p1 = new LinkedList();
        p1.add(wenote_1);
        p1.add(wenote_2);

        /** 搜索:      "/note_fav_not_support.png"           667
         * com/tencent/mm/plugin/wenote/model/b        =>      com/tencent/mm/plugin/wenote/model/d
         * 进入类文件, 搜索:  substring(II)Ljava/lang/String;
         * invoke-static {v7}, Lcom/tencent/mm/plugin/wenote/model/b;->al(Ljava/util/LinkedList;)V
         * invoke-virtual {v8, v7}, Lcom/tencent/mm/protocal/c/rd;->ap(Ljava/util/LinkedList;)Lcom/tencent/mm/protocal/c/rd;
         * ========>
         * invoke-static {v7, p1}, Lcom/tencent/mm/plugin/wenote/model/d;->a(Ljava/util/LinkedList;Ljava/util/List;)V
         * invoke-virtual {v8, v7}, Lcom/tencent/mm/protocal/c/wl;->ar(Ljava/util/LinkedList;)Lcom/tencent/mm/protocal/c/wl;
         */
        Object v8 = newInstance(WechatClass.get("com/tencent/mm/protocal/c/wl"));
        callStaticMethod(WechatClass.get("com/tencent/mm/plugin/wenote/model/d"), "a", v7, p1);// note: #call1
        favitem = callMethod(v8, "ar", v7);// note: #call2
        return favitem;
    }


    /**
     * 转发Emoji表情
     * @param req_dict
     * {      </br>
     *      "wxid" : "123456@chatroom",      </br>
     *      "msgid" : 6,    // long 类型   </br>
     * }
     */
    void transmit_emoji(final JSONObject req_dict) throws Exception{
        String wxid = req_dict.getString("wxid");
        long msgid = req_dict.getLong("msgid");
        XLog.d("transmit emoji, wxid: %s, msgid: %d", wxid, msgid);

        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select talker, imgPath from message where msgid=?", new String[]{String.valueOf(msgid)} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        String filename = contentValues.getAsString("imgPath");
        /** 搜索: "sendEmoji: userName or imgPath is null"        向下寻找
         * invoke-static {}, Lcom/tencent/mm/plugin/emoji/model/i;->aEA()Lcom/tencent/mm/plugin/emoji/e/l;
         * move-result-object v0
         * iget-object v0, v0, Lcom/tencent/mm/plugin/emoji/e/l;->igx:Lcom/tencent/mm/storage/emotion/d;
         * invoke-virtual {v0, p3}, Lcom/tencent/mm/storage/emotion/d;->Zy(Ljava/lang/String;)Lcom/tencent/mm/storage/emotion/EmojiInfo;
         */
        Object storage_emotion = getObjectField(callStaticMethod(WechatClass.get("com/tencent/mm/plugin/emoji/model/i"), "aEA"), "igx");
        Object EmojiInfo = callMethod(storage_emotion, "Zy", filename);
        //Object EmojiInfo = newInstance(WechatClass.get("com/tencent/mm/storage/emotion/EmojiInfo"));
        //callMethod(EmojiInfo, "d", "");
        String time_ms = String.valueOf(System.currentTimeMillis());
        /** 搜索:     "isNeedShowTip reward tip is disable. mRewardTipEnable:%b isOpenForWallet:%b"       向上寻找
         * invoke-direct/range {v0 .. v5}, Lcom/tencent/mm/plugin/emoji/f/r;-><init>(Ljava/lang/String;Ljava/lang/String;Lcom/tencent/mm/storage/emotion/EmojiInfo;J)V
         */
        Object sendemoji = newInstance(WechatClass.get("com/tencent/mm/plugin/emoji/f/r", "/cgi-bin/micromsg-bin/sendemoji"), time_ms, wxid, EmojiInfo, msgid);     // note 该类是 /cgi-bin/micromsg-bin/newsendmsg
        WechatClass.postTask(sendemoji);
    }

    /**
     * 发送附件接口.
     * @param req_dict
     * {        </br>
     *      "wxid_list" : "wxid_1;wxid_2;wxid_3"        // 发送目标, 多个以分号分隔      </br>
     *      "title" : "service_20180824_1624.log",   // 文件名      </br>
     *      "description" : "8.2 KB",   // 描述      </br>
     *      "filebuf" : "b64 binary buffer",       // 文件二进制字符串, base64编码      </br>
     * }
     */
    void send_attachment(final JSONObject req_dict) throws Exception {
        String wxid_list = req_dict.getString("wxid_list");
        String title = req_dict.getString("title");
        String description = req_dict.getString("description");
        String filebuf = req_dict.getString("filebuf");
        XLog.d("send attach, wxid_list:%s, title:%s, description:%s", wxid_list, title, description);

        // 保存文件:  /storage/emulated/0/Download/xlog/service_20180824_1624.log
        String fileFullPath = MyPath.join(WechatHook.localTmpDir, title);
        XLog.d("fileFullPath: %s", fileFullPath);
        // note. 读取filebuf为byte[]
        byte[] attach_byte_array = Base64.decode(filebuf, Base64.DEFAULT);
        // 把byte[]写入成文件
        SystemUtil.writeByte2File(attach_byte_array, fileFullPath);
        long totalLen = attach_byte_array.length;

        String[] wxids = wxid_list.split(";", 2);
        String cc_wxid_list = null;
        if( wxids.length == 2 ) {
            cc_wxid_list = wxids[1];
        }

        String wxid = wxids[0];
        {
            // 插入 message 表
            /** note: 下面这几个方法与发送语音的一样!!!
             * 本java文件中搜索:  [oneliang] fix send msg create time, before return, msg id:%s, now is :%s"      直接拷贝下来
             */
            Object obj_foundation_a_h = callStaticMethod(WechatClass.get("com/tencent/mm/kernel/g"), "l", WechatClass.get("com/tencent/mm/plugin/messenger/foundation/a/i"));
            Object obj_message_row = callMethod(obj_foundation_a_h, "bcY");

            /** 本java文件中搜索:  : "protect:c2c msg should not here"        直接拷贝下  */
            Object obj_storage_ah$b = callMethod(obj_message_row, "Ze", wxid);

            /** 本java文件中搜索: "check table name from id:%d table:%s getTableNameByLocalId:%s"        直接拷贝下  */
            callMethod(obj_storage_ah$b, "bdn");  // incMsgLocalId 方法自增bQj, 而 msgid 与此关联
            long msgId = (long) getObjectField(obj_storage_ah$b, "dTS");

            /** 本java文件中搜索: "insert:%d talker:%s id:%d type:%d svrid:%d msgseq:%d flag:%d create:%d issend:%d"      直接拷贝下  */
            int field_talkerId = (int) (long) callMethod(obj_message_row, "Za", wxid);

            // 插入 appattach 表
            ContentValues appattach_row = new ContentValues();
            long createTime = System.currentTimeMillis();
            appattach_row.put("createTime", createTime);
            appattach_row.put("appId", "");
            appattach_row.put("status", 101);   // 初始为 200, upload 附件任务前改为 101
            appattach_row.put("fakeAeskey", "");
            appattach_row.put("fullXml", "");
            appattach_row.put("msgInfoId", msgId);
            appattach_row.put("totalLen", totalLen);
            appattach_row.put("mediaSvrId", String.valueOf(createTime));
            appattach_row.put("type", 6);       // subtype 6: 附件卡片
            appattach_row.put("fileFullPath", fileFullPath);       // 例如:   /storage/emulated/0/Download/xlog/service_20180824_1624.log
            appattach_row.put("netTimes", 0);
            appattach_row.put("clientAppDataId", String.valueOf(createTime));
            appattach_row.put("isUpload", true);
            appattach_row.put("offset", 0);
            appattach_row.put("isUseCdn", 0);
            appattach_row.put("sdkVer", 0);
            appattach_row.put("lastModifyTime", createTime/1000);
            long attachid = WechatClass.EnMicroMsg.rawInsert("appattach", "", appattach_row);
            XLog.w("appattach id: %d", attachid);

            String extention = FilenameUtils.getExtension(title);
            String msg_xml = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>"
                    + title + "</title><des>8.2&#x20;KB</des><username></username><action>view</action><type>6</type><showtype>0</showtype><content></content><url></url><lowurl></lowurl><dataurl></dataurl><lowdataurl></lowdataurl><contentattr>0</contentattr><streamvideo><streamvideourl></streamvideourl><streamvideototaltime>0</streamvideototaltime><streamvideotitle></streamvideotitle><streamvideowording></streamvideowording><streamvideoweburl></streamvideoweburl><streamvideothumburl></streamvideothumburl><streamvideoaduxinfo></streamvideoaduxinfo><streamvideopublishid></streamvideopublishid></streamvideo><canvasPageItem><canvasPageXml><![CDATA[]]></canvasPageXml></canvasPageItem><appattach><totallen>"
                    + totalLen + "</totallen><attachid>"
                    + attachid + "</attachid><cdnattachurl></cdnattachurl><emoticonmd5></emoticonmd5><aeskey></aeskey><thumbheight>-1</thumbheight><thumbwidth>-1</thumbwidth><fileext>"
                    + extention + "</fileext><islargefilemsg>0</islargefilemsg></appattach><extinfo></extinfo><androidsource>4</androidsource><thumburl></thumburl><mediatagname></mediatagname><messageaction><![CDATA[]]></messageaction><messageext><![CDATA[]]></messageext><emoticongift><packageflag>0</packageflag><packageid></packageid></emoticongift><emoticonshared><packageflag>0</packageflag><packageid></packageid></emoticonshared><designershared><designeruin>0</designeruin><designername>null</designername><designerrediretcturl>null</designerrediretcturl></designershared><emotionpageshared><tid>0</tid><title>null</title><desc>null</desc><iconUrl>null</iconUrl><secondUrl>null</secondUrl><pageType>0</pageType></emotionpageshared><webviewshared><shareUrlOriginal></shareUrlOriginal><shareUrlOpen></shareUrlOpen><jsAppId></jsAppId><publisherId></publisherId></webviewshared><template_id></template_id><md5></md5><weappinfo><username></username><appid></appid><appservicetype>0</appservicetype></weappinfo><statextstr></statextstr><websearch><rec_category>0</rec_category></websearch></appmsg></msg>";
            ContentValues message_row = new ContentValues();
            message_row.put("content", msg_xml);
            message_row.put("createTime", createTime);
            message_row.put("status", 1);
            message_row.put("bizChatId", -1);
            message_row.put("msgId", msgId);
            message_row.put("isSend", 1);
            message_row.put("type", 49);
            message_row.put("talker", wxid);      // 发送对象
            message_row.put("talkerId", field_talkerId);
            WechatClass.EnMicroMsg.rawInsert("message", "", message_row);

            // 插入 AppMessage 表
            ContentValues appmessage_row = new ContentValues();
            appmessage_row.put("msgId", msgId);
            appmessage_row.put("title", title);
            appmessage_row.put("xml", msg_xml);
            appmessage_row.put("type", 6);
            appmessage_row.put("description", description);
            WechatClass.EnMicroMsg.rawInsert("AppMessage", "", appmessage_row);

            // 抄送
            if( cc_wxid_list != null ) {
                MySync.msgid2card_cc.put(msgId, cc_wxid_list);
            }

            // Call
            Object uploadappattach = newInstance(WechatClass.get("com/tencent/mm/pluginsdk/model/app/al", "/cgi-bin/micromsg-bin/uploadappattach"), attachid, null, null);
            WechatClass.postTask(uploadappattach);
        }
    }


    private Object get_favitem2(String fav_filepath, String inner_title, String inner_content, String inner_extenion){
        Object favitem;
        // note: 触发断点流程:  我 -> 收藏 -> 右上角+号 -> 完成编辑后, 返回
        // 流程参考: 6510/发送附件/插入favItemInfo表.txt
        /** 搜索:     "do WNNoteBase.ConvertNote2FavProtoItem"
         * com/tencent/mm/plugin/wenote/model/a/h
         */
        Object wenote_1 = newInstance(WechatClass.get("com/tencent/mm/plugin/wenote/model/a/h"));
        setObjectField(wenote_1, "content", "<br/>");
        setObjectField(wenote_1, "jdM", "38bd34554dd3d0888aad66aaaab2c407");    // note 参考:  添加文件/2.png
        setObjectField(wenote_1, "qpc", "-1");
        setObjectField(wenote_1, "type", 1);
        setObjectField(wenote_1, "qpb", null);

        /** 搜索:     "/note_fav_not_support.png"     667
         * com/tencent/mm/plugin/wenote/model/b     =>      com/tencent/mm/plugin/wenote/model/d
         * 进入类中, 向下搜索:       ".htm"
         * 再向下搜索:   0x1         往上寻找
         * invoke-virtual {v10, v1}, Lcom/tencent/mm/protocal/c/qu;->Nt(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/qu;       =>      invoke-virtual {v1, v0}, Lcom/tencent/mm/protocal/c/we;->Vo(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/we;
         */
        //
        /**
         * com/tencent/mm/plugin/wenote/model/a/c
         */
        Object wenote_2 = newInstance(WechatClass.get("com/tencent/mm/plugin/wenote/model/a/c"));
        setObjectField(wenote_2, "title", inner_title);
        setObjectField(wenote_2, "content", inner_content);
        setObjectField(wenote_2, "fTs", "/data/data/com.tencent.mm/files/public/wenote/res/fav_fileicon_unknow.png");
        setObjectField(wenote_2, "qpc", "WeNote_1");
        setObjectField(wenote_2, "type", 0x5);
        setObjectField(wenote_2, "qpd", true);
        setObjectField(wenote_2, "qoT", inner_extenion);
        setObjectField(wenote_2, "jdM", "76c6bedba41055a744c6470907c0632d");
        setObjectField(wenote_2, "bVd", fav_filepath);    // 文件路径, 例如: /storage/emulated/0/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/favorite/220/server.log

        ArrayList fav_2_Item = new ArrayList();
        fav_2_Item.add(wenote_1);
        fav_2_Item.add(wenote_2);

        /** 搜索:     "fav_note_item_updatetime"      向下寻找
         * invoke-static {}, Lcom/tencent/mm/plugin/wenote/model/nativenote/manager/c;->bZD()Lcom/tencent/mm/plugin/wenote/model/nativenote/manager/c;
         * invoke-virtual {v0, p1}, Lcom/tencent/mm/plugin/wenote/model/nativenote/manager/c;->Sm(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/wl;
         */
        Object nativenote_manager_c = callStaticMethod(WechatClass.get("com/tencent/mm/plugin/wenote/model/nativenote/manager/c"), "bZD");
        setObjectField(nativenote_manager_c, "gBc", fav_2_Item);
        setObjectField(nativenote_manager_c, "qpS", newInstance(WechatClass.get("com/tencent/mm/protocal/c/vx")));
        setObjectField(nativenote_manager_c, "qpT", 0x0);
        setObjectField(nativenote_manager_c, "qpU", 0x0);
        setObjectField(nativenote_manager_c, "qpV", 0x0);

        String p1 = "<br/><div><object data-type=\"5\" id=\"WeNote_1\" class=\"item item-\"></object></div>";
        favitem = callMethod(nativenote_manager_c, "Sm", p1);
        return favitem;
    }


    /**
     * 转发收藏.
     * @param req_dict
     * {      </br>
     *      "wxid" : "123456@chatroom"      </br>
     *      "local_favorite_id" : "1511402822119",      </br>
     * }
     */
    void transmit_favorite(final JSONObject req_dict) throws Exception{
        String wxid = req_dict.getString("wxid");
        XLog.d("send attach, wxid:%s", wxid);
        // 方式1: 从favProto字段中转换成 favitem
        Object favitem;
        if( req_dict.has("local_favorite_id")) {  // note: 该分支仅用于测试
            // 方式1: 从favProto字段中转换成 favitem
            String local_favorite_id = req_dict.getString("local_favorite_id");
            ContentValues contentValues = WechatClass.enFavorite.select("select favProto from FavItemInfo where localId=?", new String[]{local_favorite_id});
            if (contentValues == null) {
                XLog.e("select error, contentValues is null");
                return;
            }

            byte[] favProto = contentValues.getAsByteArray("favProto");
            if (favProto == null) {
                XLog.e("send attach error, favProto is null");
                return;
            }
            // 参考:  从收藏中发送.txt
            /** 搜索:   "FavIndexItem protoData is null or data length is 0"      向下寻找
             * invoke-virtual {v1, v0}, Lcom/tencent/mm/protocal/c/rd;->aA([B)Lcom/tencent/mm/bb/a;     =>      invoke-virtual {v1, v0}, Lcom/tencent/mm/protocal/c/wl;->aG([B)Lcom/tencent/mm/bk/a;
             */
            favitem = newInstance(WechatClass.get("com/tencent/mm/protocal/c/wl"));
            favitem = callMethod(favitem, "aG", favProto);
        }else {
            String inner_title = "标题";
            String inner_content = "内容";
            String inner_extenion = "Log";
            String attach_filepath = "/storage/emulated/0/server.log";      // note 拷贝路径:   /sdcard/server.log
            favitem = this.get_favitem2(attach_filepath, inner_title, inner_content, inner_extenion);
        }

        String outside_title = "笔记";String outside_desc = "[文件]server.log\n";
        /** 搜索:     "send record msg, to %s, thumbPath %s, thumbId %s"      667
         * com/tencent/mm/plugin/record/a/q     =>      com/tencent/mm/plugin/record/b/l
         * 进入类中, 向下寻找第一个    goto/16 :goto_0
         * invoke-static/range {v0 .. v6}, Lcom/tencent/mm/plugin/record/a/m;->a(Ljava/lang/String;Lcom/tencent/mm/protocal/c/rd;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)I
         * =>   invoke-static/range {v0 .. v6}, Lcom/tencent/mm/plugin/record/b/h;->a(Ljava/lang/String;Lcom/tencent/mm/protocal/c/wl;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)I
         */
        callStaticMethod(WechatClass.get("com/tencent/mm/plugin/record/b/h"), "a", wxid, favitem, outside_title, outside_desc, null, 0, null);
    }


    /**
     * 发送文字消息接口
     * @param req_dict
     * {        </br>
     *      "wxid" : "123456@chatroom"       </br>
     *      "content" : "内容"     </br>
     *      "atuserlist" : "wxid_1,wxid_2"   //多个以逗号分隔, 这是因为微信客户端原样如此       </br>
     * }
     */
    void send_text(final JSONObject req_dict) throws Exception{
        String wxid = req_dict.getString("wxid");
        String content = req_dict.getString("content");
        String atuserlist = "";
        if( req_dict.has("atuserlist") ) {
            atuserlist = (String) req_dict.get("atuserlist");
        }
        int type = 1;
        if( !TextUtils.isEmpty(atuserlist) ){
            // 新@人接口
            HashMap<String, String> hashmap = new HashMap<>();
            hashmap.put("atuserlist", String.format(Locale.ENGLISH, "<![CDATA[%s]]>", atuserlist) );
            /** 搜索:    "publishMsgSendFailEvent for msgId:%d"           667
             * com/tencent/mm/modelmulti/j      =>      com/tencent/mm/modelmulti/i
             * .method public constructor <init>(Ljava/lang/String;Ljava/lang/String;IILjava/lang/Object;)V
             */
            Object newsendmsg = newInstance(WechatClass.get("com/tencent/mm/modelmulti/i", "newsendmsg"), wxid, content, type, 291, hashmap);
            WechatClass.postTask(newsendmsg);
            XLog.i("send text, target:%s, atuserlist:%s, content:%s", wxid, hashmap.get("atuserlist"), content);
            return;
        }
        // 旧接口
        XLog.i("send text, target:%s, content:%s", wxid, content);
        // Call
        /** 搜索:    "publishMsgSendFailEvent for msgId:%d"           667
         * com/tencent/mm/modelmulti/j      =>      com/tencent/mm/modelmulti/i
         * .method public constructor <init>(Ljava/lang/String;Ljava/lang/String;I)V
         */
        Object newsendmsg = newInstance(WechatClass.get("com/tencent/mm/modelmulti/i", "newsendmsg"), wxid, content, type);     // note 该类是 /cgi-bin/micromsg-bin/newsendmsg
        WechatClass.postTask(newsendmsg);
    }


    /**
     * 修改群公告
     * @param req_dict
     * {        </br>
     *      "room" : "1@chatroom"        </br>
     *      "content" : "群公告内容"      </br>
     * }
     */
    void change_roomnotice(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String content = req_dict.getString("content");
        XLog.i("change_roomnotice. room:%s, content:%s", room, content);

        // Call
        /** 搜索:  "/cgi-bin/micromsg-bin/setchatroomannouncement"            667
         * com/tencent/mm/plugin/chatroom/d/m       =>      com/tencent/mm/plugin/chatroom/d/m
         */
        Object v3 = newInstance(WechatClass.get("com/tencent/mm/plugin/chatroom/d/m", "setchatroomannouncement"), room, content);       // note 该类是: setchatroomannouncement
        WechatClass.postTask(v3);
    }


    /**
     * 群踢人接口
     * @param req_dict
     * {        </br>
     *      "room" : "123456@chatroom",      </br>
     *      "kick_list" : "wxid_1;wxid_2;wxid_3",     // 多个以分号分隔   </br>
     * }
     */
    void kick_member(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String kick_list = req_dict.getString("kick_list");
        XLog.i("kick_member.kick_list:%s", kick_list);
        // Call
        String[] wxid_array = kick_list.split(WechatAction.FenHao);
        ArrayList wxid_list = new ArrayList();
        for (String wxid : wxid_array) {
            wxid_list.add(wxid);
        }

        /** 搜索:        "/cgi-bin/micromsg-bin/delchatroommember"        667
         * com/tencent/mm/plugin/chatroom/d/g       =>      com/tencent/mm/plugin/chatroom/d/g
         */
        Object v3 = newInstance(WechatClass.get("com/tencent/mm/plugin/chatroom/d/g"), room, wxid_list);
        WechatClass.postTask(v3);
    }


    /**
     * 群加人接口
     * @param req_dict
     * {        </br>
     *      "room" : "123456@chatroom",      </br>
     *      "add_list" : "wxid_1;wxid_2;wxid_3",    // 多个以分号分隔   </br>
     * }
     */
    void add_member(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String add_list = req_dict.getString("add_list");
        XLog.i("add_member.add_list:%s", add_list);

        // Call
        String[] wxid_array = add_list.split(WechatAction.FenHao);
        ArrayList wxid_arraylist = new ArrayList();
        for (String wxid: wxid_array) {
            wxid_arraylist.add( wxid );
            WechatHook.room_wxid2true.put(room+wxid, true);
        }
        XLog.d("wxid_arraylist:%s, size:%d", wxid_arraylist, wxid_arraylist.size());

        /** 搜索:      "/cgi-bin/micromsg-bin/addchatroommember"      667
         * com/tencent/mm/plugin/chatroom/d/d       =>      com/tencent/mm/plugin/chatroom/d/d
         */
        Object obj_addchatroommember = newInstance(WechatClass.get("com/tencent/mm/plugin/chatroom/d/d", "addchatroommember"), room, wxid_arraylist, null);      // note 该类是 addchatroommember
        WechatClass.postTask(obj_addchatroommember);
    }


    /**
     * 从数据库中删除群记录
     * @param req_dict
     * {        </br>
     *      "room" : "123456@chatroom",     </br>
     * }
     */
    void del_chatroom(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        XLog.i("del_chatroom.room:%s", room);
        _del_table_rcontact_and_chatroom(room);
    }


    void _del_table_rcontact_and_chatroom(final String room)  {
        /** 现搜索
         * 先定位调用函数的上层
         * com/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI           =>        com/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI
         * 看到字符串 "deleteWholeChatroom: room:[", 定位上下文
         * invoke-static {v0}, Lcom/tencent/mm/model/f;->dJ(Ljava/lang/String;)Z        =>       invoke-static {p0}, Lcom/tencent/mm/s/j;->ew(Ljava/lang/String;)Z
         */
        // delete 表 chatroom 记录
        // callStaticMethod(WechatClass.model_f, "dJ", room);
        WechatClass.EnMicroMsg.rawDelete("chatroom", "chatroomname=?", new String[]{room});
        // delete 表 rconversation 记录
        //Object rconversation_obj = callMethod(callStaticMethod(WechatClass.MMCore, "tD"), "rt");
        //callMethod(rconversation_obj, "Ey", room);
        WechatClass.EnMicroMsg.rawDelete("rconversation", "username=?", new String[]{room});
        // delete 表 message
    }


    /**
     * 退群接口
     * @param req_dict
     * {        </br>
     *      "room" : 123456@chatroom     </br>
     * }
     */
    void quit_chatroom(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        XLog.i("wx_exit_chatroom.room:%s", room);

        _del_table_rcontact_and_chatroom(room);

        // Call
        /** 参考:  退群/退出群按钮.txt
         * 爆栈insert oplog2
         */
         /** 搜索:     "dz[dealQuitChatRoom owner click room_owner_delete_direct]"      ( 或者搜索:  "] is not exist"  onClick函数, 实际场景)
          * .method static synthetic x(Lcom/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI;)V     =>      invoke-static {v0}, Lcom/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI;->y(Lcom/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI;)V
          * * 进入类中, 搜索:     0x4000000   (在 0x2 和 0x4000000 中间)
          * invoke-direct {v2, v0, v3}, Lcom/tencent/mm/al/p;-><init>(Ljava/lang/String;Ljava/lang/String;)V;      =>      invoke-direct {v2, v0, v3}, Lcom/tencent/mm/aq/n;-><init>(Ljava/lang/String;Ljava/lang/String;)V
         */
        // 退群, 插入oplog
        String wxid = WechatHook.get_robot_info(MySync.g_robot_wxid);
        Object oplog2_row_obj = newInstance(WechatClass.get("com/tencent/mm/aq/n"), wxid, room);
        WechatClass.Oplog2.call_insert(oplog2_row_obj);

        // 返回给python让其删除对应群资料
        JSONArray data_array = new JSONArray();
        JSONObject chatroom_info_dict = new JSONObject();
        chatroom_info_dict.put("room", room);
        chatroom_info_dict.put("roomowner", "");
        //chatroom_info_dict.put("memberlist", "");
        //chatroom_info_dict.put("nicknamelist", "");
        chatroom_info_dict.put("members", new JSONObject());
        data_array.put(0, chatroom_info_dict);
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.chatroom_info_res);
        res_dict.put("data", data_array);
        // 插入redis
        XLog.i("rpush msgtype:chatroom_info_res, data:%s", data_array);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    void _save_singleroom_to_contact(String room, boolean is_save){
        // Call
        /**
         * 搜索:      "kevin service(IMessengerStorage.class).getContactStg().getShowHeadDistinct("           667
         * com/tencent/mm/s/o       =>      com/tencent/mm/model/s
         * 在 Com/tencent/mm/plugin/chatroom/ui/ChatroomInfoUI.smal 中搜索:     com/tencent/mm/s/o;->       =>      com/tencent/mm/model/s;->
         * invoke-static {v1}, Lcom/tencent/mm/s/o;->t(Lcom/tencent/mm/storage/x;)V
         * invoke-static {v1}, Lcom/tencent/mm/s/o;->q(Lcom/tencent/mm/storage/x;)V
         * ========>
         * invoke-static {v1}, Lcom/tencent/mm/model/s;->t(Lcom/tencent/mm/storage/ab;)V
         * invoke-static {v1}, Lcom/tencent/mm/model/s;->q(Lcom/tencent/mm/storage/ab;)V
         */
        Object row_obj = WechatClass.Rcontact.call_select(room);   //note 不能查表代替, 因为下面需要用到row_obj
        String field_username = (String) WechatClass.Rcontact.get_field(row_obj, "field_username");  // 应该是roomid
        if( TextUtils.isEmpty(field_username) ){
            XLog.e("not exitst rcontact row, wxid:%s", room);
            return;
        }
        int field_type = (int)WechatClass.Rcontact.get_field(row_obj, "field_type");
        int isSaved = field_type & 0x01;
        if( isSaved == 1 ){ // true 为已经保存到通讯录
            return;
        }
        if(is_save) {
            callStaticMethod(WechatClass.get("com/tencent/mm/model/s"), "q", row_obj);
        } else{
            callStaticMethod(WechatClass.get("com/tencent/mm/model/s"), "t", row_obj);
        }
    }


    /**
     * 保存群到通讯录,
     * @param req_dict
     * {        </br>
     *     "room_list" : "1@chatroom;2@chatroom;3@chatroom"     </br>
     *     "is_save" : true or false        </br>
     * }
     */
    void save_room_to_contact(final JSONObject req_dict) throws Exception{
        String room_list = req_dict.getString("room_list");
        boolean is_save = req_dict.getBoolean("is_save");
        XLog.i("save_room_to_contact, room_list:%s", room_list);

        String[] room_array = room_list.split(WechatAction.FenHao);
        for (String room: room_array) {
            this._save_singleroom_to_contact(room, is_save);
        }
    }


    /**
     * 群主转让接口
     * @param req_dict
     * {                                        </br>
     *      "room" : "123456@chatroom"           </br>
     *      "wxid" : "wxid_1"        // 转让目标 </br>
     * }
     */
    void change_roomowner(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String wxid = req_dict.getString("wxid");
        XLog.i("change_roomowner.room:%s, wxid:%s", room, wxid);

        // Call
        /** 搜索:    "/cgi-bin/micromsg-bin/transferchatroomowner"            667
         * com/tencent/mm/plugin/chatroom/d/n       =>      com/tencent/mm/plugin/chatroom/d/n
         */
        Object v3 = newInstance(WechatClass.get("com/tencent/mm/plugin/chatroom/d/n"), room, wxid);
        WechatClass.postTask(v3);
    }


    /**
     * 获取所有群信息
     * @param req_dict
     * {                    </br>
     *      "pid" : 1     // int类型, 后端进程号  </br>
     *  }
     * @param 返回
     * {        </br>
     *        "msgtype":'all_chatroom_info_res,     </br>
     *       "pid":1,       </br>
     *      "robot_wxid":"", "robot_alias":"", "robot_nickname":"", "robot_qrcode_url":"",      </br>
     *      "data":[        </br>
     *           {      </br>
     *              "room":"123@chatroom", "roomowner":"wxid_1", "chatroomnick":"群名",       </br>
     *              "members":{                                                               </br>
     *                  "wxid_1":{'id':1, 'name': '昵称', 'alias':'weixin12345',}            </br>
     *              },                                                                          </br>
     *           },                                                                             </br>
     *      ]                                                                                   </br>
     *  }
     */
    void all_chatroom_info_req(final JSONObject req_dict) throws Exception{
        int pid = (int)req_dict.get("pid");
        //
        JSONObject res_dict = new JSONObject();
        JSONArray data_array = WechatClass.getAllChatroomMemberList();    /** 结果都在这个函数获取!!!!! */
        if(data_array == null){
            XLog.e("all_chatroom_info_req error!");
            return;
        }
        res_dict.put("msgtype", WechatAction.all_chatroom_info_res);
        res_dict.put("pid", pid);
        res_dict.put(MySync.g_robot_wxid, WechatHook.get_robot_info(MySync.g_robot_wxid));
        res_dict.put("robot_alias", WechatHook.get_robot_info("robot_alias"));
        res_dict.put("robot_nickname", WechatHook.get_robot_info("robot_nickname"));
        res_dict.put("data", data_array);
        // note: 判断上次发送getqrcode的时候, 超时则重发
        this._recreate_robot_qrcode(true);
        if( WechatHook.has_robot_info("robot_qrcode_url") ) {
            res_dict.put("robot_qrcode_url", WechatHook.get_robot_info("robot_qrcode_url"));
        } else{
            res_dict.put("robot_qrcode_url", "");
        }
        // 插入redis
        XLog.i("rpush msgtype:all_chatroom_info_res, data:%s, pid:%d", data_array, pid);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    void _recreate_robot_qrcode(boolean wait) throws Exception{
        /**
         * 一. 变量已存在, 直接return   <br/>
         * 二. 变量不存在:    <br/>
         *  如果上次生成时间距离现在超过3秒, 则先生成, 等待3秒内, 则
         * @param wait
         */
        if( WechatHook.has_robot_info("robot_qrcode_url") ){
            return;
        }
        // Call
        JSONObject input = new JSONObject();
        input.put("wxid", WechatHook.get_robot_info(MySync.g_robot_wxid));
        input.put("style", 20);
        this.get_qrcode( input );
        // wait
        if( !wait ){
            return;
        }
        Thread.sleep(1000);     //单位: 毫秒.
        if( ! WechatHook.has_robot_info("robot_qrcode_url") ){
            Thread.sleep(5000);     //单位: 毫秒.
        }
    }


    /**
     * 更新Alias, 对应Response是hookInsertContact,
     * @param req_dict
     * {        </br>
     *      "updatedict" : {wxid1:room1}        </br>
     * }
     */
    void update_alias(final JSONObject req_dict) throws Exception{
        JSONObject updatedict = req_dict.getJSONObject("updatedict");
        //XLog.d("update_alias.updatedict:%s", updatedict.toString());
        /** Call */
        LinkedList wxid_list = new LinkedList();
        LinkedList room_list = new LinkedList();

        //
        String wxid, room;
        Iterator iterator = updatedict.keys();
        while(iterator.hasNext()){
            wxid = (String) iterator.next();
            room = updatedict.getString(wxid);
            ConcurrentHashMap<String, Boolean> submap = WechatHook.wxid2rooms.get(wxid);
            if(submap==null){
                submap = new ConcurrentHashMap<>();
                submap.put(room, true);
                WechatHook.wxid2rooms.put(wxid, submap);
            }else{
                submap.put(room, true);
            }

            /** 搜索:     "getFromDb add user:%s room:%s"     向上寻找            667
             * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/atu;-><init>()V
             * invoke-virtual {v0, v6}, Lcom/tencent/mm/protocal/c/atu;->Op(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/atu;
             * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/atu;-><init>()V
             * invoke-virtual {v0, v7}, Lcom/tencent/mm/protocal/c/atu;->Op(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/atu;
             * ========>
             * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/bhz;-><init>()V
             * invoke-virtual {v0, v5}, Lcom/tencent/mm/protocal/c/bhz;->VO(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/bhz;
             * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/bhz;-><init>()V
             * invoke-virtual {v0, v6}, Lcom/tencent/mm/protocal/c/bhz;->VO(Ljava/lang/String;)Lcom/tencent/mm/protocal/c/bhz;
             */
            Object obj_protocal_b_aly = newInstance(WechatClass.protocal_b_aly);
            obj_protocal_b_aly = callMethod(obj_protocal_b_aly, "VO", wxid);
            wxid_list.add(obj_protocal_b_aly);

            obj_protocal_b_aly = newInstance(WechatClass.protocal_b_aly);
            obj_protocal_b_aly = callMethod(obj_protocal_b_aly, "VO", room);
            room_list.add(obj_protocal_b_aly);
        }

        // note: "select getcontactinfov2.username,getcontactinfov2.inserttime,getcontactinfov2.type,getcontactinfov2.lastgettime,getcontactinfov2.reserved1,getcontactinfov2.reserved2,getcontactinfov2.reserved3,getcontactinfov2.reserved4 from getcontactinfov2 where inserttime> ?  order by inserttime asc limit ?"
        /** 搜索:     "getFromDb now reqlist size:%d , this req usr count:%d"     向上寻找            667
         * invoke-direct {v6}, Lcom/tencent/mm/protocal/c/wh;-><init>()V
         * iput-object v2, v6, Lcom/tencent/mm/protocal/c/wh;->sTc:Ljava/util/LinkedList;
         * iput v0, v6, Lcom/tencent/mm/protocal/c/wh;->tmR:I
         * iput-object v1, v6, Lcom/tencent/mm/protocal/c/wh;->tmV:Ljava/util/LinkedList;
         * iput v0, v6, Lcom/tencent/mm/protocal/c/wh;->tmU:I
         * =====>
         * invoke-direct {v5}, Lcom/tencent/mm/protocal/c/ack;-><init>()V
         * iput-object v2, v5, Lcom/tencent/mm/protocal/c/ack;->rgF:Ljava/util/LinkedList;
         * iput v0, v5, Lcom/tencent/mm/protocal/c/ack;->rGU:I
         * iput-object v1, v5, Lcom/tencent/mm/protocal/c/ack;->rGY:Ljava/util/LinkedList;
         * iput v0, v5, Lcom/tencent/mm/protocal/c/ack;->rGX:I
         */
        //初始化
        Object obj_protocal_b_rx = newInstance(WechatClass.get("com/tencent/mm/protocal/c/ack"));
        setObjectField(obj_protocal_b_rx, "rgF", wxid_list);
        setObjectField(obj_protocal_b_rx, "rGU", wxid_list.size());
        setObjectField(obj_protocal_b_rx, "rGY", room_list);
        setObjectField(obj_protocal_b_rx, "rGX", room_list.size());

        /** 搜索:     "/cgi-bin/micromsg-bin/getcontact"      定位当前函数      667
         * .method public final declared-synchronized pl()V     =>      .method final declared-synchronized OI()V
         * 方法中, 向上寻找
         * nvoke-direct {v1}, Lcom/tencent/mm/w/b$a;-><init>()V
         * iput-object v0, v1, Lcom/tencent/mm/w/b$a;->hDe:Lcom/tencent/mm/bb/a;
         * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/wi;-><init>()V
         * iput-object v0, v1, Lcom/tencent/mm/w/b$a;->hDf:Lcom/tencent/mm/bb/a;
         * iput-object v0, v1, Lcom/tencent/mm/w/b$a;->uri:Ljava/lang/String;
         * iput v0, v1, Lcom/tencent/mm/w/b$a;->hDd:I
         * invoke-virtual {v1}, Lcom/tencent/mm/w/b$a;->Bg()Lcom/tencent/mm/w/b;
         * invoke-direct {v1, p0}, Lcom/tencent/mm/ac/c$2;-><init>(Lcom/tencent/mm/ac/c;)V
         * invoke-static {v0, v1, v2}, Lcom/tencent/mm/w/u;->a(Lcom/tencent/mm/w/b;Lcom/tencent/mm/w/u$a;Z)Lcom/tencent/mm/w/k;
         * =======>
         * invoke-direct {v1}, Lcom/tencent/mm/ab/b$a;-><init>()V
         * iput-object v0, v1, Lcom/tencent/mm/ab/b$a;->dIG:Lcom/tencent/mm/bk/a;
         * invoke-direct {v0}, Lcom/tencent/mm/protocal/c/acl;-><init>()V
         * iput-object v0, v1, Lcom/tencent/mm/ab/b$a;->dIH:Lcom/tencent/mm/bk/a;
         * iput-object v0, v1, Lcom/tencent/mm/ab/b$a;->uri:Ljava/lang/String;
         * iput v0, v1, Lcom/tencent/mm/ab/b$a;->dIF:I
         * invoke-virtual {v1}, Lcom/tencent/mm/ab/b$a;->KT()Lcom/tencent/mm/ab/b;
         * invoke-direct {v1, p0}, Lcom/tencent/mm/ai/c$3;-><init>(Lcom/tencent/mm/ai/c;)V
         * invoke-static {v0, v1, v2}, Lcom/tencent/mm/ab/v;->a(Lcom/tencent/mm/ab/b;Lcom/tencent/mm/ab/v$a;Z)Lcom/tencent/mm/ab/l;
         */
        Object obj_r_a$a = newInstance(WechatClass.get("com/tencent/mm/ab/b$a"));
        setObjectField(obj_r_a$a, "dIG", obj_protocal_b_rx);
        Object obj_protocal_b_ry = newInstance(WechatClass.get("com/tencent/mm/protocal/c/acl"));
        setObjectField(obj_r_a$a, "dIH", obj_protocal_b_ry);

        setObjectField(obj_r_a$a, "uri", "/cgi-bin/micromsg-bin/getcontact");       // note: /cgi-bin/micromsg-bin/getcontact
        setObjectField(obj_r_a$a, "dIF", 0xb6);     // 182
        Object v0 = callMethod(obj_r_a$a, "KT");       //参数1
        if( WechatClass.mtimer_handler == null){
            XLog.e("mtimer_handler is null!");
            return;
        }
        Object v1 = newInstance(WechatClass.get("com/tencent/mm/ai/c$3"), WechatClass.mtimer_handler);    //参数2
        callStaticMethod(WechatClass.get("com/tencent/mm/ab/v"), "a", new Class[]{WechatClass.get("com/tencent/mm/ab/b"), WechatClass.get("com/tencent/mm/ab/v$a"), boolean.class}, v0, v1, true);
    }


    /**
     * 请求生成二维码.
     * @param req_dict
     * {        </br>
     *      "wxid" : 'wxid_1' or "123@chatroom"  <br/>
     *      "style": 20 or 0,        </br>
     * }
     */
    void get_qrcode(final JSONObject req_dict) throws Exception {
        String wxid = req_dict.getString("wxid");
        int style = req_dict.getInt("style");
        XLog.d("get_qrcode, wxid:%s, style:%d", wxid, style);

        // Call
         /** 搜索:   /cgi-bin/micromsg-bin/getqrcode          667
         * com/tencent/mm/an/a      =>      com/tencent/mm/as/a
         */
        WechatHook.wxid2getcode.put(wxid, true);
        Object obj_ai_a = newInstance(WechatClass.get("com/tencent/mm/as/a"), wxid, style);
        WechatClass.postTask(obj_ai_a);
    }


    /**
     * 获取新成员的邀请者.
     * @param req_dict
     * {                                            <br/>
     *     "room" : "123@chatroom",                 <br/>
     *     "newbie_list": "wxid_1;wxid_2;wxid_3"      // 多个以分号分隔     <br/>
     * }
     * @param 返回
     * {                                <br/>
     * "msgtype":'get_newbie_res',      <br/>
     * "room" : "123@chatroom",         <br/>
     * "data": [ {"wxid":"1", "inviter":"1", "displayname":"1",  } ]        <br/>
     * }
     */
    void get_newbie(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String newbie_list = req_dict.getString("newbie_list");
        String[] target_array = newbie_list.split(WechatAction.FenHao);
        XLog.d("get_newbie, room:%s, newbie_list:%s", room, newbie_list);

        // Call
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select roomdata from chatroom where chatroomname=?", new String[]{room} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        byte[] roomdata = contentValues.getAsByteArray("roomdata");
        if( roomdata == null ){
            XLog.e("get_newbie error, roomdata is null");
            return;
        }
        JSONArray jsonArray = WechatClass.parseChatroomRoomdata( roomdata, target_array );
        if( jsonArray == null){
            XLog.e("get_newbie error, parseChatroomRoomdata is null");
            return;
        }
        if(BuildConfig.DEBUG) XLog.d("wxid_2_roomdata:%s", jsonArray);

        // 返回
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.get_newbie_res);
        res_dict.put("room", room);
        res_dict.put("data", jsonArray);
        XLog.i("rpush msgtype:get_newbie_res, data:%s", jsonArray);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    /**
     * 获取新成员的邀请者.
     * @param req_dict
     * {                                    <br/>
     *      "room" : "123@chatroom",        <br/>
     * }
     * @param 返回
     * {                                    <br/>
     *     "msgtype":'get_roomdata_res',    <br/>
     *     "room" : "123@chatroom",         <br/>
     *     "data": [ {"wxid":"1", "inviter":"1", "displayname":"1",  } ]        <br/>
     * }
     */
    void get_roomdata(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        XLog.d("get_roomdata, room:%s", room);
        // Call
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select roomdata from chatroom where chatroomname=?", new String[]{room} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        byte[] roomdata = contentValues.getAsByteArray("roomdata");
        if( roomdata == null ){
            XLog.e("get_roomdata error, roomdata is null");
            return;
        }
        JSONArray jsonArray = WechatClass.parseChatroomRoomdata( roomdata, null );
        if( jsonArray == null){
            XLog.e("get_roomdata error, parseChatroomRoomdata is null");
            return;
        }
        if(BuildConfig.DEBUG) XLog.d("wxid_2_roomdata:%s", jsonArray);

        // 返回
        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.get_roomdata_res);
        res_dict.put("room", room);
        res_dict.put("data", jsonArray);
        XLog.i("rpush msgtype:get_roomdata_res, data:%s", jsonArray);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    /**
     * 向APP侧请求获取群成员头像.     <br/>
     * 启动时初始化, 触发,
     * @param req_dict
     * {                <br/>
     *      "start_time" : '1497428000'     <br/>
     *      "end_time" : '1497428999'       <br/>
     * }
     * @param 返回
     * {        <br/>
     *      "msgtype":'latest_nickname_res,     <br/>
     *      "data": {       <br/>
     *          "wxid_1":"nickname1", "wxid_2":"nickname2",     <br/>
     *          "robot_wxid\u007flastupdatetime": end_time,     <br/>
     *      }       <br/>
     * }
     */
    void latest_nickname(final JSONObject req_dict) throws Exception{
        String start_time = req_dict.getString("start_time");
        String end_time = req_dict.getString("end_time");
        XLog.d("latest_nickname, start_time:%s, end_time:%s", start_time, end_time);

        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.latest_nickname_res);
        // data 对象
        JSONObject data_array = WechatClass.getLatestNickname(start_time, end_time);
        if(data_array == null){
            XLog.e("getLatestHeader error!");
            return;
        }
        res_dict.put("data", data_array);
        // 插入redis
        XLog.i("rpush msgtype:latest_nickname_res, data:%s", data_array);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    /**
     * 向APP侧请求获取群成员头像.      <br/>
     * 1. 启动时初始化, 触发,           <br/>
     * 2. 遇到头像为空时, 触发
     * @param req_dict
     * {        <br/>
     *      "start_time" : '1497428000'     <br/>
     *      "end_time" : '1497428999'       <br/>
     * }
     * @param 返回
     * {        <br/>
     *    "msgtype":'latest_nickname_res,       <br/>
     *    "data": {     <br/>
     *        "wxid_1":"nickname1", "wxid_2":"nickname2",       <br/>
     *       "robot_wxid\u007flastupdatetime": end_time,        <br/>
     *    }     <br/>
     * }
     */
    void latest_header(final JSONObject req_dict) throws Exception{
        String start_time = req_dict.getString("start_time");
        String end_time = req_dict.getString("end_time");
        XLog.d("latest_header, start_time:%s, end_time:%s", start_time, end_time);

        JSONObject res_dict = new JSONObject();
        res_dict.put("msgtype", WechatAction.latest_header_res);
        // data 对象
        JSONObject data_array = WechatClass.getLatestHeader(start_time, end_time);
        if(data_array == null){
            XLog.e("getLatestHeader error!");
            return;
        }
        res_dict.put("data", data_array);
        // 插入redis
        XLog.i("rpush msgtype:latest_header_res, data:%s", data_array);
        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
    }


    /**
     * 打开红包
     * @param req_dict
     * {        </br>
     *      "msgid" : 123,  //long类型.       </br>
     * }
     */
    void open_luckymoney(final JSONObject req_dict) throws Exception{
        long msgid = req_dict.getLong("msgid");
        XLog.d("open_luckymoney, msgid:%d", msgid);
        // Call
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select talker, content from message where msgid=?", new String[]{String.valueOf(msgid)} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        String talker = contentValues.getAsString("talker");
        String content = contentValues.getAsString("content");
        if( content == null ){
            XLog.e("open_luckymoney error, content is null");
            return;
        }
        Map appmsg = WechatClass.parseMessageContent(content, null);
        String nativeurl = (String)appmsg.get(".msg.appmsg.wcpayinfo.nativeurl");
        Uri uri = Uri.parse(nativeurl);
        int msgtype = Integer.parseInt(uri.getQueryParameter("msgtype"));
        int channelid = Integer.parseInt(uri.getQueryParameter("channelid"));
        String sendid = uri.getQueryParameter("sendid");

        MySync.sendid2luckymoney.put(String.format(Locale.ENGLISH, "%s.luckymoney.nativeurl", sendid), nativeurl);
        MySync.sendid2luckymoney.put(String.format(Locale.ENGLISH, "%s.luckymoney.talker", sendid), talker);

        // 参考:  analyse6510/红包/红包未抢光.txt
        /** 搜索:     "/cgi-bin/mmpay-bin/receivewxhb"
         *  com/tencent/mm/plugin/luckymoney/c/ae    =>      com/tencent/mm/plugin/luckymoney/b/ag
         *  .method public constructor <init>(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;)V        =>      .method public constructor <init>(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;)V
         */
        Object receivewxhb = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ag", "/cgi-bin/mmpay-bin/receivewxhb"), channelid, sendid, nativeurl, 0, "v1.0");
        WechatClass.postTask(receivewxhb);
    }


    /**
     * 发送红包
     * @param req_dict
     * {        </br>
     *      "room":  "123@chatroom",     // 群ID      </br>
     *      "num":  1,              // int类型, 红包个数      </br>
     *      "total_fee":  100,      // long类型, 红包总金额, 单位: 分     </br>
     *      "password" : "b64 password",       // 密码字符串, base64编码. (note: 使用base64编码后, 需要额外在string_array[0]位置, 插入1个任意字符串用于混淆)       </br>
     *      "title":  "恭喜发财，大吉大利",     // 标题语. Option, 默认值: "恭喜发财，大吉大利"     </br>
     * }
     */
    void send_hb(final JSONObject req_dict) throws Exception{
        String room = req_dict.getString("room");
        String title = "恭喜发财，大吉大利";
        if( req_dict.has("title") ) title = req_dict.getString("title");
        int num = req_dict.getInt("num");
        long total_fee = req_dict.getLong("total_fee");
        String password = req_dict.getString("password");
        String decrypt_password = new String(MyBase64.b64decode(password.substring(1)));
        XLog.d("send_hb, password: %s, decrypt password: %s", password, decrypt_password);
        MySync.hb_password = decrypt_password;

        long now = System.currentTimeMillis();
        String str_hb_qunique_id = String.valueOf(now);

        // Call
        // 请求发送红包. 参考:  analyse6510/红包/红包未抢光.txt
        /** 搜索:     "/cgi-bin/mmpay-bin/receivewxhb"
         *  com/tencent/mm/plugin/luckymoney/c/ae    =>      com/tencent/mm/plugin/luckymoney/b/ag
         *  .method public constructor <init>(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;)V        =>      .method public constructor <init>(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;)V
         */
        String robot_header_url = (String) callStaticMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/o"), "baX");
        String room_name = (String) callStaticMethod(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/o"), "gS", room);
        int v3 = num;
        long v4_5 = total_fee;
        long v6_7 = 0;
        int v8 = 1;
        String v9 = title + WechatAction.DB_F + WechatAction.DB_F;
        String v10 = robot_header_url;    // 自己的头像地址, "http://wx.qlogo.cn/mmhead/ver_1/R4aGdj2icC58AicFibx12adplibYMmjUAVGHrc9MLACYGjmHeCQbrnF9YhluicehwFcReicDibMNqcqf6TfNUricn6OBLibibq3CCF5B5B3bkHCREMBu8/96"
        String v11 = room;      //  群ID, "123@chatroom"
        String v12 = room_name;     // 群名, "欣主场"
        String v13 = WechatHook.get_robot_info(MySync.g_robot_wxid);        // 机器人wxid, "wxid_w6f7i8zvvtbc12"
        String v14 = WechatHook.get_robot_info("robot_nickname");           // 机器人昵称, "A小管家Service"
        int v15 = 1;
        Object receivewxhb = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/ae", "/cgi-bin/mmpay-bin/requestwxhb"), v3, v4_5, v6_7, v8, v9, v10, v11, v12, v13, v14, v15);
        WechatClass.postTask(receivewxhb);
    }


    /**
     * 获取红包详情
     * @param req_dict
     * {        </br>
     *      "msgid" : 123,   //long类型.       </br>
     *      "receiveTime" : (只查询在此时间点之前的红包详情)       </br>
     * }
     */
    void get_luckymoney_detail(final JSONObject req_dict) throws Exception{
        long msgid = req_dict.getLong("msgid");
        long receiveTime = 0;
        if( req_dict.has("receiveTime") ) receiveTime = req_dict.getLong("receiveTime");
        XLog.d("get_luckymoney_detail, msgid:%d", msgid);

        // Call
        ContentValues contentValues = WechatClass.EnMicroMsg.select( "select talker, content from message where msgid=?", new String[]{String.valueOf(msgid)} );
        if( contentValues == null ){
            XLog.e("select error, contentValues is null");
            return;
        }

        String talker = contentValues.getAsString("talker");
        String content = contentValues.getAsString("content");
        if( content == null ){
            XLog.e("get_luckymoney_detail error, content is null");
            return;
        }
        Map appmsg = WechatClass.parseMessageContent(content, null);
        String nativeurl = (String)appmsg.get(".msg.appmsg.wcpayinfo.nativeurl");
        Uri uri = Uri.parse(nativeurl);
        int msgtype = Integer.parseInt(uri.getQueryParameter("msgtype"));
        int channelid = Integer.parseInt(uri.getQueryParameter("channelid"));
        String sendid = uri.getQueryParameter("sendid");
        XLog.d("get_luckymoney_detail, nativeurl:%s, talker:%s, receiveTime:%d", nativeurl, talker, receiveTime);
        Object qrydetailwxhb = null;

        // 方法1:
        /** 搜索:     "/cgi-bin/mmpay-bin/qrydetailwxhb"
         * com/tencent/mm/plugin/luckymoney/c/u     =>      com/tencent/mm/plugin/luckymoney/b/w
         * .method public constructor <init>(Ljava/lang/String;ILjava/lang/String;JLjava/lang/String;Ljava/lang/String;)V       =>  .method public constructor <init>(Ljava/lang/String;ILjava/lang/String;JLjava/lang/String;Ljava/lang/String;)V
         */
        if( receiveTime == 0 ) {
            qrydetailwxhb = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/w", "/cgi-bin/mmpay-bin/qrydetailwxhb"), sendid, 0xb, 0, nativeurl, "v1.0");
        }else {
            qrydetailwxhb = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/b/w", "/cgi-bin/mmpay-bin/qrydetailwxhb"), sendid, 0xb, nativeurl, receiveTime, "v1.0", "");
        }
//        {
//            // 方法2:
//            // weixin://weixinhongbao/opendetail?sendid=1000039401201712287023132689317&sign=eb102c96cd9d1fdedfb8b0e02bb293a9dbd687a87c535b90674636fb7d950cde559781eac63ae61c594f57aafa4e1e7cbadfd43fe6f4206f047d2c49a1aa4f0a32b01cd381f28eac1afaf178de7c96c8&ver=6
//            String sign = uri.getQueryParameter("sign");
//            nativeurl = String.format("weixin://weixinhongbao/opendetail?sendid=%s&sign=%s&ver=6", sendid, sign);
//            XLog.e("URL:%s", nativeurl);
//            qrydetailwxhb = newInstance(WechatClass.get("com/tencent/mm/plugin/luckymoney/c/u", "/cgi-bin/mmpay-bin/qrydetailwxhb"), sendid, 0xb, 0, nativeurl, "v1.0", "");
//        }
        WechatClass.postTask(qrydetailwxhb);
    }


    /*
     * note 最新修改时间: 2017-10-24  22:00
    // A
     1.all_chatroom_info_req(input)
     2.add_member(input);
     3.add_friend_to_contact_by_wxid(input);
     4.add_friend_to_contact_by_qrid(input);
     5.accept_friend(input);
    // C
     6.change_roomnotice(input);
     7.change_roomowner(input);
     8.chatroom_invite_url_req(input);
    // D
     9.download_attachment(input);
     10.download_bigimg(input);
     11.del_chatroom(input);
    // G
     12.get_contact_by_wxid(input);
     13.get_qrcode(input);
    // K
     14.kick_member(input);
    // R
     transmit_card
    // Q
     15.quit_chatroom(input);
    // S
     16.send_text(input);
     17.sqlite_select(input);
     18.save_room_to_contact(input);
     19.send_img(input);
     20.send_card(input);
     21.send_voice(input);
     22.set_selfname_inroom(input);
     23.settings_need_verify(input);
     24.sqlite_delete(input);
     25.sqlite_execute(input);
     26.sqlite_update(input);
    // U
     27.update_alias(input);
     28.get_newbie(input);
     29.get_roomdata(input);
    */
    boolean _button_click(String[] text_list, final ClassLoader loader) throws Exception{
        try {
            JSONObject input = new JSONObject();
            switch(text_list[0]){
                //      case "/1":{}return true;
                case "/1":{//note:请求所有群消息       /1|0
                    input.put("pid", Integer.parseInt(text_list[1]) );
                    this.all_chatroom_info_req(input);
                }return true;
                case "/2":{//note:群加人小号      /2|123456@chatroom|wxid_00bhs8wxvrlp12
                    input.put("room", text_list[1]);
                    input.put("add_list", text_list[2]);
                    this.add_member(input);
                }return true;
                case "/3":{// note 通过qrid加人 案例1) 互删好友, Door免验证, Service点击button., 响应返回"Everything is OK", 得知Door免验证, 触发发送verifyuser_<0x1>添加 /3|wxid_jy8batoqm0so12|https://u.wechat.com/MBmrHQStahFUWEww_UACSx8@qr
                    // note 通过qrid加人 案例2) 互删好友, Door开验证, Service点击button., 响应返回"user need verify", 得知Door开启验证, 触发发送verifyuser_<0x2>请求验证 /3|wxid_jy8batoqm0so12|https://u.wechat.com/MBmrHQStahFUWEww_UACSx8@qr
                    /**
                    * note Qrid规则:
                    * note 1)qrcode_url类似于http://weixin.qq.com/r/MBmrHQStahFUWEww_UACSx8, 则qrid=MBmrHQStahFUWEww_UACSx8@qr
                    * note 2)rcode_url类似于https://u.wechat.com/MBmrHQStahFUWEww_UACSx8, 则qrid=https://u.wechat.com/MBmrHQStahFUWEww_UACSx8@qr
                    */
                    input.put("wxid", text_list[1]);
                    input.put("qrid", text_list[2]);
                    // this.add_friend_to_contact_by_qrid(input);
                    this._searchcontact(input);
                }return true;
                case "/4":{// note 通过wxid加人 案例1) 互删好友, Door免验证, Service点击button(触发verifyuser_<0x1>添加), Door再点击添加对方, 至此双方完成好友添加      /4|wxid_jy8batoqm0so12
                    // note 通过wxid加人 案例2) 互删好友, Service免验证, Door先添加Service, Service点击button(触发verifyuser_<0x1>添加), 至此双方完成好友添加           /4|wxid_jy8batoqm0so12
                    input.put("wxid", text_list[1]);
                    //input.put("force", Boolean.parseBoolean(text_list[2]));
                    //this.add_friend_to_contact_by_wxid(input);
                    this._verifyuser(input);
                }return true;
                case "/5":{// note 接受好友    /5|wxid_jy8batoqm0so12|v2_cef872f4189e83de2cc5f7d9e467383ed56926eb13860207ca68205a86e267498346c2f4c4a6d26085e67b54f2f9e8f53821a09810701cb821f8a63f00bd5372@stranger|14
                    input.put("wxid", text_list[1]);
                    input.put("ticket", text_list[2]);
                    input.put("scene", Integer.parseInt(text_list[3]));// scene="14" 从群里添加
                    this.accept_friend(input);
                }return true;
                case "/change_roomnotice":{// note 群公告    /change_roomnotice|123456@chatroom|hahaha
                    input.put("room", text_list[1]);
                    input.put("content", text_list[2]);
                    this.change_roomnotice(input);
                }return true;
                case "/change_roomowner":{// note 群主转让    /change_roomowner|123456@chatroom|weixin12345
                    input.put("room", text_list[1]);
                    input.put("wxid", text_list[2]);
                    this.change_roomowner(input);
                }return true;
                case "/8":{// note 点入群邀请链接     /8|http://support.weixin.qq.com/cgi-bin/mmsupport-bin/addchatroombyinvite?ticket=AXIsFRGmD9ev8jgGvVF6%2BQ%3D%3D&from=singlemessage|weixin12345
                    input.put("geta8key_data_req_url", text_list[1]);
                    input.put("geta8key_data_username", text_list[2]);
                    this.chatroom_invite_url_req(input);
                }return true;
                case "/11": {// note del_chatroom 是被 quit_chatroom 所包含, 所以只需测试退群接口
                }return true;
                case "/12": {// note get_contact_by_wxid 只是send_text, 所以不需要测试
                }return true;
                case "/get_qrcode":{// note 获取个人或群二维码    /get_qrcode|123456@chatroom|0
                    input.put("wxid", text_list[1]);
                    input.put("style", Integer.parseInt(text_list[2]));
                    this.get_qrcode(input);
                }return true;
                case "/download_attachment":{// note 下载附件    /download_attachment|41
                    input.put("msgid", Long.parseLong(text_list[1]));
                    this.download_attachment(input);
                }return true;
                case "/download_voice":{// note 下载语音    /download_voice|84|true|false
                    input.put("msgid", Long.parseLong(text_list[1]));
                    input.put("needbuf", Boolean.parseBoolean(text_list[2]));
                    input.put("live", Boolean.parseBoolean(text_list[3]));
                    this.download_voice(input);
                }return true;
                case "/download_bigimg":{// note 下载图片    /download_bigimg|14|6140490350295704428|true
                    input.put("msgId", Long.parseLong(text_list[1]));
                    input.put("msgSvrId", Long.parseLong(text_list[2]));
                    input.put("parseQR", Boolean.parseBoolean(text_list[3]));
                    this.download_bigimg(input);
                }return true;
                case "/kick_member":{// note 群踢人    /kick_member|123456@chatroom|wxid_00bhs8wxvrlp12
                    input.put("room", text_list[1]);
                    input.put("kick_list", text_list[2]);
                    this.kick_member(input);
                }return true;
                case "/quit_chatroom":{// note 退群    /quit_chatroom|123456@chatroom
                    input.put("room", text_list[1]);
                    input.put("wxid", WechatHook.get_robot_info(MySync.g_robot_wxid));
                    this.quit_chatroom(input);
                }return true;
                case "/transmit_card":{// note 转发卡片.(含附件卡片; 小程序卡片; 网站链接卡片)    /transmit_card|weixin12345|39
                    input.put("wxid", text_list[1]);
                    input.put("content", text_list[2]);
                    this.transmit_card(input);
                }return true;
                case "/transmit_emoji":{// note 转发Emoji表情.    /transmit_emoji|weixin12345|10
                    input.put("wxid", text_list[1]);
                    long msgid = Long.parseLong(text_list[2]);
                    input.put("msgid", msgid);
                    this.transmit_emoji(input);
                }return true;
                case "/send_text":{// note 发送消息    /send_text|123456@chatroom|hello
                    input.put("wxid", text_list[1]);
                    input.put("content", text_list[2]);
                    this.send_text(input);
                }return true;
                case "/16.1":{// note 发送消息    /16.1|123456@chatroom|hello|wxid_00bhs8wxvrlp12
                    input.put("wxid", text_list[1]);
                    input.put("content", text_list[2]);
                    input.put("atuserlist", text_list[3]);
                    this.send_text(input);
                }return true;
                case "/sqlite_select":{// note 数据库Select   /sqlite_select|select * from rcontact where username='weixin12345'
                    input.put("sql", text_list[1]);
                    input.put("response", false);
                    this.sqlite_select(input);
                }return true;
                case "/save_room_to_contact":{// note 保存群到通讯录     /save_room_to_contact|123456@chatroom|true
                    input.put("room_list", text_list[1]);
                    input.put("is_save", Boolean.parseBoolean(text_list[2]) );
                    this.save_room_to_contact( input );
                }return true;
                case "/send_img":{// note 发送图片     /send_img|123456@chatroom|true
                    input.put("wxid", text_list[1]);
                    input.put("filename", text_list[2]);
                    input.put("filebuf", text_list[3]);
                    this.send_img( input );
                }return true;
                case "/send_gif":{// note 发送gif     /send_gif|weixin12345
                    input.put("wxid", text_list[1]);
                    input.put("filename", text_list[2]);
                    input.put("filebuf", text_list[3]);
                    this.send_gif( input );
                }return true;
                case "/send_card":{// note 发送链接     /send_card|123456@chatroom|www.baidu.com|title|description|https://salescdn.pa18.com/salesinfo/eLifeAssist/201705/image-_-_-1495870268757.jpg
                    input.put("wxid", text_list[1]);
                    input.put("url", text_list[2] );
                    input.put("title", text_list[3] );
                    input.put("description", text_list[4] );
                    input.put("thumburl", text_list[5] );
                    this.send_card( input );
                }return true;
                case "/set_selfname_inroom":{// note 设置自己的群内别名     /set_selfname_inroom|123456@chatroom|modify
                    input.put( "room", text_list[1] );
                    input.put( "name", text_list[2] );
                    this.set_selfname_inroom(input);
                }return true;
                case "/settings_need_verify":{// note 加我为好友时需要验证,     /settings_need_verify|false
                    input.put( "need_verify", Boolean.parseBoolean(text_list[1]) );
                    this.settings_need_verify(input);
                }return true;
                case "/24":{// note 数据库Delete  sqlite_delete 列表参数在编辑框不容易传送
                }return true;
                case "/sqlite_execute":{// note 数据库Execute   /sqlite_execute|select username from rcontact
                    input.put( "sql", text_list[1] );
                    this.sqlite_execute(input);
                }return true;
                case "/26":{// note 数据库Update   sqlite_update 列表参数在编辑框不容易传送
                }return true;
                case "/update_alias":{// note 点开群员名片, 更新群员别名   /update_alias|123456@chatroom|wxid_00bhs8wxvrlp12
                    String room = text_list[1];
                    String wxid = text_list[2];
                    JSONObject updatedict = new JSONObject();
                    updatedict.put(wxid, room);
                    input.put( "updatedict", updatedict );
                    this.update_alias(input);
                }return true;
                case "/get_roomdata": {// note 解析 chatroom 表字段 roomdata   /get_roomdata|123456@chatroom
                    String room = text_list[1];
                    input.put( "room", room );
                    this.get_roomdata(input);
                }
                return true;
                case "/get_newbie": {// note 解析 chatroom 表字段 roomdata   /get_newbie|123456@chatroom|wxid_00bhs8wxvrlp12
                    String room = text_list[1];
                    String newbie_list = text_list[2];
                    input.put( "room", room );
                    input.put( "newbie_list", newbie_list );
                    this.get_newbie(input);
                }
                return true;
                case "/transmit_voice": {// note 转发语音   /transmit_voice|123456@chatroom|39
                    String wxid = text_list[1];
                    long msgid = Long.parseLong(text_list[2]);
                    input.put( "wxid", wxid );
                    input.put( "msgid", msgid );
                    this.transmit_voice(input);
                }
                return true;
                case "/send_voice": {// note 发送语音   /send_voice|123456@chatroom|100842040817d25c8752852104
                    String wxid = text_list[1];
                    String filename = text_list[2];
                    input.put( "wxid", wxid );
                    input.put( "voicelength", 2000 );

                    String fullpath = WechatClass.voiceFullpath(filename);
                    XLog.d("fullpath:%s", fullpath);
                    File voice = new File( fullpath );
                    if( !voice.exists() ){
                        XLog.e("voice file not exists, fullpath:%s", fullpath);
                        return true;
                    }
                    int size = (int) voice.length();
                    byte[] amr_byte_array = new byte[size];
                    BufferedInputStream buf = new BufferedInputStream( new FileInputStream( voice ) );
                    buf.read(amr_byte_array, 0, amr_byte_array.length);
                    buf.close();
                    String filebuf = Base64.encodeToString(amr_byte_array, Base64.DEFAULT);  //与Python通信时建议用default
                    input.put( "filebuf", filebuf );
                    this.send_voice(input);
                }
                return true;
                case "/transmit_video": {// note 转发视频   /transmit_video|123456@chatroom|77
                    String wxid = text_list[1];
                    long msgid = Long.parseLong(text_list[2]);
                    input.put( "wxid", wxid );
                    input.put( "msgid", msgid );
                    this.transmit_video(input);
                }
                return true;
                case "/download_video": {// note 下载视频   /download_video|1401001011174e643709840
                    String filename = text_list[1];
                    input.put( "filename", filename );
                    this.download_video(input);
                }
                return true;
                case "/transmit_favorite": {// note 转发收藏   /transmit_favorite|weixin12345|1532173367531         或者     /transmit_favorite|weixin12345
                    /** 使用如下 SQL 查出 local favorite id:       /sqlite_select|select * from FavItemInfo
                     * 转发文件: server.log
                     */
                    String wxid = text_list[1];
                    input.put( "wxid", wxid );
                    if(text_list.length == 3) {
                        String local_favorite_id = text_list[2];
                        input.put( "local_favorite_id", local_favorite_id );
                    }
                    this.transmit_favorite(input);
                }
                return true;
                case "/open_luckymoney": {// note 打开红包   /open_luckymoney|80
                    long msgid = Long.parseLong(text_list[1]);
                    input.put( "msgid", msgid );
                    this.open_luckymoney(input);
                }
                return true;
                case "/get_luckymoney_detail": {// note 红包详情   /get_luckymoney_detail|80|0
                    long msgid = Long.parseLong(text_list[1]);
                    long receiveTime = Long.parseLong(text_list[2]);
                    input.put( "msgid", msgid );
                    input.put( "receiveTime", receiveTime );
                    this.get_luckymoney_detail(input);
                }
                return true;
                case "/send_hb": {// note 请求发送红包   /send_hb|1ODYwNDMz|123@chatroom|2|4
                    input.put( "password", text_list[1] );
                    input.put( "room", text_list[2] );
                    input.put( "num", Integer.parseInt(text_list[3]) );
                    input.put( "total_fee", Long.parseLong(text_list[4]) );
                    this.send_hb(input);
                }
                return true;
                //////////////////////////////////////////////////////////////////////////////////
                case "/test": {
                    Object wechatxlog = getStaticObjectField(WechatClass.get("com/tencent/mm/xlog/app/XLogSetup"), "xlog");
                    int log_level = (int)callMethod(wechatxlog, "getLogLevel");
                    XLog.w("wechatxlog getLogLevel: %d", log_level);
                }
                return true;
                case "/exit": {
                    System.exit(1);
                }
                return true;
                //////////////////////////////////////////////////////////////////////////////////
                case "/parse1": {// 解析 fmessage_msginfo 表字段 msgContent
                    String field_content = "<msg fromusername=\"wxid_g8y1jwysmqj712\" encryptusername=\"v1_7e77b0c56975a18cdd6c830a88a1f201a12b1b1d7c2e372213b33a6199ae70f253f8cce65b20a87edfabbda2f3f453c2@stranger\" fromnickname=\"724助理总管\" content=\"好友申请语\" imagestatus=\"3\" scene=\"3\" country=\"\" province=\"\" city=\"\" sign=\"\" percard=\"1\" sex=\"0\" alias=\"group_admin002\" weibo=\"\" weibonickname=\"\" albumflag=\"0\" albumstyle=\"0\" albumbgimgid=\"\" snsflag=\"1\" snsbgimgid=\"\" snsbgobjectid=\"0\" mhash=\"\" mfullhash=\"\" bigheadimgurl=\"http://wx.qlogo.cn/mmhead/ver_1/SptkgHVIuqwicf2GHBhwibmW6x2N5ttJzscjP9lkxKdibtbYR19Zy1scRlyZ7AUUaZFicBnD66V56ylGFxA8iaCu9jFqxFmpwfGTdpogVHuHJCq0/0\" smallheadimgurl=\"http://wx.qlogo.cn/mmhead/ver_1/SptkgHVIuqwicf2GHBhwibmW6x2N5ttJzscjP9lkxKdibtbYR19Zy1scRlyZ7AUUaZFicBnD66V56ylGFxA8iaCu9jFqxFmpwfGTdpogVHuHJCq0/96\" ticket=\"v2_e9dd3976d5f63af36343982f5eae444156a53e3999638eaad842174128f3a72f89da748d54888c191974ca6d79f338d0379b9c728daa5d042f34f5d51608ce94@stranger\" opcode=\"2\" googlecontact=\"\" qrticket=\"\" chatroomusername=\"\" sourceusername=\"\" sourcenickname=\"\"><brandlist count=\"0\" ver=\"650420173\"></brandlist></msg>";
                    Map appmsg = WechatClass.parseMessageContent(field_content, null);
                    XLog.d("parseAppMsg2, result:%s", appmsg);
                }
                return true;
                case "/qrcode":{// 判断图片是否二维码, 被下载图片测试案例包含   /qrcode
                    String robot_qrcode_path = MyPath.join(WechatHook.localQrcodeDir, WechatHook.get_robot_info(MySync.g_robot_wxid) + ".jpg");
                    String qrcode_url = WechatClass.parseQrcode(robot_qrcode_path);
                    XLog.i("qrcode_url:%s", qrcode_url);
                }return true;
                case "/cpuinfo":{// 测试cpuinfo       /cpuinfo
                    Class<?> compatible_d_p = findClass("com.tencent.mm.compatible.d.p", loader);
                    HashMap aa = (HashMap)callStaticMethod(compatible_d_p, "oJ");
                    XLog.d("cpuinfo:%s", aa);
                }return true;
            }
        } catch (Throwable e) {
            XLog.e("hookSendButton error. stack:%s", android.util.Log.getStackTraceString(e));
        }
        return false;
    }
}

/**
 * 查询最近的红包消息:   /sqlite_select|select * from message where type in(469762097, 436207665) and msgId > 200 order by msgId desc
 */
