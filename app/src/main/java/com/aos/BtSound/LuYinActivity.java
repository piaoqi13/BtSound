package com.aos.BtSound;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.receiver.PhoneReceiver;
import com.aos.BtSound.recorder.MyMediaRecorder;
import com.aos.BtSound.setting.TtsSettings;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;

public class LuYinActivity extends Activity implements View.OnClickListener {
    private ImageView mIvState = null;
    private TextView mTvState = null;
    private ImageView mIvBack = null;


    private MyMediaRecorder mMyMediaRecorder;                   // 录音器
    private boolean mWantToRecord;                              // 是否录音标识
    private boolean isRecording = false;                        // 是否正在录音

    private Context mContext = null;
    private SpeechSynthesizer mTts = null;
    private SharedPreferences mSharedPreferences = null;
    public static String voicerCloud = "xiaoyan";
    public static String voicerLocal = "xiaoyan";
    private int mPercentForBuffering = 0;
    private int mPercentForPlaying = 0;
    private Toast mToast = null;

    private String mEngineType = SpeechConstant.TYPE_CLOUD;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1://结束
                    isRecording = false;
                    mTvState.setText("停止录音");
                    mIvState.setBackgroundResource(R.drawable.luyin_stop);
                    // 停止录音；
                    mMyMediaRecorder.stopRecording();
                    mWantToRecord = false;
                    break;
                case 2://开始
                    isRecording = true;
                    mTvState.setText("正在录音");
                    mIvState.setBackgroundResource(R.drawable.luyin_start);
                    mMyMediaRecorder = new MyMediaRecorder();
                    mMyMediaRecorder.startRecording();
                    mHandler.sendEmptyMessageDelayed(1, 60 * 60 * 1000);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showTip("正在录音，60分钟后自动结束");
                        }
                    });
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_luyin);
        initView();
        setListener();
        setData();
    }

    private void initView() {
        mIvState = (ImageView) findViewById(R.id.iv_state);
        mIvBack = (ImageView) findViewById(R.id.iv_back);
        mTvState = (TextView) findViewById(R.id.tv_state);
    }

    private void setListener() {
        mIvState.setOnClickListener(this);
        mIvBack.setOnClickListener(this);
    }

    private void setData() {
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mSharedPreferences = this.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        setParam();
        int code = mTts.startSpeaking("开始语音记事，请说话", mTtsListener);
        if (code != ErrorCode.SUCCESS) {
            showTip("语音合成失败,错误码: " + code);
        } else {
            DebugLog.i("CollinWang", "code=" + code);
        }
        mHandler.sendEmptyMessage(2);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_state:
                // ToDo
                // CollinWang2015.12.15
                if (isRecording) {
                    mHandler.sendEmptyMessage(1);
                } else {
                    mWantToRecord = true;
                }
                break;
            case R.id.iv_back:
                finish();
                break;
        }
    }

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
        } else if (VoiceCellApplication.mEngineType.equals(SpeechConstant.TYPE_LOCAL)) {
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
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, ResourceUtil.RESOURCE_TYPE.assets, "tts/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, ResourceUtil.RESOURCE_TYPE.assets, "tts/" + PhoneReceiver.voicerLocal + ".jet"));
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
                if (error.getErrorCode() == 20001) {
                    showTip("当前是试用版不支持离线功能噢");
                } else {
                    showTip("错误码=" + error.getErrorCode());
                }
                Log.i("CollinWang", "error=" + error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
        }
    };
}
