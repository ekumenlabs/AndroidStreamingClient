package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by ashi on 1/13/15.
 */
public class RtpMediaBufferWithJitterAvoidance implements RtpMediaBuffer {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final java.lang.String FRAMES_WINDOW_PROPERTY = "FRAMES_WINDOW";

    public static final int FPS = 30;

    public static long timeBetweenFrames = 1000 / FPS;

    private State streamingState;

    ConcurrentSkipListMap.Entry<Long, Frame> currentFrameEntry;

    private BufferDelayTracer bufferDelayTracer;
    private long playHead = 0;
    private int counter = 0;
    public static final int H264_STANDARD_MULTIPLIER = 9000;
    private List<Long> timestamps = new ArrayList<Long>();


    public void addDataListener(BufferDelayTracer bufferDelayTracer) {
        this.bufferDelayTracer = bufferDelayTracer;
    }

    protected enum State {
        IDLE,       // Just started. Didn't receive any packets yet
        WAITING,    // Wait until there are enough frames
        STREAMING   // Receiving packets
    }

    private static boolean DEBUGGING = false;
    private static boolean DEBUGGING_JITTER = false;
    private static long SENDING_DELAY = 28;
    private static int FRAMES = 2;

    private final RtpMediaExtractor rtpMediaExtractor;
    private final DataPacketSenderThread dataPacketSenderThread;
    // frames sorted by their convertedTimestamp
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
        FRAMES = Integer.parseInt(properties.getProperty(FRAMES_WINDOW_PROPERTY, "10"));
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        if (DEBUGGING) {
            log.info("Packet Arriving " + packet.getTimestamp() + ", " + getConvertedTimestamp(packet) + ", " + System.currentTimeMillis());
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
                log.info("Discarded getPacket with convertedTimestamp " + getConvertedTimestamp(packet) + ", buffer size: " + frames.size());
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
        return packet.getTimestamp() / H264_STANDARD_MULTIPLIER;
    }

    private void addPacketToFrame(DataPacket packet) {
        // TODO: change!
        long timestamp = getConvertedTimestamp(packet);
        Frame frame = frames.get(timestamp);
        if (frame != null) {
            // if a frame with this convertedTimestamp already exists, add getPacket to it
            // add getPacket to frame
            frame.addPacket(packet);
        } else {
            // if no frames with this convertedTimestamp exists, create a new one
            frame = new Frame(packet);
            frames.put(timestamp, frame);
            timestamps.add(timestamp);

            // update time between frames
            timeBetweenFrames = calculateAverageOfDiffs();
            if (DEBUGGING_JITTER) {
                log.info("New time between frames: " + timeBetweenFrames);
            }
        }
    }

    private long calculateAverageOfDiffs() {
        long sum = 0;
        if (timestamps.size() > 1) {
            for (int i = timestamps.size() - 1; i > 0; i--) {
                sum += (timestamps.get(i) - timestamps.get(i-1));
            }

            return sum / (timestamps.size() - 1);
        }
        return timeBetweenFrames;
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

            while (true) {
                timeWhenCycleStarted = System.nanoTime();
                // go through all the frames which convertedTimestamp is the range [downTimestampBound,upTimestampBound)

                currentFrameEntry = getNextFrame();

                if (currentFrameEntry != null) {
                    frame = currentFrameEntry.getValue();

                    if (frame.isCompleted()) {
                        sendFrame(frame);
                    } else if (DEBUGGING_JITTER) {
                        log.info("Discarded frame. It was not completed.");
                    }

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

            log.info(counter + ") Playhead: " + playHead + " Frames: " + framesToPrint);

        }

        private Map.Entry<Long, Frame> getNextFrame() {
            Map.Entry<Long, Frame> nextEntry;

            nextEntry = frames.firstEntry();

            counter++;

            if (DEBUGGING_JITTER) {
                log.info("Looking for frames between: [" + (playHead - timeBetweenFrames) + " - " + (playHead + timeBetweenFrames) + ")");
                printFrames();
            }

            while (nextEntry != null && nextEntry.getKey() < playHead - timeBetweenFrames) {
                frames.remove(nextEntry.getKey());
                if (DEBUGGING_JITTER) {
                    log.info("Removing frame: " + nextEntry.getKey());
                }
                nextEntry = frames.firstEntry();
            }

            if (nextEntry != null && nextEntry.getKey() < playHead + timeBetweenFrames) {
                if (DEBUGGING_JITTER) {
                    log.info("Sending frame: " + nextEntry.getKey());
                }
                return nextEntry;
            }

            return null;
        }

        private void sendFrame(Frame frame) {
            if (DEBUGGING_JITTER) {
                log.info("Consuming frame");
            }

            for (DataPacketWithNalType packet : frame.getPackets()) {
                rtpMediaExtractor.sendPacket(packet);
            }
        }

        private void waitForNextFrame() {
            playHead += timeBetweenFrames;

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
