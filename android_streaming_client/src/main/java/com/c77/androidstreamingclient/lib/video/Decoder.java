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

package com.c77.androidstreamingclient.lib.video;

import com.c77.androidstreamingclient.lib.BufferedSample;
import com.c77.androidstreamingclient.lib.RtpPlayerException;

/**
 * Created by julian on 12/15/14.
 */
public interface Decoder {
    // Retrieves a buffer from the decoder
    public BufferedSample getSampleBuffer() throws RtpPlayerException;

    // Returns a new frame to be decoded
    public void decodeFrame(BufferedSample frame) throws Exception;
}
