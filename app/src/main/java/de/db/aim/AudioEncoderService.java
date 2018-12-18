package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioEncoderService extends Service implements AudioCollectorListener {

    private static final String TAG = AudioEncoderService.class.getSimpleName();

    private AudioEncoderBinder mBinder = new AudioEncoderBinder();
    private MediaCodec mCodec;
    AudioCollectorService mService;
    private List<AudioEncoderListener> mListeners = new ArrayList<AudioEncoderListener>();
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioCollectorService.AudioCollectorBinder binder = (AudioCollectorService.AudioCollectorBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerAudioCollectorListener(AudioEncoderService.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService.unregisterAudioCollectorListener(AudioEncoderService.this);
            mBound = false;
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_format_type_key).equals(key)
                    || getString(R.string.pref_bit_rate_key).equals(key)) {
                Log.i(TAG, "A preference has been changed: " + key);
                setupService();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        setupService();
        Intent intent = new Intent(this, AudioCollectorService.class);
        Log.d(TAG,"Binding AudioCollectorService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        unbindService(mConnection);
        if (mCodec != null) {
            mCodec.flush();
            mCodec.stop();
            mCodec.release();
        }
        super.onDestroy();
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

    private void setupService() {
        if (mCodec != null) {
            mCodec.flush();
            mCodec.stop();
            mCodec.release();
        }
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        //MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        /*for (MediaCodecInfo codecInfo : codecInfos) {
            Log.d(TAG, "Codec: " + codecInfo.getName());
            for (String type : codecInfo.getSupportedTypes()) {
                Log.d(TAG, "--- " + type);
            }
        }*/
        MediaFormat format = MediaFormat.createAudioFormat(stringPreferenceValue(R.string.pref_format_type_key), 44100, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, integerPreferenceValue(R.string.pref_bit_rate_key));
        String codecName = codecList.findEncoderForFormat(format);
        if (codecName == null) {
            throw new RuntimeException("No matching codec found");
        }
        Log.d(TAG, "Found matching codec: " + codecName);
        try {
            mCodec = MediaCodec.createByCodecName(codecName);
            mCodec.setCallback(new EncoderCallback());
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }
    }

    @Override
    public void onNewAudioFrame(long timestamp, short[] audioData) {
        Log.d(TAG, "New audio frame received");
    }

    public byte[] encode(short[] audioData) {
        return null;
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

    public void registerAudioEncoderListener(AudioEncoderListener listener) {
        Log.d(TAG, "Adding listener: " + listener.toString());
        mListeners.add(listener);
    }

    public void unregisterAudioEncoderListener(AudioEncoderListener listener) {
        Log.d(TAG, "Removing listener: " + listener.toString());
        mListeners.remove(listener);
    }

    List<AudioEncoderListener> getAudioEncoderListeners() {
        return mListeners;
    }

    class AudioEncoderBinder extends Binder {
       AudioEncoderService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AudioEncoderService.this;
        }
    }

    private class EncoderCallback extends MediaCodec.Callback {

        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int i) {

        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {

        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {

        }
    }
}
