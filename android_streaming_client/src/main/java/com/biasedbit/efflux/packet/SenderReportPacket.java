/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biasedbit.efflux.packet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class SenderReportPacket extends AbstractReportPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    // TODO this might not be a long...
    private long ntpTimestamp;
    private long rtpTimestamp;
    private long senderPacketCount;
    private long senderOctetCount;

    // constructors ---------------------------------------------------------------------------------------------------

    public SenderReportPacket() {
        super(Type.SENDER_REPORT);
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static SenderReportPacket decode(ChannelBuffer buffer, boolean hasPadding, byte innerBlocks, int length) {
        SenderReportPacket packet = new SenderReportPacket();

        packet.setSenderSsrc(buffer.readUnsignedInt());
        packet.setNtpTimestamp(buffer.readLong());
        packet.setRtpTimestamp(buffer.readUnsignedInt());
        packet.setSenderPacketCount(buffer.readUnsignedInt());
        packet.setSenderOctetCount(buffer.readUnsignedInt());

        int read = 24;
        for (int i = 0; i < innerBlocks; i++) {
            packet.addReceptionReportBlock(ReceptionReport.decode(buffer));
            read += 24; // Each SR/RR block has 24 bytes (6 32bit words)
        }

        // Length is written in 32bit words, not octet count.
        int lengthInOctets = (length * 4);
        // (hasPadding == true) check is not done here. RFC respecting implementations will set the padding bit to 1
        // if length of packet is bigger than the necessary to convey the data; therefore it's a redundant check.
        if (read < lengthInOctets) {
            // Skip remaining bytes (used for padding).
            buffer.skipBytes(lengthInOctets - read);
        }

        return packet;
    }

    public static ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize, SenderReportPacket packet) {
        if ((currentCompoundLength < 0) || ((currentCompoundLength % 4) > 0)) {
            throw new IllegalArgumentException("Current compound length must be a non-negative multiple of 4");
        }
        if ((fixedBlockSize < 0) || ((fixedBlockSize % 4) > 0)) {
            throw new IllegalArgumentException("Padding modulus must be a non-negative multiple of 4");
        }

        // Common header + other fields (sender ssrc, ntp timestamp, rtp timestamp, packet count, octet count)
        int size = 4 + 24;
        ChannelBuffer buffer;
        if (packet.receptionReports != null) {
            size += packet.receptionReports.size() * 24;
        }

        // If packet was configured to have padding, calculate padding and add it.
        int padding = 0;
        if (fixedBlockSize > 0) {
            // If padding modulus is > 0 then the padding is equal to:
            // (global size of the compound RTCP packet) mod (block size)
            // Block size alignment might be necessary for some encryption algorithms
            // RFC section 6.4.1
            padding = fixedBlockSize - ((size + currentCompoundLength) % fixedBlockSize);
            if (padding == fixedBlockSize) {
                padding = 0;
            }
        }
        size += padding;

        // Allocate the buffer and write contents
        buffer = ChannelBuffers.buffer(size);
        // First byte: Version (2b), Padding (1b), RR count (5b)
        byte b = packet.getVersion().getByte();
        if (padding > 0) {
            b |= 0x20;
        }
        b |= packet.getReceptionReportCount();
        buffer.writeByte(b);
        // Second byte: Packet Type
        buffer.writeByte(packet.type.getByte());
        // Third byte: total length of the packet, in multiples of 4 bytes (32bit words) - 1
        int sizeInOctets = (size / 4) - 1;
        buffer.writeShort(sizeInOctets);
        // Next 24 bytes: ssrc, ntp timestamp, rtp timestamp, octet count, packet count
        buffer.writeInt((int) packet.senderSsrc);
        buffer.writeLong(packet.ntpTimestamp);
        buffer.writeInt((int) packet.rtpTimestamp);
        buffer.writeInt((int) packet.senderPacketCount);
        buffer.writeInt((int) packet.senderOctetCount);
        // Payload: report blocks
        if (packet.getReceptionReportCount() > 0) {
            for (ReceptionReport block : packet.receptionReports) {
                buffer.writeBytes(block.encode());
            }
        }

        if (padding > 0) {
            // Final bytes: padding
            for (int i = 0; i < (padding - 1); i++) {
                buffer.writeByte(0x00);
            }

            // Final byte: the amount of padding bytes that should be discarded.
            // Unless something's wrong, it will be a multiple of 4.
            buffer.writeByte(padding);
        }

        return buffer;
    }

    // ControlPacket --------------------------------------------------------------------------------------------------

    @Override
    public ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize) {
        return encode(currentCompoundLength, fixedBlockSize, this);
    }

    @Override
    public ChannelBuffer encode() {
        return encode(0, 0, this);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public long getNtpTimestamp() {
        return ntpTimestamp;
    }

    public void setNtpTimestamp(long ntpTimestamp) {
        // TODO
//        if ((ntpTimestamp < 0) || (ntpTimestamp > 0xffffffffffffffffl)) {
//            throw new IllegalArgumentException("Valid range for NTP timestamp is [0;0xffffffffffffffff]");
//        }
        this.ntpTimestamp = ntpTimestamp;
    }

    public long getRtpTimestamp() {
        return rtpTimestamp;
    }

    public void setRtpTimestamp(long rtpTimestamp) {
        if ((rtpTimestamp < 0) || (rtpTimestamp > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for RTP timestamp is [0;0xffffffff]");
        }
        this.rtpTimestamp = rtpTimestamp;
    }

    public long getSenderPacketCount() {
        return senderPacketCount;
    }

    public void setSenderPacketCount(long senderPacketCount) {
        if ((senderPacketCount < 0) || (senderPacketCount > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for Sender Packet Count is [0;0xffffffff]");
        }
        this.senderPacketCount = senderPacketCount;
    }

    public long getSenderOctetCount() {
        return senderOctetCount;
    }

    public void setSenderOctetCount(long senderOctetCount) {
        if ((senderOctetCount < 0) || (senderOctetCount > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for Sender Octet Count is [0;0xffffffff]");
        }
        this.senderOctetCount = senderOctetCount;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("SenderReportPacket{")
                .append("senderSsrc=").append(this.senderSsrc)
                .append(", ntpTimestamp=").append(this.ntpTimestamp)
                .append(", rtpTimestamp=").append(this.rtpTimestamp)
                .append(", senderPacketCount=").append(this.senderPacketCount)
                .append(", senderOctetCount=").append(this.senderOctetCount)
                .append(", receptionReports=").append(this.receptionReports)
                .append('}').toString();
    }
}
