/*
* Copyright (C) 2015 Creativa77 SRL and others
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* Contributors:
*
* Ayelen Chavez ashi@creativa77.com.ar
* Julian Cerruti jcerruti@creativa77.com.ar
*
*/

package com.c77.androidstreamingclient.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Frame represented as a set of packets that share the same timestamp and have consecutive
 * sequence number.
 *
 * @author Ayelen Chavez
 */
public class H264Frame {
    private static final boolean DEBUGGING = false;
    private final long timestamp;
    // packets sorted by their sequence number
    ConcurrentSkipListMap<Integer, H264DataPacket> packets;
    private Log log = LogFactory.getLog(H264Frame.class);

    /**
     * Creates a frame from an H.264 packet
     *
     * @param packet
     */
    public H264Frame(DataPacket packet) {
        packets = new ConcurrentSkipListMap<Integer, H264DataPacket>();
        timestamp = packet.getTimestamp();
        packets.put(new Integer(packet.getSequenceNumber()), new H264DataPacket(packet));
    }

    /**
     * Adds a packet to the frame (this packet shares the timestamp with the frame.
     *
     * @param packet to be added to the frame's set of packets.
     */
    public void addPacket(DataPacket packet) {
        assert packet.getTimestamp() == timestamp : "Packet's timestamp is not equal to the Frame's timestamp";
        packets.put(new Integer(packet.getSequenceNumber()), new H264DataPacket(packet));
    }

    /**
     * Retrieves the known packets that share the same timestamp forming a frame. This frame may
     * not be completed.
     *
     * @return the H.264 packets that form the frame.
     */
    public java.util.Collection<H264DataPacket> getPackets() {
        return packets.values();
    }

    /**
     * Check whether the frame is completed or not.
     * If it contains a FULL or STAP-A packet, it is complete as it only needs that packet in order
     * to be so. If it is a NOT-FULL frame, it is completed only if there is a start, and end packet
     * and the ones in the middle.
     *
     * @return whether the frame is completed or not.
     */
    public boolean isCompleted() {
        int startSeqNum = -1;
        H264DataPacket packet = null;
        for (ConcurrentSkipListMap.Entry<Integer, H264DataPacket> entry : packets.entrySet()) {
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
                case STAPA:
                    return true;
            }
        }
        return false;
    }

    /**
     * @return the frame's timestamp
     */
    public long timestamp() {
        return timestamp;
    }
}