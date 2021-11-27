package com.app.gyroscope_test;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.util.Calendar;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;

/*
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;
*/


public class MyService extends Service {


    NotificationManager manager;

    private static String CHANNEL_ID = "channel1";
    private static String CHANNEL_NAME = "Channel1";


    //Using the Accelometer & Gyroscoper
    private static SensorManager mSensorManager = null;

    //Using the Gyroscope
    private static SensorEventListener mSenserLis;
    private Sensor mGgyroSensor = null;

    //Roll and Pitch
    private double pitch;
    private double roll;
    private double yaw;

    //timestamp and dt
    private double timestamp;
    private double dt;

    // for radian -> dgree
    private double RAD2DGR = 180 / Math.PI;
    private static final float NS2S = 1.0f / 1000000000.0f;
    int roll_counter = 0;

    private Sensor lightSensor;
    private Sensor mAccelometerSensor;
    double lightValue;

    MediaRecorder recorder;
    MediaPlayer player;

    File file;
    String filename;

    @Override
    public void onCreate() {
        super.onCreate();

        file = getOutputFile();
        if (file != null) {
            filename = file.getAbsolutePath();
        }
/*
        AndPermission.with(this)
                .runtime()
                .permission(
                        Permission.RECORD_AUDIO,
                        Permission.READ_EXTERNAL_STORAGE,
                        Permission.WRITE_EXTERNAL_STORAGE)
                .onGranted(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> permissions) {
                        showToast("허용된 권한 갯수 : " + permissions.size());
                    }
                })
                .onDenied(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> permissions) {
                        showToast("거부된 권한 갯수 : " + permissions.size());
                    }
                })
                .start();

*/
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d("LOG", "onStartCommand() called");
        Log.d("LOG", "intent" + intent);

        //showNoti();
        setSensorListener();

        if (intent == null) {
            return Service.START_STICKY;     //서비스가 종료되어도 자동으로 다시 실행시켜줘!
        } else {
            String command = intent.getStringExtra("command");
            String name = intent.getStringExtra("name");
            Log.d("LOG", "전달받은 데이터: " + command+ ", " +name);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public void showNoti(){
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = null;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ));

            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        }else{
            builder = new NotificationCompat.Builder(this);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentTitle("간단 알림");
        builder.setContentText("알림 메세지입니다.");
        builder.setSmallIcon(android.R.drawable.ic_menu_view);
        builder.setAutoCancel(true);
        builder.setContentIntent(pendingIntent);

        Notification noti = builder.build();

        manager.notify(2,noti);
    }

    public void setSensorListener() {

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //조도 센서 세팅
        lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if( lightSensor == null ){
            Log.d("LOG","No Light Sensor Found!");
        }


        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mGgyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSenserLis = new SensorListener();

        mSensorManager.registerListener(mSenserLis, mGgyroSensor,SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSenserLis, lightSensor, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSenserLis, mAccelometerSensor,SensorManager.SENSOR_DELAY_UI);

    }


    public static void unregisterListener() {
        mSensorManager.unregisterListener(mSenserLis);
    }


    private class SensorListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if( event.sensor.getType() == Sensor.TYPE_LIGHT){
                lightValue = event.values[0];
                //Log.d("LOG","조도센서 : " + lightValue );

            }else if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                double accX = event.values[0];
                double accY = event.values[1];
                double accZ = event.values[2];

                double angleXZ = Math.atan2(accX,  accZ) * 180/Math.PI;
                double angleYZ = Math.atan2(accY,  accZ) * 180/Math.PI;

                Log.d("LOG", "ACCELOMETER   [X]:" + String.format("%.1f", event.values[0])
                        + "  [Y]:" + String.format("%.1f", event.values[1])
                        + "  [Z]:" + String.format("%.1f", event.values[2])
                        + "  [angleXZ]: " + String.format("%.1f", angleXZ)
                        + "  [angleYZ]: " + String.format("%.1f", angleYZ));


                if(angleXZ > 170 && lightValue < 20) {
                    Log.d("LOG","뒤집힘 : " + Math.abs(roll*RAD2DGR) + "조도센서 : " + lightValue);
                    startREC();



                }

            }




            /*
            else{

                double gyroX = event.values[0];
                double gyroY = event.values[1];
                double gyroZ = event.values[2];

                dt = (event.timestamp - timestamp) * NS2S;
                timestamp = event.timestamp;


                // 맨 센서 인식을 활성화 하여 처음 timestamp가 0일때는 dt값이 올바르지 않으므로 넘어간다.
                if (dt - timestamp*NS2S != 0) {

                    // 각속도 성분을 적분 -> 회전각(pitch, roll)으로 변환.
                    //  여기까지의 pitch, roll의 단위는 '라디안'이다.
                    //  SO 아래 로그 출력부분에서 멤버변수 'RAD2DGR'를 곱해주어 degree로 변환해줌.
                    pitch = pitch + gyroX*dt;
                    roll = roll + gyroY*dt;
                    yaw = yaw + gyroZ*dt;


                    if(Math.abs(roll * RAD2DGR) > 5) {

                        Log.d("LOG", "GYROSCOPE "
                                + "  [Pitch]:" + String.format("%.1f", pitch * RAD2DGR)
                                + "  [Roll]:" + String.format("%.1f", roll * RAD2DGR)
                                + "  [X]:" + String.format("%.4f", event.values[0])
                                + "  [Y]:" + String.format("%.4f", event.values[1])
                                + "  [Z]:" + String.format("%.4f", event.values[2])
                                + "  [Yaw]:" + String.format("%.1f", yaw * RAD2DGR)
                                + "  [dt]:" + String.format("%.4f", dt));


                    }

                    if(Math.abs(roll*RAD2DGR) > 45 && lightValue < 20){
                        //Log.d("LOG","뒤집힘 : " + Math.abs(roll*RAD2DGR) + "조도센서 : " + lightValue);

                        long[] pattern = {100, 700, 100, 200};
                        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(pattern, -1); //패턴진동, 반복 없음


                        //setAlarmTimer();
                        roll = 0;
                    }

                    if(Math.abs(gyroY) <= 0.001){
                        roll_counter++;
                        //Log.d("LOG","roll_counter:" + roll_counter);
                        if(roll_counter >= 2) {
                            roll = 0;
                            roll_counter = 0;
                        }
                    }
                }

                */



        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public void startREC(){
        //녹음 시작
        long[] pattern = {100, 700, 100, 200};
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(pattern, -1); //패턴진동, 반복 없음
        mSensorManager.unregisterListener(mSenserLis);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent myIntent = new Intent(getApplicationContext(),AlarmRecever.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, 0);

        alarmManager.cancel(pendingIntent);

        //11.25 서비스 안죽음, 아예 죽이는 법을 찾아봐야 함.
    }


    @Override
    public void onDestroy(){
        Log.d("LOG", "onDestroy()");
        super.onDestroy();
        setAlarmTimer();
    }

    AlarmManager mAlarmManager;

    protected void setAlarmTimer() {
        Log.d("LOG", "set alarm");
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.add(Calendar.SECOND, 1);
        Intent intent = new Intent(this, AlarmRecever.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0,intent,0);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        //mAlarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);

        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public File getOutputFile() {
        File mediaFile = null;
        try {
            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "MyApp");
            if (!mediaStorageDir.exists()){
                if (!mediaStorageDir.mkdirs()){
                    Log.d("MyCameraApp", "failed to create directory");
                    return null;
                }
            }

            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "recorded.mp4");
        } catch(Exception e) {
            e.printStackTrace();
        }

        return mediaFile;
    }
}