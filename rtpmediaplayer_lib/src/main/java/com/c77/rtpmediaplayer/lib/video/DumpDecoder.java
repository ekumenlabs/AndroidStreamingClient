package com.c77.rtpmediaplayer.lib.video;

import android.util.Log;

import com.c77.rtpmediaplayer.lib.BufferedSample;
import com.c77.rtpmediaplayer.lib.RtpPlayerException;

import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by julian on 12/15/14.
 */
public class DumpDecoder implements Decoder {
    private static final String TAG = DumpDecoder.class.getName();
    private ByteBuffer buffer = ByteBuffer.allocate(50000);

    @Override
    public BufferedSample getSampleBuffer() throws RtpPlayerException {
        buffer.reset();
        return new BufferedSample(buffer, 0);
    }

    @Override
    public void decodeFrame(BufferedSample frame) {
        String debugging = "Size = " + frame.getSampleSize();
        debugging += " [" + new String(Hex.encodeHex(Arrays.copyOf(frame.getBuffer().array(), 16))) + "]";
        Log.i("DumpDecoder", debugging);
    }

    @Override
    public void restart() {

    }
}
