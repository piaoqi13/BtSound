package com.aos.BtSound.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Created by linky on 15-8-22.
 */
public class MyPreference {

    private SharedPreferences mSharedPreferences;

    public MyPreference(Context context, String file) {
        mSharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    public void putBoolean(String name, boolean value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(name, value);
        editor.commit();
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        return mSharedPreferences.getBoolean(name, defaultValue);
    }

}
