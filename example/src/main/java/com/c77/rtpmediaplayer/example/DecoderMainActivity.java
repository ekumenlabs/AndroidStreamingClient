package com.c77.rtpmediaplayer.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;

import com.c77.rtpmediaplayer.example.R;
import com.c77.rtpmediaplayer.lib.AlgronDecoder;


public class DecoderMainActivity extends Activity {

    private SurfaceView surfaceView;
    private AlgronDecoder algronDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        algronDecoder = new AlgronDecoder(surfaceView);
        algronDecoder.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        algronDecoder.release();
    }
}
