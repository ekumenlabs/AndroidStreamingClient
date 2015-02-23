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
import java.util.Collections;
import java.util.List;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class SourceDescriptionPacket extends ControlPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    private List<SdesChunk> chunks;

    // constructors ---------------------------------------------------------------------------------------------------

    public SourceDescriptionPacket() {
        super(Type.SOURCE_DESCRIPTION);
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static SourceDescriptionPacket decode(ChannelBuffer buffer, boolean hasPadding, byte innerBlocks,
                                                 int length) {
        SourceDescriptionPacket packet = new SourceDescriptionPacket();
        int readable = buffer.readableBytes();
        for (int i = 0; i < innerBlocks; i++) {
            packet.addItem(SdesChunk.decode(buffer));
        }

        if (hasPadding) {
            // Number of 32bit words read.
            int read = (readable - buffer.readableBytes()) / 4;

            // Rest is padding 32bit words, so skip it.
            buffer.skipBytes((length - read) * 4);
        }

        return packet;
    }

    public static ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize, SourceDescriptionPacket packet) {
        if ((currentCompoundLength < 0) || ((currentCompoundLength % 4) > 0)) {
            throw new IllegalArgumentException("Current compound length must be a non-negative multiple of 4");
        }
        if ((fixedBlockSize < 0) || ((fixedBlockSize % 4) > 0)) {
            throw new IllegalArgumentException("Padding modulus must be a non-negative multiple of 4");
        }

        int size = 4;
        ChannelBuffer buffer;
        List<ChannelBuffer> encodedChunks = null;
        if (packet.chunks != null) {
            encodedChunks = new ArrayList<ChannelBuffer>(packet.chunks.size());
            for (SdesChunk chunk : packet.chunks) {
                ChannelBuffer encodedChunk = chunk.encode();
                encodedChunks.add(encodedChunk);
                size += encodedChunk.readableBytes();
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
        if (packet.chunks != null) {
            b |= packet.chunks.size();
        }
        buffer.writeByte(b);
        // Second byte: Packet Type
        buffer.writeByte(packet.type.getByte());
        // Third byte: total length of the packet, in multiples of 4 bytes (32bit words) - 1
        int sizeInOctets = (size / 4) - 1;
        buffer.writeShort(sizeInOctets);
        // Remaining bytes: encoded chunks
        if (encodedChunks != null) {
            for (ChannelBuffer encodedChunk : encodedChunks) {
                buffer.writeBytes(encodedChunk);
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

    public boolean addItem(SdesChunk chunk) {
        if (this.chunks == null) {
            this.chunks = new ArrayList<SdesChunk>();
            return this.chunks.add(chunk);
        }

        // 5 bits (31) is the limit of chunks
        return (this.chunks.size() < 31) && this.chunks.add(chunk);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public List<SdesChunk> getChunks() {
        if (this.chunks == null) {
            return null;
        }
        return Collections.unmodifiableList(this.chunks);
    }

    public void setChunks(List<SdesChunk> chunks) {
        if (chunks.size() >= 31) {
            throw new IllegalArgumentException("At most 31 SSRC/CSRC chunks can be sent in a SourceDescriptionPacket");
        }
        this.chunks = chunks;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("SourceDescriptionPacket{")
                .append("chunks=").append(this.chunks)
                .append('}').toString();
    }
}
