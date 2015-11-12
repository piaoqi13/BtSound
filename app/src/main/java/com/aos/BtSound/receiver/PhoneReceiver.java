package com.aos.BtSound.receiver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.aos.BtSound.R;
import com.aos.BtSound.VoiceCellApplication;
import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.setting.TtsSettings;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.cloud.util.ResourceUtil.RESOURCE_TYPE;

/**
 * 类名：PhoneReceiver.java
 * 注释：监听来电广播
 * 日期：2015年8月19日
 * 作者：王超
 */
public class PhoneReceiver extends BroadcastReceiver {
    private Context mContext = null;
    private SpeechSynthesizer mTts = null;
    private SharedPreferences mSharedPreferences = null;
    public static String voicerCloud = "xiaoyan";
    public static String voicerLocal = "xiaoyan";
    private int mPercentForBuffering = 0;
    private int mPercentForPlaying = 0;
    private Toast mToast = null;
    private String tip = "";

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 4444:
                    setParam();
                    int code = mTts.startSpeaking(tip, mTtsListener);
                    if (code != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code);
                    }
                    break;
            }
        }
    };

    private BluetoothHeadsetUtils mBlueHelper;

    public PhoneReceiver(BluetoothHeadsetUtils bleHelper) {
        mBlueHelper = bleHelper;
    }

    @SuppressLint("ShowToast")
    @Override
    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(context, mTtsInitListener);
        mToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
        mSharedPreferences = context.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            DebugLog.i("CollinWang", "拨打号码=" + phoneNumber);
        } else {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            AudioManager audio = (AudioManager) mContext.getSystemService(Service.AUDIO_SERVICE);
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    DebugLog.i("CollinWang", "挂断");
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    DebugLog.i("CollinWang", "接听");
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    String name = "";
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (incomingNumber.equals(VoiceCellApplication.mContacts.get(i).getPhoneNumber().replace(" ", ""))) {
                            name = VoiceCellApplication.mContacts.get(i).getName();
                            DebugLog.i("CollinWang", "name=" + name);
                            break;
                        }
                    }

                    DebugLog.i("CollinWang", "来电号码=" + incomingNumber);
                    DebugLog.i("CollinWang", "名字=" + name);

                    if (name.equals("")) {
                        tip = "请注意号码" + incomingNumber + "正在呼叫您";
                    } else {
                        tip = "请注意" + name + "正在呼叫您";
                    }

                    mHandler.sendEmptyMessageDelayed(4444, 1500);
                    break;
            }
        }
    };

    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            DebugLog.d("CollinWang", "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code);
            }
        }
    };

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    private void setParam() {
        mTts.setParameter(SpeechConstant.PARAMS, null);
        if (VoiceCellApplication.mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerCloud);
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mTts.setParameter(ResourceUtil.TTS_RES_PATH, getResourcePath());
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerLocal);
        }
        mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "50"));
        mTts.setParameter(SpeechConstant.PITCH, mSharedPreferences.getString("pitch_preference", "50"));
        mTts.setParameter(SpeechConstant.VOLUME, mSharedPreferences.getString("volume_preference", "50"));
        mTts.setParameter(SpeechConstant.STREAM_TYPE, mSharedPreferences.getString("stream_preference", "3"));
    }

    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, RESOURCE_TYPE.assets, "tts/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, RESOURCE_TYPE.assets, "tts/" + PhoneReceiver.voicerLocal + ".jet"));
        return tempBuffer.toString();
    }

    private SynthesizerListener mTtsListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {
            showTip("开始播放");
        }

        @Override
        public void onSpeakPaused() {
            showTip("暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            showTip("继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos, String info) {
            mPercentForBuffering = percent;
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            mPercentForPlaying = percent;
            showTip(String.format(mContext.getString(R.string.tts_toast_format), mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                showTip("播放完成");
            } else if (error != null) {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
        }
    };

}
