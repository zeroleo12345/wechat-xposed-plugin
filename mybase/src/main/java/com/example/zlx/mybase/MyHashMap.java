package com.example.zlx.mybase;

import java.util.HashMap;

public class MyHashMap extends HashMap {
    public static Object get_or_default(HashMap hashMap, Object key, Object defaultValue) {
        return hashMap.containsKey(key) ? hashMap.get(key) : defaultValue;
    }
}
