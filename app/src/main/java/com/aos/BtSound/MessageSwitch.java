package com.aos.BtSound;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;

/**
 * Created by collin on 2015-10-11.
 */
public class MessageSwitch extends Activity implements View.OnClickListener {
    private Button mBtnClosed = null;
    private Button mBtnOpen = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_switch);
        initView();
        initData();
        initListener();
    }

    private void initView() {
        mBtnClosed = (Button) findViewById(R.id.btn_closed);
        mBtnOpen = (Button) findViewById(R.id.btn_open);
    }

    private void initData() {
        Settings.setBoolean(Config.IS_READ_SMS, true, false);
        mBtnOpen.setTextColor(this.getResources().getColor(R.color.gray_text));
        mBtnOpen.setEnabled(false);
        mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.green_background));
        mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.white_text));
        mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
    }

    private void initListener() {
        mBtnClosed.setOnClickListener(this);
        mBtnOpen.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_closed:
                Settings.setBoolean(Config.IS_READ_SMS, false, false);
                mBtnOpen.setTextColor(this.getResources().getColor(R.color.black_text));
                mBtnClosed.setEnabled(false);
                mBtnOpen.setEnabled(true);
                mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.white_text));
                mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.green_background));
                mBtnClosed.setTextColor(this.getResources().getColor(R.color.gray_text));
                Toast.makeText(this, "已关闭", Toast.LENGTH_LONG).show();
                break;
            case R.id.btn_open:
                Settings.setBoolean(Config.IS_READ_SMS, true, false);
                mBtnOpen.setTextColor(this.getResources().getColor(R.color.gray_text));
                mBtnOpen.setEnabled(false);
                mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.green_background));
                mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.white_text));
                mBtnClosed.setEnabled(true);
                mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
                Toast.makeText(this, "已打开", Toast.LENGTH_LONG).show();
                break;
        }
    }
}
