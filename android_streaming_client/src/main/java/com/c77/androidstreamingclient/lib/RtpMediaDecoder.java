package com.c77.androidstreamingclient.lib;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.biasedbit.efflux.SsrcListener;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.session.AbstractRtpSession;
import com.biasedbit.efflux.session.SingleParticipantSession;
import com.c77.androidstreamingclient.lib.rtp.BufferDelayTracer;
import com.c77.androidstreamingclient.lib.rtp.MediaExtractor;
import com.c77.androidstreamingclient.lib.rtp.NoDelayRtpMediaBuffer;
import com.c77.androidstreamingclient.lib.rtp.OriginalRtpMediaExtractor;
import com.c77.androidstreamingclient.lib.rtp.RtpMediaBuffer;
import com.c77.androidstreamingclient.lib.rtp.RtpMediaBufferWithJitterAvoidance;
import com.c77.androidstreamingclient.lib.rtp.RtpMediaExtractor;
import com.c77.androidstreamingclient.lib.rtp.RtpMediaJitterBuffer;
import com.c77.androidstreamingclient.lib.video.Decoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * Created by ashi on 1/3/15.
 */
public class RtpMediaDecoder implements Decoder, SurfaceHolder.Callback {
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final String CONFIG_USE_NIO = "USE_NIO";
    public static final String CONFIG_BUFFER_TYPE = "BUFFER_TYPE";
    public static final String CONFIG_RECEIVE_BUFFER_SIZE = "RECEIVE_BUFFER_SIZE_BYTES";

    public static boolean DEBUGGING;
    public String bufferType = "fixed-frame-number";
    public boolean useNio = true;
    public int receiveBufferSize = 50000;

    private final SurfaceView surfaceView;

    private PlayerThread playerThread;
    private MediaExtractor rtpMediaExtractor;
    private RTPClientThread rtpSessionThread;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private MediaCodec decoder;
    private Log log = LogFactory.getLog(RtpMediaDecoder.class);

    private final Properties configuration;

    // If this stream is set, use it to trace packet arrival times
    private OutputStream traceOuputStream = null;

    public void setTraceOuputStream(OutputStream out) {
        traceOuputStream = out;
    }

    /**
     * @param surfaceView surface where to play video streaming
     * @param properties  used to configure the debugging variable
     */
    public RtpMediaDecoder(SurfaceView surfaceView, Properties properties) {
        configuration = (properties != null) ? properties : new Properties();

        // Read configuration
        DEBUGGING = Boolean.parseBoolean(properties.getProperty(DEBUGGING_PROPERTY, "false"));
        bufferType = properties.getProperty(CONFIG_BUFFER_TYPE, bufferType);
        useNio = Boolean.parseBoolean(configuration.getProperty(CONFIG_USE_NIO, Boolean.toString(useNio)));
        receiveBufferSize = Integer.parseInt(configuration.getProperty(CONFIG_RECEIVE_BUFFER_SIZE, Integer.toString(receiveBufferSize)));

        log.info("RtpMediaDecoder started with params (" + DEBUGGING + "," + bufferType + "," + useNio + "," + receiveBufferSize + ")");

        this.surfaceView = surfaceView;
        surfaceView.getHolder().addCallback(this);
    }

    public void start() {
        rtpStartClient();
    }

    /**
     * Restarts the underlying RTP session
     */
    public void restart() {
        rtpStopClient();
        rtpStartClient();
    }

    public void release() {
        rtpStopClient();
        if (decoder != null) {
            try {
                decoder.stop();
            } catch (Exception e) {
                log.error("Encountered error while trying to stop decoder", e);
            }
            decoder.release();
            decoder = null;
        }
    }

    private void rtpStartClient() {
        rtpSessionThread = new RTPClientThread();
        rtpSessionThread.start();
    }

    private void rtpStopClient() {
        rtpSessionThread.interrupt();
    }

    @Override
    public BufferedSample getSampleBuffer() throws RtpPlayerException {
        int inIndex = decoder.dequeueInputBuffer(-1);
        if (inIndex < 0) {
            throw new RtpPlayerException("Didn't get a buffer from the MediaCodec");
        }
        return new BufferedSample(inputBuffers[inIndex], inIndex);
    }

