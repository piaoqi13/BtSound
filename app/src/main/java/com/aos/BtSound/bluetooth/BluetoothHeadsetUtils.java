package com.aos.BtSound.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;

import com.aos.BtSound.log.DebugLog;

import java.util.List;

/**
 * Created by linky on 15-9-19.
 * modify by collin on 2015-10-15.delete annotation
 */

public abstract class BluetoothHeadsetUtils {
    private static final String TAG = "BluetoothHeadsetUtils";
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothDevice mConnectedHeadset;
    private AudioManager mAudioManager;
    private boolean mIsCountDownOn;
    private boolean mIsStarting;
    private boolean mIsOnHeadsetSco;
    private boolean mIsStarted;

    public BluetoothHeadsetUtils(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean start() {
        if (!mIsStarted) {
            mIsStarted = true;
            DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:start " + "SDK_INT: " + Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                mIsStarted = startBluetooth();
            } else {
                mIsStarted = startBluetooth11();
            }
        }
        return mIsStarted;
    }

    public void stop() {
        if (mIsStarted) {
            mIsStarted = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                stopBluetooth();
            } else {
                stopBluetooth11();
            }
        }
    }

    public boolean isOnHeadsetSco() {
        return mIsOnHeadsetSco;
    }

    public abstract void onHeadsetDisconnected();

    public abstract void onHeadsetConnected();

    public abstract void onScoAudioDisconnected();

    public abstract void onScoAudioConnected();

    @SuppressWarnings("deprecation")
    private boolean startBluetooth() {
        Log.d(TAG, "startBluetooth");
        if (mBluetoothAdapter != null) {
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
                mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
                mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
                mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                mIsCountDownOn = true;
                mCountDown.start();
                mIsStarting = true;
                return true;
            }
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean startBluetooth11() {
        Log.d(TAG, "startBluetooth11");
        if (mBluetoothAdapter != null) {
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                if (mBluetoothAdapter.getProfileProxy(mContext, mHeadsetProfileListener, BluetoothProfile.HEADSET)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stopBluetooth() {
        DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:stopBluetooth ");
        if (mIsCountDownOn) {
            mIsCountDownOn = false;
            mCountDown.cancel();
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
        mAudioManager.stopBluetoothSco();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void stopBluetooth11() {
        Log.d(TAG, "stopBluetooth11"); //$NON-NLS-1$
        if (mIsCountDownOn) {
            mIsCountDownOn = false;
            mCountDown11.cancel();
        }
        if (mBluetoothHeadset != null) {
            mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
            mContext.unregisterReceiver(mHeadsetBroadcastReceiver);
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mBluetoothHeadset = null;
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @SuppressWarnings({"deprecation", "synthetic-access"})
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                mConnectedHeadset = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                BluetoothClass bluetoothClass = mConnectedHeadset.getBluetoothClass();
                if (bluetoothClass != null) {
                    int deviceClass = bluetoothClass.getDeviceClass();
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "deviceClass : " + deviceClass);
                    if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
                            || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {
                        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                        mIsCountDownOn = true;
                        mCountDown.start();
                        onHeadsetConnected();
                    }
                }
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + mConnectedHeadset.getName() + " connected");
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Headset disconnected");
                if (mIsCountDownOn) {
                    mIsCountDownOn = false;
                    mCountDown.cancel();
                }
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                onHeadsetDisconnected();
            } else if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED)) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + " SCO Audio State: " + state);
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    mIsOnHeadsetSco = true;
                    if (mIsStarting) {
                        mIsStarting = false;
                        onHeadsetConnected();
                    }
                    if (mIsCountDownOn) {
                        mIsCountDownOn = false;
                        mCountDown.cancel();
                    }
                    onScoAudioConnected();
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Sco connected");
                } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Sco disconnected");
                    if (!mIsStarting) {
                        mIsOnHeadsetSco = false;
                        mAudioManager.stopBluetoothSco();
                        onScoAudioDisconnected();
                    }
                }
            }
        }
    };

    private CountDownTimer mCountDown = new CountDownTimer(10000, 1000) {
        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished) {
            mAudioManager.startBluetoothSco();
            Log.d(TAG, "\nonTick start bluetooth Sco");
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish() {
            mIsCountDownOn = false;
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
            DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onFinish " + "\nonFinish fail to connect to headset audio");
        }
    };

    private BluetoothProfile.ServiceListener mHeadsetProfileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "Profile listener onServiceDisconnected");
            stopBluetooth11();
        }

        @SuppressWarnings("synthetic-access")
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "Profile listener onServiceConnected");
            mBluetoothHeadset = (BluetoothHeadset) proxy;
            List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
            if (devices.size() > 0) {
                mConnectedHeadset = devices.get(0);
                onHeadsetConnected();
                mIsCountDownOn = true;
                mCountDown11.start();
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onServiceConnected " + "Start count down");
            }
            mContext.registerReceiver(mHeadsetBroadcastReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
            mContext.registerReceiver(mHeadsetBroadcastReceiver, new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
        }
    };

    private BroadcastReceiver mHeadsetBroadcastReceiver = new BroadcastReceiver() {
        @SuppressWarnings("synthetic-access")
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state;
            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "\nAction = " + action + " State = " + state);
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    mConnectedHeadset = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mIsCountDownOn = true;
                    mCountDown11.start();
                    onHeadsetConnected();
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Start count down");
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    if (mIsCountDownOn) {
                        mIsCountDownOn = false;
                        mCountDown11.cancel();
                    }
                    mConnectedHeadset = null;
                    onHeadsetDisconnected();
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Headset disconnected");
                }
            } else { // audio
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive "
                        + "Action = " + action + " State = " + state);
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    DebugLog.d(DebugLog.TAG, "BluetoothHeadsetUtils:onReceive " + "Headset audio connected");
                    mIsOnHeadsetSco = true;
                    if (mIsCountDownOn) {
                        mIsCountDownOn = false;
                        mCountDown11.cancel();
                    }
                    onScoAudioConnected();
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    mIsOnHeadsetSco = false;
                    mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
                    onScoAudioDisconnected();
                    Log.d(TAG, "Headset audio disconnected");
                }
            }
        }
    };

    private CountDownTimer mCountDown11 = new CountDownTimer(10000, 1000) {
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished) {
            mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset);
            Log.d(TAG, "onTick startVoiceRecognition");
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish() {
            mIsCountDownOn = false;
            Log.d(TAG, "\nonFinish fail to connect to headset audio");
        }
    };
}
