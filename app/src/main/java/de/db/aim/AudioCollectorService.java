package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AudioCollectorService extends Service {

    private static final String TAG = "AudioCollectorService";

    private final IBinder mBinder = new AudioCollectorBinder();

    private List<AudioCollectorListener> mListeners = new ArrayList<AudioCollectorListener>();
    private AudioCollectorWorker mWorker;
    private Thread mHandle;

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
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    public void registerAudioCollectorListener(AudioCollectorListener listener) {
        Log.d(TAG, "Adding listener: " + listener.toString());
        mListeners.add(listener);
    }

    public void unregisterAudioCollectorListener(AudioCollectorListener listener) {
        Log.d(TAG, "Removing listener: " + listener.toString());
        mListeners.remove(listener);
    }

    List<AudioCollectorListener> getAudioCollectorListeners() {
        return mListeners;
    }

    private void setupService() {
        stopCapture();
        int bufferSizeInMilliseconds = integerPreferenceValue(R.string.pref_buffer_size_key);
        int chunkSizeInMilliseconds = integerPreferenceValue(R.string.pref_chunk_size_key);
        int frameLengthInMilliseconds = integerPreferenceValue(R.string.pref_frame_length_key);
        Log.i(TAG, "Starting to capture audio");
        Log.d(TAG, "Setting up worker...");
        mWorker = new AudioCollectorWorker(this,
                bufferSizeInMilliseconds,
                chunkSizeInMilliseconds,
                frameLengthInMilliseconds);
        mHandle = new Thread(mWorker);
        mHandle.setName("AudioCollectorWorker");
        mHandle.start();
        Log.d(TAG,"Setting up worker...Done");
    }

    private void stopCapture() {
        if (mWorker != null) {
            Log.d(TAG, "Calling worker.doStop()");
            mWorker.doStop();
            Log.d(TAG,"Waiting for worker to finish...");
            try {
                mHandle.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG,"Waiting for worker to finish...Done");
        }
    }

    private int integerPreferenceValue(int key) {
        return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    class AudioCollectorBinder extends Binder {
        AudioCollectorService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AudioCollectorService.this;
        }
    }
}
