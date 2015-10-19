package com.aos.BtSound;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Toast;

import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.contact.ObtainContactsUtil;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.receiver.SMSReceiver;
import com.aos.BtSound.recorder.MyMediaRecorder;
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
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

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

    private SpeechRecognizer mSpeechRecognizer = null;  // 语音对象
    private BluetoothAdapter mBluetoothAdapter = null;  // 蓝牙适配器
    private RecognizerDialog mRecognizerDialog = null;  // 语音对话框
    private String mSmsBody = null;                     // 短信内容
    private SoundPool mSoundPool = null;
    private boolean mIsStarted = false;

    private WakeUpRecognizer mWakeUpRecognizer = null;  // 唤醒对象
    private Vibrator mVibrator = null;                  // 唤醒震动

    private int mStartCount = 0;                        // 控制信息，尝试打开蓝牙麦克风次数；

    private ContentObserver mContentObserver = null;

    // 跟踪语音唤醒是否开启；
    private boolean mIsWakeUpStarted = false;

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

    private MyMediaRecorder mMyMediaRecorder;

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
                    break;

                case 2:
                    mSoundPool.release();
                    break;
                case 4444:
                    upDateDictionary();
                    break;
            }
        }
    };

    // 蓝牙辅助类
    private BluetoothHelper mBluetoothHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mBluetoothHelper = new BluetoothHelper(this);
        mContext = this;

        initView();
        setListener();

        // 获取联系人信息
        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();

        // 友盟更新初始化
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

        mContentObserver = new SMSReceiver(mHandler, this);
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, mContentObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(mPageName);
        MobclickAgent.onResume(this);
        mEdtTransformResult.setText("Speak Result");
        DebugLog.d(DebugLog.TAG, "MainActivity:onResume " + "");

        initWakeUp();               // 初始化唤醒
        wakeUpStart();              // 启动唤醒
        //initRecognizer();           // 初始化语音识别
        mBluetoothHelper.start();   // 启动、检测蓝牙模块
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
        // 第一次上传联系人
//        if (Settings.getBoolean(Config.IS_FIRST_TIME, true, false)) {
//            ContactManager mgr = ContactManager.createManager(mContext, mContactListener);
//            mgr.asyncQueryAllContactsName();
//        }
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
                if (text.equals("")) {
                    showTip("请清晰说话噢");
                } else {
                    if (text.contains("打电话")) {
                        mEdtTransformResult.setText(text);
                        String contactName = text.substring(text.indexOf("【") + 1, text.indexOf("】"));
                        for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                            if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                MainActivity.this.startActivity(intent);
                                break;
                            }
                        }
                    } else if (text.contains("拍照") && VoiceCellApplication.mSc > 60) {
                        mEdtTransformResult.setText(text);
                        Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                        MainActivity.this.startActivity(intent);
                    } else if (text.contains("拍照") && VoiceCellApplication.mSc <= 60) {
                        mWakeUpRecognizer.start();
                    } else if (text.contains("录音")) {
                        onClick(findViewById(R.id.btn_recorder));
                    }
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
            mWakeUpRecognizer.start();
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

                mMyMediaRecorder = new MyMediaRecorder();
                mMyMediaRecorder.startRecording();
                mHandler.sendEmptyMessageDelayed(1, 10 * 1000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        showTip("正在录音，10s 中后自动结束");
                    }
                });

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
                            // release sound
                            mHandler.obtainMessage(2).sendToTarget();
                            mIsStarted = false;
                            //showSpeakDialog();
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

