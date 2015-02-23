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

import com.biasedbit.efflux.participant.RtpParticipant;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public interface RtpSessionEventListener {

    static final Throwable TERMINATE_CALLED = new Throwable("RtpSession.terminate() called");

    void participantJoinedFromData(RtpSession session, RtpParticipant participant);

    void participantJoinedFromControl(RtpSession session, RtpParticipant participant);

    void participantDataUpdated(RtpSession session, RtpParticipant participant);

    void participantLeft(RtpSession session, RtpParticipant participant);

    void participantDeleted(RtpSession session, RtpParticipant participant);

    void resolvedSsrcConflict(RtpSession session, long oldSsrc, long newSsrc);

    void sessionTerminated(RtpSession session, Throwable cause);
}
