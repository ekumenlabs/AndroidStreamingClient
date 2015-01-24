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
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;

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

        // Try to trace
        OutputStream out;
        try {
            File file = new File(Environment.getExternalStorageDirectory()
                    + File.separator + "example.txt");
            file.createNewFile();
            out = new FileOutputStream(file);
            rtpMediaDecoder.setTraceOuputStream(out);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

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
