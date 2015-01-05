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

import com.biasedbit.efflux.network.ControlPacketReceiver;
import com.biasedbit.efflux.network.DataPacketReceiver;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipant;
import java.util.Set;
import java.util.Map;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public interface RtpSession extends DataPacketReceiver, ControlPacketReceiver {

    String getId();

    Set<Integer> getPayloadType();

    boolean init();

    void terminate();

    boolean sendData(byte[] data, long timestamp, boolean marked);

    boolean sendDataPacket(DataPacket packet);

    boolean sendControlPacket(ControlPacket packet);

    boolean sendControlPacket(CompoundControlPacket packet);

    RtpParticipant getLocalParticipant();

    boolean addReceiver(RtpParticipant remoteParticipant);

    boolean removeReceiver(RtpParticipant remoteParticipant);

    RtpParticipant getRemoteParticipant(long ssrsc);

    Map<Long, RtpParticipant> getRemoteParticipants();

    void addDataListener(RtpSessionDataListener listener);

    void removeDataListener(RtpSessionDataListener listener);

    void addControlListener(RtpSessionControlListener listener);

    void removeControlListener(RtpSessionControlListener listener);

    void addEventListener(RtpSessionEventListener listener);

    void removeEventListener(RtpSessionEventListener listener);
}
