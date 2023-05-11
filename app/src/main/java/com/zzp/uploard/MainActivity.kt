package com.zzp.uploard

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.Camera
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.*
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowManager
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.*


class MainActivity : AppCompatActivity() {
    private lateinit var sender: LocalSocket
    private lateinit var mRecorder: MediaRecorder
    private lateinit var mSurfaceHolder: SurfaceHolder
    private lateinit var mCamera: Camera
    private lateinit var mMediaPlayer: MediaPlayer
    private lateinit var mSurfaceview: SurfaceView
    private lateinit var path: String //最终视频路径
    private val TAG = "MainActivity"
    private lateinit var startBtn: Button
    private var mStartedFlag = false //录像中标志
    private lateinit var dirPath: String //目标文件夹地址

    private lateinit var received: LocalSocket;
    private lateinit var lss: LocalServerSocket
    private lateinit var inputStream: InputStream
    lateinit var getDataThread: Thread
    lateinit var receiverData: Thread

    var count: Int = 0
    var time: Int = 0
    var handler = Handler()
    var runnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            time++
            if (time < 2) {

                handler.postDelayed(this, 10000)
            } else {
                time = 0
//                stopRecord()
//                mRecorder.setNextOutputFile(File(application.externalCacheDir!!.absolutePath,"output$count.ts"))

                startRecord()
//                MainActivity4.createM3U8File(count)
//                MainActivity4.fileUploaderM3u8(File(application.externalCacheDir!!.absolutePath, "output.m3u8"))
//                val fileName = "output$count.ts"
//                MainActivity4.fileUploader(File(application.externalCacheDir!!.absolutePath, fileName))
//                count++

                handler.postDelayed(this, 1000)
            }


        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mSurfaceview = findViewById(R.id.mSurfaceview)
        startBtn = findViewById(R.id.startCode)
        mMediaPlayer = MediaPlayer()
        var holder = mSurfaceview.holder

