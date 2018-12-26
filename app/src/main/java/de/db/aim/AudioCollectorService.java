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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AudioCollectorService extends MonitorableService {

    private static final String TAG = AudioCollectorService.class.getSimpleName();

    private final IBinder mBinder = new AudioCollectorBinder();

    private List<AudioCollectorListener> mListeners = new ArrayList<AudioCollectorListener>();
    private ExecutorService mExecutor;

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
        Log.d(TAG,"Creating service");
        super.onCreate();
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        broadcastStatus("Initializing");
        setupService();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"Destroying service");
        super.onDestroy();
        stopCapture();
        sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        broadcastStatus("Terminated");
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
        Log.d(TAG,"Stopping worker if exists...");
        stopCapture();
        Log.d(TAG,"Stopping worker if exists...Done.");
        int bufferSizeInMilliseconds = integerPreferenceValue(R.string.pref_buffer_size_key);
        int chunkSizeInMilliseconds = integerPreferenceValue(R.string.pref_chunk_size_key);
        int frameLengthInMilliseconds = integerPreferenceValue(R.string.pref_frame_length_key);
        Log.i(TAG, "Starting to capture audio");
        mExecutor = Executors.newSingleThreadExecutor();
        Log.d(TAG,"Executing worker");
        mExecutor.execute(new AudioCollectorWorker(this,
                bufferSizeInMilliseconds,
                chunkSizeInMilliseconds,
                frameLengthInMilliseconds));
        broadcastStatus("Capturing");
    }

    private void stopCapture() {
        if (mExecutor != null) {
            Log.d(TAG, "Shutting down Executor");
            mExecutor.shutdownNow();
            Log.d(TAG,"Waiting for worker to finish...");
            try {
                if (mExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    Log.d(TAG, "Waiting for worker to finish...Done");
                } else {
                    throw new RuntimeException("Could not shutdown worker.");
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Waiting for worker to finish...Done");
            }
            broadcastStatus("Stopped");
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
