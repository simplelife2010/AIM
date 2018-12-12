package de.db.aim;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioUtils {

    private static int BITS_PER_SAMPLE = 16;
    private static int NUMBER_OF_CHANNELS = 1;
    private static long SAMPLE_RATE = 44100;
    private static int HEADER_LENGTH = 44;

    public static void writeWavFile(String pathName, short[] audioData) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(pathName);
            writeWavFileHeader(out, audioData.length);
            ByteBuffer audioByteBuffer = ByteBuffer.allocate(2 * audioData.length);
            audioByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer audioShortBuffer = audioByteBuffer.asShortBuffer();
            audioShortBuffer.put(audioData);
            out.getChannel().write(audioByteBuffer);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void writeWavFileHeader(FileOutputStream out,
                                     long numberOfSamples)
            throws IOException {

        long fileSizeInBytes = HEADER_LENGTH + BITS_PER_SAMPLE * numberOfSamples / 8;
        long dataSizeInBytes = fileSizeInBytes - HEADER_LENGTH;
        long byteRate = SAMPLE_RATE * BITS_PER_SAMPLE / 8;

        byte[] header = new byte[HEADER_LENGTH];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) ((fileSizeInBytes - 8) & 0xff);
        header[5] = (byte) (((fileSizeInBytes - 8) >> 8) & 0xff);
        header[6] = (byte) (((fileSizeInBytes - 8) >> 16) & 0xff);
        header[7] = (byte) (((fileSizeInBytes - 8) >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) NUMBER_OF_CHANNELS;
        header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (NUMBER_OF_CHANNELS * 2); // block align
        header[33] = 0;
        header[34] = (byte) BITS_PER_SAMPLE;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (dataSizeInBytes & 0xff);
        header[41] = (byte) ((dataSizeInBytes >> 8) & 0xff);
        header[42] = (byte) ((dataSizeInBytes >> 16) & 0xff);
        header[43] = (byte) ((dataSizeInBytes >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
