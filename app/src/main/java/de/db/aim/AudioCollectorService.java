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

public class AudioCollectorService extends Service {

    private static final int CHANNEL_CONFIG_MONO = 16;
    private static final int CHANNEL_CONFIG_STEREO = 12;
    private static final int ENCODING_PCM_8_BIT = 3;
    private static final int ENCODING_PCM_16_BIT = 2;

    private final IBinder mBinder = new AudioCollectorBinder();
    private AudioRecord mRecorder;

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

        int encoding = integerPreferenceValue(R.string.pref_encoding_key);
        int sampleRate = integerPreferenceValue(R.string.pref_sample_rate_key);
        int channelConfig = integerPreferenceValue(R.string.pref_channel_config_key);
        int bufferSizeInSeconds = integerPreferenceValue(R.string.pref_buffer_size_key);

        int bufferSizeInBytes = bufferSizeInSeconds * sampleRate * bytesPerSampleAndChannel(encoding) * numberOfChannels(channelConfig);

        mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSizeInBytes);
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
}
