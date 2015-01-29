package com.c77.rtpmediaplayer.lib.rtp;

        import com.biasedbit.efflux.packet.DataPacket;
        import com.biasedbit.efflux.participant.RtpParticipantInfo;
        import com.biasedbit.efflux.session.RtpSession;
        import com.biasedbit.efflux.session.RtpSessionDataListener;

        import java.io.OutputStream;
        import java.io.PrintWriter;

/**
 * Created by julian on 1/20/15.
 */
public class BufferDelayTracer implements RtpSessionDataListener {
    private final PrintWriter traceWriter;

    public BufferDelayTracer(OutputStream out) {
        traceWriter = new PrintWriter(out);
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        traceWriter.write(System.currentTimeMillis() + "," + packet.getSequenceNumber() + "," + packet.getTimestamp() + "\n");
    }

    public void dataPacketReceived(DataPacketWithNalType lastPacket) {
        if (lastPacket != null) {
            DataPacket packet = lastPacket.getPacket();
            // return 0 if is not start, 1 if it is start
            // return 0 if is not end, 1 if it is end
            // return 0 if is NOT_FULL, 1 if it is FULL
            int type = lastPacket.nalType() == DataPacketWithNalType.NalType.NOT_FULL ? 0 : (lastPacket.nalType() == DataPacketWithNalType.NalType.STAPA ? 2 : 1);
            traceWriter.write(System.nanoTime() + "," + packet.getSequenceNumber() + "," + packet.getTimestamp()/9000 + "," + (lastPacket.isStart() ? 1 : 0) + "," + (lastPacket.isEnd() ? 1 : 0) + "," + type + "\n");
        }
    }
}

