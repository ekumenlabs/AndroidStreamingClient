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

package com.c77.androidstreamingclient.lib.rtp.buffer;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.androidstreamingclient.lib.rtp.RtpMediaDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * RTP buffer that sends packets upstream for processing immediately as long as they arrive in order.
 * <p/>
 * Approach: a packet will be sent upstream only if it is the one being expected. If a received packet
 * is newer than the one being expected, it will be stored in order.
 * If stored packages are older than the configured threshold, they will be discarded.
 *
 * @author Julian Cerruti
 */
public class MinDelayRtpMediaBuffer implements RtpMediaBuffer {
    public static final String CONFIG_TIMEOUT_MS = "NODELAY_TIMEOUT";

    private static final Log log = LogFactory.getLog(MinDelayRtpMediaBuffer.class);
    // Object that will receive ordered packets
    private final RtpSessionDataListener upstream;
    // milliseconds. Wait up to this amount of time for missing packets to arrive. If we start
    // getting packets newer than this, discard the old ones and restart
    private long OUT_OF_ORDER_MAX_TIME = 1000;
    // Temporary cache map of packets received out of order
    private Map<Integer, DataPacket> packetMap = new HashMap();
    private Map<Integer, Long> timestampMap = new HashMap();
    private State currentState;
    private int nextExpectedSequenceNumber;
    // The timestamp of the last packet we were able to successfully send upstream for processing
    private long lastProcessedTimestamp;
    // Keep track of the difference between the packet timestamps and this device's time at the
    // time we received the first packet
    private long timestampDifference;

    /**
     * Creates a RTP buffer with a given configuration.
     *
     * @param upstream      object that will receive packets in order
     * @param configuration if OUT_OF_ORDER_MAX_TIME, its value will replace the default one (1000 ms)
     */
    public MinDelayRtpMediaBuffer(RtpSessionDataListener upstream, Properties configuration) {
        configuration = (configuration != null) ? configuration : new Properties();
        this.upstream = upstream;
        currentState = State.IDLE;

        OUT_OF_ORDER_MAX_TIME = Long.parseLong(configuration.getProperty(CONFIG_TIMEOUT_MS, Long.toString(OUT_OF_ORDER_MAX_TIME)));
        log.info("Using MinDelayRtpMediaBuffer with OUT_OF_ORDER_MAX_TIME = [" + OUT_OF_ORDER_MAX_TIME + "]");
    }

    /**
     * Does nothing on stop.
     */
    @Override
    public void stop() {

    }

    /**
     * When a new packet is received, it decides whether to send it to upstream or not.
     * The sent packets are ordered.
     *
     * @param session
     * @param participant
     * @param packet
     */
    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (currentState == State.IDLE) {
            nextExpectedSequenceNumber = packet.getSequenceNumber();
            timestampDifference = System.currentTimeMillis() - packet.getTimestamp() / 90;

            if (RtpMediaDecoder.DEBUGGING) {
                log.info("Stream started. Timestamps: " + timestampDifference);
            }

            currentState = State.DIRECT;
        }

        // If the received packet is the one we were expecting: send it for processing
        if (packet.getSequenceNumber() == nextExpectedSequenceNumber) {
            try {
                upstream.dataPacketReceived(session, participant, packet);
            } catch (Exception e) {
                log.error("Error while trying to pass packet to upstream", e);
            }
            lastProcessedTimestamp = packet.getTimestamp() / 90;
            nextExpectedSequenceNumber = packet.getSequenceNumber() + 1;

            // Also send any subsequent packets that we were buffering!
            while (packetMap.containsKey(nextExpectedSequenceNumber)) {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Sending old buffered packet. #" + nextExpectedSequenceNumber);
                }
                DataPacket oldPacket = packetMap.remove(nextExpectedSequenceNumber);
                timestampMap.remove(nextExpectedSequenceNumber);

                try {
                    upstream.dataPacketReceived(session, participant, oldPacket);
                } catch (Exception e) {
                    log.error("Error while trying to pass packet to upstream", e);
                }
                lastProcessedTimestamp = oldPacket.getTimestamp() / 90;
                nextExpectedSequenceNumber = oldPacket.getSequenceNumber() + 1;
            }

        } else {
            // If we are receiving packets that are much newer than what we were waiting for, discard
            // our buffers and restart from here
            if (packet.getTimestamp() / 90 - lastProcessedTimestamp > OUT_OF_ORDER_MAX_TIME) {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Out of order packets are getting too old. Resetting");
                }
                try {
                    upstream.dataPacketReceived(session, participant, packet);
                } catch (Exception e) {
                    log.error("Error while trying to pass packet to upstream", e);
                }
                lastProcessedTimestamp = packet.getTimestamp() / 90;
                nextExpectedSequenceNumber = packet.getSequenceNumber() + 1;

                packetMap.clear();
                timestampMap.clear();

                // Otherwise, store the packet in the buffer for later
            } else {
                if (RtpMediaDecoder.DEBUGGING) {
                    log.warn("Saving out of order packet. #" + packet.getSequenceNumber());
                }

                packetMap.put(packet.getSequenceNumber(), packet);
                timestampMap.put(packet.getSequenceNumber(), packet.getTimestamp() / 90);
            }
        }
    }

    /**
     * State constants.
     */
    private enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        DIRECT,     // No packets out of order pending
        REORDER,    // There are out of order packets waiting to be processed
    }
}
