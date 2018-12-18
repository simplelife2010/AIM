package de.db.aim;

import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
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

public class FileWriterService extends Service implements AudioEncoderListener {

    private static final String TAG = FileWriterService.class.getSimpleName();
    private static final int FILE_REMOVER_JOB_ID = 1;

    private final IBinder mBinder = new FileWriterService.FileWriterBinder();
    AudioEncoderService mService;
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioEncoderService.AudioEncoderBinder binder = (AudioEncoderService.AudioEncoderBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerAudioEncoderListener(FileWriterService.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_remove_period_key).equals(key) ||
                    getString(R.string.pref_file_prefix_key).equals(key) ||
                    getString(R.string.pref_keep_files_key).equals(key)) {
                Log.i(TAG, "A preference has been changed: " + key);
                cancelFileRemoverJob();
                scheduleFileRemoverJob();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        Intent intent = new Intent(this, AudioEncoderService.class);
        Log.d(TAG,"Binding AudioEncoderService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        scheduleFileRemoverJob();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mService.unregisterAudioEncoderListener(this);
        Log.d(TAG, "Unbinding AudioCollectorService");
        unbindService(mConnection);
        cancelFileRemoverJob();
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
    public void onNewEncodedAudioFrame(long timestamp, byte[] encodedAudioData) {

    }


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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scheduleFileRemoverJob() {
        int removePeriod = integerPreferenceValue(R.string.pref_remove_period_key);
        Log.d(TAG, "Scheduling file remover job to run every " + String.valueOf(removePeriod) + " minutes");
        JobScheduler jobScheduler =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(new JobInfo.Builder(FILE_REMOVER_JOB_ID,
                new ComponentName(this, FileRemoverJobService.class))
                .setPeriodic(60 * 1000 * removePeriod)
                .build());
    }

    private void cancelFileRemoverJob() {
        Log.d(TAG, "Cancelling file remover job");
        JobScheduler jobScheduler =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(FILE_REMOVER_JOB_ID);
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

        private static final String TAG = FileWriterTask.class.getSimpleName();

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
}
