package com.zzp.uploard;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.ViewDataBinding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity2 extends AppCompatActivity implements SurfaceHolder.Callback,
        Camera.PreviewCallback, Encode.IEncoderListener {

    private Camera camera;
    private boolean isPreview = false;
    private SurfaceView surfaceView;

    private static final String TAG = "MainActivity";
    private static final String VLC_HOST = "192.168.6.217";
    private static final int VLC_PORT = 5500;

    private InetAddress address;
    private DatagramSocket socket;
    private ExecutorService executor;
    private Encode encode;
    private FileOutputStream fos;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);

        surfaceView = findViewById(R.id.camera_surfaceview);
        surfaceView.getHolder().addCallback(this);
        try {
            fos = new FileOutputStream(new File(getApplication().getExternalCacheDir(), "mytest.h264"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            address = InetAddress.getByName(VLC_HOST);
            socket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }

        executor = Executors.newSingleThreadExecutor();
    }

    private void startPreview() {
        if (camera == null) {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        }

        try {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewFormat(ImageFormat.NV21);

            Camera.Size previewSize = parameters.getPreviewSize();
            int size = previewSize.width * previewSize.height;
            size = size * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;

            if (encode == null) {
                encode = new Encode(previewSize.width, previewSize.height,
                        2000 * 1000, 30, this);
            }

            camera.addCallbackBuffer(new byte[size]);
            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.setPreviewCallbackWithBuffer(this);
            camera.setParameters(parameters);
            camera.startPreview();
            isPreview = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopPreview() {
        if (camera != null) {
            if (isPreview) {
                isPreview = false;
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
            }
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (isPreview) {
            stopPreview();
        }

        startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        encode.encoderYUV420(data);
        camera.addCallbackBuffer(data);
    }

    @Override
    public void onH264(final byte[] data) {
        try {
            fos.write(data);
            fos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encode != null) {
            encode.releaseMediaCodec();
            encode = null;
        }
    }
}