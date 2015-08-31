package com.aos.BtSound.util;

import android.content.Context;

import java.io.InputStream;

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
}
