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
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Traces packages' data into a file while they arrive.
 *
 * @author Julian Cerruti
 */
public class DataPacketTracer implements RtpSessionDataListener {
    private final PrintWriter traceWriter;

    /**
     * Creates a tracer initializing the object where packet's data will be written
     *
     * @param out
     */
    public DataPacketTracer(OutputStream out) {
        traceWriter = new PrintWriter(out);
    }

    /**
     * Writes packet's data when it arrives
     *
     * @param session
     * @param participant
     * @param packet
     */
    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        traceWriter.write(System.currentTimeMillis() + "," + packet.getSequenceNumber() + "," + packet.getTimestamp() + "\n");
    }
}

