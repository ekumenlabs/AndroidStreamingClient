package com.c77.androidstreamingclient.example;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;

import com.c77.androidstreamingclient.lib.rtp.RtpMediaDecoder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

public class DecoderMainActivity extends Activity implements View.OnClickListener {

    private SurfaceView surfaceView;
    private RtpMediaDecoder rtpMediaDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        Properties configuration = new Properties();
        try {
            configuration.load(getApplicationContext().getAssets().open("configuration.ini"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        rtpMediaDecoder = new RtpMediaDecoder(surfaceView, configuration);

        // Try to trace
        OutputStream out;
        try {
            out = getApplicationContext().openFileOutput("example.trace", Context.MODE_PRIVATE);
            rtpMediaDecoder.setTraceOutputStream(out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        rtpMediaDecoder.DEBUGGING = false;

        rtpMediaDecoder.start();

        surfaceView.setOnClickListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        rtpMediaDecoder.release();
    }

    @Override
    public void onClick(View view) {
        rtpMediaDecoder.restart();
    }
}
