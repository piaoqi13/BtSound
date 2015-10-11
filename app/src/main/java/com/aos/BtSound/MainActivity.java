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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
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
import com.iflytek.cloud.ErrorCode;
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
    private EditText mEdtTransformResult = null;

    private SpeechRecognizer mSpeechRecognizer = null;  // 语音对象
    private SharedPreferences mSharedPreferences = null;// 存储对象
    private AudioManager mAudioManager = null;          // 音频管理类
    private BluetoothAdapter mBluetoothAdapter = null;  // 蓝牙适配器
    private RecognizerDialog mRecognizerDialog = null;  // 语音对话框
    private String mSmsBody = null;                     // 短信内容
    private SoundPool mSoundPool = null;
    private boolean mIsStarted = false;

    private WakeUpRecognizer mWakeUpRecognizer = null;  // 唤醒对象
    private Vibrator mVibrator = null;                   // 唤醒震动

    private int mStartCount = 0;                        // 控制信息，尝试打开蓝牙麦克风次数；

    private ContentObserver mContentObserver = null;

    // 跟踪语音唤醒是否开启；
    private boolean mIsWakeUpStarted = false;

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (!mIsStarted)
                        showSoundHint();
                    break;
                case 1:
                    Toast.makeText(MainActivity.this, "启动蓝牙麦克风失败", Toast.LENGTH_SHORT).show();
                    mAudioManager.stopBluetoothSco();
                    break;

                case 2:
                    mSoundPool.release();
                    break;
            }
        }
    };

    // 蓝牙辅助类
    private BluetoothHelper mBluetoothHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mBluetoothHelper = new BluetoothHelper(this);
        mContext = this;

        initView();
        setListener();

        // 获取联系人信息
        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();

        // 友盟更新初始化
        UmengUpdateAgent.update(this);

        mContentObserver = new SMSReceiver(mHandler, this);
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, mContentObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageEnd(mPageName);
        MobclickAgent.onResume(this);
        DebugLog.d(DebugLog.TAG, "MainActivity:onResume " + "");

        initWakeUp();               // 初始化唤醒
        wakeUpStart();              // 启动唤醒
        initRecognizer();           // 初始化语音识别
        mBluetoothHelper.start();   // 启动、检测蓝牙模块
    }

    private void initView() {
        mBtnTakeCall = (Button) findViewById(R.id.btn_take_call);
        mBtnSendMessages = (Button) findViewById(R.id.btn_send_messages);
        mBtnNavigation = (Button) findViewById(R.id.btn_navigation);
        mBtnWeb = (Button) findViewById(R.id.btn_web);
        mBtnSettings = (Button) findViewById(R.id.settings);
        mEdtTransformResult = (EditText) findViewById(R.id.edt_result_text);

        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        // 第一次上传联系人
        if (Settings.getBoolean(Config.IS_FIRST_TIME, true, false)) {
            ContactManager mgr = ContactManager.createManager(mContext, mContactListener);
            mgr.asyncQueryAllContactsName();
        }
    }

    private void setListener() {
        mBtnTakeCall.setOnClickListener(this);
        mBtnSendMessages.setOnClickListener(this);
        mBtnNavigation.setOnClickListener(this);
        mBtnWeb.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_call:
                showSpeakDialog();
                break;
            case R.id.btn_send_messages:
                Intent intent3 = new Intent(MainActivity.this, MessageSwitch.class);
                MainActivity.this.startActivity(intent3);
                break;
            case R.id.btn_navigation:
                Intent intent = new Intent(MainActivity.this, AndroidCameraActivity.class);
                MainActivity.this.startActivity(intent);
                break;
            case R.id.btn_web:
                openWeb();
                break;
            case R.id.settings:
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
                            showSpeakDialog();
                        }
                    }, 4000);
                }
            }
        });
    }

    public void playSound(int id) {
        mSoundPool.play(id, 0.5f, 0.5f, 1, 1, 1f);
    }

    private void showSpeakDialog() {
        mEdtTransformResult.setText(null);
        setParam();
        boolean isShowDialogII = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
        if (isShowDialogII) {
            mRecognizerDialog.setListener(recognizerDialogListener);
            mRecognizerDialog.show();
            mWakeUpRecognizer.stop();
        } else {
            int errorCode = mSpeechRecognizer.startListening(recognizerListener);
            if (errorCode != ErrorCode.SUCCESS) {
                Toast toastII = Toast.makeText(mContext, "听写失败" + errorCode, Toast.LENGTH_LONG);
                toastII.show();
            }
        }

    }

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

    public void setParam() {
        mSpeechRecognizer.setParameter(SpeechConstant.PARAMS, null);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        mSpeechRecognizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        if (lag.equals("en_us")) {
            mSpeechRecognizer.setParameter(SpeechConstant.LANGUAGE, "en_us");
        } else {
            mSpeechRecognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            mSpeechRecognizer.setParameter(SpeechConstant.ACCENT, lag);
        }
        mSpeechRecognizer.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
        mSpeechRecognizer.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
        mSpeechRecognizer.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
        mSpeechRecognizer.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/iflytek/wavaudio.pcm");
    }

    private ContactListener mContactListener = new ContactListener() {
        @Override
        public void onContactQueryFinish(String contactInfos, boolean changeFlag) {
            mSpeechRecognizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mSpeechRecognizer.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            int ret = mSpeechRecognizer.updateLexicon("contact", contactInfos, lexiconListener);
            if (ret != ErrorCode.SUCCESS) {
                //ToDo
            }
        }
    };

    // 上传联系人监听器
    private LexiconListener lexiconListener = new LexiconListener() {
        @Override
        public void onLexiconUpdated(String lexiconId, SpeechError error) {
            if (error != null) {
                //ToDo
            } else {
                DebugLog.d(DebugLog.TAG, "MainActivity:onLexiconUpdated "
                        + "CollinWang" + "上传联系人成功");
            }
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
                //Toast.makeText(MainActivity.this, "语音唤醒已开始", Toast.LENGTH_SHORT).show();
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
                    showSpeakDialog();
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
        MobclickAgent.onResume(this);
        mBluetoothHelper.stop();
        stopSppechRecognizer();
        stopWakeupRecognizer();
    }

    private void stopSppechRecognizer() {
        mSpeechRecognizer.cancel();
        mSpeechRecognizer.destroy();
        mSpeechRecognizer = null;

        if(mRecognizerDialog.isShowing())
            mRecognizerDialog.dismiss();
        mRecognizerDialog = null;
    }

    private void stopWakeupRecognizer() {
        mWakeUpRecognizer.stop();
        mWakeUpRecognizer.cancel();
        mWakeUpRecognizer = null;
    }

    // inner class
    // BluetoothHeadsetUtils is an abstract class that has
    // 4 abstracts methods that need to be implemented.
    private class BluetoothHelper extends BluetoothHeadsetUtils {
        public BluetoothHelper(Context context) {
            super(context);
        }

        @Override
        public void onScoAudioDisconnected() {
            // Cancel speech recognizer if desired
            DebugLog.d(DebugLog.TAG, "BluetoothHelper:onScoAudioDisconnected " + "");
        }

        @Override
        public void onScoAudioConnected() {
            // Should start speech recognition here if not already started
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
