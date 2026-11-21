package io.github.jok86.pokehabitwrapper;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    public static synchronized void configure(Context ctx,String user,JSONArray definitions){
        if(safeUser(user).isEmpty())return;
        JSONArray clean=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<Math.min(definitions.length(),35);i++){
            JSONObject v=definitions.optJSONObject(i);if(v==null||!v.optBoolean("enabled",true))continue;
            String id=v.optString("id"),title=v.optString("name").trim(),emoji=v.optString("emoji");
            if(!id.matches("[a-zA-Z0-9_-]{2,75}")||title.isEmpty()||ids.contains(id))continue;
            ids.add(id);
            JSONObject o=new JSONObject();try{o.put("id",id);o.put("name",title.substring(0,Math.min(22,title.length())));
                o.put("emoji",emoji.substring(0,Math.min(12,emoji.length())));o.put("mode","repeat".equals(v.optString("mode"))?"repeat":"once");clean.put(o);}catch(JSONException ignored){}
        }
        prefs(ctx).edit().putString("user",user).putString("buttons",clean.toString()).apply();
        TimeLogWidget.refresh(ctx);
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
