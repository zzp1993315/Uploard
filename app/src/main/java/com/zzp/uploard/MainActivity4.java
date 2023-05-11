package com.zzp.uploard;

import androidx.annotation.LongDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity4 extends AppCompatActivity {
    private Button button;
    public static  String timestamp="1678676078";
   public static  OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);
        button = findViewById(R.id.button);

        createM3U8File(0);
        button.setOnClickListener(view -> {
            new Thread(() -> {
                boolean isUploader = true;
                int count = 0;
                while (isUploader) {
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

//                    fileUploaderM3u8(new File(getApplication().getExternalCacheDir().getAbsolutePath(), "output.m3u8"));
                    String fileName = "output" + count + ".ts";
//                    fileUploader(new File(getApplication().getExternalCacheDir().getAbsolutePath(), fileName));
                    count++;
                    if (count > 3) {
                        isUploader = false;
                    }

                }

            }).start();
        });
        Log.d("zzpTag", "onCreate: " + getApplication().getExternalCacheDir().getAbsolutePath());

        checkPermission();


//            }
//        }).start();

    }

    public static void buildClient(){
        client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
//                .writeTimeout(10,TimeUnit.SECONDS)
//                .connectionPool(new ConnectionPool(32,5,TimeUnit.MINUTES))
                .build();
    }


    public  static void createM3U8File(int count) {
        //
        //先合成文件，然后
//ffmpeg -i "concat:1.ts|2.ts|3.ts|4.ts|.5.ts|" -c copy output.mp4 合并命令
        StringBuffer sb = new StringBuffer();
        File file = new File("/storage/emulated/0/Android/data/com.zzp.uploard/cache/output.m3u8");
        if (count == 0) {
            sb.append("#EXTM3U");
            sb.append("\r\n");
            sb.append("#EXT-X-VERSION:3");
            sb.append("\r\n");
            sb.append("#EXT-X-TARGETDURATION:11");
            sb.append("\r\n");
            sb.append("#EXT-X-MEDIA-SEQUENCE:0");
            sb.append("\r\n");
            sb.append("#EXTINF:");
            sb.append(String.format("%.4f", getTsDur(count)));
//            sb.append(String.format("%.6f", 10.0f));
            sb.append(",");
            sb.append("\r\n");
            sb.append("output" + count + ".ts");
            sb.append("\r\n");

//            String[] mergerCmd = {"-i", "concat:/storage/emulated/0/Android/data/com.zzp.uploard/cache/test00000.ts|/storage/emulated/0/Android/data/com.zzp.uploard/cache/test00001.ts|", "-c", "copy", "/storage/emulated/0/Android/data/com.zzp.uploard/cache/test.mp4"};
//            int rc = FFmpeg.execute(mergerCmd);
        } else {
            try {
                BufferedReader br = new BufferedReader(new FileReader("/storage/emulated/0/Android/data/com.zzp.uploard/cache/output.m3u8"));
                String str;
                while ((str = br.readLine()) != null) {//逐行读取
                    if (str.endsWith("#EXT-X-ENDLIST")) {
                        break;
                    }
                    sb.append(str);//加在StringBuffer尾
                    sb.append("\r\n");//行尾 加换行符
                }
                br.close();//别忘记，切记
            } catch (Exception e) {

            }

            sb.append("#EXTINF:");
            sb.append(String.format("%.4f", getTsDur(count)));
//            sb.append(String.format("%.6f",10.0f));
            sb.append(",");
//            Log.d("zzpTag", "createM3U8File: " + String.format("%.6f", getTsDur(count)));
            sb.append("\r\n");
            sb.append("output" + count + ".ts");
            sb.append("\r\n");

        }

        sb.append("#EXT-X-ENDLIST");
        sb.append("\r\n");
        Log.d("zzpTag",  sb.toString());
        try {
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();

            BufferedWriter bf = new BufferedWriter(new FileWriter(file));
            bf.write(sb.toString());
            bf.flush();
            bf.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //适配6.0权限
            if (ContextCompat.checkSelfPermission(getApplication(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(),
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                     || ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                     || ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.INTERNET,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_WIFI_STATE,
                                Manifest.permission.RECORD_AUDIO
                        }, 1);
            } else {
                //已经有权限
//                havePermission();
            }
        }
    }


    public static  void fileUploader(File file,upLoadCallBack callBack) {


        Log.d("zzpTag", "fileUploader: file.name=: " + file.getName());

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
//                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("test/plain"), file))
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("text/html"), file))
                .addFormDataPart("esn", "123")
                .addFormDataPart("timestamp", timestamp)
                .build();

        Request request = new Request.Builder()
                .url("http://cs-t.juziwulian.com/cloudStorage/file/upload")
                .post(requestBody)
                .addHeader("content-type", "multipart/form-data")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
//                .addHeader("cache-control", "no-cache")
                .build();

        try {
            Response response = client.newCall(request).execute();
                    Log.d("zzpTag", "fileUploader: " + response.body().string());
//            client.newCall(request).enqueue(new Callback() {
//                @Override
//                public void onFailure(Call call, IOException e) {
//
//                }
//
//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                }
//            });

        } catch (Exception e) {
            if(callBack!=null)
                callBack.onFailure();
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    public  static void fileUploaderM3u8(File file,upLoadCallBack callBack) {

        long timeStamp = System.currentTimeMillis() / 1000;
        Log.d("zzpTag", "fileUploader: file.name=: " + file.getName());

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("test/plain"), file))
                .addFormDataPart("esn", "123")
                .addFormDataPart("timestamp", timestamp)
                .build();

        Request request = new Request.Builder()
                .url("http://cs-t.juziwulian.com/cloudStorage/file/upload")
                .post(requestBody)
                .addHeader("content-type", "multipart/form-data")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
//                .addHeader("cache-control", "no-cache")
                .build();

        try {
            Response response = client.newCall(request).execute();
                    Log.d("zzpTag", "fileUploader: " + response.body().string());
                    if(callBack!=null)
            callBack.onSuccess();
//            client.newCall(request).enqueue(new Callback() {
//                @Override
//                public void onFailure(Call call, IOException e) {
//
//                }
//
//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                }
//            });

        } catch (Exception e) {
            e.printStackTrace();
            if(callBack!=null)
            callBack.onFailure();
//            throw new RuntimeException(e);
        }
    }

    public  static float getTsDur(int count) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

        File file =new File("/storage/emulated/0/Android/data/com.zzp.uploard/cache/output" + count + ".ts");

        mediaMetadataRetriever.setDataSource("/storage/emulated/0/Android/data/com.zzp.uploard/cache/output" + count + ".ts");
        String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); // 播放时长单位为毫秒
        Log.d("zzpTag", "getTsDur: duration=:"+duration);
        float time =Float.valueOf(duration)/1000;
        return time;
    }

    public interface  upLoadCallBack{
        void onSuccess();
        void onFailure();
    }

}