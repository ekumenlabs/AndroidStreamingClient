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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.biasedbit.efflux.SsrcListener;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.session.SingleParticipantSession;
import com.c77.androidstreamingclient.lib.exceptions.RtpPlayerException;
import com.c77.androidstreamingclient.lib.rtp.buffer.MinDelayRtpMediaBuffer;
import com.c77.androidstreamingclient.lib.rtp.buffer.RtpMediaBuffer;
import com.c77.androidstreamingclient.lib.rtp.buffer.TimeWindowRtpMediaBuffer;
import com.c77.androidstreamingclient.lib.video.BufferedSample;
import com.c77.androidstreamingclient.lib.video.Decoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * Implementation of the decoder that uses RTP as transport protocol to decode H264 encoded frames.
 * This object wraps up an Android API decoder and uses it to decode video frames.
 *
 * @author Ayelen Chavez
 */
public class RtpMediaDecoder implements Decoder, SurfaceHolder.Callback {

    // configuration constants
    public static final String DEBUGGING_PROPERTY = "DEBUGGING";
    public static final String CONFIG_USE_NIO = "USE_NIO";
    public static final String CONFIG_BUFFER_TYPE = "BUFFER_TYPE";
    public static final String CONFIG_RECEIVE_BUFFER_SIZE = "RECEIVE_BUFFER_SIZE_BYTES";
    public static final int DATA_STREAMING_PORT = 5006;
    public static final int SURFACE_WIDTH = 640;
    public static final int SURFACE_HEIGHT = 480;
    public static final String TRANSPORT_PROTOCOL = "RTP";
    public static final String VIDEO_CODEC = "H.264";

    // constant used to activate and deactivate logs
    public static boolean DEBUGGING;
    // surface view where to play video
    private final SurfaceView surfaceView;
    private final Properties configuration;
    public String bufferType = "time-window";
    public boolean useNio = true;
    public int receiveBufferSize = 50000;
    private PlayerThread playerThread;
    private RtpMediaExtractor rtpMediaExtractor;
    private RTPClientThread rtpSessionThread;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private MediaCodec decoder;
    private Log log = LogFactory.getLog(RtpMediaDecoder.class);
    // If this stream is set, use it to trace packet arrival data
    private OutputStream traceOutputStream = null;

    /**
     * Creates an RTP decoder indicating where to play the video
     *
     * @param surfaceView view where video will be displayed
     */
    public RtpMediaDecoder(SurfaceView surfaceView) {
        this(surfaceView, null);
    }

    /**
     * Creates an RTP decoder
     *
     * @param surfaceView view where to play video streaming
     * @param properties  used to configure the debugging variable
     *
     * The configuration properties includes:
     *  DEBUGGING: boolean indicating if information should be sent to logcat.
     *  USE_NIO: boolean indicating how to change the underlying I/O mechanism used by Netty.
     *      'false' will force the usage of NioDatagramChannelFactory vs. the default (true) which
     *      uses OioDatagramChannelFactory
     *  RECEIVE_BUFFER_SIZE_BYTES: number of bytes to configure underlying RTP session.
     *  BUFFER_TYPE: Has to be one of: 'time-window' or 'min-delay' in order to choose between existing
     *      buffering approaches.
     *  NODELAY_TIMEOUT: Maximum delay in milliseconds for the min-delay buffer.
     *  FRAMES_WINDOW_TIME=1000: Window size in milliseconds for the time-window buffer
     *
     */
    public RtpMediaDecoder(SurfaceView surfaceView, Properties properties) {
        configuration = (properties != null) ? properties : new Properties();

        // Read configuration
        DEBUGGING = Boolean.parseBoolean(configuration.getProperty(DEBUGGING_PROPERTY, "false"));
        bufferType = configuration.getProperty(CONFIG_BUFFER_TYPE, bufferType);
        useNio = Boolean.parseBoolean(configuration.getProperty(CONFIG_USE_NIO, Boolean.toString(useNio)));
        receiveBufferSize = Integer.parseInt(configuration.getProperty(CONFIG_RECEIVE_BUFFER_SIZE, Integer.toString(receiveBufferSize)));

        log.info("RtpMediaDecoder started with params (" + DEBUGGING + "," + bufferType + "," + useNio + "," + receiveBufferSize + ")");

        this.surfaceView = surfaceView;
        surfaceView.getHolder().addCallback(this);
    }

