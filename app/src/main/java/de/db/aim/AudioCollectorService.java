package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioCollectorService extends Service {

    private static final int CHANNEL_CONFIG_MONO = 16;
    private static final int CHANNEL_CONFIG_STEREO = 12;
    private static final int ENCODING_PCM_8_BIT = 3;
    private static final int ENCODING_PCM_16_BIT = 2;

    private static final String TAG = "AudioCollectorService";

    private final IBinder mBinder = new AudioCollectorBinder();

    private AudioRecord mRecorder;
    private Worker mWorker;

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_encoding_key).equals(key) ||
                    getString(R.string.pref_sample_rate_key).equals(key) ||
                    getString(R.string.pref_channel_config_key).equals(key) ||
                    getString(R.string.pref_buffer_size_key).equals(key) ||
                    getString(R.string.pref_chunk_size_key).equals(key) ||
                    getString(R.string.pref_frame_length_key).equals(key)) {
                Log.i(TAG, "A preference has been changed: " + key);
                AudioCollectorService.this.setupService();
            }
        }
    };

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
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        setupService();
    }

    private void setupService() {
        if (mWorker != null) {
            Log.d(TAG, "Stopping worker");
            mWorker.doStop();
        }
        if (mRecorder != null) {
            Log.d(TAG, "Stopping audio capture");
            mRecorder.stop();
        }
        Log.i(TAG, "Configuring service...");
        int encoding = integerPreferenceValue(R.string.pref_encoding_key);
        int sampleRate = integerPreferenceValue(R.string.pref_sample_rate_key);
        int channelConfig = integerPreferenceValue(R.string.pref_channel_config_key);
        int bufferSizeInMilliseconds = integerPreferenceValue(R.string.pref_buffer_size_key);
        int chunkSizeInMilliseconds = integerPreferenceValue(R.string.pref_chunk_size_key);
        int frameLengthInMilliseconds = integerPreferenceValue(R.string.pref_frame_length_key);

        int bufferSizeInBytes = bufferSizeInMilliseconds * sampleRate * bytesPerSampleAndChannel(encoding) * numberOfChannels(channelConfig) / 1000;
        int samplesPerFrame = sampleRate * numberOfChannels(channelConfig) * frameLengthInMilliseconds / 1000;
        int chunkSizeInSamples = sampleRate * chunkSizeInMilliseconds / 1000;

        mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSizeInBytes);
        Log.i(TAG, "Configuring service...Done");
        Log.d(TAG, "AudioRecord state: " + mRecorder.getState());
        Log.i(TAG, "Starting to capture audio");
        mRecorder.startRecording();
        mWorker = new Worker(samplesPerFrame, chunkSizeInSamples);
        new Thread(mWorker).start();
    }

    private int integerPreferenceValue(int key) {
        return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private int bytesPerSampleAndChannel(int encoding) {
        if (encoding == ENCODING_PCM_8_BIT) {
            return 1;
        }
        else if (encoding == ENCODING_PCM_16_BIT) {
            return 2;
        }
        else {
            throw new RuntimeException("Invalid encoding");
        }
    }

    private int numberOfChannels(int channelConfig) {
        if (channelConfig == CHANNEL_CONFIG_MONO) {
            return 1;
        } else if (channelConfig == CHANNEL_CONFIG_STEREO) {
            return 2;
        } else {
            throw new RuntimeException("Invalid channel configuration");
        }
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

    private class Worker implements Runnable {
        public Worker(int samplesPerFrame, int chunkSize) {
            this.mSamplesPerFrame = samplesPerFrame;
            this.mChunkSize = chunkSize;
        }

        private int mSamplesPerFrame;
        private int mChunkSize;

        private boolean mDoStop = false;

        public synchronized void doStop() {
            this.mDoStop = true;
        }

        private synchronized boolean keepRunning() {
            return this.mDoStop == false;
        }

        @Override
        public void run() {
            int currentOffset = 0;
            short[] audioData = new short[mSamplesPerFrame];
            while(keepRunning()) {
                Log.d(TAG, "Capturing audio data...");
                Log.d(TAG, "Current offset: " + currentOffset);
                int samplesToFrameCompletion = mSamplesPerFrame - currentOffset;
                Log.d(TAG, "Samples to frame completion: " + samplesToFrameCompletion);
                int samplesToCapture;
                if (samplesToFrameCompletion < mChunkSize) {
                    samplesToCapture = samplesToFrameCompletion;
                } else {
                    samplesToCapture = mChunkSize;
                }
                Log.d(TAG, "Capturing " + samplesToCapture + " samples...");
                int samplesCaptured = mRecorder.read(audioData, currentOffset, samplesToCapture);
                currentOffset += samplesCaptured;
                Log.d(TAG, "Capturing " + samplesToCapture + " samples...Done");
                Log.d(TAG, "Current offset: " + currentOffset);

                if (currentOffset >= mSamplesPerFrame) {
                    currentOffset = 0;
                    Log.d(TAG, "Frame complete, resetting offset");
                }

                Log.d(TAG, "Capturing audio data...Done");
            }
        }
    }
}
