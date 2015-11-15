package com.aos.BtSound;

/**
 * Created by wtt on 2015/8/30.
 */

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.receiver.PhoneReceiver;
import com.aos.BtSound.setting.TtsSettings;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AndroidCameraActivity extends Activity implements OnClickListener, PictureCallback {
    private Context mContext = null;
    private CameraSurfacePreview mCameraSurPreview = null;
    private Button mCaptureButton = null;
    private String TAG = "Dennis";
    private FileOutputStream fos = null;

    private SpeechSynthesizer mTts = null;
    private SharedPreferences mSharedPreferences = null;
    public static String voicerCloud = "xiaoyan";
    public static String voicerLocal = "xiaoyan";
    private String mEngineType = SpeechConstant.TYPE_LOCAL;
    private int mPercentForBuffering = 0;
    private int mPercentForPlaying = 0;
    private Toast mToast = null;
    private String tip = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mian);
        mContext = this;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // Create our Preview view and set it as the content of our activity.
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        mCameraSurPreview = new CameraSurfacePreview(this);
        preview.addView(mCameraSurPreview);
        // Add a listener to the Capture button
        mCaptureButton = (Button) findViewById(R.id.button_capture);
        mCaptureButton.setOnClickListener(this);

        mTts = SpeechSynthesizer.createSynthesizer(mContext, mTtsInitListener);
        mToast = Toast.makeText(mContext, "", Toast.LENGTH_SHORT);
        mSharedPreferences = mContext.getSharedPreferences(TtsSettings.PREFER_NAME, Activity.MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int code = mTts.startSpeaking("准备拍照，1，2，3", mTtsListener);
        if (code != ErrorCode.SUCCESS) {
            showTip("语音合成失败,错误码: " + code);
        } else {
            DebugLog.i("CollinWang", "code=" + code);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCameraSurPreview.takePicture(AndroidCameraActivity.this);
            }
        }, 3000);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        // save the picture to sdcard
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions: ");
            return;
        }

        String resultMessage = null;
        try {
            Bitmap bm0 = BitmapFactory.decodeByteArray(data, 0, data.length);
            // 旋转图片
            Bitmap bm = convertBmp(bm0);
            // 保存到本地
            fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            // 手动刷新系统相册
            MediaScannerConnection.scanFile(this, new String[]{pictureFile.toString()}, null, null);
            resultMessage = "照片保存在 " + pictureFile;
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
            resultMessage = e.getMessage();
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
            resultMessage = e.getMessage();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                resultMessage = e.getMessage();
            }
        }

        Toast.makeText(AndroidCameraActivity.this, resultMessage, Toast.LENGTH_SHORT).show();
        finish();
        // Restart the preview and re-enable the shutter button so that we can take another picture
        //        camera.startPreview();
    }

    private Bitmap convertBmp(Bitmap bmp) {
        Matrix m = new Matrix();
        m.setRotate(-90, (float) bmp.getWidth() / 2, (float) bmp.getHeight() / 2);
        Bitmap bm = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

        int w = bm.getWidth();
        int h = bm.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1);// 镜像水平翻转
        Bitmap convertBmp = Bitmap.createBitmap(bm, 0, 0, w, h, matrix, true);
        return convertBmp;
    }

    @Override
    public void onClick(View v) {
        // get an image from the camera
        mCameraSurPreview.takePicture(this);
    }

    private File getOutputMediaFile() {
        // get the mobile Pictures directory
        String picPath = "/sdcard/DCIM/Camera/";
        File picDir = new File(picPath);
        if (!picDir.exists())
            return null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(picPath + File.separator + "IMG_" + timeStamp + ".jpg");

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
            } else if (error != null) {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
        }
    };
}
