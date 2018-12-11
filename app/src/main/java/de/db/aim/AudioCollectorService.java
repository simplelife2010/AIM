package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class AudioCollectorService extends Service {

    private static final int CHANNEL_CONFIG_MONO = 16;
    private static final int CHANNEL_CONFIG_STEREO = 12;
    private static final int ENCODING_PCM_8_BIT = 3;
    private static final int ENCODING_PCM_16_BIT = 2;

    private static final String TAG = "AudioCollectorService";

    private final IBinder mBinder = new AudioCollectorBinder();
    private AudioRecord mRecorder;
    private Timer mTimer;
    private TimerTask mTimerTask;

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
        int pollPeriodInSeconds = integerPreferenceValue(R.string.pref_poll_period_key);

        int bufferSizeInBytes = bufferSizeInSeconds * sampleRate * bytesPerSampleAndChannel(encoding) * numberOfChannels(channelConfig);

        mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSizeInBytes);
        Log.i(TAG, "Configuring service...Done");
        Log.i(TAG, "AudioRecord state: " + mRecorder.getState());
        Log.i(TAG, "Starting to poll audio data each " + pollPeriodInSeconds + " seconds");
        mRecorder.startRecording();
        startTimer(1000 * pollPeriodInSeconds);
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

    private void startTimer(int pollPeriod) {
        mTimer = new Timer();
        initializeTimerTask();
        mTimer.schedule(mTimerTask, pollPeriod, pollPeriod);
    }

    private void initializeTimerTask() {
        mTimerTask = new TimerTask() {
            public void run() {
                Log.i(TAG, "Polling audio data...");

                short[] audioData = new short[150000];
                int i = mRecorder.read(audioData, 0,150000);
                Log.i(TAG, "Return value: " + i);

                Log.i(TAG, "Polling audio data...Done");
            }
        };
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
