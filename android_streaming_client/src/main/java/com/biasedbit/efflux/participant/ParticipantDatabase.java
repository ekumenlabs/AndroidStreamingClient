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

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface ParticipantDatabase {

    String getId();

    Collection<RtpParticipant> getReceivers();

    Map<Long, RtpParticipant> getMembers();

    void doWithReceivers(ParticipantOperation operation);

    void doWithParticipants(ParticipantOperation operation);

    boolean addReceiver(RtpParticipant remoteParticipant);

    boolean removeReceiver(RtpParticipant remoteParticipant);

    RtpParticipant getParticipant(long ssrc);

    RtpParticipant getOrCreateParticipantFromDataPacket(SocketAddress origin, DataPacket packet);

    RtpParticipant getOrCreateParticipantFromSdesChunk(SocketAddress origin, SdesChunk chunk);

    int getReceiverCount();

    int getParticipantCount();

    void cleanup();
}
