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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Explicit per-user reminder planner; each PendingIntent is rearmed to the next matching date. */
public final class AlarmStore {
 private AlarmStore(){}
 private static final String PREFS="pokehabit_native_alarms_v1",ITEMS="active_items",USER="active_user",CHANNEL="pokehabit_daily_reminders";
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 private static JSONArray read(Context c){try{return new JSONArray(prefs(c).getString(ITEMS,"[]"));}catch(Exception e){return new JSONArray();}}
 public static boolean notificationsGranted(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
 public static boolean exactGranted(Context c){if(Build.VERSION.SDK_INT<31)return true;AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);return a!=null&&a.canScheduleExactAlarms();}
 public static void ensureChannel(Context c){NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null){NotificationChannel ch=new NotificationChannel(CHANNEL,"PokeHabit 개인·퀘스트 알림",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("요일별·일회성·퀘스트 알림");m.createNotificationChannel(ch);}}
 private static JSONObject find(Context c,String id){JSONArray a=read(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
 private static PendingIntent pending(Context c,String id,boolean create){Intent it=new Intent(c,NotificationReceiver.class);it.setAction("io.github.jok86.pokehabitwrapper.REMINDER");it.setData(Uri.parse("pokehabit-alarm://scheduled/"+Uri.encode(id)));it.putExtra("reminder_id",id);return PendingIntent.getBroadcast(c,0,it,PendingIntent.FLAG_IMMUTABLE|(create?PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_NO_CREATE));}
 private static void cancel(Context c,String id){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent p=pending(c,id,false);if(a!=null&&p!=null)a.cancel(p);if(p!=null)p.cancel();}
 private static boolean validDate(String date){if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))return false;try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);return f.parse(date)!=null;}catch(Exception e){return false;}}
 private static boolean matches(JSONObject item,Calendar day){String mode=item.optString("repeat","daily");int w=day.get(Calendar.DAY_OF_WEEK),dow=w==Calendar.SUNDAY?0:w-1;
    if("once".equals(mode))return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(day.getTime()).equals(item.optString("date"));
    if("weekdays".equals(mode))return dow>=1&&dow<=5;
    if("weekends".equals(mode))return dow==0||dow==6;
    if("weekly".equals(mode))return dow==item.optInt("weekday",1);
    if("custom".equals(mode)){JSONArray days=item.optJSONArray("days");if(days==null)return false;for(int i=0;i<days.length();i++)if(days.optInt(i,-1)==dow)return true;return false;}
    return "daily".equals(mode);
 }
 private static long next(JSONObject item){String hm=item.optString("time");if(!hm.matches("([01]\\d|2[0-3]):[0-5]\\d"))return -1;
    Calendar day=Calendar.getInstance();day.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm.substring(0,2)));
    day.set(Calendar.MINUTE,Integer.parseInt(hm.substring(3,5)));day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
    for(int delta=0;delta<=370;delta++,day.add(Calendar.DAY_OF_YEAR,1))if(day.getTimeInMillis()>System.currentTimeMillis()+1000&&matches(item,day))return day.getTimeInMillis();
    return -1;
 }
 private static void schedule(Context c,JSONObject item){if(item==null||!item.optBoolean("enabled",true))return;
    String id=item.optString("id");if(!id.matches("[a-zA-Z0-9_-]{4,90}"))return;
    long when=next(item);if(when<0)return;
    AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a==null)return;
    PendingIntent pi=pending(c,id,true);
    try{if(exactGranted(c))a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}
    catch(SecurityException e){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}
 }
 public static synchronized int replace(Context c,String user,JSONArray incoming){
    JSONArray old=read(c);for(int i=0;i<old.length();i++){JSONObject r=old.optJSONObject(i);if(r!=null)cancel(c,r.optString("id"));}
    JSONArray valid=new JSONArray();Set<String> ids=new HashSet<>();
    for(int i=0;i<Math.min(incoming.length(),150);i++){
        JSONObject r=incoming.optJSONObject(i);if(r==null)continue;
        String id=r.optString("id"),time=r.optString("time"),title=r.optString("title").trim(),repeat=r.optString("repeat","daily");
        if(!id.matches("[a-zA-Z0-9_-]{4,90}")||!time.matches("([01]\\d|2[0-3]):[0-5]\\d")||title.isEmpty()||ids.contains(id))continue;
        if(!repeat.matches("daily|weekdays|weekends|weekly|custom|once"))repeat="daily";
        if("once".equals(repeat)&&!validDate(r.optString("date")))continue;
        ids.add(id);JSONObject clean=new JSONObject();
        try{clean.put("id",id);clean.put("time",time);clean.put("title",title.substring(0,Math.min(80,title.length())));
           clean.put("enabled",r.optBoolean("enabled",true));clean.put("repeat",repeat);
           clean.put("date",r.optString("date"));clean.put("questId",r.optString("questId"));
           clean.put("weekday",Math.max(0,Math.min(6,r.optInt("weekday",1))));
           JSONArray days=new JSONArray();JSONArray input=r.optJSONArray("days");if(input!=null)for(int d=0;d<input.length();d++){int n=input.optInt(d,-1);if(n>=0&&n<=6)days.put(n);}clean.put("days",days);
           valid.put(clean);}catch(JSONException ignored){}
    }
    SharedPreferences.Editor edit=prefs(c).edit();
    for(int i=0;i<old.length();i++){JSONObject prev=old.optJSONObject(i);if(prev==null)continue;
        JSONObject after=null;for(int j=0;j<valid.length();j++){JSONObject other=valid.optJSONObject(j);if(other!=null&&prev.optString("id").equals(other.optString("id"))){after=other;break;}}
        if(after==null||!prev.toString().equals(after.toString()))edit.remove("fired_"+prev.optString("id"));
    }
    edit.putString(USER,user).putString(ITEMS,valid.toString()).commit();
    for(int i=0;i<valid.length();i++)schedule(c,valid.optJSONObject(i));
    return valid.length();
 }
 public static synchronized void rescheduleAll(Context c){JSONArray a=read(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){cancel(c,o.optString("id"));schedule(c,o);}}}
 public static synchronized void fired(Context c,String id){JSONObject r=find(c,id);if(r==null||!r.optBoolean("enabled",true))return;
    // Triggered time is today; only rearm a next matching date. A one-off never repeats.
    if(!"once".equals(r.optString("repeat","daily")))schedule(c,r);
    String date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
    // Reject stale one-off PendingIntent after its specified day.
    if("once".equals(r.optString("repeat"))&&!date.equals(r.optString("date")))return;
    SharedPreferences p=prefs(c);String key="fired_"+id;if(date.equals(p.getString(key,"")))return;
    p.edit().putString(key,date).apply();if(!notificationsGranted(c))return;
    ensureChannel(c);NotificationManager mgr=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(mgr==null)return;
    Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    String sub=r.optString("questId").isEmpty()?"설정한 알림 시간입니다.":"퀘스트를 시작할 시간이에요.";
    Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
       .setContentTitle("🔔 "+r.optString("title","PokeHabit 알림")).setContentText(sub)
       .setAutoCancel(true).setContentIntent(tap).setCategory(Notification.CATEGORY_REMINDER)
       .setVisibility(Notification.VISIBILITY_PRIVATE).build();mgr.notify(id.hashCode(),n);
 }
 public static void test(Context c){if(!notificationsGranted(c))return;ensureChannel(c);
    NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.notify(1234567,
    new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("🔔 PokeHabit 테스트 알림")
       .setContentText("Android 알림 연결이 정상적으로 동작합니다.").setAutoCancel(true).build());}
 public static String status(Context c){if(!notificationsGranted(c))return "Android 알림 권한이 꺼져 있음 · 권한 요청을 눌러 주세요";
     return exactGranted(c)?"Android 알림·정확한 알람 권한 허용됨":"알림 허용됨 · 정확한 알람 권한 미허용(지연 가능)";}
}
