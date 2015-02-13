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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class SdesChunkPrivItem extends SdesChunkItem {

    // internal vars --------------------------------------------------------------------------------------------------

    private final String prefix;

    // constructors ---------------------------------------------------------------------------------------------------

    protected SdesChunkPrivItem(String prefix, String value) {
        super(SdesChunkItem.Type.PRIV, value);
        this.prefix = prefix;
    }

    // public methods -------------------------------------------------------------------------------------------------

    @Override
    public ChannelBuffer encode() {
        byte[] prefixBytes;
        if (this.prefix != null) {
            // RFC section 6.5 mandates that this must be UTF8
            // http://tools.ietf.org/html/rfc3550#section-6.5
            prefixBytes = this.prefix.getBytes(CharsetUtil.UTF_8);
        } else {
            prefixBytes = new byte[]{};
        }

        byte[] valueBytes;
        if (this.value != null) {
            // RFC section 6.5 mandates that this must be UTF8
            // http://tools.ietf.org/html/rfc3550#section-6.5
            valueBytes = this.value.getBytes(CharsetUtil.UTF_8);
        } else {
            valueBytes = new byte[]{};
        }

        if ((prefixBytes.length + valueBytes.length) > 254) {
            throw new IllegalArgumentException("Content (prefix + text) can be no longer than 255 bytes and this has " +
                                               valueBytes.length);
        }

        // Type (1b), total item length (1b), prefix length (1b), prefix (xb), text (xb)
        ChannelBuffer buffer = ChannelBuffers.buffer(2 + 1 + prefixBytes.length + valueBytes.length);
        buffer.writeByte(this.type.getByte());
        buffer.writeByte(1 + prefixBytes.length + valueBytes.length);
        buffer.writeByte(prefixBytes.length);
        buffer.writeBytes(prefixBytes);
        buffer.writeBytes(valueBytes);

        return buffer;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getPrefix() {
        return prefix;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("SdesChunkPrivItem{")
                .append("prefix='").append(this.prefix).append('\'')
                .append(", value='").append(this.value).append('\'')
                .append('}').toString();
    }
}
