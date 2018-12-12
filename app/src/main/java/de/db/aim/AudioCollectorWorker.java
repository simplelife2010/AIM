package de.db.aim;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioCollectorWorker implements Runnable {

    private static final String TAG = "AudioCollectorWorker";

    private AudioCollectorService mService;
    private int mBufferSizeInMilliseconds;
    private int mChunkSizeInMilliseconds;
    private int mFrameLengthInMilliseconds;
    private int mSampleRate = 44100;
    private boolean mDoStop = false;

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
        this.mDoStop = true;
    }

    private synchronized boolean keepRunning() {
        return !this.mDoStop;
    }

    @Override
    public void run() {
        int samplesPerFrame = mSampleRate * mFrameLengthInMilliseconds / 1000;
        int chunkSizeInSamples = mSampleRate * mChunkSizeInMilliseconds / 1000;
        Log.d(TAG, "Creating recorder...");
        AudioRecord recorder = getRecorder();
        Log.d(TAG, "Creating recorder...Done");
        recorder.startRecording();
        int currentOffset = 0;
        short[] audioData = new short[samplesPerFrame];
        long timestamp = System.currentTimeMillis();
        while(keepRunning()) {
            Log.d(TAG, "Capturing audio data...");
            Log.d(TAG, "Current offset: " + currentOffset);
            int samplesToFrameCompletion = samplesPerFrame - currentOffset;
            Log.d(TAG, "Samples to frame completion: " + samplesToFrameCompletion);
            int samplesToCapture;
            if (samplesToFrameCompletion < chunkSizeInSamples) {
                samplesToCapture = samplesToFrameCompletion;
            } else {
                samplesToCapture = chunkSizeInSamples;
            }
            Log.d(TAG, "Capturing " + samplesToCapture + " samples...");
            int samplesCaptured = recorder.read(audioData, currentOffset, samplesToCapture);
            currentOffset += samplesCaptured;
            Log.d(TAG, "Capturing " + samplesToCapture + " samples...Done");
            Log.d(TAG, "Current offset: " + currentOffset);

            if (currentOffset >= samplesPerFrame) {
                currentOffset = 0;
                Log.d(TAG, "Frame complete, resetting offset");
                publishToListeners(timestamp, audioData);
                timestamp = System.currentTimeMillis();
            }

            Log.d(TAG, "Capturing audio data...Done");
        }
        recorder.stop();
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
