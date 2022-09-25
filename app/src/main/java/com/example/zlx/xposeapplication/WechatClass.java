package com.example.zlx.xposeapplication;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.example.zlx.mybase.MyPath;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class WechatClass {
    static Context wechatContext = null; // wechatContext

    static Activity currentActivity = null;
    //
    static Object mtimer_handler = null;
    static Object webwx_handler = null;
    static Object voiceinfo_handler = null;
    static Object videoinfo2_handler = null;
    static Object ImgInfo2_handler = null;
    static Db EnMicroMsg = null;
    static Db enFavorite = null;
    static Object test_handler = null;
    //static Object oplog2_handler = null;

    //Class
    public static Class<?> pointers_PString = null;
    public static Class<?> pointers_PInt = null;
    static Class<?> webwx_a_c = null;


    static void findClass_before_auth(final ClassLoader loader) {
        // note 写法1: findClass("", loader), 写法2: loader.loadClass("")
        XLog.i("findClass_before_auth");
    }


    /** -- */
    static void findClass_after_auth(final ClassLoader loader) {
        XLog.i("findClass_after_auth");
        WechatClass.init_postTask(loader);      // 发送task
        WechatClass.init_hookGetBigimgFullPath(loader);
        WechatClass.init_hookTaskResponse(loader);
        WechatClass.init_hookVerifykUserResponse(loader);
        WechatClass.init_callSendImg(loader);

        // TODO smali更新 hookWebwxObject
//        WechatClass.storage_k = findClass("com.tencent.mm.storage.k", loader);
//        WechatClass.storage_an = findClass("com.tencent.mm.storage.an", loader);      // 修改群成员备注
//        WechatClass.pointers_PString = findClass("com.tencent.mm.pointers.PString", loader);      // 发送图片
//        WechatClass.pointers_PInt = findClass("com.tencent.mm.pointers.PInt", loader);        // 发送图片
//        WechatClass.sdk_platformtools_h = findClass("com.tencent.mm.sdk.platformtools.h", loader);    // 发送语音
//        WechatClass.an_d = findClass("com.tencent.mm.an.d", loader);  // 发送视频
//        WechatClass.sdk_platformtools_d = findClass("com.tencent.mm.sdk.platformtools.d", loader);        // 发送视频
    }


    /** -- */
    static Class<?> r_e = null;
    static void init_callSendImg(final ClassLoader loader) {
        /** 更新步骤: TODO
         com/tencent/mm.r/e
         */
        //WechatClass.r_e = WechatClass.get("com/tencent/mm.r/e");
    }


    /** -- */
    static Class<?> protocal_b_aly = null;
    static Class<?> platformtools_n = null;
    static void init_hookTaskResponse(final ClassLoader loader) {
        // 更新步骤: 6510版本更新.txt, 文件内搜索: "微信号, 返回获取Alias"
        /** 搜索:  "/cgi-bin/micromsg-bin/getqrcode"      (投机方法: 6510版本更新.txt, "保存个人二维码")         667
         * 进入类文件, 搜索 "onGYNetEnd errType:"      定位当前函数
         * .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/p;[B)V       =>      .method public final a(IIILjava/lang/String;Lcom/tencent/mm/network/q;[B)V
         * 函数内搜索 [B, 向上寻找
         * invoke-static {v0}, Lcom/tencent/mm/platformtools/n;->a(Lcom/tencent/mm/protocal/c/atu;)Ljava/lang/String;       =>      invoke-static {v0}, Lcom/tencent/mm/platformtools/ab;->a(Lcom/tencent/mm/protocal/c/bhz;)Ljava/lang/String;
         */
        WechatClass.platformtools_n = WechatClass.get("com/tencent/mm/platformtools/ab");
        WechatClass.protocal_b_aly = WechatClass.get("com/tencent/mm/protocal/c/bhz");
    }


    static Class<?> NetSceneVerifyUser_dkverify = null;
    static void init_hookVerifykUserResponse(final ClassLoader loader) {
        // 加好友/函数代码.txt
         /**  搜索:     "jacks catch add Contact errCode: %d && errMsg: %s"           667
         *  进入类文件, 搜索: chatroomName     向下寻找
         *  invoke-direct/range {v0 .. v9}, Lcom/tencent/mm/pluginsdk/model/m;-><init>(ILjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;)V
         *  =>  invoke-direct/range {v0 .. v9}, Lcom/tencent/mm/pluginsdk/model/m;-><init>(ILjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;)V
         */
        WechatClass.NetSceneVerifyUser_dkverify = WechatClass.get("com/tencent/mm/pluginsdk/model/m");
    }


    /** -0- */
    static Class<?> MMCore = null;
    static String tE = null;
    static String d = null;
    static Class<?> r_j = null;
    static void init_postTask(final ClassLoader loader) {
        /**
         new-instance v1, Lcom/tencent/mm/modelvoice/f;
         invoke-direct {v1, v0}, Lcom/tencent/mm/modelvoice/f;-><init>(Ljava/lang/String;)V
         invoke-static {}, Lcom/tencent/mm/model/ah;->tE()Lcom/tencent/mm/r/m;
         move-result-object v0
         invoke-virtual {v0, v1}, Lcom/tencent/mm/r/m;->d(Lcom/tencent/mm/r/j;)Z
         ==================>
         new-instance v1, Lcom/tencent/mm/modelvoice/f;             667
         invoke-direct {v1, v0}, Lcom/tencent/mm/modelvoice/f;-><init>(Ljava/lang/String;)V
         invoke-static {}, Lcom/tencent/mm/kernel/h;->uH()Lcom/tencent/mm/w/n;
         move-result-object v0
         const/4 v2, 0x0
         invoke-virtual {v0, v1, v2}, Lcom/tencent/mm/w/n;->a(Lcom/tencent/mm/w/k;I)Z
         */

        /** 更新步骤: 6510版本更新.txt, model/ah->tE()->ah()关联
         * invoke-static {}, Lcom/tencent/mm/s/ao;->uH()Lcom/tencent/mm/w/n;        =>      invoke-static {}, Lcom/tencent/mm/model/au;->DF()Lcom/tencent/mm/ab/o;
         */
        WechatClass.MMCore = WechatClass.get("com/tencent/mm/model/au");

        /** 更新步骤: 6510版本更新.txt, model/ah->tE()->ah()关联
         "uH"        =>      "DF"
         */
        WechatClass.tE = "DF";

        /** 更新步骤: 6510版本更新.txt, model/ah->tE()->ah()关联
         "a"        =>      "a"
         */
        WechatClass.d = "a";

        /** 更新步骤: 6510版本更新.txt, model/ah->tE()->ah()关联
         * invoke-virtual {v0, v1, v2}, Lcom/tencent/mm/w/n;->a(Lcom/tencent/mm/w/k;I)Z       =>      invoke-virtual {v0, v1, v2}, Lcom/tencent/mm/ab/o;->a(Lcom/tencent/mm/ab/l;I)Z
         */
        WechatClass.r_j = WechatClass.get("com/tencent/mm/ab/l");
    }


    static void postTask(Object task) {
        callMethod(callStaticMethod(WechatClass.MMCore, WechatClass.tE), WechatClass.d, new Class[]{WechatClass.r_j, int.class}, task, 0);
    }


    /** -0- */
    static Class<?> ImgInfoStorage = null;
    static String ImgInfoStorage_getObject = null;
    static String ImgInfoStorage_getFullPath = null;
    static void init_hookGetBigimgFullPath(final ClassLoader loader) {
        /** 搜索 "read img buf failed: "
         * 进入类中文件, 搜索:  field_isSend:I
         * invoke-static {}, Lcom/tencent/mm/af/n;->Gw()Lcom/tencent/mm/af/f;     =>      invoke-static {}, Lcom/tencent/mm/ak/o;->Pf()Lcom/tencent/mm/ak/g;
         */
        WechatClass.ImgInfoStorage = WechatClass.get("com/tencent/mm/ak/o");
        WechatClass.ImgInfoStorage_getObject = "Pf";

        /** 当前文件, 正则表达式搜索: .method.*Ljava\/lang\/String;Ljava\/lang\/String;Ljava\/lang\/String;Z\)Ljava/lang/String;$
         .method public final a(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;       =>      .method public final b(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;
         */
        WechatClass.ImgInfoStorage_getFullPath = "b";
    }


    static String getPersonalDir(String subdir){
        // note 返回例子: "/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/"
        /** 搜索: "MMCore has not been initialize ?"      定位当前函数      667
         * .method public static yC()Lcom/tencent/mm/s/c;       =>      .method public static HU()Lcom/tencent/mm/model/c;
         * 分别进入返回函数内层: com/tencent/mm/s/c 和 com/tencent/mm/model/c, 搜索: "attachment/"       当年更为当前函数
         * .method public static wO()Ljava/lang/String;     =>      .method public static Gk()Ljava/lang/String;
         */
        String attach_dir = (String)callMethod( callStaticMethod(WechatClass.MMCore, "HU"), "Gk" );  // 返回: "/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/attachment/"
        String personnal_dir = attach_dir.split("attachment", 2)[0];
        return MyPath.join(personnal_dir, subdir);
    }


    static String getAttachmentDir(){
        // note 返回例子: "/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/attachment/"
        /** 搜索: "MMCore has not been initialize ?"     定位当前函数       667
         * com/tencent/mm/s/ao      =>      com/tencent/mm/model/au
         * .method public static yC()Lcom/tencent/mm/s/c;       =>      .method public static HU()Lcom/tencent/mm/model/c;
         * 分别进入函数返回对象的类内: com/tencent/mm/s/c 和 com/tencent/mm/model/c       搜索: "attachment/"       定位当前函数
         * .method public static wO()Ljava/lang/String;     =>      .method public static Gk()Ljava/lang/String;
         */
        // invoke-static {}, Lcom/tencent/mm/s/ao;->yC()Lcom/tencent/mm/s/c;
        return (String)callMethod( callStaticMethod(WechatClass.get("com/tencent/mm/model/au"), "HU"), "Gk" );
    }


    static String getBigImgFullPath(String bigImgPath) {
        // 通过表字段的大图路径转换成: 磁盘文件全路径
        Object obj_ab_f = callStaticMethod(WechatClass.ImgInfoStorage, WechatClass.ImgInfoStorage_getObject);
        String picture_path = (String) callMethod(obj_ab_f, WechatClass.ImgInfoStorage_getFullPath, bigImgPath, "", "", true);
        if ( TextUtils.isEmpty(picture_path) ) {
            XLog.e("picture_path is null");
            return null;
        }
        return picture_path;
    }


    static String voiceFilename() {
        /** note 返回字符串如: "011917040817d25c87563ad102" */
        /** 搜索: "Alter table voiceinfo add MsgSeq INT"      667
         * com/tencent/mm/modelvoice/u      =>      com/tencent/mm/modelvoice/u
         * 进入类中, 搜索:    (Ljava/lang/String;)Ljava/lang/String;
         * .method public static mb(Ljava/lang/String;)Ljava/lang/String;       =>      .method public static ov(Ljava/lang/String;)Ljava/lang/String;
         */
        String filename = (String) callStaticMethod(WechatClass.get("com/tencent/mm/modelvoice/u"), "ov", WechatHook.get_robot_info(MySync.g_robot_wxid) );
        return filename;
    }


    static String voiceFullpath(String filename) {
        /** note. 根据filename字段, 以该字符串的unicode byte, 用MD5计算结果值, 以该结果前4个字母为2个目录名
         * note. 参数: filename 格式如:　"100842040817d25c8752852104"
         */
        /** 搜索: "getAmrFullPath cost: "                 667
         * com/tencent/mm/modelvoice/q      =>      com/tencent/mm/modelvoice/q
         * 类中向上寻找
         * invoke-static {}, Lcom/tencent/mm/modelvoice/q;->wG()Ljava/lang/String;
         * const-string/jumbo v2, "msg_"
         * const-string/jumbo v3, ".amr"
         * const/4 v4, 0x2
         * invoke-static {v0, v2, p0, v3, v4}, Lcom/tencent/mm/sdk/platformtools/h;->a(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;
         * ==============>
         * invoke-static {}, Lcom/tencent/mm/modelvoice/q;->Gd()Ljava/lang/String;
         * const-string/jumbo v2, "msg_"
         * const-string/jumbo v3, ".amr"
         * const/4 v4, 0x2
         * invoke-static {v0, v2, p0, v3, v4}, Lcom/tencent/mm/sdk/platformtools/h;->b(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;
         */
        String param_v1 = (String)callStaticMethod( WechatClass.get("com/tencent/mm/modelvoice/q"), "Gd");      // note 得到字符串格式如: /storage/sdcard/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/voice2/
        String AmrFullPath = (String) callStaticMethod(WechatClass.get("com/tencent/mm/sdk/platformtools/h"), "b", param_v1, "msg_", filename, ".amr", 0x2);
        return AmrFullPath;
    }


    static String videoFilename() {
        /** 返回字符串如: "100842040817d25c8752852104"
         * 搜索: "HHmmssddMMyy"      667
         * com/tencent/mm/modelvideo/s      =>      com/tencent/mm/modelvideo/s
         * 进入类中, 向上定位当前函数
         * .method public static lp(Ljava/lang/String;)Ljava/lang/String;       =>      .method public static nJ(Ljava/lang/String;)Ljava/lang/String;
         */
        String filename = (String)callStaticMethod(WechatClass.get("com/tencent/mm/modelvideo/s"), "nJ", WechatHook.get_robot_info(MySync.g_robot_wxid));
        return filename;
    }


    static String videoMp4Fullpath(String filename) {
        /** 搜索: "HHmmssddMMyy"          667
         * com/tencent/mm/modelvideo/s      =>      com/tencent/mm/modelvideo/s
         * 进入类中, 搜索: ".mp4"
         * .method public static lq(Ljava/lang/String;)Ljava/lang/String;       =>      .method public static nK(Ljava/lang/String;)Ljava/lang/String;
         */
        String mp4FullPath = (String) callStaticMethod(WechatClass.get("com/tencent/mm/modelvideo/s"), "nK", filename);
        return mp4FullPath;
    }


    static String videoJpgFullpath(String filename) {
        /** 搜索: "HHmmssddMMyy"      667
         * com/tencent/mm/modelvideo/s      =>      com/tencent/mm/modelvideo/s
         *  进入类中, 搜索:   ".jpg"      向上定位当前函数
         * .method public static lr(Ljava/lang/String;)Ljava/lang/String;       =>      .method public static nL(Ljava/lang/String;)Ljava/lang/String;
         */
        String jpgFullPath = (String) callStaticMethod(WechatClass.get("com/tencent/mm/modelvideo/s"), "nL", filename);
        return jpgFullPath;
    }


    /** -0- */
    static String parseQrcode(String picture_path) {
        if(BuildConfig.DEBUG) XLog.d("parseQrcode, picture_path:%s", picture_path);
        /** 更新步骤 6510版本更新.txt,  搜索:     "in decodeFile, file == null"           677
         * com/tencent/mm/plugin/u/a/a      =>      com/tencent/mm/plugin/af/a/a
         */
        Object obj_scanner_DecodeFile = newInstance(WechatClass.get("com/tencent/mm/plugin/af/a/a"));         // note 改类是 scanner_DecodeFile
        /** 类中搜索类成员
         * .field oCq:J     =>      .field miQ:J
         */
        setObjectField(obj_scanner_DecodeFile, "miQ", java.lang.System.currentTimeMillis());
        /** 类中搜索
         * .method final BR(Ljava/lang/String;)Lcom/tencent/mm/plugin/u/a/d;        =>      .method public final Kf(Ljava/lang/String;)Lcom/tencent/mm/plugin/af/a/d;
         */
        Object plugin_u_a_d = callMethod(obj_scanner_DecodeFile, "Kf", picture_path);
        if( plugin_u_a_d == null) {
            return "";
        }
        /** 类中搜索
         * iget-object v6, v0, Lcom/tencent/mm/plugin/u/a/d;->result:Ljava/lang/String;     =>      iget-object v7, v0, Lcom/tencent/mm/plugin/af/a/d;->result:Ljava/lang/String;
         */
        String imgurl = (String) getObjectField(plugin_u_a_d, "result");
        return imgurl;
    }


    /** -0- */
    static HashMap parseMessageContent(String field_content, String field_reserved) {
        // note: 解析 message 表 的 content 字段, 第二个参数 reserved字段 一般为 null, 获取解析结果为 String title = (String)appmsg.get(".msg.appmsg.title");
        // 更新步骤: 6510版本更新.txt
        /** 搜索: ".msg.appmsg.url"    667
         * com/tencent/mm/r/f$a     =>      com/tencent/mm/y/g$a
         * 进入类中, 搜索:    "parse msg failed"
         * .method public static final B(Ljava/lang/String;Ljava/lang/String;)Lcom/tencent/mm/r/f$a;        =>      .method public static final J(Ljava/lang/String;Ljava/lang/String;)Lcom/tencent/mm/y/g$a;
         */
        // 表message里面的reserved字段内容, 版本6.3.11和6.5.10, 都因解析函数内部不使用p2参数field_reserved， 所以可控
        Object obj_AppMessage = callStaticMethod(WechatClass.get("com/tencent/mm/y/g$a", "AppMessage"), "J", field_content, field_reserved);        // note 该类是: AppMessage

        /** 类中搜索:  ".msg.appmsg.$appid"      向上寻找       667
         * iput-object p1, p0, Lcom/tencent/mm/r/f$a;->hse:Ljava/util/Map;      =>      iput-object p1, p0, Lcom/tencent/mm/y/g$a;->dwm:Ljava/util/Map;
         */
        HashMap appmsg = (HashMap)getObjectField(obj_AppMessage, "dwm");
        return appmsg;
    }


    static Map parseChatroomMsgsource(String field_content, String xml_header) {
        // note: 解析正常的 xml.
        // 解析 chatroom 表 的 msgsource 字段, 获取解析结果为 String title = (String)appmsg.get(".msg.appmsg.title");
        // note: 举例如, 传入 field_content: <msgsource> <atuserlist>wxid_u9d5k0sn17xo12,wxid_2</atuserlist> <silence>0</silence> <membercount>8</membercount> </msgsource>
        // note: 也可以解析: <msg> <appmsg appid="" sdkver="0"></msg>
        /** 搜索: "text not in xml format"
         * Lcom/tencent/mm/b/f      =>      com/tencent/mm/c/f          667
         * .method public static q(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;     =>      .method public static z(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;
         */
        // note xml消息头 = "msg"
        return (Map)callStaticMethod(WechatClass.get("com/tencent/mm/c/f", "XmlParser"), "z", field_content, xml_header);        // note 这是最底层的xml解析函数
    }


    static ContentValues parseFmessageMsgcontent(String msgContent) {// note ContentValues 只是 HashMap 的包装类, 且把key固定成String类
        // note: 解析 fmessage_conversation 或 fmessage_msginfo 表 的 msgcontent 字段,
        // note: 举例如: <msg fromusername="wxid_g8y1jwysmqj712" encryptusername="v1_7e77b0c56975a18cdd6c830a88a1f201a12b1b1d7c2e372213b33a6199ae70f253f8cce65b20a87edfabbda2f3f453c2@stranger" fromnickname="724助理总管" content="好友申请语" fullpy="724zhulizongguan" shortpy="724ZLZG" imagestatus="3" scene="3" country="" province="" city="" sign="" percard="1" sex="0" alias="group_admin002" weibo="" weibonickname="" albumflag="0" albumstyle="0" albumbgimgid="" snsflag="1" snsbgimgid="" snsbgobjectid="0" mhash="" mfullhash="" bigheadimgurl="http://wx.qlogo.cn/mmhead/ver_1/SptkgHVIuqwicf2GHBhwibmW6x2N5ttJzscjP9lkxKdibtbYR19Zy1scRlyZ7AUUaZFicBnD66V56ylGFxA8iaCu9jFqxFmpwfGTdpogVHuHJCq0/0" smallheadimgurl="http://wx.qlogo.cn/mmhead/ver_1/SptkgHVIuqwicf2GHBhwibmW6x2N5ttJzscjP9lkxKdibtbYR19Zy1scRlyZ7AUUaZFicBnD66V56ylGFxA8iaCu9jFqxFmpwfGTdpogVHuHJCq0/96" ticket="v2_e9dd3976d5f63af36343982f5eae444156a53e3999638eaad842174128f3a72f89da748d54888c191974ca6d79f338d0379b9c728daa5d042f34f5d51608ce94@stranger" opcode="2" googlecontact="" qrticket="" chatroomusername="" sourceusername="" sourcenickname=""><brandlist count="0" ver="650420173"></brandlist></msg>
        /** 更新步骤: 6510版本更新.txt
         "ET"       =>      "Rg"
         "fvG"      =>      "opa"
         "asc"      =>      "scene"
         */
        /** 搜索 "dkverify ticket:%s"
         * com/tencent/mm/storage/ag$e      =>      com/tencent/mm/storage/aw$d
         */
        Object obj_MsgInfo = callStaticMethod(WechatClass.get("com/tencent/mm/storage/aw$d"), "Rg", msgContent);
        String ticket = (String) getObjectField(obj_MsgInfo, "opa");
        int scene = (int)getObjectField(obj_MsgInfo, "scene");
        ContentValues contentValues = new ContentValues();
        contentValues.put("ticket", ticket);
        contentValues.put("scene", scene);
        return contentValues;
    }


    static JSONArray parseChatroomRoomdata(byte[] roomdata, String[] target_array) throws JSONException{
        // note: 解析 chatroom 表 的 roomdata 字段,
        // note: 举例如: 二进制结构: wxid1口口wix2口口wxid3
        JSONArray jsonArray = new JSONArray();

        // 参考:   6510/roomdata/分析.roomdata.txt
        /** 搜索:     "parse RoomData failed"     667
         * 进入类中, 搜索:        ;->field_roomdata:[B        想下寻找
         * invoke-virtual {v0, v1}, Lcom/tencent/mm/g/a/a/a;->aA([B)Lcom/tencent/mm/bb/a;       =>      invoke-virtual {v0, v1}, Lcom/tencent/mm/i/a/a/a;->aG([B)Lcom/tencent/mm/bk/a;
         */
        Object roomdataParser = newInstance( WechatClass.get("com/tencent/mm/i/a/a/a") );
        Object obj_g_a_a_a = callMethod(roomdataParser, "aG", roomdata);
        if( obj_g_a_a_a == null ){
            XLog.e("obj_g_a_a_a == null");
            return null;
        }

        /** 进入类中, 搜索第一个:       ".RoomData.Member"      向上寻找     667
         * iget-object v0, v1, Lcom/tencent/mm/g/a/a/a;->gZc:Ljava/util/LinkedList;     =>      iget-object v0, v1, Lcom/tencent/mm/i/a/a/a;->dav:Ljava/util/LinkedList;
         *
         */
        LinkedList room_linkedlist = (LinkedList)getObjectField(obj_g_a_a_a, "dav");
        if( room_linkedlist == null ){
            XLog.e("room_arraylist == null");
            return null;
        }

        String wxid, inviter, displayname;
        for(Object memberdata: room_linkedlist){
            JSONObject jsonObject = new JSONObject();
            /** 进入类中, 搜索:       containsKey     向下寻找        667
             *  check-cast v1, Lcom/tencent/mm/g/a/a/b;     =>      check-cast v0, Lcom/tencent/mm/i/a/a/b;
             *  进入  com/tencent/mm/g/a/a/b      =>      com/tencent/mm/i/a/a/b
             * .field public gZg:Ljava/lang/String;
             * .field public gZh:I
             * .field public gZi:Ljava/lang/String;
             * .field public userName:Ljava/lang/String;
             * =====>
             * .field public daA:Ljava/lang/String;
             * .field public daB:I
             * .field public daC:Ljava/lang/String;
             * .field public userName:Ljava/lang/String;
             */
            displayname = (String) getObjectField(memberdata, "daA");
            inviter = (String) getObjectField(memberdata, "daC");
            wxid = (String) getObjectField(memberdata, "userName");
            if(BuildConfig.DEBUG) XLog.d("parseChatroomRoomdata, wxid:%s, inviter:%s, displayname:%s", wxid, inviter, displayname);
            if( target_array == null ) {
                if( TextUtils.isEmpty(inviter) && TextUtils.isEmpty(displayname))
                    continue;
                jsonObject.put("wxid", wxid);
                jsonObject.put("inviter", inviter);
                jsonObject.put("displayname", displayname);
                jsonArray.put(jsonObject);
            }else{
                // note. 从目标对象中筛选
                for (String target : target_array) {
                    if( TextUtils.equals(target, wxid) ) {
                        jsonObject.put("wxid", wxid);
                        jsonObject.put("inviter", inviter);
                        jsonObject.put("displayname", displayname);
                        jsonArray.put(jsonObject);
                        //if(BuildConfig.DEBUG) XLog.d("match, wxid:%s, target:%s", wxid, target);
                        break;
                    }else{
                        //if(BuildConfig.DEBUG) XLog.e("not match, wxid:%s, target:%s", wxid, target);
                    }
                }
            }
        }
        return jsonArray;
    }


    static String parseMessageLvbuffer(byte[] lvbuffer) {
        // note 解析 message 表 的 lvbuffer 字段,
        // note: 举例如: 二进制结构: {口口M<msgsource><slience>口口}
        /** 可参考: ./atuserlist/@人分析.txt
         * 搜索: "Buffer For Parse"
         * 得到该类文件
         * com/tencent/mm/sdk/platformtools/s       =>      com/tencent/mm/sdk/platformtools/u          667
         * .method public final bh([B)I     =>      .method public final by([B)I
         * .method public final bGR()Z      =>      .method public final chD()Z
         */
        Object lvbufferParser = newInstance( WechatClass.get("com/tencent/mm/sdk/platformtools/u") );
        int ret = (int)callMethod(lvbufferParser, "by", lvbuffer);
        if( ret != 0) {// note =0是正常, 非0不正常
            return null;
        }
        boolean bret;
        bret = (boolean)callMethod(lvbufferParser, "chD");
        if( !bret ) {
            callMethod(lvbufferParser, "getString");
        }
        bret = (boolean)callMethod(lvbufferParser, "chD");
        if( !bret ) {
            callMethod(lvbufferParser, "getInt");
        }
        bret = (boolean)callMethod(lvbufferParser, "chD");
        if( !bret ) {
            // note 目标是: p0->gKb 变量, 它在第三个位置, 所以调用3次chD函数时候, 即可返回
            return (String)callMethod(lvbufferParser, "getString");
        }
        return null;
    }



    /*********************************************************************************/
    /** note: 数据库操作 */
    /**
     com/tencent/mm/az/e        =>      com/tencent/mm/bh/e
     .method public final rawQuery(Ljava/lang/String;[Ljava/lang/String;)Landroid/database/Cursor;      =>      .method public final a(Ljava/lang/String;[Ljava/lang/String;I)Landroid/database/Cursor;
     */
        static void insertMessage(String talker, int talkerId, String msg, int type, long createTime) {
            int status = 3;
            long msgSvrId = createTime + (new Random().nextInt());
            long msgId = getNextMsgId();
            ContentValues v = new ContentValues();
            v.put("msgid", msgId);
            v.put("msgSvrid", msgSvrId);
            v.put("type", type);
            v.put("status", status);
            v.put("createTime", createTime);
            v.put("talker", talker);
            v.put("content", msg);
            if (talkerId != -1) {
                v.put("talkerid", talkerId);
            }
            WechatClass.EnMicroMsg.rawInsert("message", "", v);
        }


        public static void insertSendMessage(String talker, int talkerId, String msg, int type, long createTime) {
            long msgId = getNextMsgId();
            ContentValues v = new ContentValues();
            v.put("msgid", msgId);
            v.put("msgSvrid", 0L);
            v.put("type", type);
            v.put("status", 1);
            v.put("createTime", createTime);
            v.put("talker", talker);
            v.put("content", msg);
            if (talkerId != -1) {
                v.put("talkerid", talkerId);
            }
            WechatClass.EnMicroMsg.rawInsert("message", "", v);
        }


        static long getNextMsgId() {
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery("SELECT max(msgId) FROM message", null);
            if (cursor == null || !cursor.moveToFirst())
                return -1;

            long id = cursor.getInt(0) + 1;
            cursor.close();
            return id;
        }


        static Cursor _getLastMsg(String username) {
            String query = "SELECT * FROM message WHERE msgId = (SELECT max(msgId) FROM message WHERE talker='" +
                    username + "')";
            return WechatClass.EnMicroMsg.rawQuery(query, null);
        }


        public static int getUnreadCount(String username) {
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery("select unReadCount from rconversation where " + "username = '" + username + "' and ( parentref is null or parentref = '' ) ", null);

            if (cursor == null || !cursor.moveToFirst())
                return 0;

            int cnt = cursor.getInt(cursor.getColumnIndex("unReadCount"));
            cursor.close();
            return cnt;
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////////
        public static ContentValues getFmessageConversationByWxid(String talker) {
            // note: fmessage_conversation 保存好友申请消息     /sqlite_select|select * from fmessage_conversation      /sqlite_select|select * from fmessage_msginfo
            String sql = "SELECT displayName, addScene, fmsgContent FROM fmessage_conversation WHERE talker = ?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{talker});
            try {
                if (cursor == null) {
                    XLog.e("getFmessageConversationByWxid rawQuery ret: null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("getFmessageConversationByWxid moveToFirst fail");
                    return null;
                }
                ContentValues contentvalues = new ContentValues();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    contentvalues.put(cursor.getColumnName(i), cursor.getString(i));
                }
                return contentvalues;
            } catch (Exception e) {
                XLog.e("getFmessageConversationByWxid error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getFmessageConversationByWxid null, talker:%d", talker);
            return null;
        }


        public static String getTalkerFromFmessagemsginfoByRowid(String rowid) {
            // rcontact 保存所有好友信息
            String sql = "SELECT talker FROM fmessage_msginfo WHERE rowid = ?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{rowid});
            try {
                if (cursor == null) {
                    XLog.e("getTalkerFromFmessagemsginfoByRowid rawQuery ret: null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("getTalkerFromFmessagemsginfoByRowid moveToFirst fail");
                    return null;
                }
                String talker_wxid = cursor.getString(0);
                return talker_wxid;
            } catch (Exception e) {
                XLog.e("getTalkerFromFmessagemsginfoByRowid error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            return null;
        }


        static int get_ImgInfo2Id_ByMsgSrvId(long msgSvrId) {
            String sql = "SELECT id FROM ImgInfo2 WHERE msgSvrId = ?";
            String args = String.valueOf(msgSvrId);
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{args});
            try {
                if (cursor == null) {
                    XLog.e("get_ImgInfo2Id_ByMsgSrvId rawQuery ret: null");
                    return -1;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("get_ImgInfo2Id_ByMsgSrvId moveToFirst fail");
                    return -1;
                }
                int id = cursor.getInt(0);
                return id;
            } catch (Exception e) {
                XLog.e("get_ImgInfo2Id_ByMsgSrvId error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            return -1;
        }


        static String get_ImgInfo2bigImgPath_ByMsgSrvId(long msgSvrId) {
            String sql = "SELECT bigImgPath FROM ImgInfo2 WHERE msgSvrId = ?";
            String args = String.valueOf(msgSvrId);
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{args});
            try {
                if (cursor == null) {
                    XLog.e("get_ImgInfo2bigImgPath_ByMsgSrvId rawQuery ret: null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("get_ImgInfo2bigImgPath_ByMsgSrvId moveToFirst fail");
                    return null;
                }
                String bigImgPath = cursor.getString(0);
                return bigImgPath;
            } catch (Exception e) {
                XLog.e("get_ImgInfo2bigImgPath_ByMsgSrvId error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            return null;
        }

        static JSONArray getAllChatroomMemberList() throws JSONException {
            /**
             chatroom表中的字段: chatroomnick 是自己的备注. (考虑不做取这个字段的逻辑了!!!!)
             rcontact表中的字段: nickname 是群的真实名字
             chatroom表中的字段: displayname 是成员名, 当上述2个字段都空的时候, 使用此字段显示.
             */
            JSONArray data_array = new JSONArray();
            // CREATE TABLE chatroom (  chatroomname TEXT default ''  PRIMARY KEY ,  addtime LONG,  memberlist TEXT,  displayname TEXT,  chatroomnick TEXT,  roomflag INTEGER,  roomowner TEXT,  roomdata BLOB,  isShowname INTEGER,  selfDisplayName TEXT,  style INTEGER,  chatroomdataflag INTEGER,  modifytime LONG,  chatroomnotice TEXT,  chatroomnoticeNewVersion INTEGER,  chatroomnoticeOldVersion INTEGER,  chatroomnoticeEditor TEXT,  chatroomnoticePublishTime LONG)
            String sql = "SELECT chatroomname, memberlist, displayname, chatroomnick, roomowner FROM chatroom";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, null);
            try {
                if (cursor == null) {
                    XLog.e("getAllChatrootMemberList FROM chatroom rawQuery ret: null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("getAllChatrootMemberList FROM chatroom moveToFirst fail. Maybe hasn't chatroom");
                    // 处理一个群组都没有的情况!!!
                    return new JSONArray();
                }
                int i = 0;
                XLog.i("AllChatrootMemberList length:%d", cursor.getCount());
                while (cursor.isAfterLast() == false) {
                    String room = cursor.getString(cursor.getColumnIndex("chatroomname"));  // roomid. 例如: 6414649690@chatroom
                    String roomowner = cursor.getString(cursor.getColumnIndex("roomowner"));    // 群主. 例如: wxid_xl35ygbrha7s22
                    String displayname = cursor.getString(cursor.getColumnIndex("displayname"));  //顿号分隔的昵称. 例如:杨嫣、小管家、邹宜静公主
                    String chatroomnick = cursor.getString(cursor.getColumnIndex("chatroomnick"));  //自改备注名. 例如: ''
                    String field_memberlist = cursor.getString(cursor.getColumnIndex("memberlist"));   // 例如: wxid_a2nwhvzwx9vf21;wxid_xl35ygbrha7s22;wxid_8r822730x9nl22
                    //int roomflag = cursor.getInt(cursor.getColumnIndex("roomflag"));  // 例如: 0. 目前没发现作用
                    XLog.i("getAllChatrootMemberList room:%s, roomowner:%s, chatroomnick:%s, memberlist:%s", room, roomowner, chatroomnick, field_memberlist);
                    /////////////////////////////////////////////////////////////////////////////////////////////////////
                    JSONObject members = new JSONObject();
                    int id = 1;
                    String _name = "", _alias = "";
                    if (!TextUtils.isEmpty(field_memberlist)) {
                        /** 当有成员wxid的时候, 才赋值该成员name和该成员alias */
                        String[] wxids = field_memberlist.split(WechatAction.FenHao);
                        ContentValues rcontact_row;
                        for (String wxid : wxids) {
                            // TODO
                            try {
                                _name = "";
                                _alias = "";
                                rcontact_row = Rcontact.getContactByWxid2(wxid);
                                if (rcontact_row == null) {
                                    XLog.w("not exist rcontact row, wxid:%s", wxid);
                                    continue;
                                }
                                String nickname = rcontact_row.getAsString("nickname"); //这里返回个人nickname， 等群变动sync回群成员自改名
                                String alias = rcontact_row.getAsString("alias");
                                if (nickname != null) {
                                    _name = nickname;
                                }
                                if (alias != null) {
                                    _alias = alias;
                                }
                            } finally {
                                JSONObject _member = new JSONObject();
                                _member.put("id", id);
                                _member.put("name", _name);
                                _member.put("alias", _alias);
                                members.put(wxid, _member);
                                id++;
                            }
                        }
                    }
                    /** 当群名为空的时候, 取群全部成员昵称作为群名. 一般情况下: chatroom表的chatroomnick都为空, 该字段是自改备注名 */
                    String roomnick = "";
                    if (displayname == null) {
                        roomnick = "None";
                    } else if (displayname.length() > 30) {
                        roomnick = displayname.substring(0, 30);
                    } else {
                        roomnick = displayname;
                    }
                    JSONObject one_room_dict = new JSONObject();
                    one_room_dict.put("room", room);
                    one_room_dict.put("roomowner", roomowner);
                    one_room_dict.put("chatroomnick", roomnick);
                    one_room_dict.put("members", members);
                    //one_room_dict.put("memberlist", field_memberlist);    // 以分号分隔的微信号
                    //one_room_dict.put("nicknamelist", nickname_list.toString());
                    //one_room_dict.put("aliaslist", alias_list.toString());
                    data_array.put(i++, one_room_dict);
                    /////////////////////////////////////////////////////////////////////////////////////////////////////
                    cursor.moveToNext();
                }
            } catch (Exception e) {
                XLog.e("getAllChatrootMemberList error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }

            /** 查rcontact, 判断群主是否有自定义群名. 群名准确的关键流程!! */
            for (int i = 0; i < data_array.length(); i++) {
                JSONObject one_room_dict = (JSONObject) data_array.get(i);
                String room = (String) one_room_dict.get("room");    //取得roomid
                sql = "SELECT nickname FROM rcontact WHERE username = ?";
                cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{room});
                try {
                    if (cursor == null) {
                        XLog.e("getAllChatrootMemberList FROM rcontact rawQuery ret: null");
                        return null;
                    }
                    if (!cursor.moveToFirst()) {
                        XLog.w("getAllChatrootMemberList FROM rcontact moveToFirst fail. Maybe hasn't rcontact");
                        continue;
                    }
                    String nickname = cursor.getString(cursor.getColumnIndex("nickname"));
                    if (TextUtils.isEmpty(nickname)) {
                        // 如果nickname是空, 则跳过
                        continue;
                    }
                    one_room_dict.put("chatroomnick", nickname);
                } catch (Exception e) {
                    XLog.e("getAllChatrootMemberList error. stack:%s", Log.getStackTraceString(e));
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
            return data_array;
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        static JSONObject getLatestNickname(String start_time, String end_time) throws JSONException {
            /**
             rcontact表中的字段:
             username是wxid
             nickname是昵称
             */
            // 返回格式如下: {"wxid_1":"nickname1", "wxid_2":"nickname2"}
            JSONObject jsonobj = new JSONObject();
            String sql = "SELECT username, nickname FROM rcontact WHERE ? <= myTime AND myTime < ?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{start_time, end_time});
            try {
                if (cursor == null) {
                    XLog.e("cursor is null, sql:%s, start_time:%s, end_time:%s", sql, start_time, end_time);
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("moveToFirst fail, sql:%s, start_time:%s, end_time:%s", sql, start_time, end_time);
                    return jsonobj;
                }
                while (cursor.isAfterLast() == false) {
                    String username = cursor.getString(cursor.getColumnIndex("username"));  // roomid或者wxid. 例如: 6414649690@chatroom 或 wxid_123
                    String nickname = cursor.getString(cursor.getColumnIndex("nickname"));  // 小头像url
                    if (!TextUtils.isEmpty(nickname)) {
                        jsonobj.put(username, nickname);
                    }
                    /////////////////////////////////////////////////////////////////////////////////////////////////////
                    cursor.moveToNext();
                }
                jsonobj.put(WechatHook.get_robot_info(MySync.g_robot_wxid) + WechatAction.DB_F + "lastupdatetime", end_time);
                return jsonobj;
            } catch (Exception e) {
                XLog.e("getLatestNickname error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getLatestNickname null");
            return null;
        }


        static JSONObject getLatestHeader(String start_time, String end_time) throws JSONException {
            /**
             img_flag表中的字段:
             username是wxid
             reserved1字段是大头像, 例如"http://wx.qlogo.cn/mmhead/ver_1/fQ3Z68dqRLuFiaIFhnPm9TiaAavK4ldvnC48IlDaibRpaL5bDHNnkZQBfherAllSFR0ibZticiaBQGUYiaCD0qYG4lZdwk4xKuQJar9ym8dZ6r0cgA/0"
             reserved2字段是小头像, 例如"http://wx.qlogo.cn/mmhead/ver_1/fQ3Z68dqRLuFiaIFhnPm9TiaAavK4ldvnC48IlDaibRpaL5bDHNnkZQBfherAllSFR0ibZticiaBQGUYiaCD0qYG4lZdwk4xKuQJar9ym8dZ6r0cgA/96"
             */
            // 返回格式如下: {"wxid_1":"url1", "wxid_2":"url2"}
            JSONObject jsonobj = new JSONObject();
            String sql = "SELECT username, reserved2 FROM img_flag WHERE ? <= lastupdatetime AND lastupdatetime < ?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{start_time, end_time});
            try {
                if (cursor == null) {
                    XLog.e("cursor is null, sql:%s, start_time:%s, end_time:%s", sql, start_time, end_time);
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.w("moveToFirst fail, sql:%s, start_time:%s, end_time:%s", sql, start_time, end_time);
                    return new JSONObject();
                }
                while (cursor.isAfterLast() == false) {
                    String username = cursor.getString(cursor.getColumnIndex("username"));  // roomid或者wxid. 例如: 6414649690@chatroom 或 wxid_123
                    String reserved2 = cursor.getString(cursor.getColumnIndex("reserved2"));  // 小头像url
                    if (!TextUtils.isEmpty(reserved2) && !reserved2.endsWith("@stranger")) {
                        jsonobj.put(username, reserved2);
                    }
                    /////////////////////////////////////////////////////////////////////////////////////////////////////
                    cursor.moveToNext();
                }
                jsonobj.put(WechatHook.get_robot_info(MySync.g_robot_wxid) + WechatAction.DB_F + "lastupdatetime", end_time);
                return jsonobj;
            } catch (Exception e) {
                XLog.e("getLatestHeader error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getLatestHeader null");
            return null;
        }


        static JSONObject getOneRoomHeader(String roomid) throws JSONException {
            /**
             chatroom表中的字段:
             memberlist是以逗号分隔的wxid, 例如 "antihurt;wxid_flbc7h82cm0d12"
             chatroomname是roomid, 例如"123456@chatroom"
             img_flag表中的字段:
             username是wxid
             reserved1字段是大头像, 例如"http://wx.qlogo.cn/mmhead/ver_1/fQ3Z68dqRLuFiaIFhnPm9TiaAavK4ldvnC48IlDaibRpaL5bDHNnkZQBfherAllSFR0ibZticiaBQGUYiaCD0qYG4lZdwk4xKuQJar9ym8dZ6r0cgA/0"
             reserved2字段是小头像, 例如"http://wx.qlogo.cn/mmhead/ver_1/fQ3Z68dqRLuFiaIFhnPm9TiaAavK4ldvnC48IlDaibRpaL5bDHNnkZQBfherAllSFR0ibZticiaBQGUYiaCD0qYG4lZdwk4xKuQJar9ym8dZ6r0cgA/96"
             */
            // 返回格式如下: {'123@chatroom': {"wxid_1":"url1", "wxid_2":"url2"} }
            JSONObject jsonobj = new JSONObject();
            String sql = "SELECT memberlist FROM chatroom WHERE chatroomname = ?";
            String sql2 = "SELECT reserved2 FROM img_flag WHERE username = ?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{roomid});
            try {
                if (cursor == null) {
                    XLog.e("cursor is null, sql:%s, chatroomname:%s", sql, roomid);
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("moveToFirst fail, sql:%s, chatroomname:%s", sql, roomid);
                    return jsonobj;
                }
                String field_memberlist = cursor.getString(cursor.getColumnIndex("memberlist"));
                //XLog.d("table chatroom rows. room:%s, memberlist:%s", roomid, field_memberlist);
                String[] wxids = field_memberlist.split(WechatAction.FenHao);
                JSONObject inner_obj = new JSONObject();
                for (String wxid : wxids) {
                    Cursor _cursor2 = WechatClass.EnMicroMsg.rawQuery(sql2, new String[]{wxid});
                    try {
                        if (_cursor2 == null) {
                            XLog.e("_cursor2 is null, sql:%s, wxid:%s", sql2, wxid);
                            return null;
                        }
                        if (!_cursor2.moveToFirst()) {
                            XLog.e("moveToFirst fail. sql:%s, wxid:%s", sql2, wxid);
                            continue;
                        }
                        String small_head = _cursor2.getString(_cursor2.getColumnIndex("reserved2"));
                        //XLog.d("table img_flag rows. wxid:%s, small_head:%s", wxid, small_head);
                        if (TextUtils.isEmpty(small_head)) {
                            continue;
                        }
                        inner_obj.put(wxid, small_head);
                    } finally {
                        if (_cursor2 != null) _cursor2.close();
                    }
                }
                jsonobj.put(roomid, inner_obj);
                return jsonobj;
            } catch (Exception e) {
                XLog.e("getOneRoomHeader error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getOneRoomHeader null");
            return null;
        }


        static Cursor _getMessageBySvrId(long msgSrvId) {
            String sql = "select * from message where msgsvrid=?";
            String args = String.valueOf(msgSrvId);
            return WechatClass.EnMicroMsg.rawQuery(sql, new String[]{args});
        }


        @Deprecated
        static ContentValues getChatroomRoomdata(String chatroomname) {
            String sql = "select roomdata from chatroom where chatroomname=?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{chatroomname});
            try {
                if (cursor == null) {
                    XLog.e("cursor is null, sql:%s, chatroomname:%s", sql, chatroomname);
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("moveToFirst fail, sql:%s, chatroomname:%s", sql, chatroomname);
                    return null;
                }
                return WechatClass.EnMicroMsg._foreach(cursor);
            } catch (Exception e) {
                XLog.e("getMessageByMsgId error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getChatroom no row, chatroomname:%s", chatroomname);
            return null;
        }


        static ContentValues getMessageByMsgId(long msgId) {
            ContentValues contentvalues = new ContentValues();
            String sql = "select msgId, msgSvrId, type, status, isSend, createTime, talker, content from message where msgId=?";
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{String.valueOf(msgId)});
            try {
                if (cursor == null) {
                    XLog.e("cursor is null, sql:%s, msgId:%d", sql, msgId);
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    XLog.e("moveToFirst fail, sql:%s, msgId:%d", sql, msgId);
                    return null;
                }
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    contentvalues.put(cursor.getColumnName(i), cursor.getString(i));
                }
                return contentvalues;
            } catch (Exception e) {
                XLog.e("getMessageByMsgId error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
            XLog.e("getMessageByMsgId null, msgid:%d", msgId);
            return null;
        }


        static class Oplog2 {
            static void call_insert(Object row_obj) {
                /** 参考  修改昵称.txt        667
                 * 返回 set_selfname_inroom 中, 定位函数最后一句
                 * invoke-static {}, Lcom/tencent/mm/s/c;->wr()Lcom/tencent/mm/plugin/messenger/foundation/a/a/d;
                 * invoke-interface {v0, v1}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/d;->b(Lcom/tencent/mm/plugin/messenger/foundation/a/a/e$b;)V
                 * ==========>
                 * nvoke-static {}, Lcom/tencent/mm/model/c;->FQ()Lcom/tencent/mm/plugin/messenger/foundation/a/a/g;
                 * invoke-interface {v0, v1}, Lcom/tencent/mm/plugin/messenger/foundation/a/a/g;->b(Lcom/tencent/mm/plugin/messenger/foundation/a/a/h$b;)V
                 */
                Object table_obj = callStaticMethod(WechatClass.get("com/tencent/mm/model/c", "AccountStorage"), "FQ");
                callMethod(table_obj, "b", row_obj);
            }
        }


        static class Rcontact {
            static Object call_select(String wxid) {
                /** note 根据上一层的 _save_singleroom_to_contact 函数处查看 */
                /** 搜索: "contact == null !!!"        向上寻找           667
                 * invoke-static {}, Lcom/tencent/mm/s/c;->ws()Lcom/tencent/mm/storage/at;
                 * invoke-interface {v0, v1}, Lcom/tencent/mm/storage/at;->QA(Ljava/lang/String;)Lcom/tencent/mm/storage/x;
                 * ========>
                 * invoke-static {}, Lcom/tencent/mm/model/c;->FR()Lcom/tencent/mm/storage/ay;
                 * invoke-interface {v0, v1}, Lcom/tencent/mm/storage/ay;->Yg(Ljava/lang/String;)Lcom/tencent/mm/storage/ab;
                 */
                Object table_obj = callStaticMethod(WechatClass.get("com/tencent/mm/model/c", "AccountStorage"), "FR");
                return callMethod(table_obj, "Yg", wxid);
            }

            static Object get_field(Object row_obj, String field_name) {
                return getObjectField(row_obj, field_name);
            }

            static Cursor getContactByWxid(String username) {
                // rcontact 保存所有好友信息
                String sql = "SELECT alias, conRemark, nickname, type FROM rcontact WHERE username = ?";
                return WechatClass.EnMicroMsg.rawQuery(sql, new String[]{username});
            }

            static ContentValues getContactByWxid2(String wxid) {
                ContentValues contentvalues = new ContentValues();
                String sql = "SELECT alias, conRemark, nickname, type FROM rcontact WHERE username = ?";
                Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, new String[]{wxid});
                try {
                    if (cursor == null) {
                        XLog.e("cursor is null, sql:%s, wxid:%s", sql, wxid);
                        return null;
                    }
                    if (!cursor.moveToFirst()) {
                        XLog.e("moveToFirst fail, sql:%s, wxid:%s", sql, wxid);
                        return null;
                    }
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        //if ( cursor.getType(i) == Cursor.FIELD_TYPE_BLOB )
                        contentvalues.put(cursor.getColumnName(i), cursor.getString(i));
                    }
                    return contentvalues;
                } catch (Exception e) {
                    XLog.e("getContactByWxid2 error. stack:%s", Log.getStackTraceString(e));
                } finally {
                    if (cursor != null) cursor.close();
                }
                XLog.e("getContactByWxid2 null");
                return null;
            }
        }


    public static ClassLoader _loader = null;
    static ConcurrentHashMap< String, Class<?> > _class_map = new ConcurrentHashMap<>();
    static Class<?> get(String classname_in_smali, String...note){
        Class<?> _class = _class_map.get(classname_in_smali);
        if( _class == null ){
//            XLog.d("findClass, name:%s", classname_in_smali);
            String real_classname = WechatClass.replace_slash(classname_in_smali);
            _class = findClass(real_classname, _loader);
            _class_map.put(classname_in_smali, _class);
        }
        return _class;
    }


    static String replace_slash(String classname_in_smali){
        // note 传入参数为 com/tencent/mm/a  转换为 com.tencent.mm.a
        return classname_in_smali.replace("/", ".");
    }


    static Class<?> load(String classname_in_smali, ClassLoader cl){
        Class<?> _class = _class_map.get(classname_in_smali);
        if( _class == null ){
//            XLog.d("findClass, name:%s", classname_in_smali);
            String real_classname = classname_in_smali.replace("/", ".");   // note 传入参数为 com/tencent/mm/a  转换为 com.tencent.mm.a
            _class = findClass(real_classname, cl);
            _class_map.put(classname_in_smali, _class);
        }
        return _class;
    }
}

