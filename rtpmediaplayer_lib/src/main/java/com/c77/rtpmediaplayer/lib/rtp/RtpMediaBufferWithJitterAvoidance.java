package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;

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
    private static final java.lang.String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW";

    private State streamingState;
    private long lastTimestamp;
    private long startingPoint;

    // Stream streamingState
    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        CONFIGURING, // looking for frame delay
        STREAMING   // Receiving packets
    }

    private static boolean DEBUGGING = false;
    private static long SENDING_DELAY = 50;
    private static long FRAMES_WINDOW_MILLISECONDS = 100;

    private final RtpSessionDataListener upstream;
    private final DataPacketSenderThread dataPacketSenderThread;
    // frames sorted by their timestamp
    SortedMap<Long, Frame> frames = new TreeMap<Long, Frame>();
    private Log log = LogFactory.getLog(RtpMediaBufferWithJitterAvoidance.class);
    private long downTimestampBound;
    private long upTimestampBound;

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
        FRAMES_WINDOW_MILLISECONDS = Long.parseLong(properties.getProperty(FRAMES_WINDOW_PROPERTY, "100"));
    }

    @Override
    public void dataPacketReceived(DataPacket packet) {
        if (streamingState == State.IDLE) {
            lastTimestamp = packet.getConvertedTimestamp();
            startingPoint = System.currentTimeMillis() - lastTimestamp;
            streamingState = State.CONFIGURING;
        } else if (streamingState == State.CONFIGURING && packet.getConvertedTimestamp() != lastTimestamp) {
            SENDING_DELAY = Math.abs(packet.getConvertedTimestamp() - lastTimestamp);

            if (DEBUGGING) {
                log.info("Sending delay: " + SENDING_DELAY);
            }
            streamingState = State.STREAMING;
            dataPacketSenderThread.start();
        }

        // discard packets that are too late
        if (State.STREAMING == streamingState && packet.getConvertedTimestamp() < downTimestampBound) {
            if (DEBUGGING) {
                log.info("Discarded packet with timestamp " + packet.getConvertedTimestamp());
            }
            return;
        }

        synchronized (frames) {
            Frame frame = getFrameForPacket(packet);
            frames.put(new Long(frame.timestamp), frame);
        }
    }

    private Frame getFrameForPacket(DataPacket packet) {
        Frame frame;
        long timestamp = packet.getConvertedTimestamp();
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
         * @param packet
         */
        public Frame(DataPacket packet) {
            packets = new TreeMap<Integer, DataPacket>();
            timestamp = packet.getConvertedTimestamp();
            packets.put(new Integer(packet.getSequenceNumber()), packet);
        }

        public void addPacket(DataPacket packet) {
            packets.put(new Integer(packet.getSequenceNumber()), packet);
        }

        public java.util.Collection<DataPacket> getPackets() {
            return packets.values();
        }
    }

    private class DataPacketSenderThread extends Thread {
        @Override
        public void run() {
            super.run();

            while (true) {
                if (RtpMediaBufferWithJitterAvoidance.State.STREAMING == streamingState) {
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
                        long timestamp = entry.getKey();

                        if (timestamp < upTimestampBound && timestamp >= downTimestampBound) {
                            Collection<DataPacket> packets = frame.getPackets();
                            for (DataPacket packet : packets) {
                                upstream.dataPacketReceived(packet);
                            }

                            synchronized (frames) {
                                frames.remove(entry.getKey());
                            }
                        } else if (timestamp < downTimestampBound) {
                            // remove old packages
                            synchronized (frames) {
                                frames.remove(entry.getKey());
                            }
                        }
                    }

                    try {
                        sleep(SENDING_DELAY);
                        downTimestampBound = System.currentTimeMillis() - startingPoint;
                        upTimestampBound = System.currentTimeMillis() - startingPoint + FRAMES_WINDOW_MILLISECONDS;
                    } catch (InterruptedException e) {
                        log.error("Error while waiting to send next frame", e);
                    }
                }
            }
        }
    }
}