package com.c77.androidstreamingclient.lib;

import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;

/**
 * Created by julian on 1/6/15.
 */
public class BufferedSample {
    private final ByteBuffer buffer;
    private final int index;

    private int sampleSize;
    private long rtpTimestamp;

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

    public long getPresentationTimeUs() {
        // NOTE: We need to convert from RTP timestamp to sampleTime as expected by MediaCodec
        return rtpTimestamp * 1000L / 90L;
    }

    public void setRtpTimestamp(long rtpTimestamp) {
        this.rtpTimestamp = rtpTimestamp;
    }

    public long getRtpTimestamp() {
        return rtpTimestamp;
    }

    @Override
    public String toString() {
        String res = "[";

        byte[] initialChunk = new byte[Math.min(16, sampleSize)];

        int oldpos = buffer.position();
        buffer.position(0);
        buffer.get(initialChunk);
        buffer.position(oldpos);
        res += new String(Hex.encodeHex(initialChunk));

        res += "], size: " + sampleSize + ", pt: " + getPresentationTimeUs();
        return res;
    }
}
