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
    private MediaCodec mCodec;
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
            mCodec.flush();
            mCodec.stop();
            mCodec.release();
        }
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
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
            Log.d(TAG, "Codec configured");
            mCodec.start();
            Log.d(TAG, "Codec started");
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }
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
        Log.d(TAG,"Capture buffer contains " + String.valueOf(mCaptureBuffer.position()) + " bytes");
        mCaptureBuffer.flip();
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
            //Log.d(TAG, "Input buffer " + String.valueOf(i) + " available");

            try {
                while (mCaptureBuffer == null) {
                    Thread.sleep(100);
                }
                while (mCaptureBuffer.limit() - mCaptureBuffer.position() == 0) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(i);
            int inputBufferAvailableBytes = inputBuffer.limit() - inputBuffer.position();
            int captureBufferAvailableBytes = mCaptureBuffer.limit() - mCaptureBuffer.position();
            byte[] subFrame = new byte[Math.min(inputBufferAvailableBytes, captureBufferAvailableBytes)];
            mCaptureBuffer.get(subFrame);
            inputBuffer.put(subFrame);
            Log.d(TAG, "Put " + String.valueOf(subFrame.length) + "/" + String.valueOf(captureBufferAvailableBytes) + " bytes from capture buffer into input buffer");
            int flags = 0;
            if (captureBufferAvailableBytes == 0) {
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                Log.d(TAG, "Setting end of stream flag");
            }
            mediaCodec.queueInputBuffer(i, 0, subFrame.length, mTimestamp, flags);
            mTimestamp += 1000 * subFrame.length / (2 * 44100);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {
            //Log.d(TAG, "Output buffer " + String.valueOf(i) + " available with " + String.valueOf(bufferInfo.size) + " bytes of available data");
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
            mediaCodec.releaseOutputBuffer(i, false);
            //Log.d(TAG, "Output buffer released");
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
