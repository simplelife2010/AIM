package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.Date;

public class FileWriterService extends Service implements AudioCollectorListener {

    private static final String TAG = "FileWriterService";

    private final IBinder mBinder = new FileWriterService.FileWriterBinder();
    AudioCollectorService mService;
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioCollectorService.AudioCollectorBinder binder = (AudioCollectorService.AudioCollectorBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerAudioCollectorListener(FileWriterService.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, AudioCollectorService.class);
        Log.d(TAG,"Binding AudioCollectorService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mService.unregisterAudioCollectorListener(this);
        Log.d(TAG, "Unbinding AudioCollectorService");
        unbindService(mConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"Service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public void onNewAudioFrame(long timestamp, short[] audioData) {
        Log.i(TAG, "Received audio frame of " + audioData.length + " samples with timestamp: " + (new Date(timestamp)).toString());
        String audioDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/AIM";
        new File(audioDirectory).mkdirs();
        String audioFilename = "AudioCollector_" + String.valueOf(timestamp) + ".wav";
        Log.d(TAG,"Destination directory for audio files: " + audioDirectory);
        AudioUtils.writeWavFile(audioDirectory + "/" + audioFilename, audioData);
    }

    class FileWriterBinder extends Binder {
        FileWriterService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FileWriterService.this;
        }
    }
}
