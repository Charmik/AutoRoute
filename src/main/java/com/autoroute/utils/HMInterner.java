package com.autoroute.utils;

import java.util.HashMap;
import java.util.Map;

public class HMInterner {

    public static HMInterner INTERNER = new HMInterner();

    private final Map<String, String> map;

    public HMInterner() {
        map = new HashMap<>();
    }

    public String intern(String s) {
        String exist = map.putIfAbsent(s, s);
        return (exist == null) ? s : exist;
    }
}
