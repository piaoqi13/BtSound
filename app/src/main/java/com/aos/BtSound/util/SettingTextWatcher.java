package com.aos.BtSound.util;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Toast;

import java.util.regex.Pattern;

/**
 * 类名：SettingTextWatcher.java
 * 注释：输入框输入范围控制
 * 日期：2015年8月5日
 * 作者：王超
 */
public class SettingTextWatcher implements TextWatcher {
    private int mEditStart;
    private int mEditCount;
    private EditTextPreference mEditTextPreference;
    private int minValue;
    private int maxValue;
    private Context mContext;

    public SettingTextWatcher(Context context, EditTextPreference e, int min, int max) {
        mContext = context;
        mEditTextPreference = e;
        minValue = min;
        maxValue = max;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mEditStart = start;
        mEditCount = count;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (TextUtils.isEmpty(s)) {
            return;
        }
        String content = s.toString();
        if (isNumeric(content)) {
            int num = Integer.parseInt(content);
            if (num > maxValue || num < minValue) {
                s.delete(mEditStart, mEditStart + mEditCount);
                mEditTextPreference.getEditText().setText(s);
                Toast.makeText(mContext, "超出有效值范围", Toast.LENGTH_SHORT).show();
            }
        } else {
            s.delete(mEditStart, mEditStart + mEditCount);
            mEditTextPreference.getEditText().setText(s);
            Toast.makeText(mContext, "只能输入数字哦", Toast.LENGTH_SHORT).show();
        }
    }

    // 判断是否为数字
    public static boolean isNumeric(String str) {
        Pattern pattern = Pattern.compile("[0-9]*");
        return pattern.matcher(str).matches();
    }

};
