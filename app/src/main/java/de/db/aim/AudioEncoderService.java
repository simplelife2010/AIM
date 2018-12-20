package de.db.aim;

import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class AudioEncoderService extends Service implements AudioCollectorListener {

    private static final String TAG = AudioEncoderService.class.getSimpleName();
    private static final int FILE_REMOVER_JOB_ID = 1;

    private AudioEncoderBinder mBinder = new AudioEncoderBinder();
    private EncoderCallback mEncoderCallback = new EncoderCallback();
    private MediaCodec mCodec;
    private boolean mEndOfStream;
    private MediaMuxer mMuxer;
    private int mAudioTrackIndex;
    private ByteBuffer mCaptureBuffer;
    private long mPresentationTimestamp;
    private AudioCollectorService mService;
    private List<AudioEncoderListener> mListeners = new ArrayList<AudioEncoderListener>();
    private boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioCollectorService.AudioCollectorBinder binder = (AudioCollectorService.AudioCollectorBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerAudioCollectorListener(AudioEncoderService.this);
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
                    || getString(R.string.pref_bit_rate_key).equals(key)
                    || getString(R.string.pref_encoder_buffer_size_key).equals(key)) {
                Log.i(TAG, "An encoder preference has been changed: " + key);
            }
            if (getString(R.string.pref_remove_period_key).equals(key)
                    || getString(R.string.pref_keep_files_key).equals(key)) {
                Log.i(TAG, "A file remover preference has been changed: " + key);
                cancelFileRemoverJob();
                scheduleFileRemoverJob();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, AudioCollectorService.class);
        Log.d(TAG,"Binding AudioCollectorService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        scheduleFileRemoverJob();
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
        cancelFileRemoverJob();
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

    @Override
    public void onNewAudioFrame(long timestamp, short[] audioData) {
        mPresentationTimestamp = 0;
        Log.d(TAG, "New audio frame with " + String.valueOf(audioData.length) + " samples and timestamp " + timestamp + " received");

        prepareCaptureBuffer(audioData);
        prepareCodec();
        prepareMuxer(timestamp);

        Log.d(TAG, "Starting codec");
        mCodec.start();
        mEndOfStream = false;
    }

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

    private void prepareCaptureBuffer(short[] audioData) {
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
    }

    private void prepareCodec() {
        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
        }
        MediaFormat format = MediaFormat.createAudioFormat(stringPreferenceValue(R.string.pref_format_type_key), 44100, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, integerPreferenceValue(R.string.pref_bit_rate_key));
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, integerPreferenceValue(R.string.pref_encoder_buffer_size_key));
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        try {
            mCodec = MediaCodec.createEncoderByType(stringPreferenceValue(R.string.pref_format_type_key));
            mCodec.setCallback(mEncoderCallback);
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create codec");
        }
    }

    private void prepareMuxer(long timestamp) {
        new File(audioDirectory(timestamp)).mkdirs();
        String audioPathName = audioDirectory(timestamp) + "/" + audioFilename(timestamp);
        Log.d(TAG, "Output file: " + audioPathName);
        try {
            mMuxer = new MediaMuxer(audioPathName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create Muxer: " + e.toString());
        }
    }

    private String audioDirectory(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedDate = format.format(date);
        format = new SimpleDateFormat("HH");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String hour = format.format(date);
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/AIM/" + formattedDate + "/" + hour;
    }

    private String audioFilename(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'Z'HH-mm-ss'.'SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedTimestamp = format.format(date);
        return stringPreferenceValue(R.string.pref_file_prefix_key) + "_" + formattedTimestamp + ".m4a";
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
            if (mEndOfStream) {
                return;
            }
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
                mEndOfStream = true;
            }
            inputBuffer.put(subFrame);
            mediaCodec.queueInputBuffer(i, 0, subFrame.length, mPresentationTimestamp, flags);
            mPresentationTimestamp += 1000000L * (long) subFrame.length / (2L * 44100L);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
            mMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, bufferInfo);
            mediaCodec.releaseOutputBuffer(i, false);

            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                Log.d(TAG, "Received end of stream flag");
                mCodec.stop();
                mCodec.release();
                mCodec = null;
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            Log.d(TAG, "CodecException: " + e.toString());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.d(TAG, "Output format has been changed");
            mAudioTrackIndex = mMuxer.addTrack(mediaFormat);
            Log.d(TAG, "Starting muxer");
            mMuxer.start();
        }
    }
}
