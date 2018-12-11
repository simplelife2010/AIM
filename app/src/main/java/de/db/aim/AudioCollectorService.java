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

    private Runnable mWorker;

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

        Log.i(TAG, "Configuring service...");
        int encoding = integerPreferenceValue(R.string.pref_encoding_key);
        int sampleRate = integerPreferenceValue(R.string.pref_sample_rate_key);
        int channelConfig = integerPreferenceValue(R.string.pref_channel_config_key);
        int bufferSizeInSeconds = integerPreferenceValue(R.string.pref_buffer_size_key);
        int chunkSizeInSeconds = integerPreferenceValue(R.string.pref_chunk_size_key);
        int frameLengthInSeconds = integerPreferenceValue(R.string.pref_frame_length_key);

        int bufferSizeInBytes = bufferSizeInSeconds * sampleRate * bytesPerSampleAndChannel(encoding) * numberOfChannels(channelConfig);
        int samplesPerFrame = sampleRate * numberOfChannels(channelConfig) * frameLengthInSeconds;
        int chunkSizeInSamples = sampleRate * chunkSizeInSeconds;

        mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSizeInBytes);
        Log.i(TAG, "Configuring service...Done");
        Log.i(TAG, "AudioRecord state: " + mRecorder.getState());
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

        @Override
        public void run() {
            int currentOffset = 0;
            short[] audioData = new short[mSamplesPerFrame];
            while(true) {
                Log.i(TAG, "Capturing audio data...");
                Log.i(TAG, "Current offset: " + currentOffset);
                int samplesToFrameCompletion = mSamplesPerFrame - currentOffset;
                Log.i(TAG, "Samples to frame completion: " + samplesToFrameCompletion);
                int samplesToCapture;
                if (samplesToFrameCompletion < mChunkSize) {
                    samplesToCapture = samplesToFrameCompletion;
                } else {
                    samplesToCapture = mChunkSize;
                }
                Log.i(TAG, "Capturing " + samplesToCapture + " samples");
                int samplesCaptured = mRecorder.read(audioData, currentOffset, samplesToCapture);
                currentOffset += samplesCaptured;
                Log.i(TAG, "Samples captured: " + samplesCaptured);
                Log.i(TAG, "Current offset: " + currentOffset);

                if (currentOffset >= mSamplesPerFrame) {
                    currentOffset = 0;
                    Log.i(TAG, "Frame complete, resetting offset");
                }

                Log.i(TAG, "Capturing audio data...Done");
            }
        }
    }
}
