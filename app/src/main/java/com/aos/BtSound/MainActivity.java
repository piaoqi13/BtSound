package com.aos.BtSound;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Toast;

import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.contact.ObtainContactsUtil;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.receiver.PhoneReceiver;
import com.aos.BtSound.receiver.SMSReceiver;
import com.aos.BtSound.recorder.MyMediaRecorder;
import com.aos.BtSound.setting.IatSettings;
import com.aos.BtSound.setting.TtsSettings;
import com.aos.BtSound.util.FucUtil;
import com.aos.BtSound.util.JsonParser;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

import java.util.ArrayList;

import cn.yunzhisheng.common.USCError;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizer;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizerListener;

public class MainActivity extends Activity implements OnClickListener {
    private final String mPageName = "MainActivity";
    private Context mContext = null;
    private Button mBtnTakeCall = null;
    private Button mBtnSendMessages = null;
    private Button mBtnNavigation = null;
    private Button mBtnWeb = null;
    private Button mBtnSettings = null;
    private Button mBtnInstruction = null;
    private EditText mEdtTransformResult = null;

    private SpeechRecognizer mSpeechRecognizer = null;          // 语音对象
    private AudioManager mAudioManager = null;                  // 音频管理类
    private BluetoothAdapter mBluetoothAdapter = null;          // 蓝牙适配器
    private BluetoothHelper mBluetoothHelper = null;            // 蓝牙辅助类
    private RecognizerDialog mRecognizerDialog = null;          // 语音对话框
    private SoundPool mSoundPool = null;
    private boolean mIsStarted = false;

    private WakeUpRecognizer mWakeUpRecognizer = null;          // 唤醒对象
    private Vibrator mVibrator = null;                          // 唤醒震动

    private int mStartCount = 0;                                // 尝试打开蓝牙麦克风次数
    private ContentObserver mContentObserver = null;
    private PhoneReceiver mPhoneReceiver = null;
    private boolean mIsWakeUpStarted = false;                   // 跟踪语音唤醒是否开启
    private SpeechRecognizer mAsr = null;                       // 语音识别对象
    private Toast mToast = null;                                // 吐司提示

    private SharedPreferences mSharedPreferences;               // 存储配置
    private String mLocalGrammar = null;                        // 本地语法文件
    private String mLocalLexicon = null;                        // 本地词典
    private String mCloudGrammar = null;                        // 云端语法文件
    private String grmPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/test";
    private String mResultType = "json";                        // Result结果格式支持Xml和JSON

    private final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private final String GRAMMAR_TYPE_ABNF = "abnf";
    private final String GRAMMAR_TYPE_BNF = "bnf";

    private String mEngineType = "local";                       // 识别方式云端和本地
    private String mContent;                                    // 语法、词典临时变量
    private int ret = 0;                                        // 函数回值

    private SpeechUnderstander mSpeechUnderstander = null;      // 语义理解对象（语音到语义）
    private MyMediaRecorder mMyMediaRecorder;
    private boolean mWantToRecord;

    private SpeechSynthesizer mTts = null;                      // 语音合成对象
    public static String voicerCloud = "xiaoyan";               // 默认云端发音人
    public static String voicerLocal = "xiaoyan";               // 默认本地发音人
    private int mPercentForBuffering = 0;                       // 缓冲进度
    private int mPercentForPlaying = 0;                         // 播放进度
    private int mIndex = -1;                                    // 0是提示唤醒已成功；1是提示正在打电话；2是提示正在拍照；3是提示正在录音
    private String mCallname = "";                              // 即将呼叫的联系人

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (!mIsStarted)
                        showSoundHint();
                    break;
                case 1:
                    // 停止录音；
                    mMyMediaRecorder.stopRecording();
                    showTip("录音结束");
                    // 停蓝牙CollinWang1101
                    if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                        mBluetoothHelper.stop();
                    }
                    mWakeUpRecognizer.stop();
                    // 语音提示唤醒成功CollinWang1101
                    mIndex = 4;
                    setSpeechSynthesizerParam();
                    int code2 = mTts.startSpeaking("录音结束", mTtsListener);
                    if (code2 != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code2);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code2);
                    }
                    mEdtTransformResult.setText("Speak Result");
                    mWakeUpRecognizer.start();// 重新开启唤醒 录音
                    findViewById(R.id.btn_recorder).setClickable(true);
                    mWantToRecord = false;
                    break;
                case 4:
                    mEdtTransformResult.setText("正在录音...");
                    mMyMediaRecorder = new MyMediaRecorder();
                    mMyMediaRecorder.startRecording();
                    mHandler.sendEmptyMessageDelayed(1, 10 * 1000);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showTip("正在录音，10s钟后自动结束");
                        }
                    });
                    break;
                case 2:
                    mSoundPool.release();
                    break;
                case 4444:
                    upDateDictionary();
                    break;
                case 6666:
                    showTip("语义理解失败,错误码:" + ret);
                    break;
                case 8888:
                    showTip(getString(R.string.text_begin));
                    break;
                case 44444:
                    // 停蓝牙CollinWang1101
