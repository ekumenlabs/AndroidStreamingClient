package com.c77.androidstreamingclient.lib.rtp;

import com.biasedbit.efflux.session.RtpSessionDataListener;

/**
 * Created by julian on 1/20/15.
 */
public interface RtpMediaBuffer extends RtpSessionDataListener {
    void stop();
}
