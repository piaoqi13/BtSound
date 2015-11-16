package com.aos.BtSound.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.aos.BtSound.R;
import com.aos.BtSound.VoiceCellApplication;
import com.aos.BtSound.bluetooth.BluetoothHeadsetUtils;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.model.SmsInfo;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;
import com.aos.BtSound.setting.TtsSettings;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;

import java.util.List;


public class SMSReceiver extends ContentObserver {
	/** 所有的短信 */
	public static final String SMS_URI_ALL = "content://sms/";
	/** 收件箱短信 */
	public static final String SMS_URI_INBOX = "content://sms/inbox";
	/** 发件箱短信 */
	public static final String SMS_URI_SEND = "content://sms/sent";
	/** 草稿箱短信 */
	public static final String SMS_URI_DRAFT = "content://sms/draft";

	private Activity mActivity = null;
	private Handler mHandler = null;
	private List<SmsInfo> mSmsInfos = null;

	private Context mContext = null;
	private SpeechSynthesizer mTts = null;
	private SharedPreferences mSharedPreferences = null;
	public static String voicerCloud = "xiaoyan";
	public static String voicerLocal = "xiaoyan";
	private int mPercentForBuffering = 0;
	private int mPercentForPlaying = 0;
	private Toast mToast = null;

	private String mEngineType = SpeechConstant.TYPE_CLOUD;

    /** 蓝牙检测 */
    private BluetoothHeadsetUtils mBlueHelper;

	public SMSReceiver(Handler handler, Context context, BluetoothHeadsetUtils bluetoothHelper) {
		super(new Handler());
		this.mActivity = (Activity)context;
		this.mHandler = handler;
        mBlueHelper = bluetoothHelper;

		mTts = SpeechSynthesizer.createSynthesizer(context, mTtsInitListener);
		mToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
		mSharedPreferences = context.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
	}

	@Override
	public void onChange(boolean selfChange) {
		if (Settings.getBoolean(Config.IS_READ_SMS, true, false)) {
			Uri uri = Uri.parse(SMS_URI_INBOX);
			SMSContent smscontent = new SMSContent(mActivity, uri);
			mSmsInfos = smscontent.getSmsInfo();
			if (!mSmsInfos.isEmpty()) {
				for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
					if (mSmsInfos.get(0).getPhoneNumber().replace("+86", "").equals(VoiceCellApplication.mContacts.get(i).getPhoneNumber().replace(" ", ""))) {
						setParam();
                        // 断开蓝牙SCO连接
                        if(mBlueHelper != null && mBlueHelper.isOnHeadsetSco())
                            mBlueHelper.stop();
						int code = mTts.startSpeaking("有短信息进来，内容是" + mSmsInfos.get(0).getSmsbody(), mTtsListener);
						if (code != ErrorCode.SUCCESS) {
							showTip("语音合成未成功，错误码: " + code);
						} else {
							DebugLog.i("CollinWang", "code=" + code);
						}
						break;
					} else {
						DebugLog.i("CollinWang", "phoneNumber=" + VoiceCellApplication.mContacts.get(i).getPhoneNumber());
					}

				}
			}
		}
		super.onChange(selfChange);
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
                // 播放完成后，连接上蓝牙SCO
                if(mBlueHelper != null && !mBlueHelper.isOnHeadsetSco())
                    mBlueHelper.start();
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
