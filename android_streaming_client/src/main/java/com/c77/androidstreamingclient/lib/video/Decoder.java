package com.c77.androidstreamingclient.lib.video;

import com.c77.androidstreamingclient.lib.BufferedSample;
import com.c77.androidstreamingclient.lib.RtpPlayerException;

/**
 * Created by julian on 12/15/14.
 */
public interface Decoder {
    // Retrieves a buffer from the decoder
    public BufferedSample getSampleBuffer() throws RtpPlayerException;

    // Returns a new frame to be decoded
    public void decodeFrame(BufferedSample frame) throws Exception;
}
