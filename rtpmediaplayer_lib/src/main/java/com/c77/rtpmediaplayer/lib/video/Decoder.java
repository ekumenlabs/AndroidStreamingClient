package com.c77.rtpmediaplayer.lib.video;

/**
 * Created by julian on 12/15/14.
 */
public interface Decoder {
    public void decodeFrame(byte[] frameBytes, long timestamp);

    void printFrame(byte[] frame);
}
