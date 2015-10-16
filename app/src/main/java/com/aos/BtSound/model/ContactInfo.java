package com.aos.BtSound.model;

/**
 * created by collin on 2015-08-06.
 */

public class ContactInfo {
    private String name = null;
    private String phoneNumber = null;

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
