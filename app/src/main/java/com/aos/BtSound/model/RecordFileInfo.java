package com.aos.BtSound.model;

/**
 * created by collin on 2015-11-11.
 */
public class RecordFileInfo {
    private String fileName = null;
    private String filePath = null;


    public RecordFileInfo(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
