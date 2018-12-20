package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class CloudService extends Service {

    private static final String TAG = CloudService.class.getSimpleName();

    private CloudBinder mBinder = new CloudBinder();
    private AudioEncoderService mService;
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioEncoderService.AudioEncoderBinder binder = (AudioEncoderService.AudioEncoderBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, AudioEncoderService.class);
        Log.d(TAG,"Binding AudioEncoderService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return mBinder;
    }

    class CloudBinder extends Binder {
        CloudService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CloudService.this;
        }
    }
}
