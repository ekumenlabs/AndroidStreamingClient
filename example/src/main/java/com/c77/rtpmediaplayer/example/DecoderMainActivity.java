package com.c77.rtpmediaplayer.example;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;

import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.OutputStream;

public class DecoderMainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "DecoderMainActivity";

    private SurfaceView surfaceView;
    private RtpMediaDecoder rtpMediaDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        rtpMediaDecoder = new RtpMediaDecoder(surfaceView);

        // Try to trace
        OutputStream out;
        try {
            out = getApplicationContext().openFileOutput("example.trace", Context.MODE_PRIVATE);
            rtpMediaDecoder.setTraceOuputStream(out);
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
