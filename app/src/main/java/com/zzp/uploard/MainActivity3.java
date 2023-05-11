package com.zzp.uploard;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.zzp.uploard.AvcEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MainActivity3 extends Activity implements SurfaceHolder.Callback, PreviewCallback, AvcEncoder.H264CallBack {

    private SurfaceView surfaceview;

    private SurfaceHolder surfaceHolder;

    private Camera camera;

    private Parameters parameters;

    int width = 1280;
//    int width = 720;

    int height = 720;
//    int height = 480;

    int framerate = 30;

    int biterate = 8500 * 1000;
//    int biterate = 1728000;

    private static int yuvqueuesize = 10;

    //待解码视频缓冲队列，静态成员！
    public static LinkedBlockingDeque<byte[]> YUVQueue = new LinkedBlockingDeque<byte[]>();

    private AvcEncoder avcCodec;
    private FileOutputStream outputStream;
    private int count = 0;

    private Handler handler = new Handler();
    private long startTime;
    private long lastTime = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);
        checkPermission();
        surfaceview = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.addCallback(this);
        deleteFile();
        createFile(count);
        MainActivity4.buildClient();

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = getBackCamera();

        startcamera(camera);
        //创建AvEncoder对象
        avcCodec = new AvcEncoder(width, height, framerate, biterate);
        avcCodec.setmCallBack(this);
        startTime = System.currentTimeMillis();
        avcCodec.start();
        MainActivity4.timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Log.d("zzpTag", "surfaceCreated: timestamp=-----:" + MainActivity4.timestamp);
        //启动编码线程
        avcCodec.StartEncoderThread();


    }

    public void deleteFile() {
        File[] fileList = new File(getApplication().getExternalCacheDir().getAbsolutePath()).listFiles();
        for (File file : fileList) {
            file.delete();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != camera) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            avcCodec.StopThread();
        }
    }


    @Override
    public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
        //将当前帧图像保存在队列中
        putYUVData(data, data.length);
    }

    //    int i =0;
    public void putYUVData(byte[] data, int length) {
//        i++;
//        long time = (System.currentTimeMillis() - startTime) / 1000;
//        if (lastTime != time && (time % 10 == 0) && time > 0) {
//            Log.d("TAG2", "h264:lastCount=: " + lastTime + "===count= " + time + "===i =:" + i);
//            lastTime = time;
//            byte[] ten = new byte[1];
//            YUVQueue.add(ten);
//        }

//        if (YUVQueue.size() >= 10) {
//            Log.d("zzpTag", "putYUVData: poll");
//            YUVQueue.poll();
//        }
        YUVQueue.add(data);


    }


    private void startcamera(Camera mCamera) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(this);
                mCamera.setDisplayOrientation(90);
                if (parameters == null) {
                    parameters = mCamera.getParameters();
                }
                //获取默认的camera配置
                parameters = mCamera.getParameters();
                //设置预览格式
                parameters.setPreviewFormat(ImageFormat.NV21);
                //设置预览图像分辨率
                parameters.setPreviewSize(width, height);


                parameters.setPreviewFrameRate(30);
                //配置camera参数
                mCamera.setParameters(parameters);

                //将完全初始化的SurfaceHolder传入到setPreviewDisplay(SurfaceHolder)中
                //没有surface的话，相机不会开启preview预览
                mCamera.setPreviewDisplay(surfaceHolder);
                //调用startPreview()用以更新preview的surface，必须要在拍照之前start Preview
                mCamera.startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Camera getBackCamera() {
        Camera c = null;
        try {
            //获取Camera的实例
            c = Camera.open(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //获取Camera的实例失败时返回null
        return c;
    }




    @Override
    public void h264CallBack(byte[] data) {
        try {
            if (data.length == 1) {
                outputStream.flush();
                outputStream.close();
                outputStream = null;
                BackgroundTaskExecutor.executeTask(new UploadThread(count));
                count++;
                createFile(count);
            } else {
                outputStream.write(data, 0, data.length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createFile(int count) {
        File file = new File(getApplication().getExternalCacheDir().getAbsolutePath(), "output" + count + ".h264");
        try {
            outputStream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class UploadThread implements Runnable {
        private int ct = 0;

        public UploadThread(int ct) {
            this.ct = ct;
        }

        @Override
        public void run() {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            synchronized (MainActivity3.class) {
                String fileName = "output" + ct + ".ts";
                String inputFileName = "output" + ct + ".h264";
                /***压缩成30针****/
            String[] mergerCmd = {"-y","-r","30","-i", getApplication().getExternalCacheDir().getAbsolutePath() + "/" + inputFileName, "-vcodec", "copy", "-f", "mpegts", getApplication().getExternalCacheDir().getAbsolutePath() + "/" + fileName};
//                String[] mergerCmd = {"-i", getApplication().getExternalCacheDir().getAbsolutePath() + "/" + inputFileName, "-vcodec", "copy", "-f", "mpegts", getApplication().getExternalCacheDir().getAbsolutePath() + "/" + fileName};
                FFmpeg.execute(mergerCmd);
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                MainActivity4.createM3U8File(ct);
                MainActivity4.fileUploaderM3u8(new File(getApplication().getExternalCacheDir().getAbsolutePath(), "output.m3u8"), new MainActivity4.upLoadCallBack() {
                    @Override
                    public void onSuccess() {
                        new File(getApplication().getExternalCacheDir().getAbsolutePath(), inputFileName).delete();
                    }

                    @Override
                    public void onFailure() {
                        Log.d("zzpTag", "onFailure: 重新上传m3u8");
                        MainActivity4.fileUploaderM3u8(new File(getApplication().getExternalCacheDir().getAbsolutePath(), "output.m3u8"),null);
                    }
                });

                MainActivity4.fileUploader(new File(getApplication().getExternalCacheDir().getAbsolutePath(), fileName), new MainActivity4.upLoadCallBack() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure() {
                        Log.d("zzpTag", "onFailure: 重新上传ts");
                        MainActivity4.fileUploader(new File(getApplication().getExternalCacheDir().getAbsolutePath(), fileName),null);
                    }
                });
            }
        }
    }

    /**
     * 获取设备支持的最大分辨率
     */
    private Camera.Size getCameraPreviewSize(Camera.Parameters parameters) {
        List<Camera.Size> list = parameters.getSupportedPreviewSizes();
        Camera.Size needSize = null;
        for (Camera.Size size : list) {
            if (needSize == null) {
                needSize = size;
                continue;
            }
            if (size.width >= needSize.width) {
                if (size.height > needSize.height) {
                    needSize = size;
                }
            }
        }
        return needSize;
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //适配6.0权限
            if (ContextCompat.checkSelfPermission(getApplication(),
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(),
                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.RECORD_AUDIO,
                                android.Manifest.permission.CAMERA,
                                android.Manifest.permission.INTERNET,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                android.Manifest.permission.ACCESS_WIFI_STATE,
                                Manifest.permission.RECORD_AUDIO
                        }, 1);
            } else {
                //已经有权限
//                havePermission();
            }
        }
    }

}