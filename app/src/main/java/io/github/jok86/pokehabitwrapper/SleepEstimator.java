package io.github.jok86.pokehabitwrapper;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Inference only: inactivity is NOT proof that someone was asleep. */
public final class SleepEstimator {
 private SleepEstimator(){}
 public static boolean permission(Context ctx){
     AppOpsManager ops=(AppOpsManager)ctx.getSystemService(Context.APP_OPS_SERVICE);
     if(ops==null)return false;
     int mode=ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),ctx.getPackageName());
     return mode==AppOpsManager.MODE_ALLOWED;
 }
 public static Intent permissionSettings(Context ctx){
    Intent settings=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    settings.setData(Uri.parse("package:"+ctx.getPackageName()));
    return settings;
 }
 public static JSONArray estimates(Context ctx){
     JSONArray result=new JSONArray();if(!permission(ctx))return result;
     UsageStatsManager manager=(UsageStatsManager)ctx.getSystemService(Context.USAGE_STATS_SERVICE);
     if(manager==null)return result;
     long now=System.currentTimeMillis();
     Calendar start=Calendar.getInstance();start.add(Calendar.DAY_OF_YEAR,-9);
     List<Long> interactions=new ArrayList<>();
     try{
        UsageEvents events=manager.queryEvents(start.getTimeInMillis(),now);
        if(events==null)return result;
        UsageEvents.Event e=new UsageEvents.Event();
        while(events.hasNextEvent()){
            events.getNextEvent(e);
            int type=e.getEventType();
            // Screen on alone can be caused by notifications; require unlock or actual foreground activity.
            if(type==UsageEvents.Event.KEYGUARD_HIDDEN||type==UsageEvents.Event.ACTIVITY_RESUMED){
               if(interactions.isEmpty()||e.getTimeStamp()-interactions.get(interactions.size()-1)>60_000L)
                   interactions.add(e.getTimeStamp());
            }
        }
     }catch(Exception ex){return result;}
     // Each morning receives at most one strongest candidate based on a night-time inactivity gap.
     for(int back=0;back<8;back++){
        Calendar morning=Calendar.getInstance();morning.set(Calendar.HOUR_OF_DAY,0);morning.set(Calendar.MINUTE,0);
        morning.set(Calendar.SECOND,0);morning.set(Calendar.MILLISECOND,0);morning.add(Calendar.DAY_OF_YEAR,-back);
        long midnight=morning.getTimeInMillis();
        long rangeStart=midnight-8*60*60*1000L,rangeEnd=Math.min(now,midnight+12*60*60*1000L);
        long bestStart=0,bestEnd=0,bestDuration=0;
        for(int i=1;i<interactions.size();i++){
            long prev=interactions.get(i-1),next=interactions.get(i);
            if(prev<rangeStart||prev>midnight+4*60*60*1000L||next<midnight+4*60*60*1000L||next>rangeEnd)continue;
            long gap=next-prev;
            if(gap>=3*60*60*1000L&&gap<=13*60*60*1000L&&gap>bestDuration){bestDuration=gap;bestStart=prev;bestEnd=next;}
        }
        if(bestDuration==0)continue;
        Calendar night=Calendar.getInstance();night.setTimeInMillis(midnight);night.add(Calendar.DAY_OF_YEAR,-1);
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.US),time=new SimpleDateFormat("HH:mm",Locale.US);
        JSONObject estimate=new JSONObject();
        try{estimate.put("night",date.format(night.getTime()));estimate.put("morning",date.format(new Date(midnight)));
            estimate.put("bed",time.format(new Date(bestStart)));estimate.put("wake",time.format(new Date(bestEnd)));
            estimate.put("gapMinutes",Math.round(bestDuration/60000f));estimate.put("source","device_usage");result.put(estimate);}catch(Exception ignored){}
     }
     return result;
 }
}
