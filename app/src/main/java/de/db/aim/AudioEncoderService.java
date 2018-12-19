package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class AudioEncoderService extends Service implements AudioCollectorListener {

    private static final String TAG = AudioEncoderService.class.getSimpleName();

    private AudioEncoderBinder mBinder = new AudioEncoderBinder();
    private EncoderCallback mEncoderCallback = new EncoderCallback();
    private MediaFormat mFormat;
    private MediaCodec mCodec;
    private String mCodecName;
    private ByteBuffer mCaptureBuffer;
    private long mTimestamp;
    private AudioCollectorService mService;
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
            setupService();
            sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService.unregisterAudioCollectorListener(AudioEncoderService.this);
            if (mCodec != null) {
                mCodec.flush();
                mCodec.stop();
                mCodec.release();
            }
            sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
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
        Intent intent = new Intent(this, AudioCollectorService.class);
        Log.d(TAG,"Binding AudioCollectorService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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
            mCodec.stop();
            mCodec.release();
        }
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        mFormat = MediaFormat.createAudioFormat(stringPreferenceValue(R.string.pref_format_type_key), 44100, 1);
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, integerPreferenceValue(R.string.pref_bit_rate_key));
        mCodecName = codecList.findEncoderForFormat(mFormat);
        if (mCodecName == null) {
            throw new RuntimeException("No matching codec found");
        }
        Log.d(TAG, "Found matching codec: " + mCodecName);
    }

    @Override
    public void onNewAudioFrame(long timestamp, short[] audioData) {
        mTimestamp = timestamp;
        Log.d(TAG, "New audio frame with " + String.valueOf(audioData.length) + " samples received");
        if (mCaptureBuffer == null) {
            mCaptureBuffer = ByteBuffer.allocate(2 * audioData.length);
        } else if (mCaptureBuffer.capacity() != 2 * audioData.length) {
            mCaptureBuffer = ByteBuffer.allocate(2 * audioData.length);
        } else {
            mCaptureBuffer.clear();
        }
        mCaptureBuffer.order(ByteOrder.nativeOrder());
        mCaptureBuffer.asShortBuffer().put(audioData);
        mCaptureBuffer.position(2 * audioData.length);
        mCaptureBuffer.flip();
        try {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mCodec.setCallback(mEncoderCallback);
            mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.d(TAG, "Starting codec");
            mCodec.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }
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

        private final String TAG = EncoderCallback.class.getSimpleName();

        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int i) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(i);
            int flags = 0;
            byte[] subFrame;
            if (mCaptureBuffer.limit() != mCaptureBuffer.position()) {
                int inputBufferAvailableBytes = inputBuffer.limit() - inputBuffer.position();
                int captureBufferAvailableBytes = mCaptureBuffer.limit() - mCaptureBuffer.position();
                subFrame = new byte[Math.min(inputBufferAvailableBytes, captureBufferAvailableBytes)];
                mCaptureBuffer.get(subFrame);
            } else{
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                inputBuffer.put(ByteBuffer.allocate(0));
                subFrame = new byte[0];
                Log.d(TAG, "Setting end of stream flag");
            }
            inputBuffer.put(subFrame);
            mediaCodec.queueInputBuffer(i, 0, subFrame.length, 1000 * mTimestamp, flags);
            mTimestamp += 1000 * subFrame.length / (2 * 44100);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
            mediaCodec.releaseOutputBuffer(i, false);

            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                Log.d(TAG, "Received end of stream flag");
                mCodec.stop();
                mCodec.release();
            }
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            Log.d(TAG, "CodecException: " + e.toString());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.d(TAG, "Output format has been changed");
        }
    }
}
