package com.c77.rtpmediaplayer.lib.video;

/**
 * Created by julian on 12/15/14.
 */
public interface Decoder {
    public void decodeFrame(byte[] frameBytes);

    void printFrame(byte[] frame);
}
