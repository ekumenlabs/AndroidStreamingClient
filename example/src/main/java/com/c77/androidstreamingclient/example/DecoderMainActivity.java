package com.c77.androidstreamingclient.example;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.c77.androidstreamingclient.lib.rtp.RtpMediaDecoder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Properties;

public class DecoderMainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = DecoderMainActivity.class.toString();
    private SurfaceView surfaceView;
    private RtpMediaDecoder rtpMediaDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = (SurfaceView) findViewById(R.id.surface_view);

        Properties configuration = new Properties();
        try {
            configuration.load(getApplicationContext().getAssets().open("configuration.ini"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Creates a RTP decoder.
        // RtpMediaDecoder is one of the main objects in the Android Streaming Library.
        // It is important to indicate the view where to play video streaming.
        // The configuration parameter is optional. It indicates if library should trace data to
        // logcat, some buffer-associated configurations, etc.
        // For more details about this configuration, check Android Streaming Library documentation
        // and the configuration.ini file from this example.
        rtpMediaDecoder = new RtpMediaDecoder(surfaceView, configuration);

        // Defines where data packet arrival information should be traced.
        // This configuration is optional but it comes up handy when trying to debug the client
        // side without messing with Android Studio debugger.
        OutputStream out;
        try {
            out = getApplicationContext().openFileOutput("example.trace", Context.MODE_PRIVATE);
            rtpMediaDecoder.setTraceOutputStream(out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Starts the decoder. It will start the underlying RTP session that listens for
        // packets to arrive and decodes them according to a certain buffering algorithm.
        rtpMediaDecoder.start();

        // listens to surface view click to restart client.
        surfaceView.setOnClickListener(this);

        showDebugInfo();
    }

    /**
     * Show main configuration values being used for the video client.
     */
    private void showDebugInfo() {
        TextView ipAndPort = (TextView) findViewById(R.id.ip_and_port);
        ipAndPort.setText(wifiIpAddress()+":"+rtpMediaDecoder.getDataStreamingPort());
        TextView resolution = (TextView) findViewById(R.id.resolution);
        resolution.setText(rtpMediaDecoder.getResolution());
        TextView transportProtocol = (TextView) findViewById(R.id.transport_protocol);
        transportProtocol.setText(rtpMediaDecoder.getTransportProtocol());
        TextView codec = (TextView) findViewById(R.id.codec);
        codec.setText(rtpMediaDecoder.getVideoCodec());
        TextView bufferType = (TextView) findViewById(R.id.buffer_type);
        bufferType.setText(rtpMediaDecoder.getBufferType());
    }

    /**
     * Get the device IP address and format it into a human readable one.
     * @return device's IP address
     */
    protected String wifiIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e(TAG, "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Releases the RTP decoder.
        // It will close the underlying RTP session and release the low level decoder.
        rtpMediaDecoder.release();
    }

    @Override
    public void onClick(View view) {
        // Restarts the RTP decoder.
        rtpMediaDecoder.restart();
    }
}
