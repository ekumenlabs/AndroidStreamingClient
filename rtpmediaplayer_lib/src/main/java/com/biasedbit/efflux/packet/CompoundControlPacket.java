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

package com.biasedbit.efflux.packet;

import java.util.Arrays;
import java.util.List;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class CompoundControlPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    private final List<ControlPacket> controlPackets;

    // constructors ---------------------------------------------------------------------------------------------------

    public CompoundControlPacket(ControlPacket... controlPackets) {
        if (controlPackets.length == 0) {
            throw new IllegalArgumentException("At least one RTCP packet must be provided");
        }
        this.controlPackets = Arrays.asList(controlPackets);
    }

    public CompoundControlPacket(List<ControlPacket> controlPackets) {
        if ((controlPackets == null) || controlPackets.isEmpty()) {
            throw new IllegalArgumentException("ControlPacket list cannot be null or empty");
        }
        this.controlPackets = controlPackets;
    }

    // public methods -------------------------------------------------------------------------------------------------

    public int getPacketCount() {
        return this.controlPackets.size();
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public List<ControlPacket> getControlPackets() {
        return this.controlPackets;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CompoundControlPacket{\n");
        for (ControlPacket packet : this.controlPackets) {
            builder.append("  ").append(packet.toString()).append('\n');
        }
        return builder.append('}').toString();
    }
}
