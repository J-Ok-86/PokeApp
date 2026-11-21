package io.github.jok86.pokehabitwrapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

/** A foreground, user-started sensor session. Stopping is ALWAYS manual, also via widget. */
public final class WalkService extends Service implements SensorEventListener {
    private SensorManager manager;
    private static final String CHANNEL="pokehabit_walking",NAME="걷기 측정";
    private static final int NOTIFICATION=8107;
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String user=NativeLogStore.activeUser(this);
        if(WalkTracker.session(this,user)==null||!WalkTracker.sensorAvailable(this)){stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,NAME,NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CHANNEL).setContentTitle("PokeHabit · 걷기 진행 중")
            .setContentText("라이프 이벤트 또는 위젯에서 걷기를 눌러 종료하세요")
            .setSmallIcon(R.drawable.app_icon).setContentIntent(pi).setOngoing(true).build();
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            else startForeground(NOTIFICATION,n);
        }catch(Exception e){stopSelf();return START_NOT_STICKY;}
        manager=(SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(sensor==null||!manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL)){
            stopSelf();return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    @Override public void onSensorChanged(SensorEvent event){
        if(event.sensor.getType()==Sensor.TYPE_STEP_COUNTER&&event.values.length>0){
            WalkTracker.sensorStep(this,event.values[0]);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onDestroy(){if(manager!=null)manager.unregisterListener(this);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
