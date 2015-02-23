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
import org.jboss.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class ByePacket extends ControlPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    private List<Long> ssrcList;
    private String reasonForLeaving;

    // constructors ---------------------------------------------------------------------------------------------------

    public ByePacket() {
        super(Type.BYE);
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static ByePacket decode(ChannelBuffer buffer, boolean hasPadding, byte innerBlocks, int length) {
        ByePacket packet = new ByePacket();
        int read = 0;
        for (int i = 0; i < innerBlocks; i++) {
            packet.addSsrc(buffer.readUnsignedInt());
            read += 4;
        }

        // Length is written in 32bit words, not octet count.
        int lengthInOctets = (length * 4);
        if (read < lengthInOctets) {
            byte[] reasonBytes = new byte[buffer.readUnsignedByte()];
            buffer.readBytes(reasonBytes);
            packet.reasonForLeaving = new String(reasonBytes, CharsetUtil.UTF_8);
            read += (1 + reasonBytes.length);
            if (read < lengthInOctets) {
                // Skip remaining bytes (used for padding). This takes care of both the null termination bytes (padding
                // of the 'reason for leaving' string and the packet padding bytes.
                buffer.skipBytes(lengthInOctets - read);
            }
        }

        return packet;
    }

    public static ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize, ByePacket packet) {
        if ((currentCompoundLength < 0) || ((currentCompoundLength % 4) > 0)) {
            throw new IllegalArgumentException("Current compound length must be a non-negative multiple of 4");
        }
        if ((fixedBlockSize < 0) || ((fixedBlockSize % 4) > 0)) {
            throw new IllegalArgumentException("Padding modulus must be a non-negative multiple of 4");
        }

        int size = 4;
        ChannelBuffer buffer;
        if (packet.ssrcList != null) {
            size += packet.ssrcList.size() * 4;
        }
        byte[] reasonForLeavingBytes = null;
        int reasonForLeavingPadding = 0;
        if (packet.reasonForLeaving != null) {
            reasonForLeavingBytes = packet.reasonForLeaving.getBytes(CharsetUtil.UTF_8);
            if (reasonForLeavingBytes.length > 255) {
                throw new IllegalArgumentException("Reason for leaving cannot exceed 255 bytes and this has " +
                                                   reasonForLeavingBytes.length);
            }

            size += (1 + reasonForLeavingBytes.length);
            // 'reason for leaving' must be 32bit aligned, so extra null octets might be needed.
            reasonForLeavingPadding = 4 - ((1 + reasonForLeavingBytes.length) % 4);
            if (reasonForLeavingPadding == 4) {
                reasonForLeavingPadding = 0;
            }
            if (reasonForLeavingPadding > 0) {
                size += reasonForLeavingPadding;
            }
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
        // First byte: Version (2b), Padding (1b), SSRC (chunks) count (5b)
        byte b = packet.getVersion().getByte();
        if (padding > 0) {
            b |= 0x20;
        }
        if (packet.ssrcList != null) {
            b |= packet.ssrcList.size();
        }
        buffer.writeByte(b);
        // Second byte: Packet Type
        buffer.writeByte(packet.type.getByte());
        // Third byte: total length of the packet, in multiples of 4 bytes (32bit words) - 1
        int sizeInOctets = (size / 4) - 1;
        buffer.writeShort(sizeInOctets);
        // Payload: ssrc list
        if (packet.ssrcList != null) {
            for (Long ssrc : packet.ssrcList) {
                buffer.writeInt(ssrc.intValue());
            }
        }
        // If 'reason for leaving' was specified, add it.
        if (reasonForLeavingBytes != null) {
            buffer.writeByte(reasonForLeavingBytes.length);
            buffer.writeBytes(reasonForLeavingBytes);
            for (int i = 0; i < reasonForLeavingPadding; i++) {
                buffer.writeByte(0x00);
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

    // public methods -------------------------------------------------------------------------------------------------

    public boolean addSsrc(long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }

        if (this.ssrcList == null) {
            this.ssrcList = new ArrayList<Long>();
        }

        return this.ssrcList.add(ssrc);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public List<Long> getSsrcList() {
        return Collections.unmodifiableList(this.ssrcList);
    }

    public void setSsrcList(List<Long> ssrcList) {
        this.ssrcList = new ArrayList<Long>(ssrcList.size());
        for (Long ssrc : ssrcList) {
            // Validate each ssrc being added.
            this.addSsrc(ssrc);
        }
    }

    public String getReasonForLeaving() {
        return reasonForLeaving;
    }

    public void setReasonForLeaving(String reasonForLeaving) {
        this.reasonForLeaving = reasonForLeaving;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("ByePacket{")
                .append("ssrcList=").append(this.ssrcList)
                .append(", reasonForLeaving='").append(reasonForLeaving).append('\'')
                .append('}').toString();
    }
}
