package com.aos.BtSound.util;

import android.content.Context;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 类名：FucUtil.java
 * 注释：读取asset目录下文件
 * 日期：2015年8月5日
 * 作者：王超
 */
public class FucUtil {
    public static String readFile(Context mContext, String file, String code) {
        int len = 0;
        byte[] buf = null;
        String result = "";
        try {
            InputStream in = mContext.getAssets().open(file);
            len = in.available();
            buf = new byte[len];
            in.read(buf, 0, len);
            result = new String(buf, code);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String getNumber(String str) {
        String regEx="[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }

    public static boolean isAvailableMobilePhone(String number) {
        String str = "^((13[0-9])|147|(15[0-9])|(18[0-9])|(17[0-9]))\\d{8}$";
        Pattern pattern = Pattern.compile(str);
        Matcher matcher = pattern.matcher(number);
        return matcher.matches();
    }
}
