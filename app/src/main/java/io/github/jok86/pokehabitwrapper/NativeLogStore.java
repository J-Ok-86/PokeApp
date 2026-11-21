package io.github.jok86.pokehabitwrapper;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Native widget writes first; WebView acknowledges IDs only AFTER durable web save. */
public final class NativeLogStore {
    private NativeLogStore() {}
    private static final String NAME="pokehabit_widget_logs_v1";
    private static SharedPreferences prefs(Context ctx){return ctx.getSharedPreferences(NAME,Context.MODE_PRIVATE);}
    private static JSONArray array(String raw){try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}}
    private static String safeUser(String id){return id!=null&&id.matches("[a-zA-Z0-9_-]{2,24}")?id:"";}
    /** Source-of-truth definitions and recent WebView records, scoped to one trainer. */
    public static synchronized void configure(Context ctx,String user,JSONArray definitions,JSONArray records){
        if(safeUser(user).isEmpty()||definitions==null)return;
        JSONArray clean=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<Math.min(definitions.length(),35)&&clean.length()<6;i++){
            JSONObject v=definitions.optJSONObject(i);if(v==null||!v.optBoolean("enabled",true))continue;
            String id=v.optString("id"),title=v.optString("name").trim(),emoji=v.optString("emoji");
            if(!id.matches("[a-zA-Z0-9_-]{2,75}")||title.isEmpty()||ids.contains(id))continue;
            ids.add(id);
            JSONObject o=new JSONObject();try{
                o.put("id",id);o.put("name",title.substring(0,Math.min(22,title.length())));
                o.put("emoji",emoji.substring(0,Math.min(12,emoji.length())));
                o.put("mode","repeat".equals(v.optString("mode"))?"repeat":"once");clean.put(o);
            }catch(JSONException ignored){}
        }
        SharedPreferences p=prefs(ctx);
        String prior=p.getString("user","");
        JSONArray safeRecords=new JSONArray();Set<String> recordIds=new HashSet<>();
        if(records!=null){
            for(int i=0;i<records.length()&&safeRecords.length()<800;i++){
                JSONObject v=records.optJSONObject(i);if(v==null)continue;
                String id=v.optString("id"),event=v.optString("eventId"),day=v.optString("day"),clock=v.optString("clock");
                long stamp=v.optLong("timestampMs",0);
                if(!id.matches("[a-zA-Z0-9_-]{4,100}")||recordIds.contains(id)||
                    !event.matches("[a-zA-Z0-9_-]{2,75}")||!day.matches("\\d{4}-\\d{2}-\\d{2}")||
                    !clock.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||stamp<=0)continue;
                recordIds.add(id);
                JSONObject o=new JSONObject();try{o.put("id",id);o.put("eventId",event);
                    o.put("day",day);o.put("clock",clock);o.put("timestampMs",stamp);
                    if("walk".equals(event))o.put("endClock",v.optString("endClock", ""));safeRecords.put(o);
                }catch(JSONException ignored){}
            }
        }
        // Preserve a walking session under its original trainer before switching profiles.
        if(!prior.isEmpty()&&!prior.equals(user)&&WalkTracker.session(ctx,prior)!=null)WalkTracker.toggle(ctx,prior);
        boolean ok=p.edit().putString("user",user).putString("buttons",clean.toString())
            .putString("records",safeRecords.toString())
            .putString("last",prior.equals(user)?last(ctx):"앱 기록 동기화 완료 · 버튼을 눌러 기록하세요")
            .commit();
        if(ok)TimeLogWidget.refresh(ctx);
    }
    private static String clockFromIso(String stamp){
        try{return new SimpleDateFormat("HH:mm",Locale.US).format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).parse(stamp));}
        catch(Exception e){return "";}
    }
    private static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private static String minusOneDay(){Calendar c=Calendar.getInstance();c.add(Calendar.DATE,-1);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());}
    private static String logicalDay(JSONObject r){
        String d=r.optString("day"),clock=r.optString("clock");
        if(!"sleep".equals(r.optString("eventId"))||clock.length()<2)return d;
        if(Integer.parseInt(clock.substring(0,2))>=6)return d;
        try{SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.US);fmt.setLenient(false);
            Date parsed=fmt.parse(d);Calendar c=Calendar.getInstance();c.setTime(parsed);c.add(Calendar.DATE,-1);
            return fmt.format(c.getTime());
        }catch(Exception e){return d;}
    }
    /** Combines app history with unacknowledged widget presses; never double-counts IDs. */
    public static synchronized JSONObject summary(Context ctx){
        SharedPreferences p=prefs(ctx);String user=activeUser(ctx),today=today();
        String nowClock=new SimpleDateFormat("HH",Locale.US).format(new Date());
        String sleepNight=Integer.parseInt(nowClock)<6?minusOneDay():today;
        JSONArray defs=buttons(ctx),saved=array(p.getString("records","[]")),pending=pending(ctx,user),walkPending=WalkTracker.pending(ctx,user);
        Map<String,JSONObject> unique=new LinkedHashMap<>();
        for(int list=0;list<2;list++){
            JSONArray arr=list==0?saved:pending;
            for(int i=0;i<arr.length();i++){
                JSONObject r=arr.optJSONObject(i);if(r==null)continue;
                String id=r.optString("id");if(!id.isEmpty())unique.put(id,r);
            }
        }
        for(int i=0;i<walkPending.length();i++){
            JSONObject w=walkPending.optJSONObject(i);if(w==null)continue;
            JSONObject row=new JSONObject();try{
                row.put("id",w.optString("id"));row.put("eventId","walk");row.put("day",w.optString("day"));
                row.put("clock",w.optString("clock"));row.put("endClock",clockFromIso(w.optString("endAt")));
                row.put("timestampMs",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).parse(w.optString("endAt")).getTime());
                unique.put(row.optString("id"),row);
            }catch(Exception ignored){}
        }
        JSONObject out=new JSONObject();
        for(int i=0;i<defs.length();i++){
            JSONObject def=defs.optJSONObject(i);if(def==null)continue;
            String key=def.optString("id"),target="sleep".equals(key)?sleepNight:today;
            boolean repeat="repeat".equals(def.optString("mode"));
            int count=0;long latest=Long.MIN_VALUE;String clock="",endClock="";
            for(JSONObject log:unique.values()){
                if(!key.equals(log.optString("eventId"))||!target.equals(logicalDay(log)))continue;
                count++;long when=log.optLong("timestampMs",0);
                if(when>=latest){latest=when;clock=log.optString("clock","");endClock=log.optString("endClock","");}
            }
            JSONObject info=new JSONObject();try{
                info.put("count",repeat?count:(count>0?1:0));info.put("clock",clock);info.put("endClock",endClock);out.put(key,info);
            }catch(JSONException ignored){}
        }
        return out;
    }
    public static synchronized String activeUser(Context ctx){return prefs(ctx).getString("user","");}
    public static synchronized JSONArray buttons(Context ctx){return array(prefs(ctx).getString("buttons","[]"));}
    public static synchronized String last(Context ctx){return prefs(ctx).getString("last", "버튼을 누르면 현재 시각이 기록됩니다");}
    public static synchronized boolean record(Context ctx,String eventId){
        String user=activeUser(ctx);if(safeUser(user).isEmpty())return false;
        JSONObject button=null;JSONArray defs=buttons(ctx);
        for(int i=0;i<defs.length();i++){JSONObject d=defs.optJSONObject(i);if(d!=null&&eventId.equals(d.optString("id"))){button=d;break;}}
        if(button==null)return false;
        Date now=new Date();String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now);
        String clock=new SimpleDateFormat("HH:mm",Locale.US).format(now);
        JSONArray all=array(prefs(ctx).getString("pending","[]"));
        // Prevent accidental double taps; once-mode existing log is still editable inside PokeHabit.
        if(all.length()>0){JSONObject recent=all.optJSONObject(all.length()-1);
            if(recent!=null&&user.equals(recent.optString("userId"))&&eventId.equals(recent.optString("eventId"))&&now.getTime()-recent.optLong("timestampMs")<3000)return false;}
        JSONObject log=new JSONObject();
        try{log.put("id","native_"+UUID.randomUUID().toString().replace("-",""));log.put("eventId",eventId);
            log.put("userId",user);log.put("day",day);log.put("clock",clock);
            log.put("at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(now));
            log.put("timestampMs",now.getTime());log.put("name",button.optString("name"));
            log.put("emoji",button.optString("emoji"));log.put("mode",button.optString("mode"));
            all.put(log);}catch(JSONException e){return false;}
        // Queue is durable before we show success. Do not remove any unacknowledged records.
        boolean committed=prefs(ctx).edit().putString("pending",all.toString()).putString("last",button.optString("emoji")+" "+button.optString("name")+" · "+clock+" ✓").commit();
        return committed;
    }
    public static synchronized JSONArray pending(Context ctx,String user){
        JSONArray out=new JSONArray(),all=array(prefs(ctx).getString("pending","[]"));
        for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&user.equals(o.optString("userId")))out.put(o);}
        return out;
    }
    public static synchronized void acknowledge(Context ctx,String user,JSONArray ids){
        Set<String> ack=new HashSet<>();for(int i=0;i<ids.length();i++)ack.add(ids.optString(i));
        JSONArray src=array(prefs(ctx).getString("pending","[]")),remaining=new JSONArray();
        for(int i=0;i<src.length();i++){JSONObject o=src.optJSONObject(i);
            if(o!=null&&!(user.equals(o.optString("userId"))&&ack.contains(o.optString("id"))))remaining.put(o);}
        prefs(ctx).edit().putString("pending",remaining.toString()).apply();
    }
}
