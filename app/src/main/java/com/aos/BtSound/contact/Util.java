package com.aos.BtSound.contact;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

import com.aos.BtSound.model.ContactInfo;

/**
*类名：Util.java
*注释：获取联系人列表
*日期：2015年8月5日
*/
public class Util {
	private Context mContext = null;
	private static final String[] PHONES_PROJECTION = new String[] {Phone.DISPLAY_NAME, Phone.NUMBER };

	private static final int PHONES_DISPLAY_NAME_INDEX = 0;
	private static final int PHONES_NUMBER_INDEX = 1;

	private List<ContactInfo> mContacts = new ArrayList<ContactInfo>();

	public Util(Context context) {
		this.mContext = context;
	}

	public void getPhoneContacts() {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor phoneCursor = resolver.query(Phone.CONTENT_URI, PHONES_PROJECTION, null, null, null);
		if (phoneCursor != null) {
			while (phoneCursor.moveToNext()) {
				// 拿到手机号码
				String phoneNumber = phoneCursor.getString(PHONES_NUMBER_INDEX);
				// 当手机号码为空或者为空字段跳过当前循环
				if (TextUtils.isEmpty(phoneNumber)) {
					continue;
				}
				// 拿到联系人名称
				String contactName = phoneCursor.getString(PHONES_DISPLAY_NAME_INDEX);
				mContacts.add(new ContactInfo(contactName, phoneNumber));
			}
			phoneCursor.close();
		}
	}

	public List<ContactInfo> getContactInfo() {
		return mContacts;
	}

}
