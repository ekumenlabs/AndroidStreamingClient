package com.c77.rtpmediaplayer.lib.rtp;

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

    public static final int H264_STANDARD_MULTIPLIER = 9000;

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
        return packet.getTimestamp() / H264_STANDARD_MULTIPLIER;
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