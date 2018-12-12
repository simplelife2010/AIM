package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioCollectorService extends Service {

    private static final String TAG = "AudioCollectorService";

    private final IBinder mBinder = new AudioCollectorBinder();

    private AudioCollectorWorker mWorker;

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_buffer_size_key).equals(key) ||
                    getString(R.string.pref_chunk_size_key).equals(key) ||
                    getString(R.string.pref_frame_length_key).equals(key)) {
                Log.i(TAG, "A preference has been changed: " + key);
                AudioCollectorService.this.setupService();
            }
        }
    };

    class AudioCollectorBinder extends Binder {
        AudioCollectorService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AudioCollectorService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        setupService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
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

    private void setupService() {
        stopCapture();
        int bufferSizeInMilliseconds = integerPreferenceValue(R.string.pref_buffer_size_key);
        int chunkSizeInMilliseconds = integerPreferenceValue(R.string.pref_chunk_size_key);
        int frameLengthInMilliseconds = integerPreferenceValue(R.string.pref_frame_length_key);
        Log.i(TAG, "Starting to capture audio");
        Log.d(TAG, "Setting up worker...");
        mWorker = new AudioCollectorWorker(bufferSizeInMilliseconds,
                chunkSizeInMilliseconds,
                frameLengthInMilliseconds);
        new Thread(mWorker).start();
        Log.d(TAG,"Setting up worker...Done");
    }

    private void stopCapture() {
        if (mWorker != null) {
            Log.d(TAG, "Stopping worker");
            mWorker.doStop();
        }
    }

    private int integerPreferenceValue(int key) {
        return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }
}
