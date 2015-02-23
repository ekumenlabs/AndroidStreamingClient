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

import com.biasedbit.efflux.packet.DataPacket;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * DataPacket wrapper that includes knowledge about the NAL type and the interpretation of the
 * packet's bits according to H.264 specifications.
 *
 * @author Ayelen Chavez
 */
public class H264DataPacket {
    public enum NalType {
        FULL,
        NOT_FULL,
        STAPA,
        UNKNOWN
    }

    // actual packet
    private DataPacket packet;
    // NAL type as defined in the H.264 spec.
    private NalType nalType;
    // indicates whether this packet is the start of a frame
    private boolean fuStart;
    // indicates whether this packet is the end of a frame
    private boolean fuEnd;
    private byte fuNalType;
    private byte nalFBits;
    private byte nalNriBits;

    /**
     * Creates a H264 packet from a data packet.
     * @param packet
     */
    public H264DataPacket(DataPacket packet) {
        this.packet = packet;
        byte nalUnitOctet = packet.getData().getByte(0);
        byte nalTypeByte = (byte) (nalUnitOctet & 0x1F);
        nalNriBits = (byte) (nalUnitOctet & 0x60);
        nalFBits = (byte) (nalUnitOctet & 0x80);

        if (nalTypeByte > 0 && nalTypeByte < 24) {
            this.nalType = NalType.FULL;
        } else if (nalTypeByte == 28) {
            this.nalType = NalType.NOT_FULL;
        } else if (nalTypeByte == 24) {
            this.nalType = NalType.STAPA;
        }

        byte fuHeader = packet.getData().getByte(1);
        fuNalType = (byte) (fuHeader & 0x1F);

        fuStart = ((fuHeader & 0x80) != 0);
        fuEnd = ((fuHeader & 0x40) != 0);
    }

    /**
     * @return packet's sequence number.
     */
    public int getSequenceNumber() {
        return packet.getSequenceNumber();
    }

    /**
     *
     * @return packet's timestamp
     */
    public long getTimestamp() {
        return getConvertedTimestamp(packet);
    }

    // not a good practice!
    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    /**
     * @return packet's data
     */
    public ChannelBuffer getData() {
        return packet.getData();
    }

    /**
     * @return packet's size
     */
    public int getDataSize() {
        return packet.getDataSize();
    }

    /**
     * @return packet's NAL type according to H.264 spec.
     */
    public NalType nalType() {
        return nalType;
    }

    /**
     * @return the wrapped packet
     */
    public DataPacket getPacket() {
        return packet;
    }

    /**
     * Indicates whether the packet is the start of its corresponding frame.
     * @return true if this packet correspond to the end of its frame
     */
    public boolean isStart() {
        return fuStart;
    }

    /**
     * Indicates whether the packet is the end of its corresponding frame.
     * @return true if this packet correspond to the end of its frame
     */
    public boolean isEnd() {
        return fuEnd;
    }
    public byte getFuNalType() {
        return fuNalType;
    }

    public byte getNalFBits() {
        return nalFBits;
    }

    public byte getNalNriBits() {
        return nalNriBits;
    }
}