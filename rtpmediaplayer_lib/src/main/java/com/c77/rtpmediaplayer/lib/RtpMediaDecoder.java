package com.c77.rtpmediaplayer.lib;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.session.AbstractRtpSession;
import com.biasedbit.efflux.session.SingleParticipantSession;
import com.c77.rtpmediaplayer.lib.rtp.RtpMediaExtractor;
import com.c77.rtpmediaplayer.lib.video.Decoder;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by ashi on 1/3/15.
 */
public class RtpMediaDecoder implements Decoder, SurfaceHolder.Callback {

    private static Log log = LogFactory.getLog(RtpMediaDecoder.class);
    private final SurfaceView surfaceView;

    private PlayerThread playerThread;
    private RtpMediaExtractor rtpMediaExtractor;
    private RTPClientThread rtpSessionThread;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private MediaCodec decoder;

    public RtpMediaDecoder(SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
        surfaceView.getHolder().addCallback(this);
    }

    private void rtpStartClient() {
        // Create a decoder and pass it to the Rtp Extractor
        rtpMediaExtractor = new RtpMediaExtractor(this);

        rtpSessionThread = new RTPClientThread();
        rtpSessionThread.start();
    }

    private void rtpStopClient() {
        rtpSessionThread.interrupt();
    }

    public void release() {
        rtpStopClient();
        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
    }

    private int readSampleData(ByteBuffer buffer, byte[] frameBytes) {
        buffer.clear();
        buffer.put(frameBytes);
        return frameBytes.length;
    }

    @Override
    public void decodeFrame(byte[] frameBytes, long currentTimestap) {
        int inIndex = decoder.dequeueInputBuffer(0);
        if (inIndex >= 0) {
            ByteBuffer buffer = inputBuffers[inIndex];

            int sampleSize = readSampleData(buffer, frameBytes);
            decoder.queueInputBuffer(inIndex, 0, sampleSize, currentTimestap, 0);
        }

        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                log.info("INFO_OUTPUT_BUFFERS_CHANGED");
                outputBuffers = decoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                log.info("New format " + decoder.getOutputFormat());
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                log.info("dequeueOutputBuffer timed out!");
                break;
            default:
                ByteBuffer buffer = outputBuffers[outIndex];
                log.info("We can't use this buffer but render it due to the API limit, " + buffer);

                decoder.releaseOutputBuffer(outIndex, true);
                break;
        }

        // All decoded frames have been rendered, we can stop playing now
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            log.info("OutputBuffer BUFFER_FLAG_END_OF_STREAM");
        }
    }

    @Override
    public void printFrame(byte[] frame) {
        String debugging = "Size = " + frame.length;
        debugging += " [" + new String(Hex.encodeHex(Arrays.copyOf(frame, 16))) + "]";
        log.info(debugging);
    }


    public void start() {
        rtpStartClient();
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
        private AbstractRtpSession session;

        public AbstractRtpSession getSession() {
            return session;
        }

        @Override
        public void run() {
            RtpParticipant participant = RtpParticipant.createReceiver("0.0.0.0", 5006, 5007);
            RtpParticipant remoteParticipant = RtpParticipant.createReceiver("10.34.0.10", 4556, 4557);
            session = new SingleParticipantSession("1", 96, participant, remoteParticipant);

            session.addDataListener(rtpMediaExtractor);

            session.setDiscardOutOfOrder(false);

            // This parameter changes the underlying I/O mechamism used by Netty
            // It is counter-intuitive: 'false' will force the usage of NioDatagramChannelFactory
            // vs. the default (true) which uses OioDatagramChannelFactory
            // It is unclear which produces better results. This value
            // (false, to make NioDatagramChannelFactory used) resolves the memory management usage
            // reported in https://github.com/creativa77/RtpMediaPlayer/issues/4
            // It may make sense that this works better for us since efflux is not used in Android normally
            // NOTE: Despite fixing the memory usage, it is still pending to test which method produces
            // best overall results
            //
            session.setUseNio(false);

            // NOTE: This parameter seems to affect the performance a lot.
            // The default value of 1500 drops many more packets than
            // the experimental value of 15000 (and later increased to 30000)
            session.setReceiveBufferSize(50000);

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
        }
    }
}
