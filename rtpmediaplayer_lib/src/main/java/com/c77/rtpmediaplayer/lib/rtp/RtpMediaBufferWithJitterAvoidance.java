package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by ashi on 1/13/15.
 */
public class RtpMediaBufferWithJitterAvoidance implements RtpMediaBuffer {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final java.lang.String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW";
    public static final int FPS = 33;
    private State streamingState;
    private long lastTimestamp;

    ConcurrentSkipListMap.Entry<Long, Frame> currentFrameEntry;

    private BufferDelayTracer bufferDelayTracer;
    private DataPacketWithNalType lastPacket;
    private long playHead = 0;
    private int counter = 0;

    public void addDataListener(BufferDelayTracer bufferDelayTracer) {
        this.bufferDelayTracer = bufferDelayTracer;
    }

    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        WAITING,    // Wait until there are enough frames
        STREAMING   // Receiving packets
    }

    private static boolean DEBUGGING = false;
    private static long SENDING_DELAY = 28;
    private static int FRAMES = 50;

    private final RtpMediaExtractor rtpMediaExtractor;
    private final DataPacketSenderThread dataPacketSenderThread;
    // frames sorted by their timestamp
    ConcurrentSkipListMap<Long, Frame> frames = new ConcurrentSkipListMap<Long, Frame>();
    private Log log = LogFactory.getLog(RtpMediaBufferWithJitterAvoidance.class);

    RtpSession session;
    RtpParticipantInfo participant;

    public RtpMediaBufferWithJitterAvoidance(RtpMediaExtractor rtpMediaExtractor) {
        this.rtpMediaExtractor = rtpMediaExtractor;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();
    }

    public RtpMediaBufferWithJitterAvoidance(RtpMediaExtractor rtpMediaExtractor, Properties properties) {
        this.rtpMediaExtractor = rtpMediaExtractor;
        streamingState = State.IDLE;
        dataPacketSenderThread = new DataPacketSenderThread();
        DEBUGGING = Boolean.parseBoolean(properties.getProperty(DEBUGGING_PROPERTY, "false"));
        FRAMES = Integer.parseInt(properties.getProperty(FRAMES_WINDOW_PROPERTY, "50"));
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (DEBUGGING) {
            log.info("Packet Arriving");
        }

        if (streamingState == State.IDLE) {
            this.session = session;
            this.participant = participant;
            playHead = getConvertedTimestamp(packet);

            streamingState = State.WAITING;
        }

        // TODO: ver de mover lastTimestamp para adelante
        // discard packets that are too late
        if (State.STREAMING == streamingState && getConvertedTimestamp(packet) < playHead) {
            if (DEBUGGING) {
                log.info("Discarded getPacket with timestamp " + getConvertedTimestamp(packet) + ", buffer size: " + frames.size());
            }
            // return;
        }

        addPacketToFrame(packet);

        // wait to have k frames to start streaming
        int bufferSize = frames.size();
        if (streamingState == State.WAITING && bufferSize >= FRAMES) {
            playHead = frames.firstKey();
            log.info("Start consuming!");
            streamingState = State.STREAMING;
            dataPacketSenderThread.start();
        }
    }

    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    private void addPacketToFrame(DataPacket packet) {
        lastPacket = new DataPacketWithNalType(packet);

        // TODO: change!
        long timestamp = getConvertedTimestamp(packet);
        Frame frame = frames.get(timestamp);
        if (frame != null) {
            // if a frame with this timestamp already exists, add getPacket to it
            // add getPacket to frame
            frame.addPacket(packet);
        } else {
            // if no frames with this timestamp exists, create a new one
            frame = new Frame(lastPacket);
            frames.put(new Long(frame.timestamp()), frame);
        }
    }

    @Override
    public void stop() {
        log.info("Video Player closed!");

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
            Frame frame = null;

            while (running) {
                timeWhenCycleStarted = System.nanoTime();
                // go through all the frames which timestamp is the range [downTimestampBound,upTimestampBound)

                currentFrameEntry = getNextFrame();

                if (currentFrameEntry != null) {
                    frame = currentFrameEntry.getValue();

                    if (frame.isCompleted()) {
                        sendFrame(frame);
                    } else {
                        log.info("Discarded frame. It was not completed.");
                    }

                    // update timestamp of last frame sent or should have been sent
                    lastTimestamp = frame.timestamp();

                    frames.remove(currentFrameEntry.getKey());
                } else {
                    // log.info("Not frame to be shown");
                }

                waitForNextFrame();
            }
        }

        private void printFrames() {
            String framesToPrint = "";

            for (Long timestamp : frames.keySet()) {
                framesToPrint += timestamp + " - ";
            }

            log.info("Frames: " + framesToPrint);
        }
        private Map.Entry<Long, Frame> getNextFrame() {
            Map.Entry<Long, Frame> nextEntry;

            nextEntry = frames.firstEntry();

            int limit = 20;
            if (counter < limit) {
                log.info("Looking for frames between: [" + (playHead - 15) + " - " + (playHead + FPS) + ")");
                printFrames();
                counter++;
            }

            while (nextEntry != null && nextEntry.getValue().timestamp() < playHead - 15) {
                frames.remove(nextEntry.getKey());
                nextEntry = frames.firstEntry();
                if (counter < limit) {
                    log.info("Removing frame: " + nextEntry.getValue().timestamp());
                }
            }

            if (nextEntry != null && nextEntry.getValue().timestamp() < playHead + FPS) {
                if (counter < limit) {
                    log.info("Sending frame: " + nextEntry.getValue().timestamp());
                }
                return nextEntry;
            }

            return null;
        }

        private void sendFrame(Frame frame) {
            log.info("Consuming frame");
            for (DataPacketWithNalType packet : frame.getPackets()) {
                rtpMediaExtractor.sendPacket(packet);
            }
        }

        private void waitForNextFrame() {
            playHead += FPS;

            try {
                delay = System.nanoTime() - timeWhenCycleStarted;

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
