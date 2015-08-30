package com.aos.BtSound.contact;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

import com.aos.BtSound.VoiceCellApplication;
import com.aos.BtSound.model.ContactInfo;

/**
*类名：ObtainContactsUtil.java
*注释：获取联系人工具类
*日期：2015年8月5日
*/
public class ObtainContactsUtil {
	private static ObtainContactsUtil mObtainContactsUtil = null;
	private static Context mContext = null;

	private static final String[] PHONES_PROJECTION = new String[] {Phone.DISPLAY_NAME, Phone.NUMBER };
	private static final int PHONES_DISPLAY_NAME_INDEX = 0;
	private static final int PHONES_NUMBER_INDEX = 1;

	private ObtainContactsUtil() {
		//ToDo
	}

	public static ObtainContactsUtil getInstance(Context context) {
		mContext = context;
		if (mObtainContactsUtil == null) {
			mObtainContactsUtil = new ObtainContactsUtil();
		}
		return mObtainContactsUtil;
	}

	public void getPhoneContacts() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor phoneCursor = resolver.query(Phone.CONTENT_URI, PHONES_PROJECTION, null, null, null);
		if (phoneCursor != null) {
			while (phoneCursor.moveToNext()) {
				// 联系人号码
				String phoneNumber = phoneCursor.getString(PHONES_NUMBER_INDEX);
				if (TextUtils.isEmpty(phoneNumber)) {
					continue;
				}
				// 联系人名称
				String contactName = phoneCursor.getString(PHONES_DISPLAY_NAME_INDEX);
				VoiceCellApplication.mContacts.add(new ContactInfo(contactName, phoneNumber));
			}
			phoneCursor.close();
		}
	}

}
