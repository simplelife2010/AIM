package de.db.aim;

import android.app.Service;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
        String audioFilename = stringPreferenceValue(R.string.pref_file_prefix_key) + "_" + formattedTimestamp + ".wav";
        String audioPathName = audioDirectory + "/" + audioFilename;
        new FileWriterTask().execute(new FileWriterTaskParams(audioPathName, audioData));
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

    private class FileWriterTaskParams {
        String pathName;
        short[] audioData;

        FileWriterTaskParams(String pathName, short[] audioData) {
            this.pathName = pathName;
            this.audioData = audioData;
        }
    }

    private static class FileWriterTask extends AsyncTask<FileWriterTaskParams, Void, Void> {

        private static final String TAG = "FileWriterTask";

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "FileWriterTask finished");
        }

        @Override
        protected Void doInBackground(FileWriterTaskParams... fileWriterTaskParams) {
            Log.d(TAG, "Writing " + fileWriterTaskParams[0].pathName + " asynchonously...");
            AudioUtils.writeWavFile(fileWriterTaskParams[0].pathName, fileWriterTaskParams[0].audioData);
            Log.d(TAG, "Writing " + fileWriterTaskParams[0].pathName + " asynchonously...Done");
            return null;
        }
    }

    private class FileRemoverWorker implements Runnable {

        private static final String TAG = "FileRemoverWorker";

        private int mNumberOfFilesToKeep;
        private List<File> mFiles;

        FileRemoverWorker(int numberOfFileToKeep) {
            this.mNumberOfFilesToKeep = numberOfFileToKeep;
        }

        @Override
        public void run() {
            Log.d(TAG,"Removing old audio files (keeping " + String.valueOf(mNumberOfFilesToKeep) + " files)...");
            File baseDirectory = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/AIM");
            mFiles = new ArrayList<File>();
            walk(baseDirectory);
            Collections.sort(mFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                }
            });
            mFiles.subList(mFiles.size() - mNumberOfFilesToKeep, mFiles.size()).clear();
            for (File file : mFiles) {
                Log.d(TAG, "Deleting " + file.getName());
                file.delete();
            }

            Log.d(TAG, "Deleting empty directories...");
            walkAndDeleteEmptyDirs(baseDirectory);
            Log.d(TAG, "Deleting empty directories...Done");

            Log.d(TAG,"Removing old audio files...Done");
        }

        private void walk(File directory) {

            File[] files = directory.listFiles();
            if (files == null) return;

            for (File file : files) {
                if (file.isDirectory()) {
                    walk(file);
                }
                else {
                    mFiles.add(file);
                }
            }
        }

        private void walkAndDeleteEmptyDirs(File directory) {

            File[] files = directory.listFiles();

            for (File file : files) {
                if (file.isDirectory()) {
                    walkAndDeleteEmptyDirs(file);
                }
            }
            if (directory.listFiles().length == 0) {
                Log.d(TAG, "Deleting directory " + directory.getName());
                directory.delete();
            }
        }
    }
}
