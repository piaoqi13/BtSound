package com.aos.BtSound;

import android.app.Application;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

/**
 *类名：VoiceCellApplication.java
 *注释：程序入口
 *日期：2015年8月5日
 *作者：王超
 */
public class VoiceCellApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		// 初始化语音模块
		StringBuffer param = new StringBuffer();
		param.append("appid=4fd96987");
		param.append(",");
		param.append(SpeechConstant.ENGINE_MODE+"="+SpeechConstant.MODE_MSC);
		SpeechUtility.createUtility(this, param.toString());
	}
}
