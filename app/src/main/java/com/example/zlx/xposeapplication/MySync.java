package com.example.zlx.xposeapplication;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MySync {
    public static String wxid="", alias="", nickname="", email="";
    public static int qq=-1;
    public static long currentTimeMillis = 0;       // note 该变量用于插件认证通过后, 控制插件是否能功能的关键
    public static String g_robot_wxid = "";         // note 该变量用于插件认证通过后, 控制插件是否能功能的关键

    static Map msg2downlaod = Collections.synchronizedMap( new LinkedHashMap<String, Object>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 200;
        }
    });
    static Map sendid2luckymoney = Collections.synchronizedMap( new LinkedHashMap<String, Object>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    });
    /**
    private static LinkedHashMap<String, Object> msg2downlaod = new LinkedHashMap<String, Object>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 200;
        }
    };
    static Object get_msg2downlaod(String key) {
        synchronized(msg2downlaod){
            return msg2downlaod.get(key);
        }
    }
    static void put_msg2downlaod(String key, Object value) {
        synchronized(msg2downlaod){
            msg2downlaod.put(key, value);
        }
    }
     */
    static Map filename2uploadvoice = Collections.synchronizedMap( new LinkedHashMap<String, Object>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 200;
        }
    });

    static Map filename2uploadvideo_task = new ConcurrentHashMap<>();
    static Map filename2uploadvoice_task = new ConcurrentHashMap<>();

    static Map msgid2card_cc = Collections.synchronizedMap( new LinkedHashMap<Long, Object>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 200;
        }
    });

    static volatile String hb_password = null;     // note 线程安全三种方法: volatile, synchronized, AtomicReference. 查看为知笔记: Java.md
}