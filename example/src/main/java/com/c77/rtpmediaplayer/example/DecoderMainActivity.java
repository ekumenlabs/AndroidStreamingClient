package com.c77.rtpmediaplayer.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;

import com.c77.rtpmediaplayer.lib.RtpMediaDecoder;


public class DecoderMainActivity extends Activity {

    private SurfaceView surfaceView;
    private RtpMediaDecoder rtpMediaDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        rtpMediaDecoder = new RtpMediaDecoder(surfaceView);
        rtpMediaDecoder.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        rtpMediaDecoder.release();
    }
}
