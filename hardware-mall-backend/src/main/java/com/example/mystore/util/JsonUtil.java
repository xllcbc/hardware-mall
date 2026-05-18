package com.example.mystore.util;

import java.util.Map;

public class JsonUtil {
    
    public static String toJson(Object obj) {
        return com.alibaba.fastjson2.JSON.toJSONString(obj);
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String json) {
        return com.alibaba.fastjson2.JSON.parseObject(json, Map.class);
    }
}
