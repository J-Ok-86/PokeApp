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
     // Consider a long overnight usage gap and allow one SHORT cluster of night-time
     // interactions (e.g. waking to check the time). A phone may still be unused
     // while its owner is awake: all outputs remain explicitly unconfirmed guesses.
     for(int back=0;back<8;back++){
        Calendar morning=Calendar.getInstance();morning.set(Calendar.HOUR_OF_DAY,0);morning.set(Calendar.MINUTE,0);
        morning.set(Calendar.SECOND,0);morning.set(Calendar.MILLISECOND,0);morning.add(Calendar.DAY_OF_YEAR,-back);
        long midnight=morning.getTimeInMillis();
        long rangeStart=midnight-6L*3600_000L,rangeEnd=Math.min(now,midnight+12L*3600_000L);
        List<Long> night=new ArrayList<>();
        for(Long t:interactions)if(t>=rangeStart&&t<=rangeEnd)night.add(t);
        long bestStart=0,bestEnd=0,bestDuration=0,bestScore=-1;
        int bestInterrupted=0;
        for(int i=0;i<night.size()-1;i++){
            long prev=night.get(i);
            if(prev>midnight+4L*3600_000L)break;
            for(int j=i+1;j<night.size();j++){
                long next=night.get(j),gap=next-prev;
                // A 13-hour-plus phone-free period is not reliably a sleep interval.
                if(gap>13L*3600_000L)break;
                if(gap<3L*3600_000L)continue;
                if(next<midnight+4L*3600_000L||next>rangeEnd)continue;
                int interruptions=j-i-1;
                if(interruptions>0){
                    // At most one short wake-up cluster, no more than 20 min long;
                    // each side must have at least 90 min of phone inactivity.
                    long first=night.get(i+1),last=night.get(j-1);
                    if(first-prev<90L*60_000L||next-last<90L*60_000L)continue;
                    if(last-first>20L*60_000L)break;
                    if(interruptions>8)break;
                }
                // Favor clean long gaps over bridged ones of similar duration.
                long score=gap-(interruptions>0?30L*60_000L:0);
                if(score>bestScore){bestScore=score;bestDuration=gap;bestStart=prev;bestEnd=next;bestInterrupted=interruptions;}
            }
        }
        if(bestDuration==0)continue;
        Calendar bedtime=Calendar.getInstance();bedtime.setTimeInMillis(bestStart);
        Calendar wakeTime=Calendar.getInstance();wakeTime.setTimeInMillis(bestEnd);
        int bedHour=bedtime.get(Calendar.HOUR_OF_DAY),wakeHour=wakeTime.get(Calendar.HOUR_OF_DAY);
        boolean plausibleBed=bedHour>=20||bedHour<=3,plausibleWake=wakeHour>=5&&wakeHour<=10;
        String confidence=bestDuration>=5L*3600_000L&&bestDuration<=10L*3600_000L&&plausibleBed&&plausibleWake&&bestInterrupted==0?"high":
            bestDuration>=4L*3600_000L&&plausibleBed&&plausibleWake?"medium":"low";
        Calendar nightDate=Calendar.getInstance();nightDate.setTimeInMillis(midnight);nightDate.add(Calendar.DAY_OF_YEAR,-1);
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.US),time=new SimpleDateFormat("HH:mm",Locale.US);
        JSONObject estimate=new JSONObject();
        try{estimate.put("night",date.format(nightDate.getTime()));estimate.put("morning",date.format(new Date(midnight)));
            estimate.put("bed",time.format(new Date(bestStart)));estimate.put("wake",time.format(new Date(bestEnd)));
            estimate.put("gapMinutes",Math.round(bestDuration/60000f));estimate.put("source","device_usage");
            estimate.put("confidence",confidence);estimate.put("nightInteractions",bestInterrupted);
            result.put(estimate);}catch(Exception ignored){}
     }
     return result;
 }
}
