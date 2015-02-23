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

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.network.ControlHandler;
import com.biasedbit.efflux.network.ControlPacketDecoder;
import com.biasedbit.efflux.network.ControlPacketEncoder;
import com.biasedbit.efflux.network.DataHandler;
import com.biasedbit.efflux.network.DataPacketDecoder;
import com.biasedbit.efflux.network.DataPacketEncoder;
import com.biasedbit.efflux.packet.AbstractReportPacket;
import com.biasedbit.efflux.packet.AppDataPacket;
import com.biasedbit.efflux.packet.ByePacket;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.packet.ReceiverReportPacket;
import com.biasedbit.efflux.packet.ReceptionReport;
import com.biasedbit.efflux.packet.SdesChunk;
import com.biasedbit.efflux.packet.SdesChunkItems;
import com.biasedbit.efflux.packet.SenderReportPacket;
import com.biasedbit.efflux.packet.SourceDescriptionPacket;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.ParticipantOperation;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public abstract class AbstractRtpSession implements RtpSession, TimerTask {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final Logger LOG = Logger.getLogger(AbstractRtpSession.class);
    protected static final String VERSION = "efflux_0.4_15092010";

    // configuration defaults -----------------------------------------------------------------------------------------

    // TODO not working with USE_NIO = false
    protected static final boolean USE_NIO = true;
    protected static final boolean DISCARD_OUT_OF_ORDER = true;
    protected static final int BANDWIDTH_LIMIT = 256;
    protected static final int SEND_BUFFER_SIZE = 1500;
    protected static final int RECEIVE_BUFFER_SIZE = 1500;
    protected static final int MAX_COLLISIONS_BEFORE_CONSIDERING_LOOP = 3;
    protected static final boolean AUTOMATED_RTCP_HANDLING = true;
    protected static final boolean TRY_TO_UPDATE_ON_EVERY_SDES = true;
    protected static final int PARTICIPANT_DATABASE_CLEANUP = 10;
    private static final boolean DEBUGGING = false;

    // configuration --------------------------------------------------------------------------------------------------

    protected final String id;
    protected final Set<Integer> payloadTypes = new HashSet<Integer>();
    protected final HashedWheelTimer timer;
    protected final OrderedMemoryAwareThreadPoolExecutor executor;
    protected String host;
    protected boolean useNio;
    protected boolean discardOutOfOrder;
    protected int bandwidthLimit;
    protected int sendBufferSize;
    protected int receiveBufferSize;
    protected int maxCollisionsBeforeConsideringLoop;
    protected boolean automatedRtcpHandling;
    protected boolean tryToUpdateOnEverySdes;
    protected int participantDatabaseCleanup;

    // internal vars --------------------------------------------------------------------------------------------------

    protected final AtomicBoolean running;
    protected final RtpParticipant localParticipant;
    protected final ParticipantDatabase participantDatabase;
    protected final List<RtpSessionDataListener> dataListeners;
    protected final List<RtpSessionControlListener> controlListeners;
    protected final List<RtpSessionEventListener> eventListeners;
    protected ConnectionlessBootstrap dataBootstrap;
    protected ConnectionlessBootstrap controlBootstrap;
    protected DatagramChannel dataChannel;
    protected DatagramChannel controlChannel;
    protected final AtomicInteger sequence;
    protected final AtomicBoolean sentOrReceivedPackets;
    protected final AtomicInteger collisions;
    protected final AtomicLong sentByteCounter;
    protected final AtomicLong sentPacketCounter;
    protected int periodicRtcpSendInterval;
    protected final boolean internalTimer;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local) {
        this(id, payloadType, local, null, null);
    }

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local,
                              HashedWheelTimer timer) {
        this(id, payloadType, local, timer, null);
    }

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local,
                              OrderedMemoryAwareThreadPoolExecutor executor) {
        this(id, payloadType, local, null, executor);
    }

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local, HashedWheelTimer timer,
                              OrderedMemoryAwareThreadPoolExecutor executor) {
        this(id, Collections.singleton(payloadType), local, timer, executor);
    }

    public AbstractRtpSession(String id, Collection<Integer> payloadTypes, RtpParticipant local, HashedWheelTimer timer,
                              OrderedMemoryAwareThreadPoolExecutor executor) {
        for (int payloadType : payloadTypes) {
            if ((payloadType < 0) || (payloadType > 127)) {
                throw new IllegalArgumentException("PayloadTypes must be in range [0;127]");
            }
        }

        if (!local.isReceiver()) {
            throw new IllegalArgumentException("Local participant must have its data & control addresses set");
        }

        this.id = id;
        this.payloadTypes.addAll(payloadTypes);
        this.localParticipant = local;
        this.participantDatabase = this.createDatabase();
        this.executor = executor;
        if (timer == null) {
            this.timer = new HashedWheelTimer(1, TimeUnit.SECONDS);
            this.internalTimer = true;
        } else {
            this.timer = timer;
            this.internalTimer = false;
        }

        this.running = new AtomicBoolean(false);
        this.dataListeners = new CopyOnWriteArrayList<RtpSessionDataListener>();
        this.controlListeners = new CopyOnWriteArrayList<RtpSessionControlListener>();
        this.eventListeners = new CopyOnWriteArrayList<RtpSessionEventListener>();
        this.sequence = new AtomicInteger(0);
        this.sentOrReceivedPackets = new AtomicBoolean(false);
        this.collisions = new AtomicInteger(0);
        this.sentPacketCounter = new AtomicLong(0);
        this.sentByteCounter = new AtomicLong(0);

        this.useNio = USE_NIO;
        this.discardOutOfOrder = DISCARD_OUT_OF_ORDER;
        this.bandwidthLimit = BANDWIDTH_LIMIT;
        this.sendBufferSize = SEND_BUFFER_SIZE;
        this.receiveBufferSize = RECEIVE_BUFFER_SIZE;
        this.maxCollisionsBeforeConsideringLoop = MAX_COLLISIONS_BEFORE_CONSIDERING_LOOP;
        this.automatedRtcpHandling = AUTOMATED_RTCP_HANDLING;
        this.tryToUpdateOnEverySdes = TRY_TO_UPDATE_ON_EVERY_SDES;
        this.participantDatabaseCleanup = PARTICIPANT_DATABASE_CLEANUP;
    }

    // RtpSession -----------------------------------------------------------------------------------------------------

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Set<Integer> getPayloadType() {
        return this.payloadTypes;
    }

    @Override
    public synchronized boolean init() {
        if (this.running.get()) {
            return true;
        }

        DatagramChannelFactory factory;
        if (this.useNio) {
            factory = new OioDatagramChannelFactory(Executors.newCachedThreadPool());
        } else {
            factory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
        }

        this.dataBootstrap = new ConnectionlessBootstrap(factory);
        this.dataBootstrap.setOption("sendBufferSize", this.sendBufferSize);
        this.dataBootstrap.setOption("receiveBufferSize", this.receiveBufferSize);
        this.dataBootstrap.setOption("receiveBufferSizePredictorFactory",
                new FixedReceiveBufferSizePredictorFactory(this.receiveBufferSize));
        this.dataBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new DataPacketDecoder());
                pipeline.addLast("encoder", DataPacketEncoder.getInstance());
                if (executor != null) {
                    pipeline.addLast("executorHandler", new ExecutionHandler(executor));
                }
                pipeline.addLast("handler", new DataHandler(AbstractRtpSession.this));
                return pipeline;
            }
        });
        this.controlBootstrap = new ConnectionlessBootstrap(factory);
        this.controlBootstrap.setOption("sendBufferSize", this.sendBufferSize);
        this.controlBootstrap.setOption("receiveBufferSize", this.receiveBufferSize);
        this.controlBootstrap.setOption("receiveBufferSizePredictorFactory",
                new FixedReceiveBufferSizePredictorFactory(this.receiveBufferSize));
        this.controlBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new ControlPacketDecoder());
                pipeline.addLast("encoder", ControlPacketEncoder.getInstance());
                if (executor != null) {
                    pipeline.addLast("executorHandler", new ExecutionHandler(executor));
                }
                pipeline.addLast("handler", new ControlHandler(AbstractRtpSession.this));
                return pipeline;
            }
        });

        SocketAddress dataAddress = this.localParticipant.getDataDestination();
        SocketAddress controlAddress = this.localParticipant.getControlDestination();

        try {
            this.dataChannel = (DatagramChannel) this.dataBootstrap.bind(dataAddress);
        } catch (Exception e) {
            LOG.error("Failed to bind data channel for session with id " + this.id, e);
            this.dataBootstrap.releaseExternalResources();
            this.controlBootstrap.releaseExternalResources();
            return false;
        }
        try {
            this.controlChannel = (DatagramChannel) this.controlBootstrap.bind(controlAddress);
        } catch (Exception e) {
            LOG.error("Failed to bind control channel for session with id " + this.id, e);
            this.dataChannel.close();
            this.dataBootstrap.releaseExternalResources();
            this.controlBootstrap.releaseExternalResources();
            return false;
        }

        if (DEBUGGING) {
            LOG.debug("Data & Control channels bound for RtpSession with id {}.", this.id);
        }

        // Send first RTCP packet.
        this.joinSession(this.localParticipant.getSsrc());
        this.running.set(true);

        // Add the cleaner.
        this.timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (!running.get()) {
                    return;
                }

                participantDatabase.cleanup();
                timer.newTimeout(this, participantDatabaseCleanup, TimeUnit.SECONDS);
            }
        }, this.participantDatabaseCleanup, TimeUnit.SECONDS);
        // Add the RTCP generator.
        if (this.automatedRtcpHandling) {
            this.timer.newTimeout(this, this.updatePeriodicRtcpSendInterval(), TimeUnit.SECONDS);
        }

        if (this.internalTimer) {
            this.timer.start();
        }

        return true;
    }

    @Override
    public void terminate() {
        this.terminate(RtpSessionEventListener.TERMINATE_CALLED);
    }

    @Override
    public boolean sendData(byte[] data, long timestamp, boolean marked) {
        if (!this.running.get()) {
            return false;
        }

        DataPacket packet = new DataPacket();
        // Other fields will be set by sendDataPacket()
        packet.setTimestamp(timestamp);
        packet.setData(data);
        packet.setMarker(marked);

        return this.sendDataPacket(packet);
    }

    @Override
    public boolean sendDataPacket(DataPacket packet) {
        if (!this.running.get()) {
            return false;
        }
        if (!this.payloadTypes.contains(packet.getPayloadType()) && this.payloadTypes.size() == 1) {
            packet.setPayloadType(this.payloadTypes.iterator().next());
        }

        packet.setSsrc(this.localParticipant.getSsrc());
        packet.setSequenceNumber(this.sequence.incrementAndGet());
        this.internalSendData(packet);
        return true;
    }

    @Override
    public boolean sendControlPacket(ControlPacket packet) {
        // Only allow sending explicit RTCP packets if all the following conditions are met:
        // 1. session is running
        // 2. automated rtcp handling is disabled (except for APP_DATA packets) 
        if (!this.running.get()) {
            return false;
        }

        if (ControlPacket.Type.APP_DATA.equals(packet.getType()) || !this.automatedRtcpHandling) {
            this.internalSendControl(packet);
            return true;
        }

        return false;
    }

    @Override
    public boolean sendControlPacket(CompoundControlPacket packet) {
        if (this.running.get() && !this.automatedRtcpHandling) {
            this.internalSendControl(packet);
            return true;
        }

        return false;
    }

    @Override
    public RtpParticipant getLocalParticipant() {
        return this.localParticipant;
    }

    @Override
    public boolean addReceiver(RtpParticipant remoteParticipant) {
        return (remoteParticipant.getSsrc() != this.localParticipant.getSsrc()) &&
                this.participantDatabase.addReceiver(remoteParticipant);
    }

    @Override
    public boolean removeReceiver(RtpParticipant remoteParticipant) {
        return this.participantDatabase.removeReceiver(remoteParticipant);
    }

    @Override
    public RtpParticipant getRemoteParticipant(long ssrc) {
        return this.participantDatabase.getParticipant(ssrc);
    }

    @Override
    public Map<Long, RtpParticipant> getRemoteParticipants() {
        return this.participantDatabase.getMembers();
    }

    @Override
    public void addDataListener(RtpSessionDataListener listener) {
        this.dataListeners.add(listener);
    }

    @Override
    public void removeDataListener(RtpSessionDataListener listener) {
        this.dataListeners.remove(listener);
    }

    @Override
    public void addControlListener(RtpSessionControlListener listener) {
        this.controlListeners.add(listener);
    }

    @Override
    public void removeControlListener(RtpSessionControlListener listener) {
        this.controlListeners.remove(listener);
    }

    @Override
    public void addEventListener(RtpSessionEventListener listener) {
        this.eventListeners.add(listener);
    }

    @Override
    public void removeEventListener(RtpSessionEventListener listener) {
        this.eventListeners.remove(listener);
    }

    // DataPacketReceiver ---------------------------------------------------------------------------------------------

    @Override
    public void dataPacketReceived(SocketAddress origin, DataPacket packet) {
        if (!this.running.get()) {
            return;
        }

        if (!this.payloadTypes.contains(packet.getPayloadType())) {
            // Silently discard packets of wrong payload.
            return;
        }

        if (packet.getSsrc() == this.localParticipant.getSsrc()) {
            // Sending data to ourselves? Consider this a loop and bail out!
            if (origin.equals(this.localParticipant.getDataDestination())) {
                this.terminate(new Throwable("Loop detected: session is directly receiving its own packets"));
                return;
            } else if (this.collisions.incrementAndGet() > this.maxCollisionsBeforeConsideringLoop) {
                this.terminate(new Throwable("Loop detected after " + this.collisions.get() + " SSRC collisions"));
                return;
            }

            long oldSsrc = this.localParticipant.getSsrc();
            long newSsrc = this.localParticipant.resolveSsrcConflict(packet.getSsrc());

            // A collision has been detected after packets were sent, resolve by updating the local SSRC and sending
            // a BYE RTCP packet for the old SSRC.
            // http://tools.ietf.org/html/rfc3550#section-8.2
            // If no packet was sent and this is the first being received then we can avoid collisions by switching
            // our own SSRC to something else (nothing else is required because the collision was prematurely detected
            // and avoided).
            // http://tools.ietf.org/html/rfc3550#section-8.1, last paragraph
            if (this.sentOrReceivedPackets.getAndSet(true)) {
                this.leaveSession(oldSsrc, "SSRC collision detected; rejoining with new SSRC.");
                this.joinSession(newSsrc);
            }

            LOG.warn("SSRC collision with remote end detected on session with id {}; updating SSRC from {} to {}.",
                    this.id, oldSsrc, newSsrc);
            for (RtpSessionEventListener listener : this.eventListeners) {
                listener.resolvedSsrcConflict(this, oldSsrc, newSsrc);
            }
        }

        // Associate the packet with a participant or create one.
        RtpParticipant participant = this.participantDatabase.getOrCreateParticipantFromDataPacket(origin, packet);
        if (participant == null) {
            // Depending on database implementation, it may chose not to create anything, in which case this packet
            // must be discarded.
            return;
        }

        // Should the packet be discarded due to out of order SN?
        if ((participant.getLastSequenceNumber() >= packet.getSequenceNumber()) && this.discardOutOfOrder) {
            if (DEBUGGING) {
                LOG.info("Discarded out of order packet from {} in session with id {} (last SN was {}, packet SN was {}).",
                        participant, this.id, participant.getLastSequenceNumber(), packet.getSequenceNumber());
            }
            return;
        }

        // Update last SN for participant.
        participant.setLastSequenceNumber(packet.getSequenceNumber());
        participant.setLastDataOrigin(origin);

        // Finally, dispatch the event to the data listeners.
        for (RtpSessionDataListener listener : this.dataListeners) {
            listener.dataPacketReceived(this, participant.getInfo(), packet);
        }
    }

    // ControlPacketReceiver ------------------------------------------------------------------------------------------

    @Override
    public void controlPacketReceived(SocketAddress origin, CompoundControlPacket packet) {
        if (!this.running.get()) {
            return;
        }

        if (!this.automatedRtcpHandling) {
            for (RtpSessionControlListener listener : this.controlListeners) {
                listener.controlPacketReceived(this, packet);
            }

            return;
        }

        for (ControlPacket controlPacket : packet.getControlPackets()) {
            switch (controlPacket.getType()) {
                case SENDER_REPORT:
                case RECEIVER_REPORT:
                    this.handleReportPacket(origin, (AbstractReportPacket) controlPacket);
                    break;
                case SOURCE_DESCRIPTION:
                    this.handleSdesPacket(origin, (SourceDescriptionPacket) controlPacket);
                    break;
                case BYE:
                    this.handleByePacket(origin, (ByePacket) controlPacket);
                    break;
                case APP_DATA:
                    for (RtpSessionControlListener listener : this.controlListeners) {
                        listener.appDataReceived(this, (AppDataPacket) controlPacket);
                    }
                default:
                    // do nothing, unknown case
            }
        }
    }

    // Runnable -------------------------------------------------------------------------------------------------------

    @Override
    public void run(Timeout timeout) throws Exception {
        if (!this.running.get()) {
            return;
        }

        final long currentSsrc = this.localParticipant.getSsrc();
        final SourceDescriptionPacket sdesPacket = buildSdesPacket(currentSsrc);
        this.participantDatabase.doWithReceivers(new ParticipantOperation() {
            @Override
            public void doWithParticipant(RtpParticipant participant) throws Exception {
                AbstractReportPacket report = buildReportPacket(currentSsrc, participant);
                internalSendControl(new CompoundControlPacket(report, sdesPacket));
            }
        });

        if (!this.running.get()) {
            return;
        }
        this.timer.newTimeout(this, this.updatePeriodicRtcpSendInterval(), TimeUnit.SECONDS);
    }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected void handleReportPacket(SocketAddress origin, AbstractReportPacket abstractReportPacket) {
        if (abstractReportPacket.getReceptionReportCount() == 0) {
            return;
        }

        RtpParticipant context = this.participantDatabase.getParticipant(abstractReportPacket.getSenderSsrc());
        if (context == null) {
            // Ignore; RTCP-SDES or RTP packet must first be received.
            return;
        }

        for (ReceptionReport receptionReport : abstractReportPacket.getReceptionReports()) {
            // Ignore all reception reports except for the one who pertains to the local participant (only data that
            // matters here is the link between this participant and ourselves).
            if (receptionReport.getSsrc() == this.localParticipant.getSsrc()) {
                // TODO
            }
        }

        // For sender reports, also handle the sender information.
        if (abstractReportPacket.getType().equals(ControlPacket.Type.SENDER_REPORT)) {
            SenderReportPacket senderReport = (SenderReportPacket) abstractReportPacket;
            // TODO
        }
    }

    protected void handleSdesPacket(SocketAddress origin, SourceDescriptionPacket packet) {
        for (SdesChunk chunk : packet.getChunks()) {
            RtpParticipant participant = this.participantDatabase.getOrCreateParticipantFromSdesChunk(origin, chunk);
            if (participant == null) {
                // Depending on database implementation, it may chose not to create anything, in which case this packet
                // must be discarded.
                return;
            }
            if (!participant.hasReceivedSdes() || this.tryToUpdateOnEverySdes) {
                participant.receivedSdes();
                // If this participant wasn't created from an SDES packet, then update its participant's description.
                if (participant.getInfo().updateFromSdesChunk(chunk)) {
                    for (RtpSessionEventListener listener : this.eventListeners) {
                        listener.participantDataUpdated(this, participant);
                    }
                }
            }
        }
    }

    protected void handleByePacket(SocketAddress origin, ByePacket packet) {
        for (Long ssrc : packet.getSsrcList()) {
            RtpParticipant participant = this.participantDatabase.getParticipant(ssrc);
            if (participant != null) {
                participant.byeReceived();
                for (RtpSessionEventListener listener : eventListeners) {
                    listener.participantLeft(this, participant);
                }
            }
        }
        LOG.trace("Received BYE for participants with SSRCs {} in session with id '{}' (reason: '{}').",
                packet.getSsrcList(), this.id, packet.getReasonForLeaving());
    }

    protected abstract ParticipantDatabase createDatabase();

    protected void internalSendData(final DataPacket packet) {
        this.participantDatabase.doWithReceivers(new ParticipantOperation() {
            @Override
            public void doWithParticipant(RtpParticipant participant) throws Exception {
                if (participant.receivedBye()) {
                    return;
                }
                try {
                    writeToData(packet, participant.getDataDestination());
                } catch (Exception e) {
                    LOG.error("Failed to send RTP packet to participants in session with id {}.", id);
                }
            }

            @Override
            public String toString() {
                return "internalSendData() for session with id " + id;
            }
        });
    }

    protected void internalSendControl(ControlPacket packet, RtpParticipant participant) {
        if (!participant.isReceiver() || participant.receivedBye()) {
            return;
        }

        try {
            this.writeToControl(packet, participant.getControlDestination());
        } catch (Exception e) {
            LOG.error("Failed to send RTCP packet to {} in session with id {}.", participant, this.id);
        }
    }

    protected void internalSendControl(CompoundControlPacket packet, RtpParticipant participant) {
        if (!participant.isReceiver() || participant.receivedBye()) {
            return;
        }

        try {
            this.writeToControl(packet, participant.getControlDestination());
        } catch (Exception e) {
            LOG.error("Failed to send RTCP compound packet to {} in session with id {}.", participant, this.id);
        }
    }

    protected void internalSendControl(final ControlPacket packet) {
        this.participantDatabase.doWithReceivers(new ParticipantOperation() {
            @Override
            public void doWithParticipant(RtpParticipant participant) throws Exception {
                if (participant.receivedBye()) {
                    return;
                }
                try {
                    writeToControl(packet, participant.getControlDestination());
                } catch (Exception e) {
                    LOG.error("Failed to send RTCP packet to participants in session with id {}.", id);
                }
            }

            @Override
            public String toString() {
                return "internalSendControl() for session with id " + id;
            }
        });
    }

    protected void internalSendControl(final CompoundControlPacket packet) {
        this.participantDatabase.doWithReceivers(new ParticipantOperation() {
            @Override
            public void doWithParticipant(RtpParticipant participant) throws Exception {
                if (participant.receivedBye()) {
                    return;
                }
                try {
                    writeToControl(packet, participant.getControlDestination());
                } catch (Exception e) {
                    LOG.error("Failed to send RTCP compound packet to participants in session with id {}.", id);
                }
            }

            @Override
            public String toString() {
                return "internalSendControl(CompoundControlPacket) for session with id " + id;
            }
        });
    }

    protected void writeToData(DataPacket packet, SocketAddress destination) {
        this.dataChannel.write(packet, destination);
    }

    protected void writeToControl(ControlPacket packet, SocketAddress destination) {
        this.controlChannel.write(packet, destination);
    }

    protected void writeToControl(CompoundControlPacket packet, SocketAddress destination) {
        this.controlChannel.write(packet, destination);
    }

    protected void joinSession(long currentSsrc) {
        if (!this.automatedRtcpHandling) {
            return;
        }
        // Joining a session, so send an empty receiver report.
        ReceiverReportPacket emptyReceiverReport = new ReceiverReportPacket();
        emptyReceiverReport.setSenderSsrc(currentSsrc);
        // Send also an SDES packet in the compound RTCP packet.
        SourceDescriptionPacket sdesPacket = this.buildSdesPacket(currentSsrc);

        CompoundControlPacket compoundPacket = new CompoundControlPacket(emptyReceiverReport, sdesPacket);
        this.internalSendControl(compoundPacket);
    }

    protected void leaveSession(final long currentSsrc, String motive) {
        if (!this.automatedRtcpHandling) {
            return;
        }

        final SourceDescriptionPacket sdesPacket = this.buildSdesPacket(currentSsrc);
        final ByePacket byePacket = new ByePacket();
        byePacket.addSsrc(currentSsrc);
        byePacket.setReasonForLeaving(motive);

        this.internalSendControl(new CompoundControlPacket(sdesPacket, byePacket));
    }

    protected AbstractReportPacket buildReportPacket(long currentSsrc, RtpParticipant context) {
        AbstractReportPacket packet;
        if (this.getSentPackets() == 0) {
            // If no packets were sent to this source, then send a receiver report.
            packet = new ReceiverReportPacket();
        } else {
            // Otherwise, build a sender report.
            SenderReportPacket senderPacket = new SenderReportPacket();
            senderPacket.setNtpTimestamp(0); // FIXME
            senderPacket.setRtpTimestamp(System.currentTimeMillis()); // FIXME
            senderPacket.setSenderPacketCount(this.getSentPackets());
            senderPacket.setSenderOctetCount(this.getSentBytes());
            packet = senderPacket;
        }
        packet.setSenderSsrc(currentSsrc);

        // If this source sent data, then calculate the link quality to build a reception report block.
        if (context.getReceivedPackets() > 0) {
            ReceptionReport block = new ReceptionReport();
            block.setSsrc(context.getInfo().getSsrc());
            block.setDelaySinceLastSenderReport(0); // FIXME
            block.setFractionLost((short) 0); // FIXME
            block.setExtendedHighestSequenceNumberReceived(0); // FIXME
            block.setInterArrivalJitter(0); // FIXME
            block.setCumulativeNumberOfPacketsLost(0); // FIXME
            packet.addReceptionReportBlock(block);
        }

        return packet;
    }

    protected SourceDescriptionPacket buildSdesPacket(long currentSsrc) {
        SourceDescriptionPacket sdesPacket = new SourceDescriptionPacket();
        SdesChunk chunk = new SdesChunk(currentSsrc);

        RtpParticipantInfo info = this.localParticipant.getInfo();
        if (info.getCname() == null) {
            info.setCname(new StringBuilder()
                    .append("efflux/").append(this.id).append('@')
                    .append(this.dataChannel.getLocalAddress()).toString());
        }
        chunk.addItem(SdesChunkItems.createCnameItem(info.getCname()));

        if (info.getName() != null) {
            chunk.addItem(SdesChunkItems.createNameItem(info.getName()));
        }

        if (info.getEmail() != null) {
            chunk.addItem(SdesChunkItems.createEmailItem(info.getEmail()));
        }

        if (info.getPhone() != null) {
            chunk.addItem(SdesChunkItems.createPhoneItem(info.getPhone()));
        }

        if (info.getLocation() != null) {
            chunk.addItem(SdesChunkItems.createLocationItem(info.getLocation()));
        }

        if (info.getTool() == null) {
            info.setTool(VERSION);
        }
        chunk.addItem(SdesChunkItems.createToolItem(info.getTool()));

        if (info.getNote() != null) {
            chunk.addItem(SdesChunkItems.createLocationItem(info.getNote()));
        }
        sdesPacket.addItem(chunk);

        return sdesPacket;
    }

    protected synchronized void terminate(Throwable cause) {
        // Always set to false, even it if was already set at false.
        if (!this.running.getAndSet(false)) {
            return;
        }

        if (this.internalTimer) {
            this.timer.stop();
        }

        this.dataListeners.clear();
        this.controlListeners.clear();

        // Close data channel, send BYE RTCP packets and close control channel.
        this.dataChannel.close();
        this.leaveSession(this.localParticipant.getSsrc(), "Session terminated.");
        this.controlChannel.close();

        this.dataBootstrap.releaseExternalResources();
        this.controlBootstrap.releaseExternalResources();

        if (DEBUGGING) {
            LOG.debug("RtpSession with id {} terminated.", this.id);
        }

        for (RtpSessionEventListener listener : this.eventListeners) {
            listener.sessionTerminated(this, cause);
        }
        this.eventListeners.clear();
    }

    protected void resetSendStats() {
        this.sentByteCounter.set(0);
        this.sentPacketCounter.set(0);
    }

    protected long incrementSentBytes(int delta) {
        if (delta < 0) {
            return this.sentByteCounter.get();
        }

        return this.sentByteCounter.addAndGet(delta);
    }

    protected long incrementSentPackets() {
        return this.sentPacketCounter.incrementAndGet();
    }

    protected long updatePeriodicRtcpSendInterval() {
        // TODO make this adaptative
        return (this.periodicRtcpSendInterval = 5);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isRunning() {
        return this.running.get();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.host = host;
    }

    public boolean useNio() {
        return useNio;
    }

    public void setUseNio(boolean useNio) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.useNio = useNio;
    }

    public boolean isDiscardOutOfOrder() {
        return discardOutOfOrder;
    }

    public void setDiscardOutOfOrder(boolean discardOutOfOrder) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.discardOutOfOrder = discardOutOfOrder;
    }

    public int getBandwidthLimit() {
        return bandwidthLimit;
    }

    public void setBandwidthLimit(int bandwidthLimit) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.bandwidthLimit = bandwidthLimit;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.sendBufferSize = sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getMaxCollisionsBeforeConsideringLoop() {
        return maxCollisionsBeforeConsideringLoop;
    }

    public void setMaxCollisionsBeforeConsideringLoop(int maxCollisionsBeforeConsideringLoop) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.maxCollisionsBeforeConsideringLoop = maxCollisionsBeforeConsideringLoop;
    }

    public boolean isAutomatedRtcpHandling() {
        return automatedRtcpHandling;
    }

    public void setAutomatedRtcpHandling(boolean automatedRtcpHandling) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.automatedRtcpHandling = automatedRtcpHandling;
    }

    public boolean isTryToUpdateOnEverySdes() {
        return tryToUpdateOnEverySdes;
    }

    public void setTryToUpdateOnEverySdes(boolean tryToUpdateOnEverySdes) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.tryToUpdateOnEverySdes = tryToUpdateOnEverySdes;
    }

    public long getSentBytes() {
        return this.sentByteCounter.get();
    }

    public long getSentPackets() {
        return this.sentPacketCounter.get();
    }

    public int getParticipantDatabaseCleanup() {
        return participantDatabaseCleanup;
    }

    public void setParticipantDatabaseCleanup(int participantDatabaseCleanup) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.participantDatabaseCleanup = participantDatabaseCleanup;
    }
}
