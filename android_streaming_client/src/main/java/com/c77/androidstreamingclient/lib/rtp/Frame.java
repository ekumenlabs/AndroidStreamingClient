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
                case STAPA:
                    return true;
            }
        }
        return false;
    }

    public long timestamp() {
        return timestamp;
    }
}