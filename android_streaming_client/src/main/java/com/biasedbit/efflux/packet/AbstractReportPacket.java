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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public abstract class AbstractReportPacket extends ControlPacket {

    // internal vars --------------------------------------------------------------------------------------------------

    protected long senderSsrc;
    protected List<ReceptionReport> receptionReports;

    // constructors ---------------------------------------------------------------------------------------------------

    protected AbstractReportPacket(Type type) {
        super(type);
    }

    // public methods -------------------------------------------------------------------------------------------------

    public boolean addReceptionReportBlock(ReceptionReport block) {
        if (this.receptionReports == null) {
            this.receptionReports = new ArrayList<ReceptionReport>();
            return this.receptionReports.add(block);
        }

        // 5 bits is the limit
        return (this.receptionReports.size() < 31) && this.receptionReports.add(block);
    }

    public byte getReceptionReportCount() {
        if (this.receptionReports == null) {
            return 0;
        }

        return (byte) this.receptionReports.size();
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public long getSenderSsrc() {
        return senderSsrc;
    }

    public void setSenderSsrc(long senderSsrc) {
        if ((senderSsrc < 0) || (senderSsrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }
        this.senderSsrc = senderSsrc;
    }

    public List<ReceptionReport> getReceptionReports() {
        if (this.receptionReports == null) {
            return null;
        }
        return Collections.unmodifiableList(this.receptionReports);
    }

    public void setReceptionReports(List<ReceptionReport> receptionReports) {
        if (receptionReports.size() >= 31) {
            throw new IllegalArgumentException("At most 31 report blocks can be sent in a *ReportPacket");
        }
        this.receptionReports = receptionReports;
    }
}