    /**
     * Defines the output stream where to trace packet's data while they arrive to the decoder
     *
     * @param outputStream stream where to dump data
     */
    public void setTraceOutputStream(OutputStream outputStream) {
        traceOutputStream = outputStream;
    }

    /**
     * Starts decoder, including the underlying RTP session
     */
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

    /**
     * Stops the underlying RTP session and properly releases the Android API decoder
     */
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

    /**
     * Starts the RTP session
     */
    private void rtpStartClient() {
        rtpSessionThread = new RTPClientThread();
        rtpSessionThread.start();
    }

    /**
     * Stops the RTP session
     */
    private void rtpStopClient() {
        rtpSessionThread.interrupt();
    }

    /**
     * Retrieves a buffer from the decoder to be filled with data getting it from the Android
     * decoder input buffers
     *
     * @return
     * @throws com.c77.androidstreamingclient.lib.exceptions.RtpPlayerException if no such buffer is currently available
     */
    @Override
    public BufferedSample getSampleBuffer() throws RtpPlayerException {
        int inIndex = decoder.dequeueInputBuffer(-1);
        if (inIndex < 0) {
            throw new RtpPlayerException("Didn't get a buffer from the MediaCodec");
        }
        return new BufferedSample(inputBuffers[inIndex], inIndex);
    }

    /**
     * Decodes a frame
     *
     * @param decodeBuffer
     * @throws Exception
     */
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
                    log.info("The output buffers have changed.");
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
                    log.info("Call to dequeueOutputBuffer timed out.");
                }
                break;
            default:
                if (DEBUGGING) {
                    ByteBuffer buffer = outputBuffers[outIndex];
                    log.info("We can't use this buffer but render it due to the API limit, " + buffer);
                }

                // return buffer to the codec
                decoder.releaseOutputBuffer(outIndex, true);
                break;
        }

        // All decoded frames have been rendered, we can stop playing now
        if (((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) && DEBUGGING) {
            log.info("All decoded frames have been rendered");
        }
    }

    /**
     * Resizes surface view to 640x480
     *
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        android.view.ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
        layoutParams.width = SURFACE_WIDTH; // required width
        layoutParams.height = SURFACE_HEIGHT; // required height
        surfaceView.setLayoutParams(layoutParams);
    }

    /**
     * Starts playing video when surface view is ready
     *
     * @param holder
     * @param format
     * @param width
     * @param height
     */
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

    public String getResolution() {
        return SURFACE_WIDTH + "x" + SURFACE_HEIGHT;
    }

    public String getTransportProtocol() {
        return TRANSPORT_PROTOCOL;
    }

    public String getVideoCodec() {
        return VIDEO_CODEC;
    }

    public int getDataStreamingPort() {
        return DATA_STREAMING_PORT;
    }

    public String getBufferType() {
        return bufferType;
    }

    /**
     * Creates the Android API decoder, configures it and starts it.
     */
    private class PlayerThread extends Thread {
        private Surface surface;

        /**
         * Thread constructor.
         *
         * @param surface where video will be played
         */
        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            // Wait a little bit to make sure the RtpClientThread had the opportunity to start
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

    /**
     * Thread that creates the underlying RTP session. It also listens to SSRC changes to restart the
     * decoder accordingly.
     * Buffer implementation (object that will receive packets while they arrive and handle them
     * according to an heuristic) is chosen according to a configuration parameter.
     */
    private class RTPClientThread extends Thread {
        private SingleParticipantSession session;

        @Override
        public void run() {
            RtpParticipant participant = RtpParticipant.createReceiver("0.0.0.0", DATA_STREAMING_PORT, 5007);
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
            if (traceOutputStream != null) {
                session.addDataListener(new DataPacketTracer(traceOutputStream));
            }

            // Choose buffer implementation according to configuration
            RtpMediaBuffer buffer;
            if ("time-window".equalsIgnoreCase(bufferType)) {
                rtpMediaExtractor = new RtpMediaExtractor(RtpMediaDecoder.this);
                buffer = new TimeWindowRtpMediaBuffer(rtpMediaExtractor, configuration);
            } else if ("min-delay".equalsIgnoreCase(bufferType)) {
                rtpMediaExtractor = new RtpMediaExtractor(RtpMediaDecoder.this);
                buffer = new MinDelayRtpMediaBuffer(rtpMediaExtractor, configuration);
            } else {
                throw new RuntimeException("Didn't recognize buffer type configuration: " + CONFIG_BUFFER_TYPE + " = " + bufferType);
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
