package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileWriterService extends Service implements AudioCollectorListener {

    private static final String TAG = "FileWriterService";

    private final IBinder mBinder = new FileWriterService.FileWriterBinder();
    AudioCollectorService mService;
    boolean mBound = false;
    ScheduledExecutorService mScheduledExecutor;
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

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_remove_period_key).equals(key) ||
                    getString(R.string.pref_file_prefix_key).equals(key) ||
                    getString(R.string.pref_keep_files_key).equals(key)) {
                Log.i(TAG, "A preference has been changed: " + key);
                FileWriterService.this.setupScheduledWorker();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        Intent intent = new Intent(this, AudioCollectorService.class);
        Log.d(TAG,"Binding AudioCollectorService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        setupScheduledWorker();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mService.unregisterAudioCollectorListener(this);
        Log.d(TAG, "Unbinding AudioCollectorService");
        unbindService(mConnection);
        Log.d(TAG,"Shutting down scheduled worker for file removal");
        mScheduledExecutor.shutdown();
        try {
            Log.d(TAG,"Waiting for Executor to terminate...");
            mScheduledExecutor.awaitTermination(1L,TimeUnit.MINUTES);
            Log.d(TAG,"Waiting for Executor to terminate...Done");
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.d(TAG,"Waiting for Executor to terminate...Done");
        }
        sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
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
        Log.i(TAG, "Received audio frame of " + audioData.length + " samples");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = format.format(timestamp);
        format = new SimpleDateFormat("HH");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String hour = format.format(timestamp);
        String audioDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/AIM/" + date + "/" + hour;
        new File(audioDirectory).mkdirs();
        format = new SimpleDateFormat("yyyy-MM-dd'Z'HH-mm-ss'.'SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedTimestamp = format.format(timestamp);
        SharedPreferences sp = sharedPreferences();
        String audioFilename = stringPreferenceValue(R.string.pref_file_prefix_key) + "_" + formattedTimestamp + ".wav";
        Log.d(TAG,"Submitting file output via Executor...");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        executor.execute(new FileWriterWorker(audioDirectory + "/" + audioFilename, Arrays.copyOf(audioData, audioData.length)));
        Log.d(TAG,"Submitting file output via Executor...Done.");
    }

    private void setupScheduledWorker() {
        Log.d(TAG,"Setting up scheduled worker for file removal");
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdown();
            try {
                Log.d(TAG,"Waiting for Executor to terminate...");
                mScheduledExecutor.awaitTermination(1L,TimeUnit.MINUTES);
                Log.d(TAG,"Waiting for Executor to terminate...Done");
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.d(TAG,"Waiting for Executor to terminate...Done");
            }
        }
        mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        SharedPreferences sp = sharedPreferences();
        mScheduledExecutor.scheduleAtFixedRate(new FileRemoverWorker(integerPreferenceValue(R.string.pref_keep_files_key)),
                integerPreferenceValue(R.string.pref_remove_period_key),
                integerPreferenceValue(R.string.pref_remove_period_key),
                TimeUnit.MINUTES);
    }

    private String stringPreferenceValue(int key) {
        return sharedPreferences().getString(getString(key), "");
    }

    private int integerPreferenceValue(int key) {
        return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    class FileWriterBinder extends Binder {
        FileWriterService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FileWriterService.this;
        }
    }

    private class FileWriterWorker implements Runnable {

        private static final String TAG = "FileWriterWorker";

        private String mPathName;
        private short[] mAudioData;

        public FileWriterWorker(String pathName, short[] audioData) {
            this.mPathName = pathName;
            this.mAudioData = audioData;
        }

        @Override
        public void run() {
            Log.d(TAG, "Writing " + mPathName + " asynchonously...");
            AudioUtils.writeWavFile(mPathName, mAudioData);
            Log.d(TAG, "Writing " + mPathName + " asynchonously...Done");
        }
    }

    private class FileRemoverWorker implements Runnable {

        private static final String TAG = "FileRemoverWorker";

        private int mNumberOfFilesToKeep;

        public FileRemoverWorker(int numberOfFileToKeep) {
            this.mNumberOfFilesToKeep = numberOfFileToKeep;
        }

        @Override
        public void run() {
            Log.d(TAG,"Removing old audio files (keeping " + String.valueOf(mNumberOfFilesToKeep) + " files)...");
            Log.d(TAG,"Removing old audio files...Done");
        }
    }
}
