package com.aos.BtSound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;

/**
 * Created by linky on 15-8-16.
 */
public class SlashActivity extends Activity {
    private Context mContext = null;
    private boolean isFirstTime = true;
    private Handler mHandler = new Handler();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.slash_layout);
        mContext = this;
        // 是否第一次启动
        isFirstTime = Settings.getBoolean(Config.IS_FIRST_TIME, true, false);
        mHandler.postDelayed(new Runnable() {
            public void run() {
                Intent in = new Intent();
                in.setClass(mContext, MainActivity.class);
                if(isFirstTime) {
                    in.putExtra(Config.IS_FIRST_TIME, true);
                    Settings.setBoolean(Config.IS_FIRST_TIME, true, false);
                }
                startActivity(in);
                finish();
            }
        }, 2000);
    }

}
