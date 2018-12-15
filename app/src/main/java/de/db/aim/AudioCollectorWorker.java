package de.db.aim;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.os.Process;

public class AudioCollectorWorker implements Runnable, AudioRecord.OnRecordPositionUpdateListener {

    private static final String TAG = "AudioCollectorWorker";

    private AudioCollectorService mService;
    private int mBufferSizeInMilliseconds;
    private int mChunkSizeInMilliseconds;
    private int mFrameLengthInMilliseconds;
    private int mSampleRate = 44100;
    private int mSamplesPerFrame;
    private int mChunkSizeInSamples;
    private long mTimestamp;
    private boolean mDoStop = false;
    private int mCurrentOffset;
    private short[] mAudioData;

    AudioCollectorWorker(AudioCollectorService service,
                         int bufferSizeInMilliseconds,
                         int chunkSizeInMilliseconds,
                         int frameLengthInMilliseconds) {
        this.mService = service;
        this.mBufferSizeInMilliseconds = bufferSizeInMilliseconds;
        this.mChunkSizeInMilliseconds = chunkSizeInMilliseconds;
        this.mFrameLengthInMilliseconds = frameLengthInMilliseconds;
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
        mSamplesPerFrame = mSampleRate * mFrameLengthInMilliseconds / 1000;
        mChunkSizeInSamples = mSampleRate * mChunkSizeInMilliseconds / 1000;
        Log.d(TAG, "Creating recorder...");
        AudioRecord recorder = getRecorder();
        Log.d(TAG, "Creating recorder...Done");
        recorder.setRecordPositionUpdateListener(this);
        recorder.setPositionNotificationPeriod(mChunkSizeInSamples);
        mCurrentOffset = 0;
        mAudioData = new short[mSamplesPerFrame];
        mTimestamp = System.currentTimeMillis();
        recorder.startRecording();
        while(keepRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        recorder.stop();
        recorder.release();
        Log.i(TAG,"End of worker thread");
    }

    @Override
    public void onMarkerReached(AudioRecord audioRecord) {
        Log.d(TAG,"Entering onMarkerReached()");
    }

    @Override
    public void onPeriodicNotification(AudioRecord recorder) {
        Log.d(TAG,"Entering onPeriodicNotification()");
        Log.d(TAG,"Current offset: " + mCurrentOffset);
        int samplesToFrameCompletion = mSamplesPerFrame - mCurrentOffset;
        Log.d(TAG, "Samples to frame completion: " + samplesToFrameCompletion);
        int samplesToCapture;
        if (samplesToFrameCompletion < mChunkSizeInSamples) {
            samplesToCapture = samplesToFrameCompletion;
        } else {
            samplesToCapture = mChunkSizeInSamples;
        }
        Log.d(TAG, "Capturing " + samplesToCapture + " samples...");
        int samplesCaptured = recorder.read(mAudioData, mCurrentOffset, samplesToCapture);
        mCurrentOffset += samplesCaptured;
        Log.d(TAG, "Capturing " + samplesToCapture + " samples...Done");
        if (mCurrentOffset >= mSamplesPerFrame) {
            Log.d(TAG, "Frame complete, resetting offset");
            mCurrentOffset = 0;
            int leftOverSamples = mChunkSizeInSamples - samplesCaptured;
            long newTimestamp = System.currentTimeMillis() - leftOverSamples * mSampleRate * 1000;
            publishToListeners(mTimestamp, mAudioData);
            mTimestamp = newTimestamp;
            if (leftOverSamples > 0) {
                Log.d(TAG, "Capturing " + leftOverSamples + " left over samples...");
                samplesCaptured = recorder.read(mAudioData, mCurrentOffset, leftOverSamples);
                if (samplesCaptured < 0) {
                    throw new RuntimeException("AudioRecord.read() returned " + String.valueOf(samplesCaptured));
                }
                mCurrentOffset += samplesCaptured;
                Log.d(TAG, "Capturing " + samplesToCapture + " samples...Done");
            }
        }
    }

    private AudioRecord getRecorder() {
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int bufferSizeInBytes = mBufferSizeInMilliseconds * mSampleRate * 2 / 1000;
        return new AudioRecord(MediaRecorder.AudioSource.MIC,
                mSampleRate,
                channelConfig,
                encoding,
                bufferSizeInBytes);
    }

    private void publishToListeners(long timestamp, short[] audioData) {
        for (AudioCollectorListener listener : mService.getAudioCollectorListeners()) {
            Log.d(TAG, "Publishing to listener: " + listener.toString());
            listener.onNewAudioFrame(timestamp, audioData);
        }
    }
}