        startBtn.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {

                receiverData = Thread {
                    received = LocalSocket()
                    try {
                        lss =LocalServerSocket("sockt_ip")//相当于serversocekt开端口

                        received.connect(LocalSocketAddress("sockt_ip"));//相当于socket客户端
                        received.setReceiveBufferSize(100*1024);
                        received.setSendBufferSize(100*1024);

                        sender = lss.accept();//相当于serversocket等待连接，成功返回一个localsocket
                        sender.setReceiveBufferSize(100*1024);
                        sender.setSendBufferSize(100*1024);
                        inputStream = received.getInputStream();
                    } catch (e1: java.io.IOException) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
                receiverData.start()

                getDataThread = Thread(object : Runnable {

                    var buffer = ByteArray(1024)
                    var length = 0
                    var len = 0
                    var readlength = 0
                    override fun run() {
                        try {
                            while (true) {
                                while (readlength < 1024) {
                                    readlength += inputStream.read(
                                        buffer,
                                        readlength,
                                        1024 - readlength
                                    );
                                    Log.v("fds", "readlength--->" + readlength);
                                }
//                    raf.write(buffer,0,readlength);
                                readlength = 0;
                            }
                        } catch (e: IOException) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }

                })

                Thread.sleep(500)
                getDataThread.start()
                Thread.sleep(500)
                startRecord()
//                handler.post(runnable)
            }

        })
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    mSurfaceHolder = holder!!
                    //使用后置摄像头
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
                    mCamera.apply {
                        setDisplayOrientation(90)//旋转90度
                        setPreviewDisplay(holder)
                        val params = mCamera.parameters
                        //注意此处需要根据摄像头获取最优像素，//如果不设置会按照系统默认配置最低160x120分辨率
                        val size = getPreviewSize()
                        Log.d(TAG, "surfaceCreated: size.firs=:"+size.first+"=== size.second=:"+ size.second)
                        params.apply {
                            setPictureSize(size.first, size.second)
                            jpegQuality = 100
                            pictureFormat = PixelFormat.JPEG
                            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE//1连续对焦
                        }
                        parameters = params
                    }
                } catch (e: RuntimeException) {
                    //Camera.open() 在摄像头服务无法连接时可能会抛出 RuntimeException
                    finish()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                mSurfaceHolder = holder!!
                mCamera.apply {
                    startPreview()
                    cancelAutoFocus()
                    // 关键代码 该操作必须在开启预览之后进行（最后调用），
                    // 否则会黑屏，并提示该操作的下一步出错
                    // 只有执行该步骤后才可以使用MediaRecorder进行录制
                    // 否则会报 MediaRecorder(13280): start failed: -19
                    unlock()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {

            }

        })


    }

    companion object{

    }

     fun getPreviewSize(): Pair<Int, Int> {
        var bestPreviewWidth: Int = 1920
        var bestPreviewHeight: Int = 1080
        var mCameraPreviewWidth: Int
        var mCameraPreviewHeight: Int
        var diffs = Integer.MAX_VALUE
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val screenResolution = Point(display.width, display.height)
        val availablePreviewSizes = mCamera.parameters.supportedPreviewSizes
        Log.e(TAG, "屏幕宽度 ${screenResolution.x}  屏幕高度${screenResolution.y}")
        for (previewSize in availablePreviewSizes) {
            Log.v(TAG, " PreviewSizes = $previewSize")
            mCameraPreviewWidth = previewSize.width
            mCameraPreviewHeight = previewSize.height
            val newDiffs =
                Math.abs(mCameraPreviewWidth - screenResolution.y) + Math.abs(mCameraPreviewHeight - screenResolution.x)
            Log.v(TAG, "newDiffs = $newDiffs")
            if (newDiffs == 0) {
                bestPreviewWidth = mCameraPreviewWidth
                bestPreviewHeight = mCameraPreviewHeight
                break
            }
            if (diffs > newDiffs) {
                bestPreviewWidth = mCameraPreviewWidth
                bestPreviewHeight = mCameraPreviewHeight
                diffs = newDiffs
            }
        }
        Log.e(TAG, "最佳宽度 $bestPreviewWidth 最佳高度 $bestPreviewHeight")
        return Pair(bestPreviewWidth, bestPreviewHeight)
    }

    private fun startRecord() {

        mRecorder = MediaRecorder().apply {
            reset()
            setCamera(mCamera)
            // 设置音频源与视频源 这两项需要放在setOutputFormat之前
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.CAMERA)
            //设置输出格式
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
            //这两项需要放在setOutputFormat之后 IOS必须使用ACC
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)  //音频编码格式
            //使用MPEG_4_SP格式在华为P20 pro上停止录制时会出现
            //MediaRecorder: stop failed: -1007
            //java.lang.RuntimeException: stop failed.
            // at android.media.MediaRecorder.stop(Native Method)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)  //视频编码格式
            //设置最终出片分辨率
            setVideoSize(640, 480)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(3 * 1024 * 1024)
            setOrientationHint(90)
            //设置记录会话的最大持续时间（毫秒）
            setMaxDuration(10 * 1000)
        }
//            if (path != null) {
//                var dir = File(path)
//                if (!dir.exists()) {
//                    dir.mkdir()
//                }
//                dirPath = dir.absolutePath
        path = application.externalCacheDir!!.absolutePath + "/" + "output$count.mp4"
        Log.d(TAG, "文件路径： $path")
        mRecorder.apply {
            setOutputFile(sender.fileDescriptor)
//            setOutputFile(path)
            prepare()
            start()
//                }
//                startTime = System.currentTimeMillis()  //记录开始拍摄时间
        }


    }

    //结束录制
    private fun stopRecord() {


//          方法2 ： 捕捉异常改为拍照
        try {
            mRecorder.apply {
                stop()
                reset()
                release()
            }

//                mCamera.apply {
//                    lock()
//                    stopPreview()
//                    release()
//                }

        } catch (e: java.lang.RuntimeException) {
            //当catch到RE时，说明是录制时间过短，此时将由录制改变为拍摄

//                mRecorder.apply {
//                    reset()
//                    release()
//                }

        }
    }
//    }


}