    @Override
    public void decodeFrame(BufferedSample decodeBuffer) throws Exception {
        if (DEBUGGING) {
            // Dump buffer to logcat
            log.info(decodeBuffer.toString());
        }

        // Queue the sample to be decoded
        decoder.queueInputBuffer(decodeBuffer.getIndex(), 0,
                decodeBuffer.getSampleSize(), decodeBuffer.getPresentationTimeUs(), 0);

        // Read the decoded output
        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                if (DEBUGGING) {
                    log.info("INFO_OUTPUT_BUFFERS_CHANGED");
                }
                outputBuffers = decoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                if (DEBUGGING) {
                    log.info("New format " + decoder.getOutputFormat());
                }
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                if (DEBUGGING) {
                    log.info("dequeueOutputBuffer timed out!");
                }
                break;
            default:
                if (DEBUGGING) {
                    ByteBuffer buffer = outputBuffers[outIndex];
                    log.info("We can't use this buffer but render it due to the API limit, " + buffer);
                }

                decoder.releaseOutputBuffer(outIndex, true);
                break;
        }

        // All decoded frames have been rendered, we can stop playing now
        if (((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) && DEBUGGING) {
            log.info("OutputBuffer BUFFER_FLAG_END_OF_STREAM");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        android.view.ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
        layoutParams.width = 640; // required width
        layoutParams.height = 480; // required height
        surfaceView.setLayoutParams(layoutParams);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (playerThread == null) {
            playerThread = new PlayerThread(holder.getSurface());
            playerThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private class PlayerThread extends Thread {
        private Surface surface;

        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            // Wait a little bit to make sure the RtpClientThread had the opporunity to start
            // and create the rtpMediaExtractor
            try {
                sleep(500);
            } catch (InterruptedException e) {
            }
            MediaFormat mediaFormat = rtpMediaExtractor.getMediaFormat();
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(mediaFormat, surface, null, 0);
            }

            if (decoder == null) {
                log.info("Can't find video info!");
                return;
            }

            decoder.start();
            inputBuffers = decoder.getInputBuffers();
            outputBuffers = decoder.getOutputBuffers();
        }
    }

    private class RTPClientThread extends Thread {
        private SingleParticipantSession session;

        @Override
        public void run() {
            RtpParticipant participant = RtpParticipant.createReceiver("0.0.0.0", 5006, 5007);
            RtpParticipant remoteParticipant = RtpParticipant.createReceiver("10.34.0.10", 4556, 4557);
            session = new SingleParticipantSession("1", 96, participant, remoteParticipant);
            // listen to ssrc changes, in order to be able to auto-magically "reconnect",
            // i.e. if video publisher is closed and re-opened.
            session.setSsrcListener(new SsrcListener() {
                @Override
                public void onSsrcChanged() {
                    log.warn("Ssrc changed, restarting decoder");
                    RtpMediaDecoder.this.restart();
                }
            });

            // Optionally trace to file
            if (traceOuputStream != null) {
                session.addDataListener(new BufferDelayTracer(traceOuputStream));
            }

            // Choose buffer implementation according to configuration
            RtpMediaBuffer buffer;
            if ("fixed-time".equalsIgnoreCase(bufferType)) {
                rtpMediaExtractor = new OriginalRtpMediaExtractor(RtpMediaDecoder.this);
                buffer = new RtpMediaJitterBuffer((OriginalRtpMediaExtractor) rtpMediaExtractor, configuration);
            } else if ("zero-delay".equalsIgnoreCase(bufferType)) {
                rtpMediaExtractor = new OriginalRtpMediaExtractor(RtpMediaDecoder.this);
                buffer = new NoDelayRtpMediaBuffer((OriginalRtpMediaExtractor) rtpMediaExtractor, configuration);
            } else {
                rtpMediaExtractor = new RtpMediaExtractor(RtpMediaDecoder.this);
                buffer = new RtpMediaBufferWithJitterAvoidance((RtpMediaExtractor) rtpMediaExtractor, configuration);
            }
            session.addDataListener(buffer);

            session.setDiscardOutOfOrder(false);

            // This parameter changes the underlying I/O mechanism used by Netty
            // It is counter-intuitive: 'false' will force the usage of NioDatagramChannelFactory
            // vs. the default (true) which uses OioDatagramChannelFactory
            // It is unclear which produces better results. This value
            // (false, to make NioDatagramChannelFactory used) resolves the memory management usage
            // reported in https://github.com/creativa77/AndroidStreamingClient/issues/4
            // It may make sense that this works better for us since efflux is not used in Android normally
            // NOTE: Despite fixing the memory usage, it is still pending to test which method produces
            // best overall results
            //
            session.setUseNio(useNio);

            // NOTE: This parameter seems to affect the performance a lot.
            // The default value of 1500 drops many more packets than
            // the experimental value of 15000 (and later increased to 30000)
            session.setReceiveBufferSize(receiveBufferSize);

            session.init();

            log.info("RTP Session created");

            try {
                while (true) {
                    sleep(1000);
                }
            } catch (InterruptedException e) {
                log.error("Exiting thread through interruption", e);
            }
            session.terminate();
            buffer.stop();
        }
    }
}
