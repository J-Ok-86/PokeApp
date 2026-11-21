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

/** Standalone home-screen widget; no WebView launch is required to record. */
public final class TimeLogWidget extends AppWidgetProvider {
    private static final String ACTION="io.github.jok86.pokehabitwrapper.WIDGET_LOG";
    private static final int[] VIEWS={R.id.widget_button_1,R.id.widget_button_2,R.id.widget_button_3,R.id.widget_button_4};
    @Override public void onReceive(Context ctx,Intent i){
        if(i!=null&&ACTION.equals(i.getAction())){
            String id=i.getStringExtra("event_id");
            if(id!=null)NativeLogStore.record(ctx,id);
            refresh(ctx);return;
        }
        super.onReceive(ctx,i);
    }
    @Override public void onUpdate(Context ctx,AppWidgetManager manager,int[] ids){refresh(ctx);}
    public static void refresh(Context ctx){
        AppWidgetManager mgr=AppWidgetManager.getInstance(ctx);if(mgr==null)return;
        ComponentName component=new ComponentName(ctx,TimeLogWidget.class);
        int[] ids=mgr.getAppWidgetIds(component);if(ids==null)return;
        JSONArray buttons=NativeLogStore.buttons(ctx);
        for(int widgetId:ids){
            RemoteViews views=new RemoteViews(ctx.getPackageName(),R.layout.time_log_widget);
            views.setTextViewText(R.id.widget_last,NativeLogStore.last(ctx));
            for(int idx=0;idx<4;idx++){
                int viewId=VIEWS[idx];JSONObject item=idx<buttons.length()?buttons.optJSONObject(idx):null;
                if(item==null){views.setViewVisibility(viewId,View.INVISIBLE);continue;}
                views.setViewVisibility(viewId,View.VISIBLE);
                views.setTextViewText(viewId,item.optString("emoji","📌")+"\n"+item.optString("name","기록"));
                String key=item.optString("id");
                Intent tapped=new Intent(ctx,TimeLogWidget.class).setAction(ACTION);
                tapped.setData(Uri.parse("pokehabit-widget://record/"+Uri.encode(key)));
                tapped.putExtra("event_id",key);
                PendingIntent pi=PendingIntent.getBroadcast(ctx,0,tapped,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                views.setOnClickPendingIntent(viewId,pi);
            }
            Intent open=new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent op=PendingIntent.getActivity(ctx,widgetId,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widget_title,op);
            mgr.updateAppWidget(widgetId,views);
        }
    }
}
