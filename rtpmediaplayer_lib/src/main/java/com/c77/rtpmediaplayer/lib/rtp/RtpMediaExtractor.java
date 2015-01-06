package com.c77.rtpmediaplayer.lib.rtp;

import android.media.MediaFormat;
import android.util.Log;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.rtpmediaplayer.lib.video.Decoder;

import org.jboss.netty.buffer.ChannelBuffer;

import java.nio.ByteBuffer;

/**
 * Created by julian on 12/12/14.
 */
public class RtpMediaExtractor implements RtpSessionDataListener {
    private NalType frameNalType;
    private byte[] sps;
    private byte[] pps;

    public enum NalType {
        STAPA, FULL, FUA
    }

    private static final String TAG = RtpMediaExtractor.class.getName();

    // Extractor settings
    //   Whether to use Byte Stream Format (H.264 spec., annex B)
    //   (prepends the byte stream 0x00000001 to each NAL unit)
    private boolean useByteStreamFormat = true;

    private final byte[] byteStreamStartCodePrefix = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01};

    private int lastSequenceNumber = 0;
    private boolean lastSequenceNumberIsValid = false;
    private boolean sequenceError = false;
    private final Decoder decoder;

    // 27400 comes from the max-input-size parameter dumped in a debug run of the media_codec app
    private ByteBuffer currentFrameBuffer = ByteBuffer.allocate(27400);
    private long currentFrameTimestamp = 0;
    private int currentFrameBufferSize = 0;
    private boolean currentFrameHasError = false;
    private byte[] currentFrame;

    public RtpMediaExtractor(Decoder decoder) {
        this.decoder = decoder;
    }

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
        Log.e(TAG, debugging);

        // Parsing the RTP Packet - http://www.ietf.org/rfc/rfc3984.txt section 5.3
        byte nalUnitOctet = packet.getData().getByte(0);
        byte nalFBits = (byte) (nalUnitOctet & 0x80);
        byte nalNriBits = (byte) (nalUnitOctet & 0x60);
        byte nalType = (byte) (nalUnitOctet & 0x1F);

        // If it's a single NAL packet then the entire payload is here
        if (nalType > 0 && nalType < 24) {
            Log.i(TAG, "NAL: full packet");

            frameNalType = NalType.FULL;

            // Send the buffer upstream for processing
            currentFrameBuffer.clear();
            if (useByteStreamFormat) {
                currentFrameBuffer.put(byteStreamStartCodePrefix);
            }
            currentFrameBuffer.put(packet.getDataAsArray());
            currentFrameBufferSize = currentFrameBuffer.position();
            sendCurrentFrame();

            // It's a FU-A unit, we should aggregate packets until done
        } else if (nalType == 28) {
            Log.i(TAG, "NAL: FU-A fragment");

            frameNalType = NalType.FUA;

            byte fuHeader = packet.getData().getByte(1);

            boolean fuStart = ((fuHeader & 0x80) != 0);
            boolean fuEnd = ((fuHeader & 0x40) != 0);
            byte fuNalType = (byte) (fuHeader & 0x1F);

            // Do we have a clean start of a frame?
            if (fuStart) {
                Log.i(TAG, "FU-A start found. Starting new frame");
                initFrame(packet.getTimestamp());

                if (useByteStreamFormat) {
                    currentFrameBuffer.put(byteStreamStartCodePrefix);
                    currentFrameBufferSize += 4;
                }

                // Re-create the H.264 NAL header from the FU-A header
                // Excerpt from the spec:
                /* "The NAL unit type octet of the fragmented
                   NAL unit is not included as such in the fragmentation unit payload,
                   but rather the information of the NAL unit type octet of the
                   fragmented NAL unit is conveyed in F and NRI fields of the FU
                   indicator octet of the fragmentation unit and in the type field of
                   the FU header"  */
                byte reconstructedNalTypeOctet = (byte) (fuNalType | nalFBits | nalNriBits);
                currentFrameBuffer.put(reconstructedNalTypeOctet);
                currentFrameBufferSize++;
            }

            // Did we miss packets in the middle of a frame transition?
            // In that case, I don't think there's much we can do other than flush our buffer
            // and discard everything until the next buffer
            if (packet.getTimestamp() != currentFrameTimestamp) {
                Log.w(TAG, "Non-consecutive timestamp found");
                currentFrameHasError = true;
            }
            if (sequenceError) {
                Log.w(TAG, "Sequence error detected, dropping frame");
                currentFrameHasError = true;
            }

            // If we survived possible errors, collect data to the current frame buffer
            if (!currentFrameHasError) {
                currentFrameBuffer.put(packet.getDataAsArray(), 2, packet.getDataSize() - 2);
                currentFrameBufferSize += (packet.getDataSize() - 2);
            }

            if (fuEnd) {
                Log.i(TAG, "FU-A end found. Sending frame!");
                try {
                    sendCurrentFrame();
                } catch (Throwable t) {
                    Log.e(TAG, "Error sending frame.", t);
                }
            }

            // STAP-A, used by libstreaming to embed SPS and PPS into the video stream
        } else if (nalType == 24) {
            Log.w(TAG, "NAL: STAP-A");

            frameNalType = NalType.STAPA;

            // This frame type includes a series of concatenated NAL units, each preceded
            // by a 16-bit size field

            // We'll use the reader index in this parsing routine
            ChannelBuffer buffer = packet.getData();
            // Discard the first byte (RTP packet type / nalType came from there)
            buffer.readByte();

            while(buffer.readable()) {
                // NAL Unit Size
                short nalUnitSize = buffer.readShort();

                // NAL Unit Data (of the size read above)
                byte[] nalUnitData = new byte[nalUnitSize];
                buffer.readBytes(nalUnitData);

                // Create and send the buffer upstream for processing
                currentFrameBuffer.clear();
                if (useByteStreamFormat) {
                    currentFrameBuffer.put(byteStreamStartCodePrefix);
                }
                currentFrameBuffer.put(nalUnitData);
                currentFrameBufferSize = currentFrameBuffer.position();
                sendCurrentFrame();
            }

            // libstreaming doesn't use anything else, so we won't implement other NAL unit types, at
            // least for now
        } else {
            Log.w(TAG, "NAL: Unimplemented unit type: " + nalType);
        }

        lastSequenceNumber = packet.getSequenceNumber();
        lastSequenceNumberIsValid = true;
    }

    private void initFrame(long timestamp) {
        if (currentFrameBufferSize > 0) {
            Log.w(TAG, "Initializing frame with pre-existing data. ts=" + currentFrameTimestamp);
        }
        currentFrameTimestamp = timestamp;
        currentFrameBufferSize = 0;
        currentFrameHasError = false;
        currentFrameBuffer.clear();
    }

    private void sendCurrentFrame() {
        if (currentFrameBufferSize == 0) {
            Log.w(TAG, "Attempt to send 0-size frame!");
            return;
        }
        Log.i(TAG, "currentFrameBufferSize,position=(" + currentFrameBufferSize + "," + currentFrameBuffer.position() + ")");

        // TODO: Horrible byte copying without even finding out how to do it properly - REPLACE!!
        // Only actually send full packets
        byte[] frame = new byte[currentFrameBuffer.position()];
        currentFrameBuffer.flip();
        currentFrameBuffer.get(frame);

        decodeFrame(frame);

        currentFrameBufferSize = 0;
    }

    private void decodeFrame(byte[] frame) {
        // Send frame down to decoder avoiding the STAP-A onces
        if (frameNalType == NalType.STAPA) {
            if (sps == null) {
                sps = frame;
            } else if (pps == null) {
                pps = frame;
            }
        }

        decoder.decodeFrame(frame);
        decoder.printFrame(frame);

    }

    public boolean isUseByteStreamFormat() {
        return useByteStreamFormat;
    }

    public void setUseByteStreamFormat(boolean useByteStreamFormat) {
        this.useByteStreamFormat = useByteStreamFormat;
    }

    public int readSampleData(ByteBuffer inputBuf) {
        inputBuf.put(currentFrame);
        return currentFrame.length;
    }

    // Think how to get CSD-0/CSD-1 codec-specific data chunks
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
        byte[] header_sps = { 0, 0, 0, 1, // header
                0x67, 0x64, (byte) 0x00, 0x1e, (byte) 0xac, (byte) 0xd9, 0x40, (byte) 0xa0, 0x3d,
                (byte) 0xa1, 0x00, 0x00, (byte) 0x03, 0x00, 0x01, 0x00, 0x00, 0x03, 0x00, 0x3C, 0x0F, 0x16, 0x2D, (byte) 0x96 }; // sps
        byte[] header_pps = { 0, 0, 0, 1, // header
                0x68, (byte) 0xeb, (byte) 0xec, (byte) 0xb2, 0x2C }; // pps


        format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        format.setInteger("durationUs", 12600000);

        return format;
    }
}
