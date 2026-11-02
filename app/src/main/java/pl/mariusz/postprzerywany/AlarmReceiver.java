package pl.mariusz.postprzerywany;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        FastWidget.refresh(c);
        Alarms.notify(c, i.getIntExtra("slot", 0), i.getStringExtra("title"), i.getStringExtra("body"), i.getStringExtra("act"));
        // Goal reached: the ongoing counter switches from countdown to count-up
        if (Alarms.prefs(c).getBoolean("og", false)) {
            android.content.SharedPreferences p = Alarms.prefs(c);
            Alarms.ongoing(c, true, p.getLong("ogS", 0), p.getLong("ogE", 0), p.getString("ogT", "Post trwa"), p.getString("ogX", ""));
        }
    }
}
