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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * RTP buffer that keeps a fixed amount of time from the initially received packet and advances
 * at a fixed rate, ordering received packets and sending upstream all received packets ordered
 * and at a fixed rate.
 * <p/>
 * Approach: It keeps two threads. One will store the packets that arrive to the client, the other one will
 * consume them with some wisdom.
 *
 * @author Ayelen Chavez
 * @author Julian Cerruti
 */
public class TimeWindowRtpMediaBuffer implements RtpMediaBuffer {
    // properties
    private static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW_TIME";

    private static final Log log = LogFactory.getLog(TimeWindowRtpMediaBuffer.class);
    private static boolean DEBUGGING = false;
    private static long SEND_LOOP_WAIT = 20;
    private static long BUFFER_SIZE_MILLISECONDS = 500;

    private final RtpSessionDataListener upstream;
    private final DataPacketSenderThread dataPacketSenderThread;

    // packets sorted by presentation time
    TreeMap<Integer, DataPacket> buffer = new TreeMap();
    private long maxTimeCycleTime = 0;
    private int counter = 0;
    private long sumTimeCycleTimes = 0;

    // Jitter buffer variables
    // Used to convert between presentation and system time
    private long presentationToSystemDifference;
    // Current position of the play head. Any packet older
    // than this time has already been sent to upstream.
    private long playHeadPresentationTime;

    private State streamingState;
    private RtpSession session;
    private RtpParticipantInfo participant;

    /**
     * Creates an RTP buffer which work is to avoid network jitter.
     * It maintains two threads. One that stores frames while packets arrive. The other one
     * consumes the frames sending them to upstream.
     *
     * @param upstream
     * @param properties
     */
    public TimeWindowRtpMediaBuffer(RtpSessionDataListener upstream, Properties properties) {
        this.upstream = upstream;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();

        // load properties
        properties = (properties != null) ? properties : new Properties();
        DEBUGGING = Boolean.parseBoolean(properties.getProperty(DEBUGGING_PROPERTY, "false"));
        BUFFER_SIZE_MILLISECONDS = Long.parseLong(properties.getProperty(FRAMES_WINDOW_PROPERTY, "500"));
        log.info("Using TimeWindowRtpMediaBuffer with BUFFER_SIZE_MILLISECONDS = [" + BUFFER_SIZE_MILLISECONDS + "]");
    }

    /**
     * When a new data packet arrives, it may be discarded if it is older than the play head.
     * If not, it will be stored in a buffer.
     * It starts the consumer's thread.
     *
     * @param session
     * @param participant
     * @param packet
     */
    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        long systemTimestamp = System.currentTimeMillis();
        long presentationTimestamp = getConvertedTimestamp(packet);

        if (streamingState == State.IDLE) {
            this.session = session;
            this.participant = participant;

            // Declare system to presentation timestamp difference based on this one packet
            presentationToSystemDifference = systemTimestamp - presentationTimestamp;
            playHeadPresentationTime = presentationTimestamp - BUFFER_SIZE_MILLISECONDS;

            streamingState = State.STREAMING;

            dataPacketSenderThread.start();
        }

        // discard packets that are too late
        if (State.STREAMING == streamingState && presentationTimestamp < playHeadPresentationTime) {
            if (DEBUGGING) {
                log.info("Discarded packet: (s#, pt, st)" + packet.getSequenceNumber() + "/" +
                        presentationTimestamp + "/" + systemTimestamp);
            }
            return;
        }

        synchronized (this) {
            buffer.put(packet.getSequenceNumber(), packet);
        }
    }

    /**
     * Retrieves a timestamp modified to revert libstreaming modifications to timestamps.
     *
     * @param packet
     * @return
     */
    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    /**
     * Stops the consuming thread.
     */
    public void stop() {
        if (dataPacketSenderThread != null) {
            dataPacketSenderThread.shutdown();
        }
    }

    /**
     * Consuming thread.
     * This thread consumes frames waiting a fixed delay between frames.
     * For every running cycle, it will consume frames which timestamp are between the last play head
     * presentation time and the current time.
     */
    private class DataPacketSenderThread extends Thread {
        private boolean running = true;

        /**
         * Runs the consumer's thread.
         * It waits a fixed time before consuming. Then calculates which frames should be consumed,
         * depending on their timestamps and the presentation time of the play head.
         */
        @Override
        public void run() {
            try {
                while (running) {
                    // Wait first, since the thread is started as soon as the first packet arrives
                    sleep(SEND_LOOP_WAIT);

                    // Advance play head to the current time
                    long previousPlayHeadPresentationTime = playHeadPresentationTime;
                    playHeadPresentationTime = System.currentTimeMillis() - presentationToSystemDifference - BUFFER_SIZE_MILLISECONDS;

                    // Get packets up to the next play head an pass on to the extractor
                    List<DataPacket> packets = new ArrayList<DataPacket>();
                    synchronized (this) {
                        while (true) {
                            Map.Entry<Integer, DataPacket> entry = buffer.firstEntry();
                            if (entry == null) {
                                break;
                            }
                            long packetPresentationTime = getConvertedTimestamp(entry.getValue());
                            if (packetPresentationTime > playHeadPresentationTime) {
                                break;
                            } else if (packetPresentationTime < previousPlayHeadPresentationTime) {
                                log.warn("Dropping packet from buffer. This shouldn't happen");
                            } else {
                                packets.add(entry.getValue());
                            }
                            buffer.remove(entry.getKey());
                        }
                    }

                    // Send to extractor and decoder outside of blocking loop
                    for (DataPacket packet : packets) {
                        try {
                            upstream.dataPacketReceived(session, participant, packet);
                        } catch (Throwable t) {
                            log.error("Exception while sending packet to extractor", t);
                        }
                    }
                }
            } catch (Throwable t) {
                log.error("Exiting jitter buffer loop due to exception", t);
            }
        }

        /**
         * Stops the consuming thread's loop.
         */
        public void shutdown() {
            running = false;
        }
    }
}
