package io.github.jok86.pokehabitwrapper;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** All schedules belong to the last signed-in user on this physical device. */
public final class AlarmStore {
    private AlarmStore() {}
    private static final String PREFS = "pokehabit_native_alarms_v1";
    private static final String KEY_ITEMS = "active_items";
    private static final String KEY_USER = "active_user";
    private static final String CHANNEL = "pokehabit_daily_reminders";

    public static boolean notificationsGranted(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean exactGranted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return mgr != null && mgr.canScheduleExactAlarms();
    }
    public static void ensureChannel(Context context) {
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "PokeHabit 개인 알림", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("시간·내용으로 설정한 매일 반복 개인 알림");
            mgr.createNotificationChannel(channel);
        }
    }
    private static SharedPreferences prefs(Context context) {return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);}
    private static JSONArray read(Context context) {
        try {return new JSONArray(prefs(context).getString(KEY_ITEMS, "[]"));}
        catch (JSONException ignored) {return new JSONArray();}
    }
    private static JSONObject find(Context context, String id) {
        JSONArray array = read(context);
        for (int i=0;i<array.length();i++) {
            JSONObject o=array.optJSONObject(i);
            if (o!=null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
    private static PendingIntent pending(Context context, String id, boolean create) {
        Intent intent=new Intent(context, NotificationReceiver.class);
        intent.setAction("io.github.jok86.pokehabitwrapper.DAILY_REMINDER");
        intent.setData(Uri.parse("pokehabit-alarm://daily/"+Uri.encode(id)));
        intent.putExtra("reminder_id", id);
        int flags=PendingIntent.FLAG_IMMUTABLE | (create? PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
    private static void cancel(Context context, String id) {
        AlarmManager mgr=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=pending(context,id,false);
        if (mgr!=null && pi!=null) mgr.cancel(pi);
        if (pi!=null) pi.cancel();
    }
    private static long nextTime(String hhmm) {
        int hour=Integer.parseInt(hhmm.substring(0,2)),minute=Integer.parseInt(hhmm.substring(3,5));
        Calendar at=Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY,hour);at.set(Calendar.MINUTE,minute);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);
        if(at.getTimeInMillis()<=System.currentTimeMillis()+1000)at.add(Calendar.DAY_OF_YEAR,1);
        return at.getTimeInMillis();
    }
    private static void schedule(Context context, JSONObject item) {
        if (item==null || !item.optBoolean("enabled",true))return;
        String id=item.optString("id"),time=item.optString("time");
        if(!id.matches("[a-zA-Z0-9_-]{4,90}")||!time.matches("([01]\\d|2[0-3]):[0-5]\\d"))return;
        AlarmManager mgr=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(mgr==null)return;
        PendingIntent pi=pending(context,id,true);
        long timeMs=nextTime(time);
        try{
            if(exactGranted(context))mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timeMs,pi);
            else mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timeMs,pi);
        }catch(SecurityException ex){mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timeMs,pi);}
    }
    public static synchronized int replace(Context context, String user, JSONArray fromWeb) {
        JSONArray old=read(context);
        for(int i=0;i<old.length();i++){JSONObject r=old.optJSONObject(i);if(r!=null)cancel(context,r.optString("id"));}
        JSONArray valid=new JSONArray(); Set<String> seen=new HashSet<>();
        for(int i=0;i<Math.min(fromWeb.length(),100);i++){
            JSONObject r=fromWeb.optJSONObject(i);if(r==null)continue;
            String id=r.optString("id"),time=r.optString("time"),title=r.optString("title").trim();
            if(!id.matches("[a-zA-Z0-9_-]{4,90}")||!time.matches("([01]\\d|2[0-3]):[0-5]\\d")||title.isEmpty()||seen.contains(id))continue;
            seen.add(id);
            JSONObject clean=new JSONObject();
            try {clean.put("id",id);clean.put("time",time);clean.put("title",title.substring(0,Math.min(80,title.length())));clean.put("enabled",r.optBoolean("enabled",true));valid.put(clean);}catch(JSONException ignored){}
        }
        // If an existing reminder time changes, the new time is a fresh schedule.
        SharedPreferences.Editor editor=prefs(context).edit();
        for(int i=0;i<old.length();i++){
            JSONObject previous=old.optJSONObject(i);if(previous==null)continue;
            String id=previous.optString("id");
            JSONObject after=null;
            for(int j=0;j<valid.length();j++){
                JSONObject candidate=valid.optJSONObject(j);
                if(candidate!=null&&id.equals(candidate.optString("id"))){after=candidate;break;}
            }
            if(after==null||!previous.optString("time").equals(after.optString("time")))editor.remove("fired_"+id);
        }
        // This device intentionally schedules reminders for one active trainer only.
        editor.putString(KEY_USER,user).putString(KEY_ITEMS,valid.toString()).commit();
        for(int i=0;i<valid.length();i++)schedule(context,valid.optJSONObject(i));
        return valid.length();
    }
    public static synchronized void rescheduleAll(Context context) {
        JSONArray array=read(context);
        for(int i=0;i<array.length();i++){
            JSONObject o=array.optJSONObject(i);
            if(o!=null){cancel(context,o.optString("id"));schedule(context,o);}
        }
    }
    public static synchronized void fired(Context context, String id) {
        JSONObject item=find(context,id);if(item==null||!item.optBoolean("enabled",true))return;
        // Re-arm the next local day even if notifications were denied.
        schedule(context,item);
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        String lastKey="fired_"+id;
        SharedPreferences p=prefs(context);
        if(date.equals(p.getString(lastKey,"")))return;
        p.edit().putString(lastKey,date).apply();
        if(!notificationsGranted(context))return;
        ensureChannel(context);
        NotificationManager mgr=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null)return;
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("🔔 "+item.optString("title","PokeHabit 개인 알림"))
            .setContentText("설정한 개인 알림 시간입니다.").setAutoCancel(true).setContentIntent(tap)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        mgr.notify(id.hashCode(),notice);
    }
    public static void test(Context context) {
        if(!notificationsGranted(context))return;
        ensureChannel(context);
        NotificationManager mgr=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null)return;
        Notification notice=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 PokeHabit 테스트 알림").setContentText("Android 알림 연결이 정상적으로 동작합니다.")
            .setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build();
        mgr.notify(1234567,notice);
    }
    public static String status(Context context) {
        if(!notificationsGranted(context))return "Android 알림 권한이 꺼져 있음 · 권한 요청을 눌러 주세요";
        if(!exactGranted(context))return "알림 허용됨 · 정확한 알람 권한 미허용(알림이 지연될 수 있음)";
        return "Android 알림·정확한 알람 권한 허용됨";
    }
}
