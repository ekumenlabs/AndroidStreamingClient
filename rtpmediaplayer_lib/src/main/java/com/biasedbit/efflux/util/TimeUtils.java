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

package com.biasedbit.efflux.util;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class TimeUtils {

    // constructors ---------------------------------------------------------------------------------------------------

    private TimeUtils() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    /**
     * Retrieve a timestamp for the current instant.
     *
     * @return Current instant.
     */
    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * Retrieve a timestamp for the current instant, in nanoseconds.
     *
     * @return Current instant.
     */
    public static long nowNanos() {
        return System.nanoTime();
    }

    /**
     * Test whether a given event has timed out (in seconds).
     *
     * @param now        Current instant.
     * @param eventTime  Instant at which the event took place.
     * @param timeBuffer The amount of time for which the event is valid (in seconds).
     *
     * @return <code>true</code> if the event has expired, <code>false</code> otherwise
     */
    public static boolean hasExpired(long now, long eventTime, long timeBuffer) {
        return hasExpiredMillis(now, eventTime, timeBuffer * 1000);
    }

    /**
     * Test whether a given event has timed out (in milliseconds).
     *
     * @param now        Current instant.
     * @param eventTime  Instant at which the event took place.
     * @param timeBuffer The amount of time for which the event is valid (in milliseconds).
     *
     * @return <code>true</code> if the event has expired, <code>false</code> otherwise
     */
    public static boolean hasExpiredMillis(long now, long eventTime, long timeBuffer) {
        return (eventTime + timeBuffer) < now;
    }
}
