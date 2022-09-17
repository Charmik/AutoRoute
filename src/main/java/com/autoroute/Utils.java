package com.autoroute;

import java.sql.Date;

public class Utils {

    public static Integer parseInteger(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Date SQLnow() {
        return new Date(System.currentTimeMillis());
    }

}
