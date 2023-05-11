package com.zzp.uploard;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;


public class AvcEncoder {
    private final static String TAG = "MeidaCodec";

    private int TIMEOUT_USEC = 10000;

    private MediaCodec mediaCodec;
    private int numbers = 300;
    int m_width;
    int m_height;
    int m_framerate;

    public byte[] configbyte;
    private H264CallBack mCallBack;

    public void setmCallBack(H264CallBack mCallBack) {
        this.mCallBack = mCallBack;
    }

    public AvcEncoder(int width, int height, int framerate, int bitrate) {

        m_width = width;
        m_height = height;
        m_framerate = framerate;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3);
        mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //配置编码器参数
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

    }

    /**
     * 启动编码器
     */
    public void start() {
        mediaCodec.start();
    }

    private void StopEncoder() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRuning = false;

    public void StopThread() {
        isRuning = false;
        try {
            StopEncoder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    int count = 0;

    public void StartEncoderThread() {
        Thread EncoderThread = new Thread(new Runnable() {

            @Override
            public void run() {
                isRuning = true;
                long currentTime = System.currentTimeMillis() / 1000;
                long lastTime =System.currentTimeMillis() / 1000;
                byte[] input = null;
                long pts = 0;
                long generateIndex = 0;
                long n = 0;
                while (isRuning) {

                    //访问MainActivity用来缓冲待解码数据的队列
                    if (MainActivity3.YUVQueue.size() > 0) {
                        //从缓冲队列中取出一帧
                        input = MainActivity3.YUVQueue.poll();
                        byte[] yuv420sp = new byte[m_width * m_height * 3 / 2];
                        //把待编码的视频帧转换为YUV420格式
                        NV21ToNV12(input, yuv420sp, m_width, m_height);
                        input = yuv420sp;

                    }
                    if (input != null) {
                        try {
                            long startMs = System.currentTimeMillis();
                            //编码器输入缓冲区
                            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                            //编码器输出缓冲区
                            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                pts = computePresentationTime(generateIndex);
//                                Log.d(TAG, "run: pts=:"+pts);
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();
                                //把转换后的YUV420格式的视频帧放到编码器输入缓冲区中
                                inputBuffer.put(input);
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                                generateIndex += 1;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            ByteArrayOutputStream os = new ByteArrayOutputStream();


                            while (outputBufferIndex >= 0) {
                                //Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                if (bufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
                                    configbyte = new byte[bufferInfo.size];
                                    configbyte = outData;
                                } else if (bufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {
                                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                    //把编码后的视频帧从编码器输出缓冲区中拷贝出来
                                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                                    os.write(keyframe, 0, keyframe.length);
                                    os.flush();

                                } else {
                                    //写到文件中
                                    os.write(outData, 0, outData.length);
                                    os.flush();

                                }

                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);


//                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                                byte[] outData = new byte[bufferInfo.size];
//                                outputBuffer.get(outData);
//                                // flags 利用位操作，定义的 flag 都是 2 的倍数
//                                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // 配置相关的内容，也就是 SPS，PPS
//                                    os.write(outData, 0, outData.length);
//                                } else if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) { // 关键帧
//                                    os.write(outData, 0, outData.length);
//                                } else {
//                                    // 非关键帧和SPS、PPS,直接写入文件，可能是B帧或者P帧
//                                    os.write(outData, 0, outData.length);
//                                }
                            /*    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
*/
                            }
                            os.close();
                            mCallBack.h264CallBack(os.toByteArray());
                            if (n >= numbers && n % numbers == 0) {
                                byte[] myByte = new byte[1];
                                currentTime =System.currentTimeMillis()/1000;
                                long time =currentTime-lastTime;
                                Log.d(TAG, "run: time=:"+time);
                                lastTime =currentTime;
                                mCallBack.h264CallBack(myByte);
                            }
                            n++;

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } else {

                    }
                }
            }
        });
        EncoderThread.start();

    }

    public static void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

    public interface H264CallBack {
        void h264CallBack(byte[] data);
    }

    private byte[] configByte;

}