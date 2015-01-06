package com.c77.rtpmediaplayer.lib.video;

import android.util.Log;

import org.apache.commons.codec.binary.Hex;

import java.util.Arrays;

/**
 * Created by julian on 12/15/14.
 */
public class DumpDecoder implements Decoder {
    private static final String TAG = DumpDecoder.class.getName();

    @Override
    public void decodeFrame(byte[] frameBytes, long timestamp) {
        String debugging = "Size = " + frameBytes.length;
        debugging += " [" + new String(Hex.encodeHex(Arrays.copyOf(frameBytes, 16))) + "]";
        Log.i(TAG, debugging);
    }

    @Override
    public void printFrame(byte[] frame) {

    }
}
