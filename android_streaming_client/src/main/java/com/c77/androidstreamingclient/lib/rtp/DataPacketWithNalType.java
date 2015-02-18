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
 * Created by ashi on 1/20/15.
 */
public class DataPacketWithNalType {
    public enum NalType {
        FULL,
        NOT_FULL,
        STAPA,
        UNKNOWN
    }

    private DataPacket packet;
    private NalType nalType;
    private boolean fuStart;
    private boolean fuEnd;
    public byte fuNalType;
    public byte nalFBits;
    public byte nalNriBits;

    public DataPacketWithNalType(DataPacket packet) {
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

    public int getSequenceNumber() {
        return packet.getSequenceNumber();
    }

    public long getTimestamp() {
        return getConvertedTimestamp(packet);
    }

    // not a good practice!
    private long getConvertedTimestamp(DataPacket packet) {
        return packet.getTimestamp() / 90;
    }

    public ChannelBuffer getData() {
        return packet.getData();
    }

    public int getDataSize() {
        return packet.getDataSize();
    }

    public NalType nalType() {
        return nalType;
    }

    public DataPacket getPacket() {
        return packet;
    }

    public boolean isStart() {
        return fuStart;
    }

    public boolean isEnd() {
        return fuEnd;
    }
}