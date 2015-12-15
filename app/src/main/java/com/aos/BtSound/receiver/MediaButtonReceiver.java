package com.aos.BtSound.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

/**
 * created by collin on 2015-11-23.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
//        KeyEvent key = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
//        int keycode = key.getKeyCode();
//        if (keycode == KeyEvent.KEYCODE_MEDIA_PLAY) {
//            Log.i("CollinWang", "KeyEvent.KEYCODE_MEDIA_PLAY");
//        } else if (keycode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
//            Log.i("CollinWang", "KeyEvent.KEYCODE_MEDIA_PAUSE");
//        } else {
//            Log.i("CollinWang", "Others");
//        }
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent key = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            int keycode = key.getKeyCode();
            Log.i("CollinWang", "keycode=" + keycode);
        }
    }
}
