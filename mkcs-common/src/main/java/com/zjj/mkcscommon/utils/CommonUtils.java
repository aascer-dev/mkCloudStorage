package com.zjj.mkcscommon.utils;

import java.util.UUID;

public class CommonUtils {
    public static String generateUUID()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
