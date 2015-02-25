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
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.androidstreamingclient.lib.exceptions.RtpPlayerException;
import com.c77.androidstreamingclient.lib.video.BufferedSample;
import com.c77.androidstreamingclient.lib.video.Decoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;

import java.nio.ByteBuffer;

/**
 * RTP Extractor that takes packets, creates frames and sends them to the decoder.
 * It has all the knowledge to parse H.264 packets, create frames from them and send them according
 * to the specs.
 *
 * @author Julian Cerruti
 */
public class RtpMediaExtractor implements RtpSessionDataListener, MediaExtractor {

    // MediaFormat fields
    public static final String CSD_0 = "csd-0";
    public static final String CSD_1 = "csd-1";
    public static final String DURATION_US = "durationUs";
    private static final Log log = LogFactory.getLog(RtpMediaExtractor.class);
    private final byte[] byteStreamStartCodePrefix = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01};
    private final Decoder decoder;
    // Extractor settings
    //   Whether to use Byte Stream Format (H.264 spec., annex B)
    //   (prepends the byte stream 0x00000001 to each NAL unit)
    private boolean useByteStreamFormat = true;
    private int lastSequenceNumber = 0;
    private boolean lastSequenceNumberIsValid = false;
    private boolean sequenceError = false;
    private boolean currentFrameHasError = false;
    private BufferedSample currentFrame;

    /**
     * Creates an RTP extractor that uses a given decoder.
     *
     * @param decoder
     */
    public RtpMediaExtractor(Decoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Processes every arriving packet parsing its content, creates frames accordingly and sends them.
     *
     * @param session
     * @param participant
     * @param packet
     */
    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        String debugging = "RTP data. ";
        debugging += packet.getDataSize() + "b ";
        debugging += "#" + packet.getSequenceNumber();
        debugging += " " + packet.getTimestamp();

        if (lastSequenceNumberIsValid && (lastSequenceNumber + 1) != packet.getSequenceNumber()) {
            sequenceError = true;
            debugging += " SKIPPED (" + (packet.getSequenceNumber() - lastSequenceNumber - 1) + ")";
        } else {
            sequenceError = false;
        }

        if (RtpMediaDecoder.DEBUGGING) {
            log.error(debugging);
        }

        H264Packet h264Packet = new H264Packet(packet);

        switch (h264Packet.h264NalType) {
            case FULL:
                if (RtpMediaDecoder.DEBUGGING) {
                    log.info("NAL: full packet");
                }
                // Send the buffer upstream for processing

                startFrame(packet.getTimestamp());
                if (currentFrame != null) {

                    if (useByteStreamFormat) {
                        currentFrame.getBuffer().put(byteStreamStartCodePrefix);
                    }
                    currentFrame.getBuffer().put(packet.getData().toByteBuffer());
                    sendFrame();
                }
                break;
            case FUA:
                if (RtpMediaDecoder.DEBUGGING) {
                    log.info("NAL: FU-A fragment");
                }

                // Do we have a clean start of a frame?
                if (h264Packet.isStart()) {
                    if (RtpMediaDecoder.DEBUGGING) {
                        log.info("FU-A start found. Starting new frame");
                    }

                    startFrame(packet.getTimestamp());

                    if (currentFrame != null) {
                        // Add stream header
                        if (useByteStreamFormat) {
                            currentFrame.getBuffer().put(byteStreamStartCodePrefix);
                        }

                        byte reconstructedNalTypeOctet = h264Packet.getNalTypeOctet();
                        currentFrame.getBuffer().put(reconstructedNalTypeOctet);
                    }
                }

                // if we don't have a buffer here, it means that we skipped the start packet for this
                // NAL unit, so we can't do anything other than discard everything else
                if (currentFrame != null) {

                    // Did we miss packets in the middle of a frame transition?
                    // In that case, I don't think there's much we can do other than flush our buffer
                    // and discard everything until the next buffer
                    if (packet.getTimestamp() != currentFrame.getRtpTimestamp()) {
                        if (RtpMediaDecoder.DEBUGGING) {
                            log.warn("Non-consecutive timestamp found");
                        }

                        currentFrameHasError = true;
                    }
                    if (sequenceError) {
                        currentFrameHasError = true;
                    }

                    // If we survived possible errors, collect data to the current frame buffer
                    if (!currentFrameHasError) {
                        currentFrame.getBuffer().put(packet.getData().toByteBuffer(2, packet.getDataSize() - 2));
                    } else if (RtpMediaDecoder.DEBUGGING) {
                        log.info("Dropping frame");
                    }

                    if (h264Packet.isEnd()) {
                        if (RtpMediaDecoder.DEBUGGING) {
                            log.info("FU-A end found. Sending frame!");
                        }
                        try {
                            sendFrame();
                        } catch (Throwable t) {
                            log.error("Error sending frame.", t);
                        }
                    }
                }
                break;
            case STAPA:
                if (RtpMediaDecoder.DEBUGGING) {
                    log.info("NAL: STAP-A");
                }
                // This frame type includes a series of concatenated NAL units, each preceded
                // by a 16-bit size field

                // We'll use the reader index in this parsing routine
                ChannelBuffer buffer = packet.getData();
                // Discard the first byte (RTP packet type / nalType came from there)
                buffer.readByte();

                while (buffer.readable()) {
                    // NAL Unit Size
                    short nalUnitSize = buffer.readShort();

                    // NAL Unit Data (of the size read above)
                    byte[] nalUnitData = new byte[nalUnitSize];
                    buffer.readBytes(nalUnitData);

                    // Create and send the buffer upstream for processing
                    startFrame(packet.getTimestamp());

                    if (currentFrame != null) {
                        if (useByteStreamFormat) {
                            currentFrame.getBuffer().put(byteStreamStartCodePrefix);
                        }
                        currentFrame.getBuffer().put(nalUnitData);
                        sendFrame();
                    }
                }
                break;
            case UNKNOWN:
                log.warn("NAL: Unimplemented unit type: " + h264Packet.getNalType());
                // libstreaming doesn't use anything else, so we won't implement other NAL unit types, at
                // least for now
                break;
        }

        lastSequenceNumber = packet.getSequenceNumber();
        lastSequenceNumberIsValid = true;
    }

    /**
     * Initializes frame for a given timestamp.
     *
     * @param rtpTimestamp
     * @throws Exception
     */
    private void startFrame(long rtpTimestamp) {
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
            log.error("Exception sending frame to decoder", e);
        }

        // Always make currentFrame null to indicate we have returned the buffer to the codec
        currentFrame = null;
    }

    /**
     * Retrieves an Android MediaFormat for H.264, 640x480 video codec.
     * SPS and PPS are hardcoded to the ones used by libstreaming.
     * TODO: Think how to get CSD-0/CSD-1 codec-specific data chunks
     *
     * @return
     */
    public MediaFormat getMediaFormat() {
        String mimeType = "video/avc";
        int width = 640;
        int height = 480;

        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);

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

    private enum NalType {
        FULL,
        FUA,
        STAPA,
        UNKNOWN
    }

    /**
     * H.264 Packet parsed following H.264 spec.
     */
    private class H264Packet {
        private final byte nalFBits;
        private final byte nalNriBits;
        private final byte nalType;
        private boolean fuStart = false;
        private boolean fuEnd = false;
        private byte fuNalType;
        private NalType h264NalType = NalType.UNKNOWN;

        /**
         * Creates a H.264 packet parsing its content
         *
         * @param packet
         */
        public H264Packet(DataPacket packet) {
            // Parsing the RTP Packet - http://www.ietf.org/rfc/rfc3984.txt section 5.3
            byte nalUnitOctet = packet.getData().getByte(0);
            nalFBits = (byte) (nalUnitOctet & 0x80);
            nalNriBits = (byte) (nalUnitOctet & 0x60);
            nalType = (byte) (nalUnitOctet & 0x1F);

            // If it's a single NAL packet then the entire payload is here
            if (nalType > 0 && nalType < 24) {
                h264NalType = NalType.FULL;
            } else if (nalType == 28) {
                h264NalType = NalType.FUA;

            } else if (nalType == 24) {
                h264NalType = NalType.STAPA;
            }

            byte fuHeader = packet.getData().getByte(1);
            fuStart = ((fuHeader & 0x80) != 0);
            fuEnd = ((fuHeader & 0x40) != 0);
            fuNalType = (byte) (fuHeader & 0x1F);
        }

        /**
         * Re-creates the H.264 NAL header for the FU-A header
         *
         * @return
         */
        public byte getNalTypeOctet() {
            // Excerpt from the spec:
            /* "The NAL unit type octet of the fragmented
               NAL unit is not included as such in the fragmentation unit payload,
               but rather the information of the NAL unit type octet of the
               fragmented NAL unit is conveyed in F and NRI fields of the FU
               indicator octet of the fragmentation unit and in the type field of
               the FU header"  */

            return (byte) (fuNalType | nalFBits | nalNriBits);
        }

        /**
         * Indicates whether this packet is the start of a frame.
         *
         * @return
         */
        public boolean isStart() {
            return fuStart;
        }

        /**
         * Indicates whether this packet is the end of a frame.
         *
         * @return
         */
        public boolean isEnd() {
            return fuEnd;
        }

        /**
         * Returns NAL type byte.
         *
         * @return
         */
        public byte getNalType() {
            return nalType;
        }
    }
}
