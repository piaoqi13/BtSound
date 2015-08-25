package com.aos.BtSound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.MyPreference;

/**
 * Created by linky on 15-8-16.
 */
public class SlashActivity extends Activity {

    private Context mContext;
    private Handler mHandler = new Handler();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.slash_layout);

        final MyPreference preference = new MyPreference(this, Config.Filename.FIRST_OPEN);
        final boolean isFirstTime = preference.getBoolean(Config.Keyname.IS_FIRST_TIME, true);

        mContext = this;
        mHandler.postDelayed(new Runnable() {
            public void run() {
                Intent in = new Intent();
                in.setClass(mContext, MainActivity.class);
                if(isFirstTime) {
                    in.putExtra(Config.Keyname.IS_FIRST_TIME, true);
                    preference.putBoolean(Config.Keyname.IS_FIRST_TIME, false);
                }
                startActivity(in);
                finish();
            }
        }, 2000);
    }
}
