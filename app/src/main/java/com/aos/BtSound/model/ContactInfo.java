package com.aos.BtSound.model;

/**
 * 类名：ContactInfo.java
 * 注释：联系人信息实体类
 * 日期：2015年8月6日
 * 作者：王超
 */
public class ContactInfo {
    private String name = "";
    private String phoneNumber = "";

    public ContactInfo(String name, String phoneNumber) {
        super();
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

}
