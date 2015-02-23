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

import android.media.MediaFormat;

import com.biasedbit.efflux.packet.DataPacket;
import com.c77.androidstreamingclient.lib.BufferedSample;
import com.c77.androidstreamingclient.lib.RtpPlayerException;
import com.c77.androidstreamingclient.lib.video.Decoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;

import java.nio.ByteBuffer;

/**
 * Extractor that knows how to manage RTP packets according to H.264 spec.
 *
 * @author Julian Cerruti
 */
public class RtpMediaExtractor implements MediaExtractor {
    public static final String CSD_0 = "csd-0";
    public static final String CSD_1 = "csd-1";
    public static final String DURATION_US = "durationUs";
    private static Log log = LogFactory.getLog(RtpMediaExtractor.class);
    private final byte[] byteStreamStartCodePrefix = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01};
    private final Decoder decoder;
    // Extractor settings
    //   Whether to use Byte Stream Format (H.264 spec., annex B)
    //   (prepends the byte stream 0x00000001 to each NAL unit)
    private boolean useByteStreamFormat = true;
    private boolean currentFrameHasError = false;
    private BufferedSample currentFrame;

    /**
     * Creates the extractor initializing the decoder that will receive the video frames to decode.
     *
     * @param decoder
     */
    public RtpMediaExtractor(Decoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Creates a STAP-A frame and sends it, as there is no need to wait for other packets.
     *
     * @param packet
     */
    private void startSTAPAFrame(DataPacket packet) {
        // This frame type includes a series of concatenated NAL units, each preceded
        // by a 16-bit size field

        // We'll use the reader index in this parsing routine
        ChannelBuffer buffer = packet.getData();
        // Discard the first byte (RTP getPacket type / nalType came from there)
        try {
            buffer.readByte();
        } catch (IndexOutOfBoundsException e) {
            log.error("jboss AbstractChannelBuffer throws exception when trying to read byte", e);
        }

        while (buffer.readable()) {
            // NAL Unit Size
            short nalUnitSize = buffer.readShort();

            // NAL Unit Data (of the size read above)
            byte[] nalUnitData = new byte[nalUnitSize];
            buffer.readBytes(nalUnitData);

            // Create and send the buffer upstream for processing
            try {
                startFrame(packet.getTimestamp());
            } catch (Exception e) {
                log.error("Error while trying to start frame", e);
            }

            if (currentFrame != null) {
                if (useByteStreamFormat) {
                    currentFrame.getBuffer().put(byteStreamStartCodePrefix);
                }
                currentFrame.getBuffer().put(nalUnitData);
                sendFrame();
            }
        }
    }

    /**
     * Creates a frame coming from a full packet. That means there is no need to wait for other
     * packets to send this one because this is complete itself.
     *
     * @param packet
     */
    private void startAndSendFrame(DataPacket packet) {
        try {
            startFrame(packet.getTimestamp());
        } catch (Exception e) {
            log.error("Error while trying to start frame", e);
        }
        if (currentFrame != null) {

            if (useByteStreamFormat) {
                currentFrame.getBuffer().put(byteStreamStartCodePrefix);
            }
            currentFrame.getBuffer().put(packet.getData().toByteBuffer());
            sendFrame();
        }
    }

    /**
     * Creates a frame formed by several packets. One packet is the start of the frame, that have
     * some header data, then we get the content and the end packet of the frame indicating the
     * frame can be sent.
     *
     * @param packet
     */
    private void startAndSendFragmentedFrame(H264DataPacket packet) {
        // Do we have a clean start of a frame?
        if (packet.isStart()) {
            try {
                startFrame(packet.getTimestamp());
            } catch (Exception e) {
                log.error("Error while trying to start frame", e);
            }

            if (currentFrame != null) {
                // Add stream header
                if (useByteStreamFormat) {
                    currentFrame.getBuffer().put(byteStreamStartCodePrefix);
                }

                // Re-create the H.264 NAL header from the FU-A header
                // Excerpt from the spec:
                    /* "The NAL unit type octet of the fragmented
                    NAL unit is not included as such in the fragmentation unit payload,
                    but rather the information of the NAL unit type octet of the
                    fragmented NAL unit is conveyed in F and NRI fields of the FU
                    indicator octet of the fragmentation unit and in the type field of
                    the FU header"  */
                byte reconstructedNalTypeOctet = (byte) (packet.getFuNalType() | packet.getNalFBits() | packet.getNalNriBits());
                currentFrame.getBuffer().put(reconstructedNalTypeOctet);
            }
        }

        // if we don't have a buffer here, it means that we skipped the start getPacket for this
        // NAL unit, so we can't do anything other than discard everything else
        if (currentFrame != null) {
            currentFrame.getBuffer().put(packet.getData().toByteBuffer(2, packet.getDataSize() - 2));

            if (packet.isEnd()) {
                try {
                    sendFrame();
                } catch (Throwable t) {
                    log.error("Error sending frame.", t);
                }
            }
        }
    }

    /**
     * Initializes frame for a given timestamp.
     *
     * @param rtpTimestamp
     * @throws Exception
     */
    private void startFrame(long rtpTimestamp) throws Exception {
        // Reset error bit
        currentFrameHasError = false;

        // Deal with potentially non-returned buffer due to error
        if (currentFrame != null) {
            currentFrame.getBuffer().clear();
            // Otherwise, get a fresh buffer from the codec
        } else {
            try {
                // Get buffer from decoder
                currentFrame = decoder.getSampleBuffer();
                currentFrame.getBuffer().clear();
            } catch (RtpPlayerException e) {
                // TODO: Proper error handling
                currentFrameHasError = true;
                e.printStackTrace();
            }
        }

        if (!currentFrameHasError) {
            // Set the sample timestamp
            currentFrame.setRtpTimestamp(rtpTimestamp);
        }
    }

    /**
     * Sends frame to decoder.
     */
    private void sendFrame() {
        currentFrame.setSampleSize(currentFrame.getBuffer().position());
        currentFrame.getBuffer().flip();
        try {
            decoder.decodeFrame(currentFrame);
        } catch (Exception e) {
            log.error("Error sending frame", e);
        }

        // Always make currentFrameEntry null to indicate we have returned the buffer to the codec
        currentFrame = null;
    }

    /**
     * Retrieves an Android MediaFormat for H.264, 640x480 video codec.
     * SPS and PPS are hardcoded to the ones used by libstreaming.
     * TODO: Think how to get CSD-0/CSD-1 codec-specific data chunks
     *
     * @return
     */
    @Override
    public MediaFormat getMediaFormat() {
        String mimeType = "video/avc";
        int width = 640;
        int height = 480;

        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
        /*
        // the one got from internet
        byte[] header_sps = { 0, 0, 0, 1, // header
                0x67, 0x42, 0x00, 0x1f, (byte)0xe9, 0x01, 0x68, 0x7b, (byte) 0x20 }; // sps
        byte[] header_pps = { 0, 0, 0, 1, // header
                0x68, (byte)0xce, 0x06, (byte)0xf2 }; // pps

        // the one got from libstreaming at HQ
        byte[] header_sps = { 0, 0, 0, 1, // header
                0x67, 0x42, (byte)0x80, 0x14, (byte)0xe4, 0x40, (byte)0xa0, (byte)0xfd, 0x00, (byte)0xda, 0x14, 0x26, (byte)0xa0}; // sps
        byte[] header_pps = { 0, 0, 0, 1, // header
                0x68, (byte)0xce, 0x38, (byte)0x80 }; // pps


        // the one got from libstreaming at home
        byte[] header_sps = { 0, 0, 0, 1, // header
                0x67, 0x42, (byte) 0xc0, 0x1e, (byte) 0xe9, 0x01, 0x40, 0x7b, 0x40, 0x3c, 0x22, 0x11, (byte) 0xa8}; // sps
        byte[] header_pps = { 0, 0, 0, 1, // header
                0x68, (byte) 0xce, 0x06, (byte) 0xe2}; // pps
         */

        // from avconv, when streaming sample.h264.mp4 from disk
        byte[] header_sps = {0, 0, 0, 1, // header
                0x67, 0x64, (byte) 0x00, 0x1e, (byte) 0xac, (byte) 0xd9, 0x40, (byte) 0xa0, 0x3d,
                (byte) 0xa1, 0x00, 0x00, (byte) 0x03, 0x00, 0x01, 0x00, 0x00, 0x03, 0x00, 0x3C, 0x0F, 0x16, 0x2D, (byte) 0x96}; // sps
        byte[] header_pps = {0, 0, 0, 1, // header
                0x68, (byte) 0xeb, (byte) 0xec, (byte) 0xb2, 0x2C}; // pps


        format.setByteBuffer(CSD_0, ByteBuffer.wrap(header_sps));
        format.setByteBuffer(CSD_1, ByteBuffer.wrap(header_pps));

        //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        format.setInteger(DURATION_US, 12600000);

        return format;
    }

    /**
     * Creates frames according to its NAL type and sends them.
     *
     * @param packet
     */
    public void sendPacket(H264DataPacket packet) {
        switch (packet.nalType()) {
            case FULL:
                startAndSendFrame(packet.getPacket());
                break;
            case NOT_FULL:
                startAndSendFragmentedFrame(packet);
                break;
            case STAPA:
                startSTAPAFrame(packet.getPacket());
                break;
        }
    }
}
