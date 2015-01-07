package com.c77.rtpmediaplayer.lib;

import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;

/**
 * Created by julian on 1/6/15.
 */
public class BufferedSample {
    private final ByteBuffer buffer;
    private final int index;

    private int sampleSize;
    private long sampleTimestamp;

    public BufferedSample(ByteBuffer buffer, int index) {
        this.buffer = buffer;
        this.index = index;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int getIndex() {
        return index;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public long getSampleTimestamp() {
        return sampleTimestamp;
    }

    public void setSampleTimestamp(long sampleTimestamp) {
        this.sampleTimestamp = sampleTimestamp;
    }

    @Override
    public String toString() {
        String res = "[";

        byte[] initialChunk = new byte[Math.min(16,sampleSize)];

        int oldpos = buffer.position();
        buffer.position(0);
        buffer.get(initialChunk);
        buffer.position(oldpos);
        res += new String(Hex.encodeHex(initialChunk));

        res += "], size: " + sampleSize + ", ts: " + sampleTimestamp;
        return res;
    }
}
