package io.github.jok86.pokehabitwrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id=intent==null?"":intent.getStringExtra("reminder_id");
        if(id!=null && !id.isEmpty())AlarmStore.fired(context,id);
    }
}
