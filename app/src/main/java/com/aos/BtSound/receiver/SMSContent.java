package com.aos.BtSound.receiver;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.aos.BtSound.model.SmsInfo;

import java.util.ArrayList;
import java.util.List;

public class SMSContent {
	private Activity mActivity = null;
	private Uri mUri = null;
	private List<SmsInfo> mSmsInfos = null;

	public SMSContent(Activity activity, Uri uri) {
		mSmsInfos = new ArrayList<SmsInfo>();
		this.mActivity = activity;
		this.mUri = uri;
	}

	@SuppressWarnings("deprecation")
	public List<SmsInfo> getSmsInfo() {
		Cursor cusor = null;
		try {
			String[] projection = new String[] { "_id", "address", "person", "body", "date", "type" };
			cusor = mActivity.managedQuery(mUri, projection, null, null, "date desc");
			int nameColumn = cusor.getColumnIndex("person");
			int phoneNumberColumn = cusor.getColumnIndex("address");
			int smsbodyColumn = cusor.getColumnIndex("body");
			int dateColumn = cusor.getColumnIndex("date");
			int typeColumn = cusor.getColumnIndex("type");
			if (cusor != null) {
				while (cusor.moveToFirst()) {
					SmsInfo smsinfo = new SmsInfo();
					smsinfo.setName(cusor.getString(nameColumn));
					smsinfo.setDate(cusor.getString(dateColumn));
					smsinfo.setPhoneNumber(cusor.getString(phoneNumberColumn));
					smsinfo.setSmsbody(cusor.getString(smsbodyColumn));
					smsinfo.setType(cusor.getString(typeColumn));
					mSmsInfos.add(smsinfo);
					break;
				}
			}
		} catch (Exception e) {
			Log.e("SMSConcent", "information=", e);
		} finally {
			cusor.close();
		}
		return mSmsInfos;
	}
}
