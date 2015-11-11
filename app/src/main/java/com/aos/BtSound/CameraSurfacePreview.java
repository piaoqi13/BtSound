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

