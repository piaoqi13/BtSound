package com.aos.BtSound;

/**
 * Created by wtt on 2015/8/30.
 */

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.ExifInterface;
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
        // Create our Preview view and set it as the content of our activity.
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        mCameraSurPreview = new CameraSurfacePreview(this);
        preview.addView(mCameraSurPreview);
        // Add a listener to the Capture button
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
        // save the picture to sdcard
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions: ");
            return;
        }

        String resultMessage = null;
        try {

            Bitmap bm0 = BitmapFactory.decodeByteArray(data, 0, data.length);

            Log.d(TAG, "a " + System.currentTimeMillis());
            Bitmap bm = convertBmp(bm0);
            Log.d(TAG, "b " + System.currentTimeMillis()+"");

            fos = new FileOutputStream(pictureFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            Log.d(TAG, "c " + System.currentTimeMillis());

            fos.flush();

            // 手动刷新系统相册
            MediaScannerConnection.scanFile(this,
                    new String[]{pictureFile.toString()}, null, null);

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

        matrix.postScale(-1, 1);       // 镜像水平翻转
        Bitmap convertBmp = Bitmap.createBitmap(bm, 0, 0, w, h, matrix, true);
        return convertBmp;
    }

    // 处理相册图片旋转问题；
    private Bitmap dealWithTriangle(byte[] data, String path) {

        if(data.length == 0) return null;

        // 获得 Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
//        int degree = readPictureDegree(path); // 先获取角度，然后旋转该角度；
        bitmap = rotaingImageView(90, bitmap);

        return bitmap;
    }

    /**
     * 旋转图片
     * @param angle     角度；
     * @param bitmap    图片；
     * @return Bitmap   a；
     */
    public Bitmap rotaingImageView(int angle , Bitmap bitmap) {
        // 旋转图片 动作
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        Log.d(TAG, "angle = " + angle);

        // 创建新的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return resizedBitmap;
    }

    /**
     * 读取图片属性：旋转的角度
     * @param path 图片绝对路径
     * @return degree旋转的角度
     */
    public static int readPictureDegree(String path) {
        int degree  = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    @Override
    public void onClick(View v) {
        // get an image from the camera
        mCameraSurPreview.takePicture(this);
    }

    /**
     *
     * @return
     */
    private File getOutputMediaFile() {
        // get the mobile Pictures directory
        String picPath = "/sdcard/DCIM/Camera/";
        File picDir = new File(picPath);
        if(!picDir.exists()) return null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(picPath + File.separator + "IMG_" + timeStamp + ".jpg");

    }
}