//    private void showSpeakDialog() {
//        mEdtTransformResult.setText(null);
//        setParam();
//        boolean isShowDialogII = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
//        if (isShowDialogII) {
//            mRecognizerDialog.setListener(recognizerDialogListener);
//            mRecognizerDialog.show();
//            mWakeUpRecognizer.stop();
//        } else {
//            int errorCode = mSpeechRecognizer.startListening(recognizerListener);
//            if (errorCode != ErrorCode.SUCCESS) {
//                Toast toastII = Toast.makeText(mContext, "听写失败" + errorCode, Toast.LENGTH_LONG);
//                toastII.show();
//            }
//        }
//
//    }

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                Toast toast = Toast.makeText(mContext, "初始化失败", Toast.LENGTH_LONG);
                toast.show();
            }
        }
    };

    private RecognizerListener recognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            //ToDo
        }

        @Override
        public void onEndOfSpeech() {
            //ToDo
        }

        @Override
        public void onError(SpeechError error) {
            DebugLog.i("CollinWang", "Information=" + error.getPlainDescription(true));
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            mEdtTransformResult.append(text);
            mEdtTransformResult.setSelection(mEdtTransformResult.length());
            if (isLast) {
                //ToDo
            }
        }

        @Override
        public void onVolumeChanged(int volume) {
            //ToDo
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            //ToDo
        }
    };

    private RecognizerDialogListener recognizerDialogListener = new RecognizerDialogListener() {


        public void onResult(RecognizerResult result, boolean isLast) {
            DebugLog.d(DebugLog.TAG, "MainActivity:onResult "
                    + "CollinWang" + "RecognizerResult=" + result.getResultString());

            String text = JsonParser.parseIatResult(result.getResultString());
            mEdtTransformResult.append(text);
            mEdtTransformResult.setSelection(mEdtTransformResult.length());

            if (mEdtTransformResult.getText().toString().contains("拍照")) {
                Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                MainActivity.this.startActivity(intent);
            }

            if (VoiceCellApplication.mContacts != null) {   // 联系人不是空
                if (mEdtTransformResult.getText().toString().contains("打电话")) {// 是打电话
                    if (!FucUtil.getNumber(mEdtTransformResult.getText().toString()).equals("")) {
                        String number = FucUtil.getNumber(mEdtTransformResult.getText().toString());
                        if (FucUtil.isAvailableMobilePhone(number)) {
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                            MainActivity.this.startActivity(intent);
                        }
                    } else {
                        for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                            if (text.contains(VoiceCellApplication.mContacts.get(i).getName()) ||
                                    VoiceCellApplication.mContacts.get(i).getName().contains(text.replaceAll("[\\p{Punct}\\s]+", "").replace("打电话给", ""))) {
                                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                                MainActivity.this.startActivity(intent);
                                break;
                            }
                        }
                    }
                } else if (mEdtTransformResult.getText().toString().contains("发短信")) {  // 发短信
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                            Uri smsToUri = Uri.parse("smsto:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber());
                            Intent intent = new Intent(Intent.ACTION_SENDTO, smsToUri);
                            intent.putExtra("sms_body", mSmsBody);
                            MainActivity.this.startActivity(intent);
                            break;
                        }
                    }
                } else {// 拿到短信内容
                    mSmsBody = mEdtTransformResult.getText().toString();
                }
            } else {
                DebugLog.d(DebugLog.TAG, "MainActivity:onResult " + "CollinWang" + "没有联系人");
            }

            // 科大讯飞结束 云知声走起
//            mWakeUpRecognizer.start();
        }

        public void onError(SpeechError error) {
            DebugLog.d(DebugLog.TAG, "MainActivity:onError "
                    + "CollinWang" + "Information=" + error.getPlainDescription(true));
        }
    };

    // 初始化本地离线唤醒
    private void initWakeUp() {
        if(mWakeUpRecognizer != null) return;

        mWakeUpRecognizer = new WakeUpRecognizer(this, Config.appKey);
        mWakeUpRecognizer.setListener(new WakeUpRecognizerListener() {

            @Override
            public void onWakeUpRecognizerStart() {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpRecognizerStart "
                        + "CollinWang" + "语音唤醒已开始");
                Toast.makeText(MainActivity.this, "语音唤醒已开始", Toast.LENGTH_SHORT).show();
                mIsWakeUpStarted = true;
            }

            @Override
            public void onWakeUpError(USCError error) {
                if (error != null) {
                    DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpError "
                            + "CollinWang" + "Information=" + error.toString());
                }
                mIsWakeUpStarted = false;
            }

            @Override
            public void onWakeUpRecognizerStop() {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpRecognizerStop "
                        + "CollinWang" + "语音唤醒已停止");
                mIsWakeUpStarted = false;
            }

            @Override
            public void onWakeUpResult(boolean succeed, String text, float score) {
                DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult " + "succeed : " + succeed);
                if (succeed) {
                    mVibrator.vibrate(300);
                    DebugLog.d(DebugLog.TAG, "MainActivity:onWakeUpResult "
                            + "CollinWang" + "语音唤醒成功");

                    // 云知声结束停止
                    mWakeUpRecognizer.stop();
                    //showSpeakDialog();
                    if (!setParam()) {
                        showTip("请构建语法再语音识别");
                        return;
                    }
                    ret = mAsr.startListening(mRecognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("识别未成功，错误码: " + ret);
                    }
                }
            }
        });
    }

    // 启动语音唤醒
    protected void wakeUpStart() {
        if(mWakeUpRecognizer.isRunning()) return;
            mWakeUpRecognizer.start();
    }

    private void initRecognizer() {
        if(mSpeechRecognizer == null)
            // 初始化语音模块
            mSpeechRecognizer = SpeechRecognizer.createRecognizer(this, mInitListener);

        if(mRecognizerDialog == null) {
            mRecognizerDialog = new RecognizerDialog(this, mInitListener);

            mRecognizerDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    DebugLog.d(DebugLog.TAG, "MainActivity:onDismiss " + "");
                    if(mWakeUpRecognizer != null)
                        mWakeUpRecognizer.start();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        DebugLog.d(DebugLog.TAG, "MainActivity:onPause " + "");

        MobclickAgent.onPageEnd(mPageName);
        MobclickAgent.onPause(this);
        mBluetoothHelper.stop();
//        stopSppechRecognizer();
        stopWakeupRecognizer();
    }

//    private void stopSppechRecognizer() {
//        mSpeechRecognizer.cancel();
//        mSpeechRecognizer.destroy();
//        mSpeechRecognizer = null;
//
//        if(mRecognizerDialog.isShowing())
//            mRecognizerDialog.dismiss();
//        mRecognizerDialog = null;
//    }

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
}
