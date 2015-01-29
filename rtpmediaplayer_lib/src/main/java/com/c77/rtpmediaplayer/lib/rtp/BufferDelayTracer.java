package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Created by julian on 1/20/15.
 */
public class BufferDelayTracer implements RtpSessionDataListener {
    private final PrintWriter traceWriter;

    public BufferDelayTracer(OutputStream out) {
        traceWriter = new PrintWriter(out);
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        traceWriter.write(System.currentTimeMillis() + "," + packet.getSequenceNumber() + "," + packet.getTimestamp() + "\n");
    }
}

