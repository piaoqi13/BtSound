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
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.aos.BtSound.R;
import com.aos.BtSound.VoiceCellApplication;
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
    // 语音合成对象
    private SpeechSynthesizer mTts = null;
    // 存储对象
    private SharedPreferences mSharedPreferences = null;
    // 默认云端发音人
    public static String voicerCloud = "xiaoyan";
    // 默认本地发音人
    public static String voicerLocal = "xiaoyan";
    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;
    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;
    // 吐司提示
    private Toast mToast = null;

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
                    //audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

                    audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamVolume(2), AudioManager.FLAG_PLAY_SOUND);
                    DebugLog.i("CollinWang", "挂断");
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    //audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamVolume(2), AudioManager.FLAG_PLAY_SOUND);
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

                    String tip = "";
                    if (name.equals("")) {
                        tip = "请注意号码" + incomingNumber + "正在呼叫您";
                    } else {
                        tip = "请注意" + name + "正在呼叫您" + "号码是" + incomingNumber;
                    }
                    setParam();// 设置参数
                    int code = mTts.startSpeaking(tip, mTtsListener);
                    if (code != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code);
                    }

                    //audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamVolume(0), AudioManager.FLAG_PLAY_SOUND);
                    // 获取音量
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_RING);
                    int current = audio.getStreamVolume(AudioManager.STREAM_RING);
                    DebugLog.i("CollinWang", "来电max=" + max + "；current=" + current);
                    break;
            }
        }
    };

    // 初始化监听
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

    // 设置属性
    private void setParam() {
        mTts.setParameter(SpeechConstant.PARAMS, null);
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
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

    // 获取发音人资源路径
    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, RESOURCE_TYPE.assets, "tts/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, RESOURCE_TYPE.assets, "tts/" + PhoneReceiver.voicerLocal + ".jet"));
        return tempBuffer.toString();
    }

    // 合成回调监听
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
            // ToDo
        }
    };

}
