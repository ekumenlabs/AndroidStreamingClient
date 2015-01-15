package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by julian on 1/7/15.
 */
public class RtpMediaBuffer implements RtpSessionDataListener {
    private static final long OUT_OF_ORDER_MAX_TIME = 1000; // milliseconds. Wait up to this amount of
    // time for missing packets to arrive. If we start getting packets newer than this, discard the old
    // ones and restart

    // Object that will receive ordered packets
    private final RtpSessionDataListener upstream;

    private static Log log = LogFactory.getLog(RtpMediaBuffer.class);

    // Temporary cache map of packets received out of order
    private Map<Integer, DataPacket> packetMap = new HashMap();
    private Map<Integer, Long> timestampMap = new HashMap();

    // State variables
    private enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        DIRECT,     // No packets out of order pending
        REORDER,    // There are out of order packets waiting to be processed
    }

    ;
    private State currentState;
    private int nextExpectedSequenceNumber;
    private long lastProcessedTimestamp;    // The timestamp of the last packet we were able to successfully
    // send upstream for processing

    private long timestampDifference;   // Keep track of the difference between the packet timestamps
    // and this device's time at the time we received the first packet

    public RtpMediaBuffer(RtpSessionDataListener upstream) {
        this.upstream = upstream;
        currentState = State.IDLE;
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (currentState == State.IDLE) {
            nextExpectedSequenceNumber = packet.getSequenceNumber();
            timestampDifference = System.currentTimeMillis() - packet.getTimestamp()/90;

            if (RtpMediaDecoder.DEBUGGING) {
                log.info("Stream started. Timestamps: " + timestampDifference);
            }

            currentState = State.DIRECT;
        }

        // If the received packet is the one we were expecting: send it for processing
        if (packet.getSequenceNumber() == nextExpectedSequenceNumber) {
            upstream.dataPacketReceived(session, participant, packet);
            lastProcessedTimestamp = packet.getTimestamp()/90;
            nextExpectedSequenceNumber = packet.getSequenceNumber() + 1;

            // Also send any subsequent packets that we were buffering!
            while (packetMap.containsKey(nextExpectedSequenceNumber)) {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Sending old buffered packet. #" + nextExpectedSequenceNumber);
                }
                DataPacket oldPacket = packetMap.remove(nextExpectedSequenceNumber);
                timestampMap.remove(nextExpectedSequenceNumber);

                upstream.dataPacketReceived(session, participant, oldPacket);
                lastProcessedTimestamp = oldPacket.getTimestamp()/90;
                nextExpectedSequenceNumber = oldPacket.getSequenceNumber() + 1;
            }

        } else {
            // If we are receiving packets that are much newer than what we were waiting for, discard
            // our buffers and restart from here
            if (packet.getTimestamp()/90 - lastProcessedTimestamp > OUT_OF_ORDER_MAX_TIME) {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Out of order packets are getting too old. Resetting");
                }
                upstream.dataPacketReceived(session, participant, packet);
                lastProcessedTimestamp = packet.getTimestamp()/90;
                nextExpectedSequenceNumber = packet.getSequenceNumber() + 1;

                packetMap.clear();
                timestampMap.clear();

                // Otherwise, store the packet in the buffer for later
            } else {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Saving out of order packet. #" + packet.getSequenceNumber());
                }

                packetMap.put(packet.getSequenceNumber(), packet);
                timestampMap.put(packet.getSequenceNumber(), packet.getTimestamp()/90);
            }
        }
    }
}
