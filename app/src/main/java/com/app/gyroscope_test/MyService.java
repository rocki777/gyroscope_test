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
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class MyService extends Service {


    NotificationManager manager;

    private static String CHANNEL_ID = "channel1";
    private static String CHANNEL_NAME = "Channel1";


    //Using the Accelometer & Gyroscoper
    private static SensorManager mSensorManager = null;

    //Using the Gyroscope
    private static SensorEventListener mGyroLis;
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

    @Override
    public void onCreate() {
        super.onCreate();
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
        //Using the Gyroscope & Accelometer
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //Using the Accelometer
        mGgyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGyroLis = new GyroscopeListener();

        //mSensorManager.registerListener(mGyroLis,mGgyroSensor,SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mGyroLis,mGgyroSensor,SensorManager.SENSOR_DELAY_UI);

    }


    public static void unregisterListener() {
        mSensorManager.unregisterListener(mGyroLis);
    }


    private class GyroscopeListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {

            double gyroX = event.values[0];
            double gyroY = event.values[1];
            double gyroZ = event.values[2];

            dt = (event.timestamp - timestamp) * NS2S;
            timestamp = event.timestamp;

            /* 맨 센서 인식을 활성화 하여 처음 timestamp가 0일때는 dt값이 올바르지 않으므로 넘어간다. */
            if (dt - timestamp*NS2S != 0) {

                /* 각속도 성분을 적분 -> 회전각(pitch, roll)으로 변환.
                 * 여기까지의 pitch, roll의 단위는 '라디안'이다.
                 * SO 아래 로그 출력부분에서 멤버변수 'RAD2DGR'를 곱해주어 degree로 변환해줌.  */
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

                if(Math.abs(roll*RAD2DGR) > 45){
                    Log.d("LOG","뒤집힘 : " + Math.abs(roll*RAD2DGR));
                    //Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    //vibrator.vibrate(100); // 0.5초간 진동

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
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }




    @Override
    public void onDestroy(){
        Log.d("LOG", "onDestroy()");
        super.onDestroy();
        setAlarmTimer();
    }


    protected void setAlarmTimer() {
        Log.d("LOG", "set alarm");
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.add(Calendar.SECOND, 1);
        Intent intent = new Intent(this, AlarmRecever.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0,intent,0);

        AlarmManager mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        //mAlarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);

        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}