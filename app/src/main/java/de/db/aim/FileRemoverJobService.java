package de.db.aim;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class FileRemoverJobService extends MonitorableJobService {

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        broadcastStatus("Initializing");
        new FileRemoverTask().execute(jobParameters);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

    private class FileRemoverTask extends AsyncTask<JobParameters, Void, JobParameters> {

        private final String TAG = FileRemoverTask.class.getSimpleName();

        private List<File> mFiles;

        @Override
        protected void onPostExecute(JobParameters jobParameters) {
            super.onPostExecute(jobParameters);
            jobFinished(jobParameters, false);
            Log.d(TAG, "Finished removing files");
        }

        @Override
        protected JobParameters doInBackground(JobParameters... jobParameters) {

            int numberOfFilesToKeep = integerPreferenceValue(R.string.pref_keep_files_key);

            Log.d(TAG,"Removing old audio files (keeping " + String.valueOf(numberOfFilesToKeep) + " files)...");
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
            mFiles.subList(mFiles.size() - numberOfFilesToKeep, mFiles.size()).clear();
            for (File file : mFiles) {
                Log.d(TAG, "Deleting " + file.getName());
                file.delete();
            }

            Log.d(TAG, "Deleting empty directories...");
            walkAndDeleteEmptyDirs(baseDirectory);
            Log.d(TAG, "Deleting empty directories...Done");

            Log.d(TAG,"Removing old audio files...Done");
            return jobParameters[0];
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

        private int integerPreferenceValue(int key) {
            return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
        }

        private SharedPreferences sharedPreferences() {
            return PreferenceManager.getDefaultSharedPreferences(FileRemoverJobService.this);
        }
    }
}
