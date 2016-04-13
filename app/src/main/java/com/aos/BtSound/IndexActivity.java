package com.aos.BtSound;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.contact.ObtainContactsUtil;
import com.aos.BtSound.dialog.LoadingDialog;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.receiver.PhoneReceiver;
import com.aos.BtSound.receiver.SMSReceiver;
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
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ResourceUtil;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

import cn.yunzhisheng.common.USCError;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizer;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizerListener;

public class IndexActivity extends Activity implements View.OnClickListener {
    private LinearLayout mLlChangTiaoBuJu = null;
    private ImageView mIvMenu = null;

    private LinearLayout mLlCall = null;
    private LinearLayout mLlPaiZhao = null;
    private LinearLayout mLlLuYin = null;
    private LinearLayout mLlSetting = null;
    private LinearLayout mLlWenTi = null;
    private LinearLayout mLlGuanWang = null;

    private EditText mEdtTransformResult = null;

    private final String mPageName = "MainActivity";
    private Context mContext = null;

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
    private ContentObserver mContentObserver = null;            // 短信内容广播
    private PhoneReceiver mPhoneReceiver = null;                // 来电播报广播
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

    private String mContent;                                    // 语法、词典临时变量
    private int ret = 0;                                        // 函数回值

    private boolean mWantToRecord;                              // 是否录音标识

    private SpeechSynthesizer mTts = null;                      // 语音合成对象
    public static String voicerCloud = "xiaoyan";               // 默认云端发音人
    public static String voicerLocal = "xiaoyan";               // 默认本地发音人
    private int mPercentForBuffering = 0;                       // 缓冲进度
    private int mPercentForPlaying = 0;                         // 播放进度
    private int mIndex = -1;                                    // 0是提示唤醒已成功；1是提示正在打电话；2是提示正在拍照；3是提示正在录音
    private String mCallname = "";                              // 即将呼叫的联系人

    private final String mSwitch = SpeechConstant.TYPE_CLOUD;   // 客户TYPE_MIX和非客户TYPE_CLOUD是否支持离线开关
    private LoadingDialog mLoading = null;                      // 加载对话框

