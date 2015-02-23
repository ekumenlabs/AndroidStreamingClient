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

import java.util.ArrayList;
import java.util.List;

/**
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      defined by profile       |           length              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        header extension                       |
 * |                             ....                              |
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class DataPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    private RtpVersion version;
    private boolean marker;
    private int payloadType;
    private int sequenceNumber;
    private long timestamp;
    private long ssrc;

    private short extensionHeaderData;
    private byte[] extensionData;

    private List<Long> contributingSourceIds;

    private ChannelBuffer data;

    // constructors ---------------------------------------------------------------------------------------------------

    public DataPacket() {
        this.version = RtpVersion.V2;
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static DataPacket decode(byte[] data) {
        return decode(ChannelBuffers.wrappedBuffer(data));
    }

    public static DataPacket decode(ChannelBuffer buffer) throws IndexOutOfBoundsException {
        if (buffer.readableBytes() < 12) {
            throw new IllegalArgumentException("A RTP packet must be at least 12 octets long");
        }

        // Version, Padding, eXtension, CSRC Count
        DataPacket packet = new DataPacket();
        byte b = buffer.readByte();
        packet.version = RtpVersion.fromByte(b);
        boolean padding = (b & 0x20) > 0; // mask 0010 0000
        boolean extension = (b & 0x10) > 0; // mask 0001 0000
        int contributingSourcesCount = b & 0x0f; // mask 0000 1111

        // Marker, Payload Type
        b = buffer.readByte();
        packet.marker = (b & 0x80) > 0; // mask 0000 0001
        packet.payloadType = (b & 0x7f); // mask 0111 1111

        packet.sequenceNumber = buffer.readUnsignedShort();
        packet.timestamp = buffer.readUnsignedInt();
        packet.ssrc = buffer.readUnsignedInt();

        // Read extension headers & data
        if (extension) {
            packet.extensionHeaderData = buffer.readShort();
            packet.extensionData = new byte[buffer.readUnsignedShort() * 4];
            buffer.readBytes(packet.extensionData);
        }

        // Read CCRC's
        if (contributingSourcesCount > 0) {
            packet.contributingSourceIds = new ArrayList<Long>(contributingSourcesCount);
            for (int i = 0; i < contributingSourcesCount; i++) {
                long contributingSource = buffer.readUnsignedInt();
                packet.contributingSourceIds.add(contributingSource);
            }
        }

        if (!padding) {
            // No padding used, assume remaining data is the packet
            byte[] remainingBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(remainingBytes);
            packet.setData(remainingBytes);
        } else {
            // Padding bit was set, so last byte contains the number of padding octets that should be discarded.
            short lastByte = buffer.getUnsignedByte(buffer.readerIndex() + buffer.readableBytes() - 1);
            byte[] dataBytes = new byte[buffer.readableBytes() - lastByte];
            buffer.readBytes(dataBytes);
            packet.setData(dataBytes);
            // Discard rest of buffer.
            buffer.skipBytes(buffer.readableBytes());
        }

        return packet;
    }

    public static ChannelBuffer encode(int fixedBlockSize, DataPacket packet) {
        int size = 12; // Fixed width
        if (packet.hasExtension()) {
            size += 4 + packet.getExtensionDataSize();
        }
        size += packet.getContributingSourcesCount() * 4;
        size += packet.getDataSize();

        // If packet was configured to have padding (fixed block size), calculate padding and add it.
        int padding = 0;
        if (fixedBlockSize > 0) {
            // If padding modulus is > 0 then the padding is equal to:
            // (global size of the compound RTCP packet) mod (block size)
            // Block size alignment might be necessary for some encryption algorithms
            // RFC section 6.4.1
            padding = fixedBlockSize - (size % fixedBlockSize);
            if (padding == fixedBlockSize) {
                padding = 0;
            }
        }
        size += padding;

        ChannelBuffer buffer = ChannelBuffers.buffer(size);

        // Version, Padding, eXtension, CSRC Count
        byte b = packet.getVersion().getByte();
        if (padding > 0) {
            b |= 0x20;
        }
        if (packet.hasExtension()) {
            b |= 0x10;
        }
        b |= packet.getContributingSourcesCount();
        buffer.writeByte(b);

        // Marker, Payload Type
        b = (byte) packet.getPayloadType();
        if (packet.hasMarker()) {
            b |= 0x80; // 1000 0000
        }
        buffer.writeByte(b);

        buffer.writeShort(packet.sequenceNumber);
        buffer.writeInt((int) packet.timestamp);
        buffer.writeInt((int) packet.ssrc);

        // Write extension headers & data
        if (packet.hasExtension()) {
            buffer.writeShort(packet.extensionHeaderData);
            buffer.writeShort(packet.extensionData.length / 4);
            buffer.writeBytes(packet.extensionData);
        }

        // Write CCRC's
        if (packet.getContributingSourcesCount() > 0) {
            for (Long contributingSourceId : packet.getContributingSourceIds()) {
                buffer.writeInt(contributingSourceId.intValue());
            }
        }

        // Write RTP data
        if (packet.data != null) {
            buffer.writeBytes(packet.data.array());
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

    // public methods -------------------------------------------------------------------------------------------------

    public ChannelBuffer encode(int fixedBlockSize) {
        return encode(fixedBlockSize, this);
    }

    public ChannelBuffer encode() {
        return encode(0, this);
    }

    public void addContributingSourceId(long contributingSourceId) {
        if (this.contributingSourceIds == null) {
            this.contributingSourceIds = new ArrayList<Long>();
        }

        this.contributingSourceIds.add(contributingSourceId);
    }

    public int getDataSize() {
        if (this.data == null) {
            return 0;
        }

        return this.data.capacity();
    }

    public int getExtensionDataSize() {
        if (this.extensionData == null) {
            return 0;
        }

        return this.extensionData.length;
    }

    public int getContributingSourcesCount() {
        if (this.contributingSourceIds == null) {
            return 0;
        }

        return this.contributingSourceIds.size();
    }

    public void setExtensionHeader(short extensionHeaderData, byte[] extensionData) {
        if (extensionData.length > 65536) {
            throw new IllegalArgumentException("Extension data cannot exceed 65536 bytes");
        }
        if ((extensionData.length % 4) != 0) {
            throw new IllegalArgumentException("Extension data must be one or more 32-bit words.");
        }
        this.extensionHeaderData = extensionHeaderData;
        this.extensionData = extensionData;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public RtpVersion getVersion() {
        return version;
    }

    public void setVersion(RtpVersion version) {
        if (version != RtpVersion.V2) {
            throw new IllegalArgumentException("Only V2 is supported");
        }
        this.version = version;
    }

    public boolean hasExtension() {
        return this.extensionData != null;
    }

    public boolean hasMarker() {
        return marker;
    }

    public void setMarker(boolean marker) {
        this.marker = marker;
    }

    public int getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(int payloadType) {
        if ((payloadType < 0) || (payloadType > 127)) {
            throw new IllegalArgumentException("PayloadType must be in range [0;127]");
        }
        this.payloadType = payloadType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSsrc() {
        return ssrc;
    }

    public void setSsrc(long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }
        this.ssrc = ssrc;
    }

    public short getExtensionHeaderData() {
        return extensionHeaderData;
    }

    public byte[] getExtensionData() {
        return extensionData;
    }

    public List<Long> getContributingSourceIds() {
        return contributingSourceIds;
    }

    public void setContributingSourceIds(List<Long> contributingSourceIds) {
        this.contributingSourceIds = contributingSourceIds;
    }

    public ChannelBuffer getData() {
        return data;
    }

    public void setData(ChannelBuffer data) {
        this.data = data;
    }

    public byte[] getDataAsArray() {
        return this.data.array();
    }

    public void setData(byte[] data) {
        this.data = ChannelBuffers.wrappedBuffer(data);
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DataPacket{V=").append(this.version)
                .append(", X=").append(this.hasExtension())
                .append(", CC=").append(this.getContributingSourcesCount())
                .append(", M=").append(this.marker)
                .append(", PT=").append(this.payloadType)
                .append(", SN=").append(this.sequenceNumber)
                .append(", TS=").append(this.timestamp)
                .append(", SSRC=").append(this.ssrc)
                .append(", CSRCs=").append(this.contributingSourceIds)
                .append(", data=").append(this.getDataSize()).append(" bytes}")
                .toString();
    }
}
