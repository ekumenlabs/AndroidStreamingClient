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

import com.biasedbit.efflux.participant.DefaultParticipantDatabase;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.ParticipantEventListener;
import com.biasedbit.efflux.participant.RtpParticipant;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import java.util.Collection;

/**
 * A regular RTP session, as described in RFC3550.
 *
 * Unlike {@link SingleParticipantSession}, this session starts off with 0 remote participants.
 *
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class MultiParticipantSession extends AbstractRtpSession implements ParticipantEventListener {

    // constructors ---------------------------------------------------------------------------------------------------

    public MultiParticipantSession(String id, int payloadType, RtpParticipant localParticipant) {
        super(id, payloadType, localParticipant, null, null);
    }

    public MultiParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                   HashedWheelTimer timer) {
        super(id, payloadType, localParticipant, timer, null);
    }

    public MultiParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                   OrderedMemoryAwareThreadPoolExecutor executor) {
        super(id, payloadType, localParticipant, null, executor);
    }

    public MultiParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                   HashedWheelTimer timer, OrderedMemoryAwareThreadPoolExecutor executor) {
        super(id, payloadType, localParticipant, timer, executor);
    }
    
    public MultiParticipantSession(String id, Collection<Integer> payloadTypes, RtpParticipant localParticipant,
    		HashedWheelTimer timer, OrderedMemoryAwareThreadPoolExecutor executor) {
    	super(id, payloadTypes, localParticipant, timer, executor);
    }

    // AbstractRtpSession ---------------------------------------------------------------------------------------------

    @Override
    protected ParticipantDatabase createDatabase() {
        return new DefaultParticipantDatabase(this.id, this);
    }

    // ParticipantEventListener ---------------------------------------------------------------------------------------

    @Override
    public void participantCreatedFromSdesChunk(RtpParticipant participant) {
        for (RtpSessionEventListener listener : this.eventListeners) {
            listener.participantJoinedFromControl(this, participant);
        }
    }

    @Override
    public void participantCreatedFromDataPacket(RtpParticipant participant) {
        for (RtpSessionEventListener listener : this.eventListeners) {
            listener.participantJoinedFromData(this, participant);
        }
    }

    @Override
    public void participantDeleted(RtpParticipant participant) {
        for (RtpSessionEventListener listener : this.eventListeners) {
            listener.participantDeleted(this, participant);
        }
    }
}
