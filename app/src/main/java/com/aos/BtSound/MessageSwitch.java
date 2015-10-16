package com.aos.BtSound;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * Created by collin on 2015-10-11.
 */

public class MessageSwitch extends Activity {
    private Switch mSwitch = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_switch);
        mSwitch = (Switch) findViewById(R.id.sw_read_message);
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    VoiceCellApplication.isReadSMS = true;
                } else {
                    VoiceCellApplication.isReadSMS = false;
                }
            }
        });
    }
}
