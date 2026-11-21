package io.github.jok86.pokehabitwrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if(intent!=null)AlarmStore.rescheduleAll(context);
    }
}
