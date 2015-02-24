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

package com.c77.androidstreamingclient.lib.tests;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.c77.androidstreamingclient.lib.rtp.buffer.TimeWindowRtpMediaBuffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * RtpMediaJitterBuffer tests.
 *
 * @author Julian Cerruti
 */
public class RtpMediaJitterBufferTest {
    // Configured delay time for the buffer
    private static final int DELAY = 200;
    // Amount of time to consider acceptable for the buffer to deliver a packet out of its expected time
    private static final int DELAY_ASSERT_THRESHOLD = 20; // tests will fail until we fix the issue
    // about the time taken by the consuming loop of the RtpMediaJitterBuffer

    MockMediaExtractor results;
    Properties configuration = new Properties();
    TimeWindowRtpMediaBuffer test;
    private long timestampDelta;

    public RtpMediaJitterBufferTest() {
        configuration.setProperty(TimeWindowRtpMediaBuffer.FRAMES_WINDOW_PROPERTY, Integer.toString(DELAY));

        try {
            testInOrder();
            testReorder();
            testDropMissingPacket();
            testDropPacketTooOld();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);
        }

        System.out.println("All tests passed!");
    }

    /**
     * Poor-man's test entry point
     * TODO: Replace with JUnit or another more proper test framework
     */
    public static void main(String argv[]) {
        new RtpMediaJitterBufferTest();
    }

    private void testDropPacketTooOld() {
        results = new MockMediaExtractor();
        test = new TimeWindowRtpMediaBuffer(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            timestampDelta = realInitialTimestamp - 10000;

            test.dataPacketReceived(null, null, makePacket(10000, 1));
            test.dataPacketReceived(null, null, makePacket(10000, 4));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10050, 5));
            test.dataPacketReceived(null, null, makePacket(10050, 6));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10100, 7));
            test.dataPacketReceived(null, null, makePacket(10100, 8));
            test.dataPacketReceived(null, null, makePacket(10000, 3));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10150, 9));
            //test.dataPacketReceived(makePacket(10150, 10));
            test.dataPacketReceived(null, null, makePacket(10150, 11));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10200, 12));
            test.dataPacketReceived(null, null, makePacket(10200, 13));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10250, 14));
            test.dataPacketReceived(null, null, makePacket(10250, 15));
            test.dataPacketReceived(null, null, makePacket(10250, 16));

            Thread.sleep(20);
            test.dataPacketReceived(null, null, makePacket(10000, 2));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            Set drops = new HashSet<Integer>();
            drops.add(10);
            drops.add(2);
            assertCorrectDroppedFramesResults(16, drops);

            System.out.println("Test passed (testDropPacketTooOld)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        test.stop();
    }

    private void testDropMissingPacket() {
        results = new MockMediaExtractor();
        test = new TimeWindowRtpMediaBuffer(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            timestampDelta = realInitialTimestamp - 10000;

            test.dataPacketReceived(null, null, makePacket(10000, 1));
            test.dataPacketReceived(null, null, makePacket(10000, 2));
            test.dataPacketReceived(null, null, makePacket(10000, 4));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10050, 5));
            test.dataPacketReceived(null, null, makePacket(10050, 6));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10100, 7));
            test.dataPacketReceived(null, null, makePacket(10100, 8));
            test.dataPacketReceived(null, null, makePacket(10000, 3));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10150, 9));
            //test.dataPacketReceived(makePacket(10150, 10));
            test.dataPacketReceived(null, null, makePacket(10150, 11));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10200, 12));
            test.dataPacketReceived(null, null, makePacket(10200, 13));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10250, 14));
            test.dataPacketReceived(null, null, makePacket(10250, 15));
            test.dataPacketReceived(null, null, makePacket(10250, 16));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            Set drops = new HashSet<Integer>();
            drops.add(new Integer(10));
            assertCorrectDroppedFramesResults(16, drops);

            System.out.println("Test passed (testDropMissingPacket)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        test.stop();
    }

    private void assertCorrectDroppedFramesResults(int numpackets, Set<Integer> droppedSequenceNumbers) {
        // Do we have the expected number of packets?
        sillyAssertEquals(results.packetList.size(), numpackets - droppedSequenceNumbers.size(), "Packets missing in decoder");

        DataPacket receivedPacket;
        int sqnoToPacketShift = 1;
        for (int i = 0; i < numpackets - droppedSequenceNumbers.size(); i++) {
            if (droppedSequenceNumbers.contains(i + sqnoToPacketShift)) {
                sqnoToPacketShift++;
            }
            // Are the packets in proper order?
            receivedPacket = results.packetList.get(i).packet;
            sillyAssertEquals(receivedPacket.getSequenceNumber(), i + sqnoToPacketShift, "packet received out of order");

            // See if the packets are indeed delayed by the configured delay amount
            sillyAssertLongDifferenceWithThreshold(results.packetList.get(i).receivedTimestamp,
                    receivedPacket.getTimestamp() / 90 + timestampDelta + DELAY, DELAY_ASSERT_THRESHOLD,
                    "packet s#" + receivedPacket.getSequenceNumber() + " at the wrong time");
        }
    }

    private void assertNoDroppedFramesResults(int numpackets) {
        // Do we have the expected number of packets?
        sillyAssertEquals(results.packetList.size(), numpackets, "Packets missing in decoder");

        DataPacket receivedPacket;
        for (int i = 0; i < numpackets; i++) {
            // Are the packets in proper order?
            receivedPacket = results.packetList.get(i).packet;
            sillyAssertEquals(receivedPacket.getSequenceNumber(), i + 1, "packet received out of order");

            // See if the packets are indeed delayed by the configured delay amount
            sillyAssertLongDifferenceWithThreshold(results.packetList.get(i).receivedTimestamp,
                    receivedPacket.getTimestamp() / 90 + timestampDelta + DELAY, DELAY_ASSERT_THRESHOLD,
                    "packet s#" + receivedPacket.getSequenceNumber() + " at the wrong time");
        }
    }

    public void testReorder() {
        results = new MockMediaExtractor();
        test = new TimeWindowRtpMediaBuffer(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            timestampDelta = realInitialTimestamp - 10000;

            test.dataPacketReceived(null, null, makePacket(10000, 1));
            test.dataPacketReceived(null, null, makePacket(10000, 2));
            test.dataPacketReceived(null, null, makePacket(10000, 4));

            Thread.sleep(34);
            test.dataPacketReceived(null, null, makePacket(10034, 5));
            test.dataPacketReceived(null, null, makePacket(10034, 6));

            Thread.sleep(34);
            test.dataPacketReceived(null, null, makePacket(10068, 7));
            test.dataPacketReceived(null, null, makePacket(10068, 8));
            test.dataPacketReceived(null, null, makePacket(10000, 3));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            assertNoDroppedFramesResults(8);

            System.out.println("Test passed (testReorder)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        test.stop();
    }

    public void testInOrder() {
        results = new MockMediaExtractor();
        test = new TimeWindowRtpMediaBuffer(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            timestampDelta = realInitialTimestamp - 10000;

            test.dataPacketReceived(null, null, makePacket(10000, 1));
            test.dataPacketReceived(null, null, makePacket(10000, 2));
            test.dataPacketReceived(null, null, makePacket(10000, 3));
            test.dataPacketReceived(null, null, makePacket(10000, 4));

            // Note: the packets are arriving a bit slower than expected
            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10034, 5));
            test.dataPacketReceived(null, null, makePacket(10034, 6));

            Thread.sleep(50);
            test.dataPacketReceived(null, null, makePacket(10068, 7));
            test.dataPacketReceived(null, null, makePacket(10068, 8));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            assertNoDroppedFramesResults(8);

            System.out.println("Test passed (testInOrder)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        test.stop();
    }

    private void sillyAssertTrue(boolean succeed, String message) {
        if (!succeed) {
            throw new RuntimeException("Assert is false: " + message);
        }
    }

    private void sillyAssertEquals(Object value, Object expected, String message) {
        if (value != expected) {
            throw new RuntimeException("Assert is not equal: " + message + " (" + value + " vs expected: " + expected + ")");
        }
    }

    private void sillyAssertLongDifferenceWithThreshold(long value, long expected, long threshold, String message) {
        if (Math.abs(value - expected) > threshold) {
            throw new RuntimeException("Assert difference is larger than threshold: " + message +
                    " (|" + value + " - " + expected + "| = " + Math.abs(value - expected) + " > " + threshold + ")");
        }
    }

    private DataPacket makePacket(long timestampMilliseconds, int sequenceNumber) {
        DataPacket testpacket = new DataPacket();
        testpacket.setTimestamp(timestampMilliseconds * 90);
        testpacket.setSequenceNumber(sequenceNumber);
        return testpacket;
    }

    /**
     * Collects packets at the other end of the buffer
     */
    class MockMediaExtractor implements RtpSessionDataListener {
        List<ReceivedPacket> packetList = new ArrayList<ReceivedPacket>();

        @Override
        public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
            packetList.add(new ReceivedPacket(packet, System.currentTimeMillis()));
        }
    }

    class ReceivedPacket {
        public final DataPacket packet;
        public final long receivedTimestamp;

        ReceivedPacket(DataPacket packet, long receivedTimestamp) {
            this.packet = packet;
            this.receivedTimestamp = receivedTimestamp;
        }
    }
}