//                    if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                    //                        mBluetoothHelper.stop();
                    //                    }
                    //                    mWakeUpRecognizer.stop();
                    // 语音提示正在打电话CollinWang1101
                    mIndex = 1;
                    setSpeechSynthesizerParam();
                    int code = mTts.startSpeaking("正在打电话给" + mCallname, mTtsListener);
                    if (code != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code);
                    }
                    break;
            }
        }
    };


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mBluetoothHelper = new BluetoothHelper(this);
        mContext = this;

        initView();
        setListener();
        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();

        UmengUpdateAgent.update(this);

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

        buildGrammar();
        mHandler.sendEmptyMessageDelayed(4444, 1000);

        mContentObserver = new SMSReceiver(mHandler, this, mBluetoothHelper);
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, mContentObserver);

        // 初始化语法理解对象
        //mSpeechUnderstander = SpeechUnderstander.createUnderstander(this, speechUnderstanderListener);

        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(mPageName);
        MobclickAgent.onResume(this);
        mEdtTransformResult.setText("Speak Result");
        //mSpeechUnderstander.stopUnderstanding();        // 停止语法理解
        mAsr.stopListening();                           // 停止语法识别
        initWakeUp();                                   // 初始化唤醒
        wakeUpStart();                                  // 再启动唤醒
        mBluetoothHelper.start();                       // 启动检测蓝牙模块

        mPhoneReceiver = new PhoneReceiver(mBluetoothHelper);
        IntentFilter itf = new IntentFilter();
        itf.addAction("android.intent.action.PHONE_STATE");
        itf.addAction("android.intent.action.NEW_OUTGOING_CALL");
        registerReceiver(mPhoneReceiver, itf);

    }

    private void initView() {
        mBtnTakeCall = (Button) findViewById(R.id.btn_take_call);
        mBtnSendMessages = (Button) findViewById(R.id.btn_send_messages);
        mBtnNavigation = (Button) findViewById(R.id.btn_navigation);
        mBtnWeb = (Button) findViewById(R.id.btn_web);
        mBtnInstruction = (Button) findViewById(R.id.btn_instruction);
        mBtnSettings = (Button) findViewById(R.id.btn_settings);
        mEdtTransformResult = (EditText) findViewById(R.id.edt_result_text);
        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
    }

    private void setListener() {
        mBtnTakeCall.setOnClickListener(this);
        mBtnSendMessages.setOnClickListener(this);
        mBtnNavigation.setOnClickListener(this);
        mBtnWeb.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);
        mBtnInstruction.setOnClickListener(this);
        findViewById(R.id.btn_recorder).setOnClickListener(this);
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
        Log.i("CollinWang", "mContent=" + mContent);
    }

    public boolean setParam() {
        boolean result = false;
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 语法识别音频CollinWang1019
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT, "pcm");
        boolean isOk = mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/asr.pcm");
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
            // 设置混合模式CollinWang1105
            mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_MIX);
            mAsr.setParameter("asr_sch", "1");
            mAsr.setParameter(SpeechConstant.NLP_VERSION, "2.0");
            mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");
            mAsr.setParameter("mixed_type", "delay");//混合模式的类型
            //mAsr.setParameter("local_prior", "1");

            mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
            mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
            mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "40");
            // 前后端点CollinWang1029
            mAsr.setParameter(SpeechConstant.VAD_BOS, "4000");//default5000
            mAsr.setParameter(SpeechConstant.VAD_EOS, "1000");//default1800
            result = true;
        }
        return result;
    }

    public void setSpeechUnderstanderParam() {
        String lag = mSharedPreferences.getString("understander_language_preference", "mandarin");
        if (lag.equals("en_us")) {
            mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "en_us");
        } else {
            mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            mSpeechUnderstander.setParameter(SpeechConstant.ACCENT, lag);
        }
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("understander_vadbos_preference", "4000"));
        mSpeechUnderstander.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("understander_vadeos_preference", "1000"));
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("understander_punc_preference", "1"));
        mSpeechUnderstander.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");// 设置为-1，通过WriteAudio接口送入音频
        mSpeechUnderstander.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/iflytek/wavaudio.pcm");
    }

    private void setSpeechSynthesizerParam() {
        mTts.setParameter(SpeechConstant.PARAMS, null);
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerCloud);
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mTts.setParameter(ResourceUtil.TTS_RES_PATH, getSpeechSynthesizerResourcePath());
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerLocal);
        }
        mSharedPreferences = this.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "50"));
        mTts.setParameter(SpeechConstant.PITCH, mSharedPreferences.getString("pitch_preference", "50"));
        mTts.setParameter(SpeechConstant.VOLUME, mSharedPreferences.getString("volume_preference", "50"));
        mTts.setParameter(SpeechConstant.STREAM_TYPE, mSharedPreferences.getString("stream_preference", "3"));
    }

    private String getSpeechSynthesizerResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, ResourceUtil.RESOURCE_TYPE.assets, "tts/common.jet"));
        tempBuffer.append(";");
        tempBuffer.append(ResourceUtil.generateResourcePath(mContext, ResourceUtil.RESOURCE_TYPE.assets, "tts/" + PhoneReceiver.voicerLocal + ".jet"));
        return tempBuffer.toString();
    }

    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/common.jet"));
        return tempBuffer.toString();
    }

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
                if (mBluetoothHelper != null && !mBluetoothHelper.isOnHeadsetSco())
                    mBluetoothHelper.start();
                if (mIndex == 0) {
                    // 启动语法识别CollinWang1101
                    if (!setParam()) {
                        showTip("请构建语法再语音识别");
                        return;
                    }
                    ret = mAsr.startListening(mRecognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("识别未成功，错误码: " + ret);
                    }
                } else if (mIndex == 1) {

                } else if (mIndex == 2) {

                } else if (mIndex == 3) {

                }
            } else if (error != null) {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) { }
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

    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            DebugLog.d("CollinWang", "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

    private ContactListener mContactListener = new ContactListener() {
        @Override
        public void onContactQueryFinish(String contactInfos, boolean changeFlag) {
            mLocalLexicon = contactInfos;
        }
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
                String contact = "";
                if (mResultType.equals("json")) {
                    Log.i("CollinWang", "ishaveCall=" + JsonParser.isHaveCallOrText(result.getResultString()));
                    if (JsonParser.isHaveCallOrText(result.getResultString())) {
                        text = JsonParser.parseMixNameResultText(result.getResultString());
                        // 直接打电话出去
                        mEdtTransformResult.setText(text);
                        if (text.contains("打电话")) {
                            if (FucUtil.isAvailableMobilePhone(FucUtil.getNumber(text))) {
                                contact = FucUtil.getNumber(text);// 此时是数字号码
                                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + contact));
                                MainActivity.this.startActivity(intent);
                                mCallname = contact;
                                mHandler.sendEmptyMessageDelayed(44444, 1500);
                            } else {
                                contact = JsonParser.parseMixNameResult(result.getResultString());
                                for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                                    if (contact.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                                        if (!VoiceCellApplication.mContacts.get(i).getPhoneNumber().equals("")) {
                                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                            MainActivity.this.startActivity(intent);
                                            mCallname = VoiceCellApplication.mContacts.get(i).getName();
                                            mHandler.sendEmptyMessageDelayed(44444, 1500);
                                            break;
                                        } else {
                                            Log.i("CollinWang", "要拨打联系人没有对应号码噢");
                                            showTip("要拨打联系人没有对应号码噢");
                                            wakeUpStart();
                                            mEdtTransformResult.setText("Speak Result");
                                        }
                                    }
                                }
                            }
                        } else if (text.contains("拍照")) {
                            // 停蓝牙CollinWang1101
                            if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                                mBluetoothHelper.stop();
                            }
                            mWakeUpRecognizer.stop();
                            // 语音提示唤醒成功CollinWang1101
                            mIndex = 2;
                            setSpeechSynthesizerParam();
                            int code = mTts.startSpeaking("准备拍照，1，2，3", mTtsListener);
                            if (code != ErrorCode.SUCCESS) {
                                showTip("语音合成失败,错误码: " + code);
                            } else {
                                DebugLog.i("CollinWang", "code=" + code);
                            }
                            mEdtTransformResult.setText(text);
                            Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                            MainActivity.this.startActivity(intent);
                        } else if (text.contains("录音")) {
                            mAsr.stopListening();
                            // 停蓝牙CollinWang1101
                            if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                                mBluetoothHelper.stop();
                            }
                            mWakeUpRecognizer.stop();
                            // 语音提示唤醒成功CollinWang1101
                            mIndex = 3;
                            setSpeechSynthesizerParam();
                            int code = mTts.startSpeaking("开始语音记事，请说话", mTtsListener);
                            if (code != ErrorCode.SUCCESS) {
                                showTip("语音合成失败,错误码: " + code);
                            } else {
                                DebugLog.i("CollinWang", "code=" + code);
                            }
                            mHandler.sendEmptyMessage(4);
                        }

                    } else {// 离线命令词
                        text = JsonParser.parseGrammarResult(result.getResultString(), mEngineType);
                        if (text.contains("打电话") && VoiceCellApplication.mSc > 55) {
                            mEdtTransformResult.setText(text);
                            String contactName = text.substring(text.indexOf("【") + 1, text.indexOf("】"));
                            for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                                if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                                    if (!VoiceCellApplication.mContacts.get(i).getPhoneNumber().equals("")) {
                                        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                        MainActivity.this.startActivity(intent);
                                        mCallname = VoiceCellApplication.mContacts.get(i).getName();
                                        mHandler.sendEmptyMessageDelayed(44444, 1500);
                                        break;
                                    } else {
                                        Log.i("CollinWang", "要拨打联系人没有对应号码噢");
                                        showTip("要拨打联系人没有对应号码噢");
                                        wakeUpStart();
                                        mEdtTransformResult.setText("Speak Result");
                                    }
                                }
                            }
                        } else if (text.contains("打电话") && VoiceCellApplication.mSc <= 55) {
                            // 语法理解去识别是否有数字号码
                        /*setSpeechUnderstanderParam();
                        if (mSpeechUnderstander.isUnderstanding()) {
                            mSpeechUnderstander.stopUnderstanding();
                        } else {
                            try {
                                String file_path = Environment.getExternalStorageDirectory()+"/msc/asr.pcm";
                                byte[] data = FucUtil.readFileFromSDcard(MainActivity.this, file_path);
                                ArrayList<byte[]> buffers = FucUtil.splitBuffer(data, data.length, 1280);
                                writeaudio(buffers);
                            } catch (Exception e) {
                                Log.i("CollinWang", "Catch=", e);
                                showTip("请清晰说话噢");
                                wakeUpStart();
                            }
                        }*/
                            showTip("请清晰说话噢");
                            wakeUpStart();
                        } else if (text.contains("拍照") && VoiceCellApplication.mSc > 60) {
                            // 停蓝牙CollinWang1101
                            if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                                mBluetoothHelper.stop();
                            }
                            mWakeUpRecognizer.stop();
                            // 语音提示唤醒成功CollinWang1101
                            mIndex = 2;
                            setSpeechSynthesizerParam();
                            int code = mTts.startSpeaking("准备拍照，1，2，3", mTtsListener);
                            if (code != ErrorCode.SUCCESS) {
                                showTip("语音合成失败,错误码: " + code);
                            } else {
                                DebugLog.i("CollinWang", "code=" + code);
                            }
                            mEdtTransformResult.setText(text);
                            Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                            MainActivity.this.startActivity(intent);
                        } else if (text.contains("拍照") && VoiceCellApplication.mSc <= 60) {
                            DebugLog.d(DebugLog.TAG, "得分小于60走噪音误判拍照else分支");
                            wakeUpStart();
                        } else if (text.contains("录音") && VoiceCellApplication.mSc > 60) {
                            mAsr.stopListening();
                            // 停蓝牙CollinWang1101
                            if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                                mBluetoothHelper.stop();
                            }
                            mWakeUpRecognizer.stop();
                            // 语音提示唤醒成功CollinWang1101
                            mIndex = 3;
                            setSpeechSynthesizerParam();
                            int code = mTts.startSpeaking("开始语音记事，请说话", mTtsListener);
                            if (code != ErrorCode.SUCCESS) {
                                showTip("语音合成失败,错误码: " + code);
                            } else {
                                DebugLog.i("CollinWang", "code=" + code);
                            }
                            mHandler.sendEmptyMessage(4);
                        }
                    }
                    Log.i("CollinWang", "text=" + text);
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
            if(error == null) {
                DebugLog.i("CollinWang", "onError: error == null");
                return;
            }

            DebugLog.i("CollinWang", "onError Code：" + error.getErrorCode());
            /*if (error.getErrorCode() == 20005) {
                setSpeechUnderstanderParam();
                if (mSpeechUnderstander.isUnderstanding()) {
                    mSpeechUnderstander.stopUnderstanding();
                } else {
                    String file_path = Environment.getExternalStorageDirectory()+"/msc/asr.pcm";
                    byte[] data = FucUtil.readFileFromSDcard(MainActivity.this, file_path);
                    ArrayList<byte[]> buffers = FucUtil.splitBuffer(data, data.length, 1280);
                    writeaudio(buffers);
                }
            } else {
                wakeUpStart();
            }*/
            showTip("错误码=" + error.getErrorCode());
            wakeUpStart();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) { }
    };

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_call:
                mWakeUpRecognizer.stop();
                if (!setParam()) {
                    showTip("请构建语法再语音识别");
                    return;
                }
                ret = mAsr.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("识别未成功，错误码: " + ret);
                }
                break;
            case R.id.btn_settings:
                Intent intent3 = new Intent(MainActivity.this, MessageSwitch.class);
                MainActivity.this.startActivity(intent3);
                break;
            case R.id.btn_navigation:
                Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                MainActivity.this.startActivity(intent);
                break;
            case R.id.btn_recorder:
                // 停止唤醒录音
                mWakeUpRecognizer.stop();
                // 停止语音识别录音
                mAsr.stopListening();
                mWantToRecord = true;
                findViewById(R.id.btn_recorder).setClickable(false);
                break;
            case R.id.btn_web:
                openWeb();
                break;
            case R.id.btn_instruction:
                Intent intent2 = new Intent(MainActivity.this, InstructionActivity.class);
                startActivity(intent2);
                break;
        }
    }

    private void openWeb() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.aossh.com"));
        startActivity(intent);
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
                            mHandler.obtainMessage(2).sendToTarget();
                            mIsStarted = false;
                            if (!setParam()) {
                                showTip("请构建语法再语音识别");
                                return;
                            }
                            ret = mAsr.startListening(mRecognizerListener);
                            if (ret != ErrorCode.SUCCESS) {
                                showTip("识别未成功，错误码: " + ret);
                            }
                        }
                    }, 4000);
                }
            }
        });
    }

    public void playSound(int id) {
        mSoundPool.play(id, 0.5f, 0.5f, 1, 1, 1f);
    }

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败");
            }
        }
    };

    private InitListener speechUnderstanderListener = new InitListener() {
        @Override
        public void onInit(int code) {
            DebugLog.d(DebugLog.TAG, "speechUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" +code);
            }
        }
    };

    private SpeechUnderstanderListener understanderListener = new SpeechUnderstanderListener() {
        @Override
        public void onResult(final UnderstanderResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != result) {
                        String text = result.getResultString();
                        DebugLog.i("CollinWang", "语法理解JSON=" + text);
                        if (!TextUtils.isEmpty(text)) {
                            if (!FucUtil.getNumber(text).equals("")) {
                                mEdtTransformResult.setText(JsonParser.parseSpeechUnderstanderResult(text));
                                String number = FucUtil.getNumber(mEdtTransformResult.getText().toString());
                                if (FucUtil.isAvailableMobilePhone(number)) {
                                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                                    MainActivity.this.startActivity(intent);
                                } else {
                                    //showTip("请清晰说出您要拨打的号码噢");
                                    mEdtTransformResult.setText("请清晰说出您要拨打的号码");
                                    DebugLog.i("CollinWang", "语法理解不是标准数字号码");
                                    wakeUpStart();
                                }
                            } else {
                                showTip("请清晰说出您要拨打的号码噢");
                                mEdtTransformResult.setText("Speak Result");
                                DebugLog.i("CollinWang", "语法理解不是数字号码");
                                wakeUpStart();
                            }
                        } else {
                            showTip("请清晰说话噢");
                            wakeUpStart();
                        }
                    } else {
                        showTip("语法理解结果不正确");
                        wakeUpStart();
                    }
                }
            });
            }

            @Override
            public void onVolumeChanged(int v) {
            showTip("正在进一步语法理解：" + v);
            }

        @Override
        public void onEndOfSpeech() {
            showTip("结束语法理解");
            }

            @Override
            public void onBeginOfSpeech() {
            showTip("onBeginOfSpeech");
            }

            @Override
            public void onError(SpeechError error) {
            showTip("onError Code：" + error.getErrorCode());
            mWakeUpRecognizer.start();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) { }
    };

    private void initWakeUp() {
        if (mWakeUpRecognizer != null)
            return;

        mWakeUpRecognizer = new WakeUpRecognizer(this, Config.appKey);
        mWakeUpRecognizer.setListener(new WakeUpRecognizerListener() {

            @Override
            public void onWakeUpRecognizerStart() {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpRecognizerStart " + "CollinWang" + "语音唤醒已开始");
                Toast.makeText(MainActivity.this, "语音唤醒已开始", Toast.LENGTH_SHORT).show();
                mIsWakeUpStarted = true;
            }

            @Override
            public void onWakeUpError(USCError error) {
                if (error != null) {
                    DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpError " + "CollinWang" + "Information=" + error.toString());
                }
                mIsWakeUpStarted = false;
            }

            @Override
            public void onWakeUpRecognizerStop() {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpRecognizerStop " + "CollinWang" + "语音唤醒已停止");
                mIsWakeUpStarted = false;

                if(mWantToRecord)
                    mHandler.sendEmptyMessage(4);
            }

            @Override
            public void onWakeUpResult(boolean succeed, String text, float score) {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult " + "succeed : " + succeed);
                if (succeed) {
                    mVibrator.vibrate(300);
                    DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult " + "CollinWang" + "语音唤醒成功");
                    // 停蓝牙CollinWang1101
                    if(mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                        mBluetoothHelper.stop();
                    }
                    mWakeUpRecognizer.stop();
                    // 语音提示唤醒成功CollinWang1101
                    mIndex = 0;
                    setSpeechSynthesizerParam();
                    int code = mTts.startSpeaking("傲石语音已唤醒，请说指令", mTtsListener);
                    if (code != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code);
                    }
                }
            }
        });
    }

    protected void wakeUpStart() {
        if (mWakeUpRecognizer.isRunning())
            return;
        initWakeUp();
        mWakeUpRecognizer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(mPageName);
        MobclickAgent.onPause(this);
        mBluetoothHelper.stop();
        stopWakeupRecognizer();

        unregisterReceiver(mPhoneReceiver);
    }

    private void stopWakeupRecognizer() {
        mWakeUpRecognizer.stop();
        mWakeUpRecognizer.cancel();
        mWakeUpRecognizer = null;
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

    private void writeaudio(final ArrayList<byte[]> buffers) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                ret = mSpeechUnderstander.startUnderstanding(understanderListener);
                if (ret != 0) {
                    mHandler.sendEmptyMessageDelayed(6666, 10);
                } else {
                    mHandler.sendEmptyMessageDelayed(8888, 10);
                }
                for (int i = 0; i < buffers.size(); i++) {
                    try {
                        mSpeechUnderstander.writeAudio(buffers.get(i), 0, buffers.get(i).length);
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mSpeechUnderstander.stopUnderstanding();
            }

        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mSpeechUnderstander.cancel();
        //mSpeechUnderstander.destroy();
        mAsr.cancel();
        mAsr.destroy();
    }
}
