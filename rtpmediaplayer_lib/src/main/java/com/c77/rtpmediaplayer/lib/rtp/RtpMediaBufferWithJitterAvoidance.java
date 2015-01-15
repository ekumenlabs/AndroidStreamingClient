package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by ashi on 1/13/15.
 */
public class RtpMediaBufferWithJitterAvoidance implements RtpSessionDataListener {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final java.lang.String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW";

    private State streamingState;
    private long lastTimestamp;

    private long maxTimeCycleTime = 0;
    private int counter = 0;
    private long sumTimeCycleTimes = 0;


    // Stream streamingState
    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        CONFIGURING, // looking for frame delay
        STREAMING   // Receiving packets
    }

    private static boolean DEBUGGING = false;
    private static long SENDING_DELAY = 30;
    private static long FRAMES_DELAY_MILLISECONDS = 500;

    private final RtpSessionDataListener upstream;
    private final DataPacketSenderThread dataPacketSenderThread;
    // frames sorted by their timestamp
    SortedMap<Long, Frame> frames = new TreeMap<Long, Frame>();
    private Log log = LogFactory.getLog(RtpMediaBufferWithJitterAvoidance.class);
    private long downTimestampBound;
    private long upTimestampBound;
    RtpSession session;
    RtpParticipantInfo participant;

    public RtpMediaBufferWithJitterAvoidance(RtpSessionDataListener upstream) {
        this.upstream = upstream;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();
    }

    public RtpMediaBufferWithJitterAvoidance(RtpSessionDataListener upstream, Properties properties) {
        this.upstream = upstream;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();
        DEBUGGING = Boolean.parseBoolean(properties.getProperty(DEBUGGING_PROPERTY, "false"));
        FRAMES_DELAY_MILLISECONDS = Long.parseLong(properties.getProperty(FRAMES_WINDOW_PROPERTY, "800"));
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (streamingState == State.IDLE) {
            this.session = session;
            this.participant = participant;
            lastTimestamp = getConvertedTimestamp(packet);

            downTimestampBound = lastTimestamp - FRAMES_DELAY_MILLISECONDS;
            upTimestampBound = downTimestampBound + SENDING_DELAY;
            streamingState = State.STREAMING;
            dataPacketSenderThread.start();
        }

        // discard packets that are too late
        if (State.STREAMING == streamingState && getConvertedTimestamp(packet) < downTimestampBound) {
            if (DEBUGGING) {
                log.info("Discarded packet with timestamp " + getConvertedTimestamp(packet));
            }
            return;
        }

        synchronized (frames) {
            Frame frame = getFrameForPacket(packet);
            frames.put(new Long(frame.timestamp), frame);
        }
    }

    public void logValues() {
        log.info("Average: " + sumTimeCycleTimes/counter);
        log.info("Max delay: " + maxTimeCycleTime);
    }

    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    private Frame getFrameForPacket(DataPacket packet) {
        Frame frame;
        long timestamp = getConvertedTimestamp(packet);
        if (frames.containsKey(timestamp)) {
            // if a frame with this timestamp already exists, add packet to it
            frame = frames.get(timestamp);
            // add packet to frame
            frame.addPacket(packet);
        } else {
            // if no frames with this timestamp exists, create a new one
            frame = new Frame(packet);
        }

        return frame;
    }

    private class Frame {
        private final long timestamp;

        // packets sorted by their sequence number
        SortedMap<Integer, DataPacket> packets;

        /**
         * Create a frame from a packet
         *
         * @param packet
         */
        public Frame(DataPacket packet) {
            packets = new TreeMap<Integer, DataPacket>();
            timestamp = getConvertedTimestamp(packet);
            packets.put(new Integer(packet.getSequenceNumber()), packet);
        }

        public void addPacket(DataPacket packet) {
            packets.put(new Integer(packet.getSequenceNumber()), packet);
        }

        public java.util.Collection<DataPacket> getPackets() {
            return packets.values();
        }
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
            super.run();

            long timeWhenCycleStarted;
            long delay;

            while (running) {
                if (RtpMediaBufferWithJitterAvoidance.State.STREAMING == streamingState) {
                    timeWhenCycleStarted = System.currentTimeMillis();
                    // go through all the frames which timestamp is the range [downTimestampBound,upTimestampBound)
                    SortedMap<Long, Frame> copy = new TreeMap<Long, Frame>();

                    synchronized (frames) {
                        if (DEBUGGING) {
                            log.info("Copying #" + frames.size() + " frames");
                        }
                        copy.putAll(frames);
                    }

                    for (SortedMap.Entry<Long, Frame> entry : copy.entrySet()) {
                        Frame frame = entry.getValue();
                        if (DEBUGGING) {
                            log.info("Looking for frames between: [" + downTimestampBound + "," + upTimestampBound + ")");
                        }
                        long timestamp = frame.timestamp;
                        synchronized (frames) {
                            if (timestamp < upTimestampBound && timestamp >= downTimestampBound) {
                                Collection<DataPacket> packets = frame.getPackets();
                                for (DataPacket packet : packets) {
                                    upstream.dataPacketReceived(session, participant, packet);
                                }
                                frames.remove(entry.getKey());
                            } else if (timestamp < downTimestampBound) {
                                // remove old packages
                                frames.remove(entry.getKey());
                            }
                        }
                    }

                    try {
                        sleep(SENDING_DELAY);
                        downTimestampBound = upTimestampBound;
                        // use actual delay instead of SENDING_DELAY
                        delay = (System.currentTimeMillis() - timeWhenCycleStarted);
                        if (DEBUGGING) {
                            log.info("actual delay: " + delay);
                            maxTimeCycleTime = Math.max(delay, maxTimeCycleTime);
                            sumTimeCycleTimes += delay;
                            counter++;
                        }
                        upTimestampBound += delay;
                    } catch (InterruptedException e) {
                        log.error("Error while waiting to send next frame", e);
                    }
                }

                if (DEBUGGING && counter == 100) {
                    logValues();
                }
            }
        }

        public void shutdown() {
            running = false;
        }
    }
}
