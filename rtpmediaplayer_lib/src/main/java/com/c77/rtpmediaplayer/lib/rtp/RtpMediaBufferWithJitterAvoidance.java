package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by ashi on 1/13/15.
 */
public class RtpMediaBufferWithJitterAvoidance implements RtpMediaBuffer {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final java.lang.String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW";
    private State streamingState;
    private long lastTimestamp;

    ConcurrentSkipListMap.Entry<Long, Frame> currentFrameEntry;

    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        WAITING,    // Wait until there are enough frames
        STREAMING   // Receiving packets
    }

    private static boolean DEBUGGING = false;
    private static long SENDING_DELAY = 25;
    private static long FRAMES_DELAY_MILLISECONDS = 132;
    // time that takes a frame to arrive
    private static final int FRAME_DELAY = 33;

    private final RtpSessionDataListener upstream;
    private final DataPacketSenderThread dataPacketSenderThread;
    // frames sorted by their timestamp
    ConcurrentSkipListMap<Long, Frame> frames = new ConcurrentSkipListMap<Long, Frame>();
    private Log log = LogFactory.getLog(RtpMediaBufferWithJitterAvoidance.class);

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
        FRAMES_DELAY_MILLISECONDS = Long.parseLong(properties.getProperty(FRAMES_WINDOW_PROPERTY, "132"));
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (DEBUGGING) {
            log.info("Packet Arriving");
        }

        if (streamingState == State.IDLE) {
            this.session = session;
            this.participant = participant;
            lastTimestamp = getConvertedTimestamp(packet);

            streamingState = State.WAITING;
        }

        // discard packets that are too late
        if (State.STREAMING == streamingState && getConvertedTimestamp(packet) < lastTimestamp - FRAMES_DELAY_MILLISECONDS) {
            if (true) {
                log.info("Discarded getPacket with timestamp " + getConvertedTimestamp(packet));
            }
            return;
        }

        Frame frame = getFrameForPacket(packet);
        frames.put(new Long(frame.timestamp()), frame);

        // wait to have k frames to start streaming
        if (streamingState == State.WAITING && frames.size() >= FRAMES_DELAY_MILLISECONDS / FRAME_DELAY) {
            log.info("Start consuming!");
            streamingState = State.STREAMING;
            dataPacketSenderThread.start();
        }
    }

    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    private Frame getFrameForPacket(DataPacket packet) {
        Frame frame;
        long timestamp = getConvertedTimestamp(packet);
        if (frames.containsKey(timestamp)) {
            // if a frame with this timestamp already exists, add getPacket to it
            frame = frames.get(timestamp);
            // add getPacket to frame
            frame.addPacket(packet);
        } else {
            // if no frames with this timestamp exists, create a new one
            frame = new Frame(new DataPacketWithNalType(packet));
        }

        return frame;
    }

    @Override   
    public void stop() {
        if (dataPacketSenderThread != null) {
            dataPacketSenderThread.shutdown();
        }
    }

    private class DataPacketSenderThread extends Thread {
        private boolean running = true;
        private long timeWhenCycleStarted;
        private long delay;

        @Override
        public void run() {
            super.run();

            Frame frame = null;

            while (running) {
                timeWhenCycleStarted = System.currentTimeMillis();
                // go through all the frames which timestamp is the range [downTimestampBound,upTimestampBound)

                currentFrameEntry = frames.firstEntry();

                if (currentFrameEntry != null) {
                    frame = currentFrameEntry.getValue();
                    if (frame.isCompleted()) {
                        sendFrame(frame);
                    } else if (DEBUGGING) {
                        log.info("Discarded frame. It was not completed.");
                    }

                    frames.remove(currentFrameEntry.getKey());
                }

                waitForNextFrame();
            }
        }

        private void sendFrame(Frame frame) {
            // update timestamp of last frame sent
            lastTimestamp = frame.timestamp();

            for (DataPacketWithNalType packet : frame.getPackets()) {
                switch (packet.nalType()) {
                    case FULL:
                        ((RtpMediaExtractor) upstream).startAndSendFrame(packet.getPacket());
                        break;
                    case NOT_FULL:
                        ((RtpMediaExtractor) upstream).startAndSendFragmentedFrame(packet);
                        break;
                }
            }
        }

        private void waitForNextFrame() {
            try {
                delay = (System.currentTimeMillis() - timeWhenCycleStarted);

                long actualDelay = Math.max(SENDING_DELAY - delay, 0);

                sleep(actualDelay);
            } catch (InterruptedException e) {
                log.error("Error while waiting to send next frame", e);
            }
        }

        public void shutdown() {
            running = false;
        }
    }
}
