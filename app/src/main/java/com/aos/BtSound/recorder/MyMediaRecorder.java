package com.aos.BtSound.recorder;

import android.media.MediaRecorder;
import android.os.Environment;

import com.aos.BtSound.log.DebugLog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 初始化时获得 MediaRecorder 对象
 * 调用 startRecording() 表示开始录音
 * 调用 stopRecording() 表示结束录音
 *
 * 音频文件存储在 sd 卡的 audio 目录下
 */
public class MyMediaRecorder {

    private MediaRecorder mMediaRecorder;

    public MyMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    // 开始录音；
    public void startRecording() {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setOutputFile(getOutputFile());
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            DebugLog.d(DebugLog.TAG, "MyMediaRecorder:startRecording " + "prepare() failed");
        }

        mMediaRecorder.start();
    }

    // 结束录音；
    public void stopRecording() {
        if(mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    // 音频输出文件保存地址；
    private String getOutputFile() {

        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        File audPath = new File(sdCard + File.separator + "audio");

        if(audPath.mkdirs() || audPath.isDirectory()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            return audPath + File.separator + "AUD_" + timeStamp + ".mp3";
        } else {
            throw new IllegalArgumentException("sd card does not exist or lack of memory");
        }
    }
}
