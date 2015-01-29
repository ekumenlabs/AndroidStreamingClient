package com.c77.rtpmediaplayer.lib.rtp;

import android.media.MediaFormat;

/**
 * Created by julian on 1/26/15.
 */
public interface MediaExtractor {
    // Think how to get CSD-0/CSD-1 codec-specific data chunks
    MediaFormat getMediaFormat();
}
