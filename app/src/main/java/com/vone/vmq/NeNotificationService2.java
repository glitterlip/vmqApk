package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.vone.vmq.util.Constant;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeNotificationService2  extends NotificationListenerService {
    private String TAG = "NeNotificationService2";
    private String host = "";
    private String key = "";
    private String id = "";
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;


    //申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(Context context) {
        if (null == mWakeLock)
        {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE, "WakeLock");
            if (null != mWakeLock)
            {
                mWakeLock.acquire();
            }
        }
    }
    //释放设备电源锁
    public void releaseWakeLock() {
        if (null != mWakeLock)
        {
            mWakeLock.release();
            mWakeLock = null;
        }
    }
    //心跳进程
    public void initAppHeart(){
        Log.d(TAG, "开始启动心跳线程");
        if (newThread!=null){
            return;
        }
        acquireWakeLock(this);
        newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "心跳线程启动！");
                while (true){

                    SharedPreferences read = getSharedPreferences(Constant.SP_KEY, MODE_PRIVATE);
                    host = read.getString("host", "");
                    key = read.getString("key", "");
                    id = read.getString("id", "");

                    //这里写入子线程需要做的工作

                    OkHttpClient okHttpClient = new OkHttpClient();
                    String url = String.format("https://%s/api/appHeart?id=%s&key=%s",host,id,key);

                    Request request = new Request.Builder().url(url).addHeader("model", Build.BRAND + " " + Build.MODEL).method("GET",null).build();
                    Call call = okHttpClient.newCall(request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            final String error = e.getMessage();
                            Handler handlerThree=new Handler(Looper.getMainLooper());
                            handlerThree.post(new Runnable(){
                                public void run(){
                                    Toast.makeText(getApplicationContext() ,"心跳状态错误，请检查配置是否正确!"+error,Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        //请求成功执行的方法
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Log.d(TAG, "onResponse heard: "+response.body().string());
                        }
                    });
                    try {
                        Thread.sleep(30*1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        newThread.start(); //启动线程
    }




    //当收到一条消息的时候回调，sbn是收到的消息
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "接受到通知消息");
        SharedPreferences read = getSharedPreferences(Constant.SP_KEY, MODE_PRIVATE);
        host = read.getString("host", "");
        id = read.getString("id", "");
        key = read.getString("key", "");


        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                String title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
                String content = extras.getString(NotificationCompat.EXTRA_TEXT, "");
                Log.d(TAG, "**********************");
                Log.d(TAG, "包名:" + pkg);
                Log.d(TAG, "标题:" + title);
                Log.d(TAG, "内容:" + content);
                Log.d(TAG, "**********************");


                if (pkg.equals("com.eg.android.AlipayGphone")){
                    if (content!=null && !content.equals("")) {
                        if (content.indexOf("通过扫码向你付款")!=-1 || content.indexOf("成功收款")!=-1){
                            String money = getMoney(content);
                            if (money!=null){
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 " + money);
                                appPush(2, Double.valueOf(money));
                            }else {
                                Handler handlerThree=new Handler(Looper.getMainLooper());
                                handlerThree.post(new Runnable(){
                                    public void run(){
                                        Toast.makeText(getApplicationContext() ,"监听到支付宝消息但未匹配到金额！",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                    }

                }else if(pkg.equals("com.tencent.mm")){

                    if (content!=null && !content.equals("")){
                        if (title.equals("微信支付") || title.equals("微信收款助手") || title.equals("微信收款商业版")){
                            String money = getMoney(content);
                            if (money!=null){
                                Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信到账 "+ money);
                                appPush(1,Double.valueOf(money));
                            }else{
                                Handler handlerThree=new Handler(Looper.getMainLooper());
                                handlerThree.post(new Runnable(){
                                    public void run(){
                                        Toast.makeText(getApplicationContext() ,"监听到微信消息但未匹配到金额！",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                        }
                    }

                }else if(pkg.equals("com.vone.qrcode")){

                    if (content.equals("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")){
                        Handler handlerThree=new Handler(Looper.getMainLooper());
                        handlerThree.post(new Runnable(){
                            public void run(){
                                Toast.makeText(getApplicationContext() ,"监听正常，如无法正常回调请联系作者反馈！",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }



            }
        }

    }
    //当移除一条消息的时候回调，sbn是被移除的消息
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
    //当连接成功时调用，一般在开启监听后会回调一次该方法
    @Override
    public void onListenerConnected() {
        //开启心跳线程
        initAppHeart();

        Handler handlerThree = new Handler(Looper.getMainLooper());
        handlerThree.post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext() ,"监听服务开启成功！",Toast.LENGTH_SHORT).show();
            }
        });


    }





    public void appPush(int type,double price){
        SharedPreferences read = getSharedPreferences(Constant.SP_KEY, MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        id = read.getString("id", "");

        String url = String.format("https://%s/api/appPush?id=%s&key=%s&type=%s&price=%s",host,id,key,type,price);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(url).method("GET",null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onResponse  push: 请求失败");

            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {

                Log.d(TAG, "onResponse  push: "+response.body().string());

            }
        });
    }

    public static String getMoney(String content){

        List<String> ss = new ArrayList<String>();
        for(String sss:content.replaceAll("[^0-9.]", ",").split(",")){
            if (sss.length()>0)
                ss.add(sss);
        }
        if (ss.size()<1){
            return null;
        }else {
            return ss.get(ss.size()-1);
        }

    }


}
