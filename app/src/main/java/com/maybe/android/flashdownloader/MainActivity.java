package com.maybe.android.flashdownloader;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {
    int threadCount = 4;
    int threadFinish = 0;
    //final static String path = "http://192.168.107.2:8080/sougou.apk";
    private ProgressBar pb;
    private TextView tv;
    private EditText et;

    //当前进度
    long currentProgress = 0;

    //刷新下载进度文本
    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            tv.setText(currentProgress * 100 / pb.getMax() + "%");
            Log.d("pb.getProgress()",pb.getProgress() + "");
            Log.d("pb.getMax()",pb.getMax() + "");
            if(tv.getText().equals("99%")){
              tv.setText("下载完成");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button)findViewById(R.id.download);
        pb = (ProgressBar)findViewById(R.id.progress_bar);
        tv = (TextView)findViewById(R.id.progress_text);
        et = (EditText)findViewById(R.id.address) ;
        final String path = et.getText().toString();




        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread thread = new Thread(){
                    public void run(){
                        try {
                            Log.d("path",path);
                            URL url = new URL(path);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);
                            if(conn.getResponseCode() == 200){
                                //拿到被下载文件的大小
                                int length = conn.getContentLength();
                                //指定临时文件的路径和文件名
                                File file = new File(getCacheDir(),getFileName(path));
                                //创建随机存储文件对象
                                RandomAccessFile raf = new RandomAccessFile(file,"rwd");

                                //设置临时文件大小等于要下载文件的大小
                                raf.setLength(length);

                                //设置进度条最大值
                                pb.setMax(length);
                                //发送消息
                                handler.sendEmptyMessage(1);

                                //计算每个线程下载的字节数
                                int size = length / threadCount ;

                                for(int i=0;i<threadCount;i++){
                                    //计算每个线程的开始位置和结束位置
                                    int startIndex = i * size;
                                    int endIndex = (i + 1) * size - 1;
                                    //如果是最后一个线程的结束位置，则特殊处理，因为可能除不尽
                                    if(i == threadCount - 1){
                                        endIndex = length - 1;
                                    }
                                    Log.d("线程" + i, startIndex + "------------" + endIndex);
                                    //开始线程，传入线程id和下载的开始位置和结束位置
                                    new DownloadThread(i,startIndex,endIndex).start();
                                    Log.d("线程","总共下载了" + pb.getMax());
                                }

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                thread.start();
            }
        });

    }

    public String getFileName(String path){
        int index = path.lastIndexOf("/");
        return path.substring(index + 1);
    }




    public class DownloadThread extends Thread{
        int threadId;
        int startIndex;
        int endIndex;

        public DownloadThread(int threadId, int startIndex, int endIndex) {
            super();
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {
            String path = et.getText().toString();
            try {
                File fileProgress = new File(getCacheDir(),threadId + ".txt");
                //File fileProgress = new File(getCacheDir(),"temp.txt");

                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                //断点续传
                if(fileProgress.exists()){
                    FileInputStream fis = new FileInputStream(fileProgress);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                    int newStartIndex = Integer.parseInt(br.readLine());

                    currentProgress += (newStartIndex - startIndex);
                    pb.setProgress((int) currentProgress);
                    //handler.sendEmptyMessage(1);

                    startIndex = newStartIndex;

                    fis.close();
                    Log.d("线程"+ threadId + "已经下载了","" + startIndex);
                    Log.d("线程" + threadId + "当前范围", startIndex + "------------" + endIndex);

                }
                //设置请求的数据范围
                conn.setRequestProperty("Range","bytes=" + startIndex + "-" + endIndex);
                if(conn.getResponseCode() == 206){
                    InputStream is = conn.getInputStream();
                    int len = 0;
                    int total = 0;
                    //偏移量：用于断点续传，往临时文件中写入每个线程发生断点时已经下载的数据地址
                    //偏移防止数据覆盖
                    int offset = 10;
                    int currentPosition = startIndex;
                    byte [] b = new byte[1024];
                    //指定临时文件的路径和文件名
                    File file = new File(getCacheDir(),getFileName(path));
                    //创建随机存储文件对象
                    RandomAccessFile raf = new RandomAccessFile(file,"rwd");
                    //设置每个线程写入数据位置
                    raf.seek(startIndex);


                    while((len = is.read(b))!= -1){
                        raf.write(b,0,len);
                        total += len;
                        RandomAccessFile rafProgress = new RandomAccessFile(fileProgress,"rwd");
                        currentPosition = startIndex + total;

                        //rafProgress.seek(threadId * offset);
                        rafProgress.write((currentPosition + "").getBytes());

                        rafProgress.close();
                        //Log.d("线程",threadId + "已经下载" + total);

                        currentProgress += len;
                        pb.setProgress((int) currentProgress);
                        handler.sendEmptyMessage(1);
                    }
                    raf.close();
                    Log.d("线程",threadId + "下载完毕");
                    threadFinish ++ ;
                    Log.d("当前threadFinish=",threadFinish + "");
                    //同步语句块，防止异常情况产生
                    synchronized (path){
                        if(threadCount == threadFinish){
                            for (int i=0;i<threadCount;i++){
                                //匹配相同路径下的临时文件，才能够正确删除
                                File temp = new File(getCacheDir(),i + ".txt");

                                String name = temp.getName();
                                Log.d("正在删除临时文件",name + "");
                                temp.delete();

                            }
                            threadFinish = 0;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}


