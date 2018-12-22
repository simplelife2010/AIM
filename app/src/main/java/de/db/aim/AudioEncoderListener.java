package de.db.aim;

public interface AudioEncoderListener {
    void onNewEncodedAudioFrame(long timestamp, String path, String filename);
}
