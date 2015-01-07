package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by julian on 1/7/15.
 */
public class RtpMediaBuffer implements RtpSessionDataListener {
    private static Log log = LogFactory.getLog(RtpMediaBuffer.class);

    // Object that will receive ordered packets
    private final RtpSessionDataListener upstream;

    // Temporary cache map of packets received out of order
    private Map<Integer,DataPacket> packetMap = new HashMap();
    private Map<Integer,Long> timestampMap = new HashMap();

    // State variables
    private enum States {
        IDLE,       // Just started. Didn't receive any packets yet
        DIRECT,     // No packets out of order pending
        REORDER,    // There are out of order packets waiting to be processed
    };
    private States currentState;
    private int nextExpectedSequenceNumber;
    private long timestampDifference;   // Keep track of the difference between the packet timestamps
        // and this device's time at the time we received the first packet

    public RtpMediaBuffer(RtpSessionDataListener upstream) {
        this.upstream = upstream;
        currentState = States.IDLE;
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if(currentState == States.IDLE) {
            nextExpectedSequenceNumber = packet.getSequenceNumber();
            timestampDifference = System.currentTimeMillis() - packet.getTimestamp();
            currentState = States.DIRECT;
            log.info("Stream started. Timestamps: " + timestampDifference);
        }

        // If the received packet is the one we were expecting: send it for processing
        if(packet.getSequenceNumber() == nextExpectedSequenceNumber) {
            upstream.dataPacketReceived(session, participant, packet);
            nextExpectedSequenceNumber++;

            // Also send any subsequent packets that we were buffering!
            while(packetMap.containsKey(nextExpectedSequenceNumber)) {
                log.warn("Sending old buffered packet. #" + nextExpectedSequenceNumber);
                upstream.dataPacketReceived(session, participant, packetMap.get(nextExpectedSequenceNumber));
                packetMap.remove(nextExpectedSequenceNumber);
                timestampMap.remove(nextExpectedSequenceNumber);
                nextExpectedSequenceNumber++;
            }

        // Otherwise, store the packet in the buffer for later
        } else {
            log.warn("Saving out of order packet. #" + packet.getSequenceNumber());
            packetMap.put(packet.getSequenceNumber(), packet);
            timestampMap.put(packet.getSequenceNumber(), packet.getTimestamp());
        }
    }
}
