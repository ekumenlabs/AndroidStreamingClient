package com.c77.rtpmediaplayer.lib.rtp;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.session.RtpSessionDataListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by julian on 1/14/15.
 */
public class RtpMediaBufferTest {
    // Configured delay time for the buffer
    private static final int DELAY = 800;
    // Amount of time to consider acceptable for the buffer to deliver a packet out of its expected time
    private static final int DELAY_ASSERT_THRESHOLD = 30;

    public RtpMediaBufferTest() {
        testInOrder();
        testReorder();

        System.out.println("All tests passed!");
    }

    public void testReorder() {
        MockMediaExtractor results = new MockMediaExtractor();

        // Configure and create the buffer
        Properties configuration = new Properties();
        configuration.setProperty(RtpMediaBufferWithJitterAvoidance.FRAMES_WINDOW_PROPERTY, Integer.toString(DELAY));
        RtpMediaBufferWithJitterAvoidance test = new RtpMediaBufferWithJitterAvoidance(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            long timestampDelta = realInitialTimestamp - 10000;
            System.out.println("Time delta = " + timestampDelta);

            test.dataPacketReceived(makePacket(10000, 1));
            test.dataPacketReceived(makePacket(10000, 2));
            test.dataPacketReceived(makePacket(10000, 4));

            Thread.sleep(34);
            test.dataPacketReceived(makePacket(10034, 5));
            test.dataPacketReceived(makePacket(10034, 6));

            Thread.sleep(34);
            test.dataPacketReceived(makePacket(10068, 7));
            test.dataPacketReceived(makePacket(10068, 8));
            test.dataPacketReceived(makePacket(10000, 3));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            // Do we have the expected number of packets?
            sillyAssertEquals(results.packetList.size(), 8, "Packets missing in decoder");

            DataPacket receivedPacket;
            for(int i=0;i<8;i++) {
                // Are the packets in proper order?
                receivedPacket = results.packetList.get(i).packet;
                sillyAssertEquals(receivedPacket.getSequenceNumber(), i + 1, "packet received out of order");

                // See if the packets are indeed delayed by the configured delay amount
                sillyAssertLongDifferenceWithThreshold(results.packetList.get(i).receivedTimestamp,
                        receivedPacket.getTimestamp()/90 + timestampDelta + DELAY, DELAY_ASSERT_THRESHOLD,
                        "packet s#" + receivedPacket.getSequenceNumber() + " at the wrong time");
            }

            System.out.println("Test passed (testReorder)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        test.dataPacketReceived(makePacket(10034, 5));
    }

    public void testInOrder() {
        MockMediaExtractor results = new MockMediaExtractor();

        // Configure and create the buffer
        Properties configuration = new Properties();
        configuration.setProperty(RtpMediaBufferWithJitterAvoidance.FRAMES_WINDOW_PROPERTY, Integer.toString(DELAY));
        RtpMediaBufferWithJitterAvoidance test = new RtpMediaBufferWithJitterAvoidance(results, configuration);

        try {
            // Feed a packet stream in order
            long realInitialTimestamp = System.currentTimeMillis();
            long timestampDelta = realInitialTimestamp - 10000;
            System.out.println("Time delta = " + timestampDelta);

            test.dataPacketReceived(makePacket(10000, 1));
            test.dataPacketReceived(makePacket(10000, 2));
            test.dataPacketReceived(makePacket(10000, 3));
            test.dataPacketReceived(makePacket(10000, 4));

            Thread.sleep(34);
            test.dataPacketReceived(makePacket(10034, 5));
            test.dataPacketReceived(makePacket(10034, 6));

            Thread.sleep(34);
            test.dataPacketReceived(makePacket(10068, 7));
            test.dataPacketReceived(makePacket(10068, 8));

            // Wait for the buffer to spit out the results and see what is there
            Thread.sleep(1000);

            // Do we have the expected number of packets?
            sillyAssertEquals(results.packetList.size(), 8, "Packets missing in decoder");

            DataPacket receivedPacket;
            for(int i=0;i<8;i++) {
                // Are the packets in proper order?
                receivedPacket = results.packetList.get(i).packet;
                sillyAssertEquals(receivedPacket.getSequenceNumber(), i + 1, "packet received out of order");

                // See if the packets are indeed delayed by the configured delay amount
                sillyAssertLongDifferenceWithThreshold(results.packetList.get(i).receivedTimestamp,
                        receivedPacket.getTimestamp()/90 + timestampDelta + DELAY, DELAY_ASSERT_THRESHOLD,
                        "packet s#" + receivedPacket.getSequenceNumber() + " at the wrong time");
            }

            System.out.println("Test passed (testInOrder)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        test.dataPacketReceived(makePacket(10034, 5));
    }

    private void sillyAssertTrue(boolean succeed, String message) {
        if(!succeed) {
            throw new RuntimeException("Assert is false: " + message);
        }
    }

    private void sillyAssertEquals(Object value, Object expected, String message) {
        if(value != expected) {
            throw new RuntimeException("Assert is not equal: " + message + " (" + value + " vs expected: " + expected + ")");
        }
    }

    private void sillyAssertLongDifferenceWithThreshold(long value, long expected, long threshold, String message) {
        if(Math.abs(value - expected) > threshold) {
            throw new RuntimeException("Assert difference is larger than threshold: " + message +
                    " (|" + value + " - " + expected + "| = " + Math.abs(value - expected) + " > " + threshold + ")");
        }
    }

    private DataPacket makePacket(long timestampMilliseconds, int sequenceNumber) {
        DataPacket testpacket = new DataPacket();
        testpacket.setTimestamp(timestampMilliseconds*90);
        testpacket.setSequenceNumber(sequenceNumber);
        return testpacket;
    }


    /**
     * Collects packets at the other end of the buffer
     */
    class MockMediaExtractor implements RtpSessionDataListener {
        List<ReceivedPacket> packetList = new ArrayList<ReceivedPacket>();

        @Override
        public void dataPacketReceived(DataPacket packet) {
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

    /**
     * Poor-man's test entry point
     * TODO: Replace with JUnit or another more proper test framework
     */
    public static void main(String argv[]) {
        new RtpMediaBufferTest();
    }
}
