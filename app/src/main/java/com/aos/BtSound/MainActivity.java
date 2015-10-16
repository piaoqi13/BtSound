package com.aos.BtSound;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.contact.ObtainContactsUtil;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;
import com.aos.BtSound.receiver.SMSReceiver;
import com.aos.BtSound.setting.IatSettings;
import com.aos.BtSound.util.FucUtil;
import com.aos.BtSound.util.JsonParser;
import com.aos.BtSound.util.XmlParser;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * created by collin on 2015-08-05.
 * modify by collin on 2015-10-15.
 */

public class MainActivity extends Activity implements OnClickListener {
    private final String mPageName = "MainActivity";    // 界面标识
    private Context mContext = null;                    // 上下文
    private Button mBtnTakeCall = null;                 // 打电话按钮
    private Button mBtnSendMessages = null;             // 发短信按钮
    private Button mBtnTakePicture = null;              // 拍照按钮
    private Button mBtnWeb = null;                      // 官网按钮
    private Button mBtnInstruction = null;              // 说明按钮
    private EditText mEdtTransformResult = null;        // 识别显示控件

    private AudioManager mAudioManager = null;          // 音频管理类
    private BluetoothAdapter mBluetoothAdapter = null;  // 蓝牙适配器
    private SoundPool mSoundPool = null;
    private boolean mIsStarted = false;

    private Vibrator mVibrator = null;                  // 唤醒震动

    private int mStartCount = 0;                        // 尝试打开蓝牙麦克风次数；
    private boolean mIsWakeUpStarted = false;           // 跟踪语音唤醒是否开启
    private BluetoothHelper mBluetoothHelper;           // 蓝牙辅助类

    private ContentObserver mContentObserver = null;

    private SpeechRecognizer mAsr = null;               // 语音识别对象
    private Toast mToast = null;                        // 吐司提示

    private SharedPreferences mSharedPreferences;       // 存储配置
    private String mLocalGrammar = null;                // 本地语法文件
    private String mLocalLexicon = null;                // 本地词典
    private String mCloudGrammar = null;                // 云端语法文件
    // 本地语法构建路径
    private String grmPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/test";
    private String mResultType = "json";                // Result结果格式支持XML和JSON

    private final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private final String GRAMMAR_TYPE_ABNF = "abnf";
    private final String GRAMMAR_TYPE_BNF = "bnf";

    private String mEngineType = "local";               // 识别方式云端和本地
    private String mContent;                            // 语法、词典临时变量
    private int ret = 0;                                // 函数回值

    private String mLocalGrammarID;                     // 本地语法id
    private VoiceWakeuper mIvw = null;                  // 语音唤醒对象
    private String resultString = null;                 // 唤醒结果内容
    private TextView tvThresh = null;                   // 设置门限值
    private final static int MAX = 60;
    private final static int MIN = -10;
    private int curThresh = MIN;
    private String threshStr = "门限值：";

