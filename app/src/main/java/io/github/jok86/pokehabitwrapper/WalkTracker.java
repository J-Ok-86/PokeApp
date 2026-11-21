package io.github.jok86.pokehabitwrapper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** One manual start/stop walking session per device; only the APK reads the step counter. */
public final class WalkTracker {
    private static final String PREF="pokehabit_walk_v1";
    private WalkTracker(){}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static JSONArray arr(String json){try{return new JSONArray(json);}catch(Exception e){return new JSONArray();}}
    private static String clock(long stamp){return new SimpleDateFormat("HH:mm",Locale.US).format(new Date(stamp));}
    private static String day(long stamp){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(stamp));}
    private static String iso(long stamp){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date(stamp));}
    public static boolean permissionGranted(Context c){
        return android.os.Build.VERSION.SDK_INT<29 ||
            c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED;
    }
    public static boolean stepSensorSupported(Context c){
        SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        return sm!=null&&sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null;
    }
    public static boolean sensorAvailable(Context c){return permissionGranted(c)&&stepSensorSupported(c);}
    public static synchronized JSONObject session(Context c,String user){
        SharedPreferences pref=p(c);if(!user.equals(pref.getString("user",""))||!pref.getBoolean("running",false))return null;
        JSONObject out=new JSONObject();long at=pref.getLong("startMs",0);
        try{out.put("userId",user);out.put("eventId","walk");out.put("startAt",iso(at));out.put("day",day(at));out.put("clock",clock(at));
            if(pref.getInt("steps",-1)>=0)out.put("steps",pref.getInt("steps",-1));}catch(JSONException ignored){}
        return out;
    }
    public static synchronized void restoreSensor(Context c){
        if(p(c).getBoolean("running",false)&&sensorAvailable(c))startService(c);
    }
    public static synchronized boolean toggle(Context c,String user){
        if(user==null||!user.matches("[a-zA-Z0-9_-]{2,24}"))return false;
        if(!user.equals(NativeLogStore.activeUser(c)))return false;
        SharedPreferences pref=p(c);
        if(pref.getBoolean("running",false)){
            if(!user.equals(pref.getString("user","")))return false;
            return finish(c,user);
        }
        long now=System.currentTimeMillis();
        boolean ok=pref.edit().putString("user",user).putBoolean("running",true).putLong("startMs",now)
            .putFloat("base",-1).putFloat("last",-1).putInt("steps",-1).putInt("baseTotal",0).commit();
        if(ok&&sensorAvailable(c))startService(c);
        return ok;
    }
    private static void startService(Context c){
        try{c.startForegroundService(new Intent(c,WalkService.class));}
        catch(Exception ignored){/* Clock-based session remains available even without step collection. */}
    }
    private static boolean finish(Context c,String user){
        SharedPreferences pref=p(c);long start=pref.getLong("startMs",0),now=System.currentTimeMillis();
        if(start<=0||now<start)return false;
        JSONObject entry=new JSONObject();try{
            entry.put("id","walk_"+UUID.randomUUID().toString().replace("-",""));entry.put("userId",user);
            entry.put("eventId","walk");entry.put("day",day(start));entry.put("clock",clock(start));
            entry.put("startAt",iso(start));entry.put("endAt",iso(now));
            int steps=pref.getInt("steps",-1);if(steps>=0)entry.put("steps",steps);
        }catch(JSONException e){return false;}
        JSONArray pending=arr(pref.getString("pending","[]"));pending.put(entry);
        boolean ok=pref.edit().putString("pending",pending.toString()).putBoolean("running",false).commit();
        if(ok){try{c.stopService(new Intent(c,WalkService.class));}catch(Exception ignored){}}
        return ok;
    }
    /** Sensor.TYPE_STEP_COUNTER is since boot, NOT steps per session; store a session baseline. */
    public static synchronized void sensorStep(Context c,float value){
        if(!Float.isFinite(value)||value<0)return;
        SharedPreferences pref=p(c);if(!pref.getBoolean("running",false))return;
        float base=pref.getFloat("base",-1),last=pref.getFloat("last",-1);
        int baseTotal=pref.getInt("baseTotal",0);
        if(base<0){base=value;}
        else if(last>=0&&value<last){baseTotal=Math.max(0,pref.getInt("steps",0));base=value;}
        int steps=Math.max(0,baseTotal+Math.round(value-base));
        pref.edit().putFloat("base",base).putFloat("last",value).putInt("baseTotal",baseTotal).putInt("steps",steps).apply();
    }
    public static synchronized JSONArray pending(Context c,String user){
        JSONArray out=new JSONArray(),all=arr(p(c).getString("pending","[]"));
        for(int i=0;i<all.length();i++){JSONObject x=all.optJSONObject(i);if(x!=null&&user.equals(x.optString("userId")))out.put(x);}
        return out;
    }
    public static synchronized void acknowledge(Context c,String user,JSONArray ids){
        Set<String> set=new HashSet<>();for(int i=0;i<ids.length();i++)set.add(ids.optString(i));
        JSONArray next=new JSONArray(),all=arr(p(c).getString("pending","[]"));
        for(int i=0;i<all.length();i++){JSONObject x=all.optJSONObject(i);
            if(x!=null&&!(user.equals(x.optString("userId"))&&set.contains(x.optString("id"))))next.put(x);}
        p(c).edit().putString("pending",next.toString()).apply();
    }
}
