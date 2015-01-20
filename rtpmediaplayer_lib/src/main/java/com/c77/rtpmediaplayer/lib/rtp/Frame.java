package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by ashi on 1/20/15.
 */
public class Frame {
    private static final boolean DEBUGGING = false;
    private final long timestamp;
    // packets sorted by their sequence number
    ConcurrentSkipListMap<Integer, DataPacketWithNalType> packets;
    private Log log = LogFactory.getLog(Frame.class);

    /**
     * Create a frame from a getPacket
     *
     * @param packet
     */
    public Frame(DataPacketWithNalType packet) {
        packets = new ConcurrentSkipListMap<Integer, DataPacketWithNalType>();
        timestamp = packet.getTimestamp();
        packets.put(new Integer(packet.getSequenceNumber()), packet);
    }

    public void addPacket(DataPacket packet) {
        packets.put(new Integer(packet.getSequenceNumber()), new DataPacketWithNalType(packet));
    }

    public java.util.Collection<DataPacketWithNalType> getPackets() {
        return packets.values();
    }

    // check whether the frame is completed
    public boolean isCompleted() {
        boolean fuEnd;
        boolean fuStart;
        byte fuHeader;
        int startSeqNum = -1;
        DataPacketWithNalType packet = null;
        for (ConcurrentSkipListMap.Entry<Integer, DataPacketWithNalType> entry : packets.entrySet()) {
            packet = entry.getValue();
            switch (packet.nalType()) {
                case FULL:
                    return true;
                case NOT_FULL:
                    // start of the frame
                    if (packet.isStart()) {
                        if (DEBUGGING) {
                            log.info("FU-A start found. Starting new frame");
                        }
                        startSeqNum = packet.getSequenceNumber();
                    }

                    if (packet.isEnd()) {
                        if (DEBUGGING) {
                            log.info("FU-A end found. Sending frame!");
                        }

                        // if startSeqNum != -1, start package was found
                        // return true if all expected packets are present
                        return startSeqNum != -1 && (packet.getSequenceNumber() - startSeqNum + 1 == packets.size());
                    }
                    break;
            }
        }
        return false;
    }

    public long timestamp() {
        return timestamp;
    }
}