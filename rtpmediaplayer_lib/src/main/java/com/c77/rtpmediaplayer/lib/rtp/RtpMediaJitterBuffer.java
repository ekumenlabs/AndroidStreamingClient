package com.c77.rtpmediaplayer.lib.rtp;

import android.provider.ContactsContract;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by ashi on 1/13/15.
 */
public class RtpMediaJitterBuffer implements RtpMediaBuffer {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW_TIME";

    private long lastTimestamp;

    private long maxTimeCycleTime = 0;
    private int counter = 0;
    private long sumTimeCycleTimes = 0;

    // Jitter buffer variables
    private long presentationToSystemDifference;    // Used to convert between presentation and system time
    private long playHeadPresentationTime;                // Current position of the play head. Any packet older
        // than this time has already been sent down to the extractor and decoder

    // Stream streamingState
    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        CONFIGURING, // looking for frame delay
        STREAMING   // Receiving packets
    }
    private State streamingState;

    private static boolean DEBUGGING = false;
    private static long SEND_LOOP_WAIT = 20;
    private static long BUFFER_SIZE_MILLISECONDS = 500;

    private final RtpSessionDataListener upstream;
    private RtpSession session;
    private RtpParticipantInfo participant;

    private final DataPacketSenderThread dataPacketSenderThread;

    // packets sorted by presentation time
    TreeMap<Integer,DataPacket> buffer = new TreeMap();

    private Log log = LogFactory.getLog(RtpMediaJitterBuffer.class);

    public RtpMediaJitterBuffer(RtpSessionDataListener upstream, Properties properties) {
        this.upstream = upstream;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();
        DEBUGGING = Boolean.parseBoolean(properties.getProperty(DEBUGGING_PROPERTY, "false"));
        BUFFER_SIZE_MILLISECONDS = Long.parseLong(properties.getProperty(FRAMES_WINDOW_PROPERTY, "800"));
        log.info("Using RtpMediaJitterBuffer with BUFFER_SIZE_MILLISECONDS = [" + BUFFER_SIZE_MILLISECONDS + "]");
    }

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

    public void logValues() {
        log.info("Average: " + sumTimeCycleTimes/counter);
        log.info("Max delay: " + maxTimeCycleTime);
    }

    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    public void stop() {
        if (dataPacketSenderThread != null) {
            dataPacketSenderThread.shutdown();
        }
    }

    private class DataPacketSenderThread extends Thread {
        private boolean running = true;

        @Override
        public void run() {
            try {
                while(running) {
                    // Wait first, since the thread is started as soon as the first packet arrives
                    sleep(SEND_LOOP_WAIT);

                    // Advance play head to the current time
                    long previousPlayHeadPresentationTime = playHeadPresentationTime;
                    playHeadPresentationTime = System.currentTimeMillis() - presentationToSystemDifference - BUFFER_SIZE_MILLISECONDS;

                    // Get packets up to the next play head an pass on to the extractor
                    int dropped = 0;
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
                                dropped++;
                            } else {
                                packets.add(entry.getValue());
                             }
                            buffer.remove(entry.getKey());
                        }
                    }

                    // Send to extractor and decoder outside of blocking loop
                    for(DataPacket packet: packets) {
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

        public void shutdown() {
            running = false;
        }
    }

    private String logPacket(DataPacket value) {
        return " (s#,pt) (" + value.getSequenceNumber() + "/" + value.getTimestamp() + ")";
    }
}
