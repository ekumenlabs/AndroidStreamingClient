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

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class AppDataPacket extends ControlPacket {

    // constructors ---------------------------------------------------------------------------------------------------

    public AppDataPacket(Type type) {
        super(type);
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize, AppDataPacket packet) {
        return null;
    }

    // ControlPacket --------------------------------------------------------------------------------------------------

    @Override
    public ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize) {
        return encode(currentCompoundLength, fixedBlockSize, this);
    }

    @Override
    public ChannelBuffer encode() {
        return encode(0, 0, this);
    }
}
