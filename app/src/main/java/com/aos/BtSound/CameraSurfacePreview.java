package com.aos.BtSound;

/**
 * Created by wtt on 2015/8/30.
 */

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class CameraSurfacePreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    Camera.Parameters parameters;
    // private boolean af;

    public CameraSurfacePreview(Context context) {
        super(context);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("Dennis", "surfaceCreated() is called");

        mCamera = Camera.open(1);
        try {
            // Open the Camera in preview mode
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.d("Dennis", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Log.d("Dennis", "surfaceChanged() is called");
        mCamera.setDisplayOrientation(90);
        try {
//            // 获得相机参数对象
//            Camera.Parameters parameters = mCamera.getParameters();
//            // 设置格式
//            parameters.setPictureFormat(PixelFormat.JPEG);
//            // 设置预览大小，这里我的测试机是Milsstone所以设置的是854x480
//            parameters.setPreviewSize(1920, 1088);
//            // 设置自动对焦
//            parameters.setFocusMode("auto");
//            // 设置图片保存时的分辨率大小
//            parameters.setPictureSize(2592, 1456);
//            // 给相机对象设置刚才设定的参数
//            mCamera.setParameters(parameters);

            mCamera.startPreview();
        } catch (Exception e) {
            Log.d("Dennis", "Error starting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        Log.d("Dennis", "surfaceDestroyed() is called");
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mCamera.autoFocus(null);
            // af = true;
        }
        return true;
    }

    public void takePicture(PictureCallback imageCallback) {
        mCamera.takePicture(null, null, imageCallback);
    }
}

