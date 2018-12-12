package de.db.aim;

public interface AudioCollectorListener {
    void onNewAudioFrame(long timestamp, short[] audioData);
}