    private SpeechUnderstander mSpeechUnderstander;     // 语义理解对象（语音到语义）

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (!mIsStarted) {
                        showSoundHint();
                    }
                    break;
                case 1:
                    showTip("启动蓝牙麦克风失败");
                    mAudioManager.stopBluetoothSco();
                    break;
                case 2:
                    mSoundPool.release();
                    break;
                case 4444:
                    upDateDictionary();
                    break;
                case 6666:
                    startWakeUp();
                    break;
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        initView();
        initData();
        setListener();
    }

    private void initData() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mBluetoothHelper = new BluetoothHelper(this);

        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);
        // 初始化语法、命令词
        mLocalLexicon = "王超\n刘雄斌\n蔡哥\n";
        mLocalGrammar = FucUtil.readFile(this, "call.bnf", "utf-8");
        mCloudGrammar = FucUtil.readFile(this, "grammar_sample.abnf", "utf-8");
        // 获取联系人、本地更新词典时使用
        ContactManager mgr = ContactManager.createManager(this, mContactListener);
        mgr.asyncQueryAllContactsName();
        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();
        UmengUpdateAgent.update(this);
        mContentObserver = new SMSReceiver(mHandler, this);
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, mContentObserver);

        buildGrammar();
        mHandler.sendEmptyMessageDelayed(4444, 1000);

        // 语音唤醒resPath为本地识别资源路径
        StringBuffer param = new StringBuffer();
        String resPath = ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "ivw/" + getString(R.string.app_id) + ".jet");
        param.append(ResourceUtil.IVW_RES_PATH + "=" + resPath);
        param.append("," + ResourceUtil.ENGINE_START + "=" + SpeechConstant.ENG_IVW);
        boolean ret = SpeechUtility.getUtility().setParameter(ResourceUtil.ENGINE_START, param.toString());
        if (!ret) {
            DebugLog.d(DebugLog.TAG, "启动本地引擎未成功!");
        }
        mIvw = VoiceWakeuper.createWakeuper(this, null);

        mSpeechUnderstander = SpeechUnderstander.createUnderstander(this, speechUnderstanderListener);
    }

    private void initView() {
        mBtnTakeCall = (Button) findViewById(R.id.btn_take_call);
        mBtnSendMessages = (Button) findViewById(R.id.btn_send_messages);
        mBtnTakePicture = (Button) findViewById(R.id.btn_navigation);
        mBtnWeb = (Button) findViewById(R.id.btn_web);
        mBtnInstruction = (Button) findViewById(R.id.settings);
        mEdtTransformResult = (EditText) findViewById(R.id.edt_result_text);

        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        if (Settings.getBoolean(Config.IS_FIRST_TIME, true, false)) {
            //for future
        }
    }

    private void setListener() {
        mBtnTakeCall.setOnClickListener(this);
        mBtnSendMessages.setOnClickListener(this);
        mBtnTakePicture.setOnClickListener(this);
        mBtnWeb.setOnClickListener(this);
        mBtnInstruction.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(mPageName);
        MobclickAgent.onResume(this);
        mBluetoothHelper.start();
        mHandler.sendEmptyMessageDelayed(6666, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAsr.cancel();
        mIvw = VoiceWakeuper.getWakeuper();
        if (mIvw != null) {
            mIvw.destroy();
        } else {
            showTip("语音唤醒未初始化");
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        switch (v.getId()) {
            case R.id.btn_take_call:
                mIvw.stopListening();
                if (!setParam()) {
                    showTip("请构建语法再语音识别");
                    return;
                }
                ret = mAsr.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("识别未成功，错误码: " + ret);
                }
                break;
            case R.id.btn_send_messages:
                intent = new Intent(MainActivity.this, MessageSwitch.class);
                MainActivity.this.startActivity(intent);
                break;
            case R.id.btn_navigation:
                intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                MainActivity.this.startActivity(intent);
                break;
            case R.id.btn_web:
                openWeb();
                break;
            case R.id.settings:
                intent = new Intent(MainActivity.this, InstructionActivity.class);
                startActivity(intent);
                break;
        }
    }

    private void buildGrammar() {
        mContent = new String(mLocalGrammar);
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        ret = mAsr.buildGrammar(GRAMMAR_TYPE_BNF, mContent, grammarListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("语法构建未成功");
        }
    }

    private void upDateDictionary() {
        mContent = new String(mLocalLexicon);
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        mAsr.setParameter(SpeechConstant.GRAMMAR_LIST, "call");
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mAsr.updateLexicon("contact", mContent, lexiconListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("更新词典未成功");
        }
        Log.i("CollinWang","mContent=" + mContent);
    }

    public boolean setParam() {
        boolean result = false;
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        if ("cloud".equalsIgnoreCase(mEngineType)) {
            String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
            if (TextUtils.isEmpty(grammarId)) {
                result = false;
            } else {
                mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
                mAsr.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
                result = true;
            }
        } else {
            mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
            mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
            mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "40");
            result = true;
        }
        return result;
    }

    private void startSpeechUnderstander() {
        setSpeechUnderstanderParam();
        if(mSpeechUnderstander.isUnderstanding()){
            mSpeechUnderstander.stopUnderstanding();
        }else {
            ret = mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
            if(ret != 0){
                showTip("语义理解未成功，错误码:"	+ ret);
            }else {
                showTip(getString(R.string.text_begin));
            }
        }
    }

    private void startWakeUp() {
        mIvw = VoiceWakeuper.getWakeuper();
        if (mIvw != null) {
            mIvw.setParameter(SpeechConstant.PARAMS, null);
            mIvw.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
            mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:" + curThresh);
            mIvw.setParameter(SpeechConstant.IVW_SST, "oneshot");
            mIvw.setParameter(SpeechConstant.RESULT_TYPE, "json");
            mIvw.setParameter(SpeechConstant.KEEP_ALIVE, "1");
            mIvw.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");

            mIvw.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            mIvw.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            mIvw.startListening(mWakeuperListener);
        } else {
            showTip("语音唤醒未初始化");
        }
    }

    private void showSoundHint() {
        mIsStarted = true;
        mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        mSoundPool.load(this, R.raw.bootaudio, 1);
        mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    playSound(sampleId);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            // release sound
                            mHandler.obtainMessage(2).sendToTarget();
                            mIsStarted = false;
                        }
                    }, 4000);
                }
            }
        });
    }

    private void playSound(int id) {
        mSoundPool.play(id, 0.5f, 0.5f, 1, 1, 1f);
    }

    private void openWeb() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.aossh.com"));
        startActivity(intent);
    }

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/common.jet"));
        return tempBuffer.toString();
    }

    public void setSpeechUnderstanderParam(){
        String lag = mSharedPreferences.getString("understander_language_preference", "mandarin");
        if (lag.equals("en_us")) {
            mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "en_us");
        }else {
            mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            mSpeechUnderstander.setParameter(SpeechConstant.ACCENT,lag);
        }
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("understander_vadbos_preference", "4000"));
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("understander_vadeos_preference", "1000"));
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("understander_punc_preference", "1"));
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/iflytek/wavaudio.pcm");
    }

    private InitListener speechUnderstanderListener = new InitListener() {
        @Override
        public void onInit(int code) {
            DebugLog.d(DebugLog.TAG, "speechUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败未成功，错误码："+code);
            }
        }
    };

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            DebugLog.d(DebugLog.TAG, "SpeechRecognizer Init Code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败未成功，错误码：" + code);
            }
        }
    };

    private LexiconListener lexiconListener = new LexiconListener() {
        @Override
        public void onLexiconUpdated(String lexiconId, SpeechError error) {
            if (error == null) {
                DebugLog.d(DebugLog.TAG, "词典更新成功");
            } else {
                showTip("词典更新未成功，错误码：" + error.getErrorCode());
            }
        }
    };

    private GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    if (!TextUtils.isEmpty(grammarId))
                        editor.putString(KEY_GRAMMAR_ABNF_ID, grammarId);
                    editor.commit();
                }
                DebugLog.d(DebugLog.TAG, "语法构建成功");
            } else {
                showTip("语法构建未成功，错误码：" + error.getErrorCode());
            }
        }
    };

    private ContactListener mContactListener = new ContactListener() {
        @Override
        public void onContactQueryFinish(String contactInfos, boolean changeFlag) {
            mLocalLexicon = contactInfos;
        }
    };

    private SpeechUnderstanderListener mSpeechUnderstanderListener = new SpeechUnderstanderListener() {
        @Override
        public void onResult(final UnderstanderResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != result) {
                        String text = result.getResultString();
                        if (!TextUtils.isEmpty(text)) {
                            mEdtTransformResult.setText(text);
                        }
                    } else {
                        showTip("开发语义识别结果不正确");
                    }
                }
            });
        }

        @Override
        public void onVolumeChanged(int v) {
            showTip("onVolumeChanged："	+ v);
        }

        @Override
        public void onEndOfSpeech() {
            showTip("onEndOfSpeech");
        }

        @Override
        public void onBeginOfSpeech() {
            showTip("onBeginOfSpeech");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code："	+ error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) { }
    };

    private RecognizerListener mRecognizerListener = new RecognizerListener() {
        @Override
        public void onVolumeChanged(int volume) {
            showTip("当前正在说话，音量大小：" + volume);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result && !TextUtils.isEmpty(result.getResultString())) {
                DebugLog.d(DebugLog.TAG, "Recognizer Result=" + result.getResultString());
                String text = "";
                if (mResultType.equals("json")) {
                    text = JsonParser.parseGrammarResult(result.getResultString(), mEngineType);
                } else if (mResultType.equals("xml")) {
                    text = XmlParser.parseNluResult(result.getResultString());
                }
                mEdtTransformResult.setText(text);
                String contactName = text.substring(text.indexOf("【") + 1, text.indexOf("】"));
                if (mEdtTransformResult.getText().toString().contains("打电话")) {
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                            MainActivity.this.startActivity(intent);
                            break;
                        }
                    }
                } else if (mEdtTransformResult.getText().toString().contains("拍照")) {
                    Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                    MainActivity.this.startActivity(intent);
                }
            } else {
                DebugLog.d(DebugLog.TAG, "Recognizer Result=null");
            }
        }

        @Override
        public void onEndOfSpeech() {
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            showTip("语法识别开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code：" + error.getErrorCode());
            //startSpeechUnderstander();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) { }
    };

    private WakeuperListener mWakeuperListener = new WakeuperListener() {
        @Override
        public void onResult(WakeuperResult result) {
            try {
                String text = result.getResultString();
                JSONObject object;
                object = new JSONObject(text);
                StringBuffer buffer = new StringBuffer();
                buffer.append("[raw]" + text);
                buffer.append("\n");
                buffer.append("[操作类型]" + object.optString("sst"));
                buffer.append("\n");
                buffer.append("[唤醒词id]" + object.optString("id"));
                buffer.append("\n");
                buffer.append("[得分]" + object.optString("score"));
                buffer.append("\n");
                buffer.append("[前端点]" + object.optString("bos"));
                buffer.append("\n");
                buffer.append("[尾端点]" + object.optString("eos"));
                resultString = buffer.toString();
                Log.i("CollinWang", "WakeUpResult=" + buffer);

                mVibrator.vibrate(300);
                mBluetoothHelper.stop();
            } catch (JSONException e) {
                resultString = "结果解析出错";
                e.printStackTrace();
            }
        }

        @Override
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
            if (error != null) {
                startWakeUp();
            } else {
                Log.i("CollinWang", "onError=null");
            }
        }

        @Override
        public void onBeginOfSpeech() {
            showTip("语音唤醒开始说话");
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            DebugLog.d(DebugLog.TAG, "eventType:" + eventType + "arg1:" + isLast + "arg2:" + arg2);
            if (SpeechEvent.EVENT_IVW_RESULT == eventType) {
                RecognizerResult reslut = ((RecognizerResult)obj.get(SpeechEvent.KEY_EVENT_IVW_RESULT));
                String recoString = JsonParser.parseGrammarResult(reslut.getResultString(), mEngineType);
                mEdtTransformResult.setText(recoString);
                if (mEdtTransformResult.getText().toString().contains("打电话")) {
                    String contactName = recoString.substring(recoString.indexOf("【") + 1, recoString.indexOf("】"));
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (recoString.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                            MainActivity.this.startActivity(intent);
                            return;
                        }
                    }
                } else if (mEdtTransformResult.getText().toString().contains("拍照")) {
                    Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                    MainActivity.this.startActivity(intent);
                }

                Log.i("CollinWang", "语法识别result=" + recoString);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(mPageName);
        MobclickAgent.onPause(this);
        mBluetoothHelper.stop();
        if (mAsr != null && mAsr.isListening()) {
            mAsr.stopListening();
        }
    }

    private class BluetoothHelper extends BluetoothHeadsetUtils {
        public BluetoothHelper(Context context) {
            super(context);
        }

        @Override
        public void onScoAudioDisconnected() {
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onScoAudioDisconnected " + "");
        }

        @Override
        public void onScoAudioConnected() {
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onScoAudioConnected " + "");
        }

        @Override
        public void onHeadsetDisconnected() {
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onHeadsetDisconnected " + "");
        }

        @Override
        public void onHeadsetConnected() {
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onHeadsetConnected " + "");
        }
    }
}
