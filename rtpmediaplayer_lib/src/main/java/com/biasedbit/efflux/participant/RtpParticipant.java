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

package com.biasedbit.efflux.participant;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.packet.SdesChunk;
import com.biasedbit.efflux.util.TimeUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class RtpParticipant {

    // constants ------------------------------------------------------------------------------------------------------

    private static final int VALID_PACKETS_UNTIL_VALID_PARTICIPANT = 3;

    // configuration --------------------------------------------------------------------------------------------------

    private final RtpParticipantInfo info;

    // internal vars --------------------------------------------------------------------------------------------------

    private SocketAddress dataDestination;
    private SocketAddress controlDestination;
    private SocketAddress lastDataOrigin;
    private SocketAddress lastControlOrigin;
    private long lastReceptionInstant;
    private long byeReceptionInstant;
    private int lastSequenceNumber;
    private boolean receivedSdes;
    private final AtomicLong receivedByteCounter;
    private final AtomicLong receivedPacketCounter;
    private final AtomicInteger validPacketCounter;

    // constructors ---------------------------------------------------------------------------------------------------

    private RtpParticipant(RtpParticipantInfo info) {
        // For internal use only.
        this.info = info;

        this.lastSequenceNumber = -1;
        this.lastReceptionInstant = 0;
        this.byeReceptionInstant = 0;

        this.receivedByteCounter = new AtomicLong();
        this.receivedPacketCounter = new AtomicLong();
        this.validPacketCounter = new AtomicInteger();
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static RtpParticipant createReceiver(String host, int dataPort, int controlPort) {
        RtpParticipant participant = new RtpParticipant(new RtpParticipantInfo());

        if ((dataPort < 0) || (dataPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }
        if ((controlPort < 0) || (controlPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }

        participant.dataDestination = new InetSocketAddress(host, dataPort);
        participant.controlDestination = new InetSocketAddress(host, controlPort);

        return participant;
    }

    public static RtpParticipant createReceiver(RtpParticipantInfo info, String host, int dataPort, int controlPort) {
        RtpParticipant participant = new RtpParticipant(info);

        if ((dataPort < 0) || (dataPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }
        if ((controlPort < 0) || (controlPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }

        participant.dataDestination = new InetSocketAddress(host, dataPort);
        participant.controlDestination = new InetSocketAddress(host, controlPort);

        return participant;
    }

    public static RtpParticipant createFromUnexpectedDataPacket(SocketAddress origin, DataPacket packet) {
        RtpParticipant participant = new RtpParticipant(new RtpParticipantInfo());
        participant.lastDataOrigin = origin;
        participant.getInfo().setSsrc(packet.getSsrc());

        return participant;
    }

    public static RtpParticipant createFromSdesChunk(SocketAddress origin, SdesChunk chunk) {
        RtpParticipant participant = new RtpParticipant(new RtpParticipantInfo());
        participant.lastControlOrigin = origin;
        participant.getInfo().updateFromSdesChunk(chunk);
        participant.receivedSdes();

        return participant;
    }

    // public methods -------------------------------------------------------------------------------------------------

    public long resolveSsrcConflict(long ssrcToAvoid) {
        // Will hardly ever loop more than once...
        while (this.getSsrc() == ssrcToAvoid) {
            this.getInfo().setSsrc(RtpParticipantInfo.generateNewSsrc());
        }

        return this.getSsrc();
    }

    public long resolveSsrcConflict(Collection<Long> ssrcsToAvoid) {
        // Probability to execute more than once is higher than the other method that takes just a long as parameter,
        // but its still incredibly low: for 1000 participants, there's roughly 2*10^-7 chance of collision
        while (ssrcsToAvoid.contains(this.getSsrc())) {
            this.getInfo().setSsrc(RtpParticipantInfo.generateNewSsrc());
        }

        return this.getSsrc();
    }


    public void byeReceived() {
        this.byeReceptionInstant = TimeUtils.now();
    }

    public void receivedSdes() {
        this.receivedSdes = true;
    }

    public void packetReceived() {
        this.lastReceptionInstant = TimeUtils.now();
    }

    public boolean isReceiver() {
        return (this.dataDestination != null) && (this.controlDestination != null);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public long getSsrc() {
        return this.getInfo().getSsrc();
    }

    public RtpParticipantInfo getInfo() {
        return info;
    }

    public long getLastReceptionInstant() {
        return lastReceptionInstant;
    }

    public long getByeReceptionInstant() {
        return byeReceptionInstant;
    }

    public int getLastSequenceNumber() {
        return lastSequenceNumber;
    }

    public void setLastSequenceNumber(int lastSequenceNumber) {
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public boolean receivedBye() {
        return this.byeReceptionInstant > 0;
    }

    public long getReceivedPackets() {
        return this.receivedPacketCounter.get();
    }

    public long getReceivedBytes() {
        return this.receivedByteCounter.get();
    }

    public boolean hasReceivedSdes() {
        return receivedSdes;
    }

    public SocketAddress getDataDestination() {
        return dataDestination;
    }

    public void setDataDestination(SocketAddress dataDestination) {
        if (dataDestination == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.dataDestination = dataDestination;
    }

    public SocketAddress getControlDestination() {
        return controlDestination;
    }

    public void setControlDestination(SocketAddress controlDestination) {
        if (dataDestination == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.controlDestination = controlDestination;
    }

    public SocketAddress getLastDataOrigin() {
        return lastDataOrigin;
    }

    public void setLastDataOrigin(SocketAddress lastDataOrigin) {
        this.lastDataOrigin = lastDataOrigin;
    }

    public SocketAddress getLastControlOrigin() {
        return lastControlOrigin;
    }

    public void setLastControlOrigin(SocketAddress lastControlOrigin) {
        this.lastControlOrigin = lastControlOrigin;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RtpParticipant)) {
            return false;
        }

        RtpParticipant that = (RtpParticipant) o;
        return this.controlDestination.equals(that.controlDestination) &&
               this.dataDestination.equals(that.dataDestination) &&
               this.info.getCname().equals(that.info.getCname());
    }

    @Override
    public int hashCode() {
        int result = dataDestination.hashCode();
        result = 31 * result + controlDestination.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return this.getInfo().toString();
    }
}
