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

package com.biasedbit.efflux.session;

import com.biasedbit.efflux.SsrcListener;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.SingleParticipantDatabase;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation that only supports two participants, a local and the remote.
 * <p/>
 * This session is ideal for calls with only two participants in NAT scenarions, where often the IP and ports negociated
 * in the SDP aren't the real ones (due to NAT restrictions and clients not supporting ICE).
 * <p/>
 * If data is received from a source other than the expected one, this session will automatically update the destination
 * IP and newly sent packets will be addressed to that new IP rather than the old one.
 * <p/>
 * If more than one source is used to send data for this session it will often get "confused" and keep redirecting
 * packets to the last source from which it received.
 * <p>
 * This is <strong>NOT</strong> a fully RFC 3550 compliant implementation, but rather a special purpose one for very
 * specific scenarios.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class SingleParticipantSession extends AbstractRtpSession {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean SEND_TO_LAST_ORIGIN = true;
    private static final boolean IGNORE_FROM_UNKNOWN_SSRC = true;

    // configuration --------------------------------------------------------------------------------------------------

    private final RtpParticipant receiver;
    private boolean sendToLastOrigin;
    private boolean ignoreFromUnknownSsrc;
    private SsrcListener ssrcListener;
    private int unknownSsrcCounter = 0;
    private final int UNKNOWN_SSRC_COUNTER_THRESHOLD = 50;

    // internal vars --------------------------------------------------------------------------------------------------

    private final AtomicBoolean receivedPackets;

    // constructors ---------------------------------------------------------------------------------------------------

    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant) {
        this(id, payloadType, localParticipant, remoteParticipant, null, null);
    }

    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant, OrderedMemoryAwareThreadPoolExecutor executor) {
        this(id, payloadType, localParticipant, remoteParticipant, null, executor);
    }

    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant, HashedWheelTimer timer) {
        this(id, payloadType, localParticipant, remoteParticipant, timer, null);
    }
    
    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
    								RtpParticipant remoteParticipant, HashedWheelTimer timer,
    								OrderedMemoryAwareThreadPoolExecutor executor) {
    	this(id, Collections.singleton(payloadType), localParticipant, remoteParticipant, timer, executor);
    }

    public SingleParticipantSession(String id, Collection<Integer> payloadTypes, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant, HashedWheelTimer timer,
                                    OrderedMemoryAwareThreadPoolExecutor executor) {
        super(id, payloadTypes, localParticipant, timer, executor);
        if (!remoteParticipant.isReceiver()) {
            throw new IllegalArgumentException("Remote participant must be a receiver (data & control addresses set)");
        }
        ((SingleParticipantDatabase) this.participantDatabase).setParticipant(remoteParticipant);
        this.receiver = remoteParticipant;
        this.receivedPackets = new AtomicBoolean(false);
        this.sendToLastOrigin = SEND_TO_LAST_ORIGIN;
        this.ignoreFromUnknownSsrc = IGNORE_FROM_UNKNOWN_SSRC;
    }

    // RtpSession -----------------------------------------------------------------------------------------------------

    @Override
    public boolean addReceiver(RtpParticipant remoteParticipant) {
        if (this.receiver.equals(remoteParticipant)) {
            return true;
        }

        // Sorry, "there can be only one".
        return false;
    }

    @Override
    public boolean removeReceiver(RtpParticipant remoteParticipant) {
        // No can do.
        return false;
    }

    @Override
    public RtpParticipant getRemoteParticipant(long ssrc) {
        if (ssrc == this.receiver.getInfo().getSsrc()) {
            return this.receiver;
        }

        return null;
    }

    @Override
    public Map<Long, RtpParticipant> getRemoteParticipants() {
        Map<Long, RtpParticipant> map = new HashMap<Long, RtpParticipant>();
        map.put(this.receiver.getSsrc(), this.receiver);
        return map;
    }

    // AbstractRtpSession ---------------------------------------------------------------------------------------------

    @Override
    protected ParticipantDatabase createDatabase() {
        return new SingleParticipantDatabase(this.id);
    }

    @Override
    protected void internalSendData(DataPacket packet) {
        try {
            // This assumes that the sender is sending is sending from the same ports where its expecting to receive.
            // Can be dangerous if the other end fully respects the RFC and supports ICE, but this is nearly the only
            // workaround that will work if the other end doesn't support ICE and is behind a NAT.
            SocketAddress destination;
            if (this.sendToLastOrigin && (this.receiver.getLastDataOrigin() != null)) {
                destination = this.receiver.getLastDataOrigin();
            } else {
                destination = this.receiver.getDataDestination();
            }
            this.writeToData(packet, destination);
            this.sentOrReceivedPackets.set(true);
        } catch (Exception e) {
            LOG.error("Failed to send {} to {} in session with id {}.", this.id, this.receiver.getInfo());
        }
    }

    @Override
    protected void internalSendControl(ControlPacket packet) {
        try {
            // This assumes that the sender is sending is sending from the same ports where its expecting to receive.
            // Can be dangerous if the other end fully respects the RFC and supports ICE, but this is nearly the only
            // workaround that will work if the other end doesn't support ICE and is behind a NAT.
            SocketAddress destination;
            if (this.sendToLastOrigin && (this.receiver.getLastControlOrigin() != null)) {
                destination = this.receiver.getLastControlOrigin();
            } else {
                destination = this.receiver.getControlDestination();
            }
            this.writeToControl(packet, destination);
            this.sentOrReceivedPackets.set(true);
        } catch (Exception e) {
            LOG.error("Failed to send RTCP packet to {} in session with id {}.",
                      this.receiver.getInfo(), this.id);
        }
    }

    @Override
    protected void internalSendControl(CompoundControlPacket packet) {
        try {
            this.writeToControl(packet, this.receiver.getControlDestination());
            this.sentOrReceivedPackets.set(true);
        } catch (Exception e) {
            LOG.error("Failed to send compound RTCP packet to {} in session with id {}.",
                      this.receiver.getInfo(), this.id);
        }
    }

    // DataPacketReceiver ---------------------------------------------------------------------------------------------

    @Override
    public void dataPacketReceived(SocketAddress origin, DataPacket packet) {
        if (!this.receivedPackets.getAndSet(true)) {
            // If this is the first packet then setup the SSRC for this participant (we didn't know it yet).
            this.receiver.getInfo().setSsrc(packet.getSsrc());
            LOG.warn("First packet received from remote source, updated SSRC to {}.", packet.getSsrc());
        } else if (this.ignoreFromUnknownSsrc && (packet.getSsrc() != this.receiver.getInfo().getSsrc())) {
            LOG.warn("Discarded packet from unexpected SSRC: {} (expected was {}). Informing ssrcListener.",
                      packet.getSsrc(), this.receiver.getInfo().getSsrc());
            unknownSsrcCounter++;
            if (ssrcListener != null && unknownSsrcCounter > UNKNOWN_SSRC_COUNTER_THRESHOLD) {
                unknownSsrcCounter = 0;
                ssrcListener.onSsrcChanged();
            }
            return;
        }

        unknownSsrcCounter = 0;
        super.dataPacketReceived(origin, packet);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public RtpParticipant getRemoteParticipant() {
        return this.receiver;
    }

    public boolean isSendToLastOrigin() {
        return sendToLastOrigin;
    }

    public void setSendToLastOrigin(boolean sendToLastOrigin) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.sendToLastOrigin = sendToLastOrigin;
    }

    public boolean isIgnoreFromUnknownSsrc() {
        return ignoreFromUnknownSsrc;
    }

    public void setIgnoreFromUnknownSsrc(boolean ignoreFromUnknownSsrc) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.ignoreFromUnknownSsrc = ignoreFromUnknownSsrc;
    }

    public void setSsrcListener(SsrcListener listener) {
        ssrcListener = listener;
    }
}
