package com.aos.BtSound;

import android.app.Application;
import android.content.Context;

import com.aos.BtSound.model.ContactInfo;
import com.aos.BtSound.preference.Settings;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.umeng.analytics.MobclickAgent;

import java.util.ArrayList;
import java.util.List;

/**
 * created by collin on 2015-08-05.
 * modify by collin on 2015-10-15.
 */
public class VoiceCellApplication extends Application {
	public static Context mApplication = null;
	public static List<ContactInfo> mContacts = new ArrayList<ContactInfo>();
	public static boolean isReadSMS = true;

	@Override
	public void onCreate() {
		super.onCreate();
		mApplication = this;
		StringBuffer param = new StringBuffer();
		param.append("appid="+getString(R.string.app_id));
		param.append(",");
		param.append(SpeechConstant.ENGINE_MODE + "=" + SpeechConstant.MODE_MSC);
		SpeechUtility.createUtility(this, param.toString());
		Settings.initPreferences(mApplication);
		MobclickAgent.openActivityDurationTrack(false);
		MobclickAgent.setCatchUncaughtExceptions(true);
	}

}
