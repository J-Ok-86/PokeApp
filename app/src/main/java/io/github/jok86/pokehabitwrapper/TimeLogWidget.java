package io.github.jok86.pokehabitwrapper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/** Six-button (3 x 2) widget with each event's in-app time and count. */
public final class TimeLogWidget extends AppWidgetProvider {
    private static final String ACTION="io.github.jok86.pokehabitwrapper.WIDGET_LOG";
    private static final int[] CELLS={R.id.widget_button_1,R.id.widget_button_2,R.id.widget_button_3,
        R.id.widget_button_4,R.id.widget_button_5,R.id.widget_button_6};
    private static final int[] EMOJIS={R.id.widget_emoji_1,R.id.widget_emoji_2,R.id.widget_emoji_3,
        R.id.widget_emoji_4,R.id.widget_emoji_5,R.id.widget_emoji_6};
    private static final int[] COUNTS={R.id.widget_count_1,R.id.widget_count_2,R.id.widget_count_3,
        R.id.widget_count_4,R.id.widget_count_5,R.id.widget_count_6};
    private static final int[] NAMES={R.id.widget_name_1,R.id.widget_name_2,R.id.widget_name_3,
        R.id.widget_name_4,R.id.widget_name_5,R.id.widget_name_6};
    private static final int[] TIMES={R.id.widget_time_1,R.id.widget_time_2,R.id.widget_time_3,
        R.id.widget_time_4,R.id.widget_time_5,R.id.widget_time_6};
    @Override public void onReceive(Context ctx,Intent i){
        if(i!=null&&ACTION.equals(i.getAction())){
            String id=i.getStringExtra("event_id");
            if(id!=null)NativeLogStore.record(ctx,id);
            refresh(ctx);
            return;
        }
        super.onReceive(ctx,i);
        if(i!=null&&(Intent.ACTION_DATE_CHANGED.equals(i.getAction()) ||
            Intent.ACTION_TIME_CHANGED.equals(i.getAction()) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())))refresh(ctx);
    }
    @Override public void onUpdate(Context ctx,AppWidgetManager manager,int[] ids){refresh(ctx);}
    @Override public void onAppWidgetOptionsChanged(Context ctx,AppWidgetManager manager,int widgetId,android.os.Bundle options){refresh(ctx);}
    public static void refresh(Context ctx){
        AppWidgetManager mgr=AppWidgetManager.getInstance(ctx);if(mgr==null)return;
        int[] ids=mgr.getAppWidgetIds(new ComponentName(ctx,TimeLogWidget.class));if(ids==null)return;
        JSONArray defs=NativeLogStore.buttons(ctx);
        JSONObject summary=NativeLogStore.summary(ctx);
        for(int widgetId:ids){
            RemoteViews v=new RemoteViews(ctx.getPackageName(),R.layout.time_log_widget);
            v.setTextViewText(R.id.widget_last,NativeLogStore.last(ctx));
            v.setViewVisibility(R.id.widget_row_2,defs.length()>3?View.VISIBLE:View.GONE);
            for(int idx=0;idx<6;idx++){
                JSONObject d=idx<defs.length()?defs.optJSONObject(idx):null;
                if(d==null){v.setViewVisibility(CELLS[idx],View.INVISIBLE);continue;}
                v.setViewVisibility(CELLS[idx],View.VISIBLE);
                String key=d.optString("id");
                JSONObject info=summary.optJSONObject(key);
                int count=info==null?0:info.optInt("count",0);
                String time=info==null?"":info.optString("clock","");
                v.setTextViewText(EMOJIS[idx],d.optString("emoji","📌"));
                v.setTextViewText(COUNTS[idx],count>99?"99+회":count+"회");
                v.setTextViewText(NAMES[idx],d.optString("name","기록"));
                v.setTextViewText(TIMES[idx],time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")?time:"＋ 기록");
                Intent tap=new Intent(ctx,TimeLogWidget.class).setAction(ACTION);
                tap.setData(Uri.parse("pokehabit-widget://record/"+Uri.encode(key)));
                tap.putExtra("event_id",key);
                PendingIntent pi=PendingIntent.getBroadcast(ctx,0,tap,
                    PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                v.setOnClickPendingIntent(CELLS[idx],pi);
            }
            Intent open=new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent op=PendingIntent.getActivity(ctx,widgetId,open,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.widget_title,op);
            mgr.updateAppWidget(widgetId,v);
        }
    }
}
