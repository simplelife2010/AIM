package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class AudioCollectorService extends Service {

    private final IBinder mBinder = new AudioCollectorBinder();
    private AudioRecord mRecorder;

    public AudioCollectorService() {
    }

    public class AudioCollectorBinder extends Binder {
        AudioCollectorService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AudioCollectorService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mRecorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(sharedPreferences.getInt(getString(R.string.pref_encoding_key),123))
                        .setSampleRate(sharedPreferences.getInt(getString(R.string.pref_sample_rate_key), 123))
                        .setChannelMask(sharedPreferences.getInt(getString(R.string.pref_channel_config_key),123))
                        .build())
                .setBufferSizeInBytes(sharedPreferences.getInt(getString(R.string.pref_buffer_size_key),123))
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
