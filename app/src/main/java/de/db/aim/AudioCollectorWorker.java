package de.db.aim;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.os.Process;

public class AudioCollectorWorker implements Runnable {

    private static final String TAG = "AudioCollectorWorker";

    private AudioCollectorService mService;
    private AudioRecord mRecorder;
    private short[] mAudioData;
    private int mCurrentOffset;
    private long mTimestamp;
    private int mSampleRate = 44100;
    private int mBufferSizeInBytes;
    private int mChunkSizeInMilliseconds;
    private int mFrameLengthInMilliseconds;
    private boolean mDoStop = false;

    AudioCollectorWorker(AudioCollectorService service,
                         int bufferSizeInMilliseconds,
                         int chunkSizeInMilliseconds,
                         int frameLengthInMilliseconds) {
        this.mService = service;
        this.mChunkSizeInMilliseconds = chunkSizeInMilliseconds;
        this.mFrameLengthInMilliseconds = frameLengthInMilliseconds;
        this.mBufferSizeInBytes = bufferSizeInMilliseconds * mSampleRate * 2 / 1000;
    }

    synchronized void doStop() {
        Log.d(TAG,"Received doStop");
        this.mDoStop = true;
    }

    private synchronized boolean keepRunning() {
        return !this.mDoStop;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        int samplesPerFrame = mSampleRate * mFrameLengthInMilliseconds / 1000;
        int chunkSizeInSamples = mSampleRate * mChunkSizeInMilliseconds / 1000;
        mRecorder = getRecorder();
        int mCurrentOffset = 0;
        mAudioData = new short[samplesPerFrame];
        mRecorder.startRecording();
        //Empty buffer before reading timestamp
        mRecorder.read(mAudioData, 0, mBufferSizeInBytes);
        mTimestamp = System.currentTimeMillis();
        while(keepRunning()) {
            capture(samplesPerFrame, chunkSizeInSamples);
        }
        mRecorder.stop();
        mRecorder.release();
        Log.i(TAG,"End of worker thread");
    }

    private AudioRecord getRecorder() {
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        return new AudioRecord(MediaRecorder.AudioSource.MIC,
                mSampleRate,
                channelConfig,
                encoding,
                mBufferSizeInBytes);
    }

    void capture(int samplesPerFrame, int chunkSizeInSamples) {
        Log.d(TAG,"Current offset: " + mCurrentOffset);
        int samplesToFrameCompletion = samplesPerFrame - mCurrentOffset;
        Log.d(TAG, "Samples to frame completion: " + samplesToFrameCompletion);
        int samplesToCapture;
        if (samplesToFrameCompletion < chunkSizeInSamples) {
            samplesToCapture = samplesToFrameCompletion;
        } else {
            samplesToCapture = chunkSizeInSamples;
        }
        int samplesCaptured = mRecorder.read(mAudioData, mCurrentOffset, samplesToCapture);
        long newTimestamp = System.currentTimeMillis();
        Log.d(TAG, "Captured " + samplesToCapture + " samples");
        mCurrentOffset += samplesCaptured;
        if (mCurrentOffset >= samplesPerFrame) {
            Log.d(TAG, "Frame complete, resetting offset");
            mCurrentOffset = 0;
            publishToListeners(mTimestamp, mAudioData);
            mTimestamp = newTimestamp;
        }
    }

    private void publishToListeners(long timestamp, short[] audioData) {
        for (AudioCollectorListener listener : mService.getAudioCollectorListeners()) {
            Log.d(TAG, "Publishing to listener: " + listener.toString());
            listener.onNewAudioFrame(timestamp, audioData);
        }
    }
}
