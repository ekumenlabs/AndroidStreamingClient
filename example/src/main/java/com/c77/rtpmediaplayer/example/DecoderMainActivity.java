package com.c77.rtpmediaplayer.example;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceView;
import android.view.View;

import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;

import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

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
