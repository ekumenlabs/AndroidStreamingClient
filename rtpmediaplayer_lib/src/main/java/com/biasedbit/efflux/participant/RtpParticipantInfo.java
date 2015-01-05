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

import com.biasedbit.efflux.packet.SdesChunk;
import com.biasedbit.efflux.packet.SdesChunkItem;
import com.biasedbit.efflux.packet.SdesChunkPrivItem;

import java.util.Random;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class RtpParticipantInfo {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Random RANDOM = new Random(System.nanoTime());

    // internal vars --------------------------------------------------------------------------------------------------

    private long ssrc;
    private String name;
    private String cname;
    private String email;
    private String phone;
    private String location;
    private String tool;
    private String note;
    private String privPrefix;
    private String priv;

    // constructors ---------------------------------------------------------------------------------------------------

    public RtpParticipantInfo(long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }

        this.ssrc = ssrc;
    }

    public RtpParticipantInfo() {
        this(generateNewSsrc());
    }

    /**
     * Randomly generates a new SSRC.
     * <p/>
     * Assuming no other source can obtain the exact same seed (or they're using a different algorithm for the random
     * generation) the probability of collision is roughly 10^-4 when the number of RTP sources is 1000.
     * <a href="http://tools.ietf.org/html/rfc3550#section-8.1">RFC 3550, Section 8.1<a>
     * <p/>
     * In this case, collision odds are slightly bigger because the identifier size will be 31 bits (0x7fffffff,
     * {@link Integer#MAX_VALUE} rather than the full 32 bits.
     *
     * @return A new, random, SSRC identifier.
     */
    public static long generateNewSsrc() {
        return RANDOM.nextInt(Integer.MAX_VALUE);
    }

    // public methods -------------------------------------------------------------------------------------------------

    public boolean updateFromSdesChunk(SdesChunk chunk) {
        boolean modified = false;
        if (this.ssrc != chunk.getSsrc()) {
            this.ssrc = chunk.getSsrc();
            modified = true;
        }
        if (chunk.getItems() == null) {
            return modified;
        }

        for (SdesChunkItem item : chunk.getItems()) {
            switch (item.getType()) {
                case CNAME:
                    if (this.willCauseModification(this.cname, item.getValue())) {
                        this.setCname(item.getValue());
                        modified = true;
                    }
                    break;
                case NAME:
                    if (this.willCauseModification(this.name, item.getValue())) {
                        this.setName(item.getValue());
                        modified = true;
                    }
                    break;
                case EMAIL:
                    if (this.willCauseModification(this.email, item.getValue())) {
                        this.setEmail(item.getValue());
                        modified = true;
                    }
                    break;
                case PHONE:
                    if (this.willCauseModification(this.phone, item.getValue())) {
                        this.setPhone(item.getValue());
                        modified = true;
                    }
                    break;
                case LOCATION:
                    if (this.willCauseModification(this.location, item.getValue())) {
                        this.setLocation(item.getValue());
                        modified = true;
                    }
                    break;
                case TOOL:
                    if (this.willCauseModification(this.tool, item.getValue())) {
                        this.setTool(item.getValue());
                        modified = true;
                    }
                    break;
                case NOTE:
                    if (this.willCauseModification(this.note, item.getValue())) {
                        this.setNote(item.getValue());
                        modified = true;
                    }
                    break;
                case PRIV:
                    String prefix = ((SdesChunkPrivItem) item).getPrefix();
                    if (this.willCauseModification(this.privPrefix, prefix) ||
                        this.willCauseModification(this.priv, item.getValue())) {
                        this.setPriv(prefix, item.getValue());
                        modified = true;
                    }
                    break;
                default:
                    // Never falls here...
            }
        }

        return modified;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private boolean willCauseModification(String originalValue, String newValue) {
        return newValue != null && !newValue.equals(originalValue);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public long getSsrc() {
        return this.ssrc;
    }

    /**
     * USE THIS WITH EXTREME CAUTION at the risk of seriously screwing up the way sessions handle data from incoming
     * participants.
     *
     * @param ssrc The new SSRC.
     */
    public void setSsrc(long ssrc) {
        if ((ssrc < 0) || (ssrc > 0xffffffffL)) {
            throw new IllegalArgumentException("Valid range for SSRC is [0;0xffffffff]");
        }

        this.ssrc = ssrc;
    }

    public String getCname() {
        return this.cname;
    }

    public void setCname(String cname) {
        this.cname = cname;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTool() {
        return this.tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getNote() {
        return this.note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPrivPrefix() {
        return this.privPrefix;
    }

    public String getPriv() {
        return this.priv;
    }

    public void setPriv(String prefix, String priv) {
        this.privPrefix = prefix;
        this.priv = priv;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("RtpParticipantInfo{")
                .append("ssrc=").append(this.ssrc);

        if (this.cname != null) {
            builder.append(", cname='").append(this.cname).append('\'');
        }

        if (this.name != null) {
            builder.append(", name='").append(this.name).append('\'');
        }

        if (this.email != null) {
            builder.append(", email='").append(this.email).append('\'');
        }

        if (this.phone != null) {
            builder.append(", phone='").append(this.phone).append('\'');
        }

        if (this.location != null) {
            builder.append(", location='").append(this.location).append('\'');
        }

        if (this.tool != null) {
            builder.append(", tool='").append(this.tool).append('\'');
        }

        if (this.note != null) {
            builder.append(", note='").append(this.note).append('\'');
        }

        if (this.priv != null) {
            builder.append(", priv='").append(this.privPrefix).append(':').append(this.priv).append('\'');
        }

        return builder.append('}').toString();
    }
}