    private boolean isRecording = false;                        // 是否正在录音
    private long mExitTime = 0;                                 // 双击退出时间

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (!mIsStarted)
                        showSoundHint();
                    break;
                case 3:
                    mSoundPool.release();
                    break;
                case 4:
                    upDateDictionary();
                    break;
                case 5:
                    mEdtTransformResult.setText("Speak Result");
                    wakeUpStart();
                    break;
                case 6:
                    mIndex = 1;
                    setSpeechSynthesizerParam();
                    int code = mTts.startSpeaking("正在打电话给" + mCallname, mTtsListener);
                    if (code != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code);
                    }
                    break;
                case 7:
                    // 停止蓝牙CollinWang0114
                    if (mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                        mBluetoothHelper.stop();
                    }
                    mVibrator.vibrate(5000);
                    setSpeechSynthesizerParam();
                    int code7 = mTts.startSpeaking("蓝牙已断开，请检查连接！", mTtsListener);
                    if (code7 != ErrorCode.SUCCESS) {
                        showTip("语音合成失败,错误码: " + code7);
                    } else {
                        DebugLog.i("CollinWang", "code=" + code7);
                    }
                    mWakeUpRecognizer.start();
                    DebugLog.i("CollinWang", "蓝牙断开");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);
        mContext = this;
        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        // 震动和蓝牙
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mBluetoothHelper = new BluetoothHelper(this);
        initView();
        setListener();
        // 获取联系人信息
        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();
        // 友盟自动更新
        UmengUpdateAgent.update(this);
        // 初始化语法、命令词
        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);
        mLocalLexicon = "王超\n刘雄斌\n蔡哥\n";
        mLocalGrammar = FucUtil.readFile(this, "call.bnf", "utf-8");
        mCloudGrammar = FucUtil.readFile(this, "call.bnf", "utf-8");
        // 获取联系人、本地更新词典时使用
        ContactManager mgr = ContactManager.createManager(this, mContactListener);
        mgr.asyncQueryAllContactsName();
        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        // 默认本地引擎构造语法更新词典
        buildGrammar();
        mHandler.sendEmptyMessageDelayed(4, 1000);
        // 短信播报广播注册
        mContentObserver = new SMSReceiver(mHandler, this, mBluetoothHelper);
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, mContentObserver);
        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);
        // 蓝牙按键广播注册
        //MediaButtonReceiver mBtnReceiver = new MediaButtonReceiver();
        //IntentFilter ittFilterButton = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        //registerReceiver(mBtnReceiver, ittFilterButton);
    }

    private void initView() {
        mLlChangTiaoBuJu = (LinearLayout) findViewById(R.id.ll_changtiaobuju);
        mEdtTransformResult = (EditText) findViewById(R.id.edt_result_text);
        mIvMenu = (ImageView) findViewById(R.id.iv_menu);
        mLlCall = (LinearLayout) findViewById(R.id.ll_call);
        mLlPaiZhao = (LinearLayout) findViewById(R.id.ll_paizhao);
        mLlLuYin = (LinearLayout) findViewById(R.id.ll_luyin);
        mLlSetting = (LinearLayout) findViewById(R.id.ll_setting);
        mLlWenTi = (LinearLayout) findViewById(R.id.ll_wenti);
        mLlGuanWang = (LinearLayout) findViewById(R.id.ll_website);
    }

    private void setListener() {
        mLlChangTiaoBuJu.setOnClickListener(this);
        mIvMenu.setOnClickListener(this);
        mLlCall.setOnClickListener(this);
        mLlPaiZhao.setOnClickListener(this);
        mLlLuYin.setOnClickListener(this);
        mLlSetting.setOnClickListener(this);
        mLlWenTi.setOnClickListener(this);
        mLlGuanWang.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(mPageName);
        MobclickAgent.onResume(this);
        mEdtTransformResult.setText("Speak Result");
        mAsr.stopListening();//停止语法识别
        initWakeUp();//初始化唤醒
        wakeUpStart();//再启动唤醒
        mBluetoothHelper.start();//启动检测蓝牙模块
        // 注册来电播报广播
        mPhoneReceiver = new PhoneReceiver(mBluetoothHelper);
        IntentFilter itf = new IntentFilter();
        itf.addAction("android.intent.action.PHONE_STATE");
        itf.addAction("android.intent.action.NEW_OUTGOING_CALL");
        registerReceiver(mPhoneReceiver, itf);
    }

    private void buildGrammar() {
        if (VoiceCellApplication.mEngineType == SpeechConstant.TYPE_CLOUD) {
            mContent = new String(mCloudGrammar);
            mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            ret = mAsr.buildGrammar(GRAMMAR_TYPE_ABNF, mContent, grammarListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("语法构建未成功");
            }
        } else {
            mContent = new String(mLocalGrammar);
            mAsr.setParameter(SpeechConstant.PARAMS, null);
            mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            ret = mAsr.buildGrammar(GRAMMAR_TYPE_BNF, mContent, grammarListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("语法构建未成功");
            }
        }
    }

    private void upDateDictionary() {
        mLoading = new LoadingDialog(mContext);
        mLoading.showDialog("正在上传通讯录...");
        if (VoiceCellApplication.mEngineType == SpeechConstant.TYPE_CLOUD) {
            mContent = new String(mLocalLexicon);
            mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            // 讯飞君沟通处理楚曲圣误判1116
            String contents = FucUtil.readFile(mContext, "userwords", "utf-8");
            ret = mAsr.updateLexicon("contact", mContent, lexiconListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("更新词典未成功");
            }
        } else {
            mContent = new String(mLocalLexicon);
            mAsr.setParameter(SpeechConstant.PARAMS, null);
            mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            mAsr.setParameter(SpeechConstant.GRAMMAR_LIST, "call");
            mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            // 讯飞君沟通处理楚曲圣误判1116
            String contents = FucUtil.readFile(mContext, "userwords", "utf-8");
            ret = mAsr.updateLexicon("contact", mContent, lexiconListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("更新词典未成功");
            }
        }

        Log.i("CollinWang", "mContent=" + mContent);
    }

    public boolean setParam() {
        boolean result = false;
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, VoiceCellApplication.mEngineType);
        // 语法识别音频CollinWang1019
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT, "pcm");
        boolean isOk = mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/asr.pcm");
        // 设置混合模式CollinWang1105
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mSwitch);
        mAsr.setParameter("asr_sch", "1");
        mAsr.setParameter(SpeechConstant.NLP_VERSION, "2.0");
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mAsr.setParameter("mixed_type", "delay");//混合模式的类型delay延时优先云端
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
        mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
        mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "40");
        // 前后端点CollinWang1029
        mAsr.setParameter(SpeechConstant.VAD_BOS, "2000");
        mAsr.setParameter(SpeechConstant.VAD_EOS, "1000");
        result = true;
        return result;
    }

    private void setSpeechSynthesizerParam() {
        mTts.setParameter(SpeechConstant.PARAMS, null);
        if (VoiceCellApplication.mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerCloud);
        } else if (VoiceCellApplication.mEngineType.equals(SpeechConstant.TYPE_LOCAL)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mTts.setParameter(ResourceUtil.TTS_RES_PATH, getSpeechSynthesizerResourcePath());
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicerLocal);
        }
        mSharedPreferences = this.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "40"));
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
            mLoading.dismiss();
            if (error == null) {
                DebugLog.d(DebugLog.TAG, "词典更新成功");
            } else {
                showTipDialog("上传通讯录未成功，请检查网络确定重试");
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
                if (error.getErrorCode() == 20001) {
                    showTip("当前是试用版不支持离线命令噢");
                } else {
                    showTip("错误码=" + error.getErrorCode());
                }
                Log.i("CollinWang", "error=" + error.getPlainDescription(true));
                // 再次启动唤醒
                mHandler.sendEmptyMessageDelayed(5, 1000);
            }
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
        }
    };

    private GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                if (VoiceCellApplication.mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    if (!TextUtils.isEmpty(grammarId))
                        editor.putString(KEY_GRAMMAR_ABNF_ID, grammarId);
                    editor.commit();
                }
                DebugLog.d(DebugLog.TAG, "语法构建成功");
            } else {
                DebugLog.d(DebugLog.TAG, "语法构建未成功，错误码：" + error.getErrorCode());
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

    private ContactManager.ContactListener mContactListener = new ContactManager.ContactListener() {
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
                        if (text.contains("打电话")) {
                            if (FucUtil.getNumber(text).length() > 0) {
                                if (FucUtil.isAvailableMobilePhone(FucUtil.getNumber(text)) || FucUtil.getNumber(text).length() != 11) {
                                    contact = FucUtil.getNumber(text);// 此时是数字号码
                                    mEdtTransformResult.setText(text.replace(contact, "【" + contact + "】"));
                                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + contact));
                                    IndexActivity.this.startActivity(intent);
                                    mCallname = contact;
                                    mHandler.sendEmptyMessageDelayed(6, 1500);
                                } else {
                                    mEdtTransformResult.setText(text);
                                    showTip("手机号码格式有误，请重新说出");
                                    mHandler.sendEmptyMessageDelayed(5, 1000);
                                }
                            } else {
                                contact = JsonParser.parseMixNameResult(result.getResultString());
                                mEdtTransformResult.setText(text.replaceAll("，", "").replaceAll("给", "").substring(0, text.lastIndexOf("话")) + "话给【" + contact + "】");
                                for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                                    if (contact.equals(VoiceCellApplication.mContacts.get(i).getName())) {
                                        if (!VoiceCellApplication.mContacts.get(i).getPhoneNumber().equals("")) {
                                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                            IndexActivity.this.startActivity(intent);
                                            mCallname = VoiceCellApplication.mContacts.get(i).getName();
                                            mHandler.sendEmptyMessageDelayed(6, 500);
                                            break;
                                        } else {
                                            Log.i("CollinWang", "要拨打联系人没有对应号码噢");
                                            showTip("要拨打联系人没有对应号码噢");
                                            mHandler.sendEmptyMessageDelayed(5, 1000);
                                        }
                                    } else {
                                        if (i == VoiceCellApplication.mContacts.size() - 1) {
                                            Log.i("CollinWang", "没有找到此联系人噢");
                                            showTip("没有找到此联系人噢");
                                            mHandler.sendEmptyMessageDelayed(5, 1000);
                                        }
                                    }
                                }
                            }
                        } else if (text.contains("拍照")) {
                            // 停蓝牙CollinWang1101
                            if (mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                                mBluetoothHelper.stop();
                            }
                            mWakeUpRecognizer.stop();
                            // 语音提示唤醒成功CollinWang1101
                            mIndex = 2;
                            setSpeechSynthesizerParam();
                            mEdtTransformResult.setText(text);
                            Intent intent = new Intent(IndexActivity.this, AndroidCameraActivity.class);
                            IndexActivity.this.startActivity(intent);
                        } else if (text.contains("录音")) {
                            Intent intent = new Intent(IndexActivity.this, LuYinActivity.class);
                            IndexActivity.this.startActivity(intent);
                        }

                    } else if (VoiceCellApplication.mEngineType == SpeechConstant.TYPE_LOCAL) {// 离线命令词
                        text = JsonParser.parseGrammarResult(result.getResultString(), VoiceCellApplication.mEngineType);
                        if (text.contains("打电话") && VoiceCellApplication.mSc > 55) {
                            mEdtTransformResult.setText(text);
                            String contactName = text.substring(text.indexOf("【") + 1, text.indexOf("】"));
                            for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                                if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                                    if (!VoiceCellApplication.mContacts.get(i).getPhoneNumber().equals("")) {
                                        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                        IndexActivity.this.startActivity(intent);
                                        mCallname = VoiceCellApplication.mContacts.get(i).getName();
                                        mHandler.sendEmptyMessageDelayed(6, 1500);
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
                            showTip("请清晰说话噢");
                            wakeUpStart();
                        } else if (text.contains("拍照") && VoiceCellApplication.mSc > 60) {
                            // 停蓝牙CollinWang1101
                            if (mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
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
                            Intent intent = new Intent(IndexActivity.this, AndroidCameraActivity.class);
                            IndexActivity.this.startActivity(intent);
                        } else if (text.contains("拍照") && VoiceCellApplication.mSc <= 60) {
                            DebugLog.d(DebugLog.TAG, "得分小于60走噪音误判拍照else分支");
                            wakeUpStart();
                        } else if (text.contains("录音") && VoiceCellApplication.mSc > 60) {
                            Intent intent = new Intent(IndexActivity.this, LuYinActivity.class);
                            IndexActivity.this.startActivity(intent);
                        }
                    } else {
                        showTip("非法指令，请阅读软件说明后再尝试！");
                        mHandler.sendEmptyMessageDelayed(5, 1000);
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
            if (error == null) {
                DebugLog.i("CollinWang", "onError: error == null");
                return;
            }

            DebugLog.i("CollinWang", "onError Code：" + error.getErrorCode());
            if (error.getErrorCode() == 20001) {
                showTip("当前是试用版不支持离线命令噢");
            } else {
                showTip("错误码=" + error.getErrorCode());
            }
            mHandler.sendEmptyMessageDelayed(5, 1000);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
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

    private void initWakeUp() {
        if (mWakeUpRecognizer != null)
            return;

        mWakeUpRecognizer = new WakeUpRecognizer(this, Config.appKey);
        mWakeUpRecognizer.setListener(new WakeUpRecognizerListener() {

            @Override
            public void onWakeUpRecognizerStart() {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpRecognizerStart " + "CollinWang" + "语音唤醒已开始");
                Toast.makeText(IndexActivity.this, "语音唤醒已开始", Toast.LENGTH_SHORT).show();
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

                if (mWantToRecord)
                    mHandler.sendEmptyMessage(2);
            }

            @Override
            public void onWakeUpResult(boolean succeed, String text, float score) {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult " + "succeed : " + succeed);
                if (succeed) {
                    mVibrator.vibrate(300);
                    DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult " + "CollinWang" + "语音唤醒成功");
                    // 停蓝牙CollinWang1101
                    if (mBluetoothHelper != null && mBluetoothHelper.isOnHeadsetSco()) {
                        mBluetoothHelper.stop();
                    }
                    mWakeUpRecognizer.stop();
                    // 语音提示唤醒成功CollinWang1101
                    mIndex = 0;
                    setSpeechSynthesizerParam();
                    int code = mTts.startSpeaking("你好，请说指令……", mTtsListener);
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
        if (mWakeUpRecognizer != null && mWakeUpRecognizer.isRunning())
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
            mHandler.sendEmptyMessageDelayed(7, 1000);
        }

        @Override
        public void onHeadsetConnected() {
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onHeadsetConnected " + "");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return doubleClickToExit();
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean doubleClickToExit() {
        if (isRecording) {
            showTip("正在录音，请停止录音！");
        } else {
            if ((System.currentTimeMillis() - mExitTime) > 2000) {
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAsr.cancel();
        mAsr.destroy();
    }

    // 蓝牙的 DeviceId 可能不是 0，这个打个 log 看一下
    private static final int BLUETOOTH_GLASS = 11;
    private boolean mIsRecording = false;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        DebugLog.d(DebugLog.TAG, "MainActivity:onKeyDown" + " event Info : " + event.toString());
        // 如果音乐没有正在播放
        if (mAudioManager != null && !mAudioManager.isMusicActive()) {
            // 如果来自 蓝牙眼镜，并且是 MEDIA_PLAY 按钮
            if (event.getDeviceId() == BLUETOOTH_GLASS) {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    DebugLog.d(DebugLog.TAG, "MainActivity:onKeyDown " + "");
                    try {
                        beginRecord();
                        mIsRecording = true;
                    } catch (Exception e) {
                        mIsRecording = false;
                    } finally {

                    }
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    DebugLog.d(DebugLog.TAG, "MainActivity:onKeyDown " + "");
                    try {
                        stopRecord();
                    } catch (Exception e) {
                        Log.i("CollinWang", "Catch=", e);
                    } finally {
                        mIsRecording = false;
                    }
                    return true;
                }
            }
        } else {
            DebugLog.d(DebugLog.TAG, "mAudioManager=" + mAudioManager);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void stopRecord() throws Exception {
        DebugLog.d(DebugLog.TAG, "停止录音");
    }

    private void beginRecord() throws Exception {
        DebugLog.d(DebugLog.TAG, "开始录音");
    }

    protected void showTipDialog(String tip) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(tip);
        builder.setTitle("温馨提示");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                upDateDictionary();
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.create().show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_menu:
                // ToDo
                if (mLlChangTiaoBuJu.getVisibility() == View.GONE) {
                    mLlChangTiaoBuJu.setVisibility(View.VISIBLE);
                    Log.i("CollinWang", "Gone");
                } else if (mLlChangTiaoBuJu.getVisibility() == View.VISIBLE) {
                    mLlChangTiaoBuJu.setVisibility(View.GONE);
                    Log.i("CollinWang", "VISIBLE");
                }

                Log.i("CollinWang", "real=" + mLlChangTiaoBuJu.getVisibility());
                break;
            case R.id.ll_call:
                // ToDo
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
            case R.id.ll_paizhao:
                // ToDo
                //Intent intent = new Intent(IndexActivity.this, AndroidCameraActivity.class);
                //IndexActivity.this.startActivity(intent);
                showTip("正在努力开发中...");
                break;
            case R.id.ll_luyin:
                // ToDo
                Intent intent4 = new Intent(IndexActivity.this, LuYinActivity.class);
                IndexActivity.this.startActivity(intent4);
                break;
            case R.id.ll_setting:
                // ToDo
                Intent intent3 = new Intent(IndexActivity.this, MessageSwitch.class);
                IndexActivity.this.startActivity(intent3);
                break;
            case R.id.ll_wenti:
                // ToDo
                Intent intent2 = new Intent(IndexActivity.this, InstructionActivity.class);
                startActivity(intent2);
                break;
            case R.id.ll_website:
                // ToDo
                openWeb();
                break;

        }
    }
}
