package com.aos.BtSound;

/**
 * Created by wtt on 2015/8/30.
 * Modify by collin on 2015-10-15. delete annotation
 */

import android.app.Activity;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AndroidCameraActivity extends Activity implements OnClickListener, PictureCallback {
    private CameraSurfacePreview mCameraSurPreview = null;
    private Button mCaptureButton = null;
    private String TAG = "Dennis";
    FileOutputStream fos = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mian);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        mCameraSurPreview = new CameraSurfacePreview(this);
        preview.addView(mCameraSurPreview);
        mCaptureButton = (Button) findViewById(R.id.button_capture);
        mCaptureButton.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCameraSurPreview.takePicture(AndroidCameraActivity.this);
            }
        }, 2000);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
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
        mCameraSurPreview.takePicture(this);
    }

    private File getOutputMediaFile() {
        String picPath = "/sdcard/DCIM/Camera/";
        File picDir = new File(picPath);
        if (!picDir.exists())
            return null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(picPath + File.separator + "IMG_" + timeStamp + ".jpg");

    }
}
