package com.c77.rtpmediaplayer.example;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceView;
import android.view.View;

import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class DecoderMainActivity extends Activity implements View.OnClickListener {
    private SurfaceView surfaceView;
    private RtpMediaDecoder rtpMediaDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        rtpMediaDecoder = new RtpMediaDecoder(surfaceView);
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
