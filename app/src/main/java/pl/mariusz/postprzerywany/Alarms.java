package pl.mariusz.postprzerywany;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Reminder scheduling and notifications. The page decides WHAT and WHEN; this class only executes. */
final class Alarms {

    static final String PREFS = "pp-native";
    static final String CH_REMIND = "remind";
    static final String CH_ONGOING = "ongoing";
    static final int ONGOING_ID = 1;
    private static final int MAX_SLOTS = 12;

    private Alarms() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel r = new NotificationChannel(CH_REMIND, "Przypomnienia o poście", NotificationManager.IMPORTANCE_HIGH);
        r.setDescription("Koniec postu, zamknięcie okna jedzenia");
        nm.createNotificationChannel(r);
        NotificationChannel o = new NotificationChannel(CH_ONGOING, "Trwający post", NotificationManager.IMPORTANCE_LOW);
        o.setDescription("Stały licznik w pasku powiadomień");
        o.setShowBadge(false);
        nm.createNotificationChannel(o);
    }

    /** json: [{id, at, title, body, act}] — replaces every previously scheduled reminder. */
    static void scheduleAll(Context c, String json) {
        prefs(c).edit().putString("alarms", json == null ? "[]" : json).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < MAX_SLOTS; i++) am.cancel(pending(c, i, null));
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length() && i < MAX_SLOTS; i++) {
                JSONObject a = arr.getJSONObject(i);
                long at = a.getLong("at");
                if (at <= now) continue;
                PendingIntent pi = pending(c, i, a);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
        } catch (Exception ignored) { }
    }

    private static PendingIntent pending(Context c, int slot, JSONObject a) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("pp.alarm." + slot);
        if (a != null) {
            i.putExtra("title", a.optString("title"));
            i.putExtra("body", a.optString("body"));
            i.putExtra("act", a.optString("act"));
            i.putExtra("slot", slot);
        }
        return PendingIntent.getBroadcast(c, slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openApp(Context c, String act, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (act != null && !act.isEmpty()) i.putExtra("act", act);
        i.setAction("pp.open." + req);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification.Builder builder(Context c, String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
    }

    static void notify(Context c, int slot, String title, String body, String act) {
        channels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b = builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFFF8A5B)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openApp(c, null, 100 + slot));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        if ("start".equals(act)) {
            b.addAction(new Notification.Action.Builder(null, "Zacznij post teraz", openApp(c, "start", 200 + slot)).build());
        } else if ("end".equals(act)) {
            b.addAction(new Notification.Action.Builder(null, "Zakończ post", openApp(c, "end", 200 + slot)).build());
        }
        nm.notify(10 + slot, b.build());
    }

    /** Persistent counter while a fast runs. startMs = fast start, endMs = goal. */
    static void ongoing(Context c, boolean show, long startMs, long endMs, String title, String text) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (show) {
            e.putBoolean("og", true).putLong("ogS", startMs).putLong("ogE", endMs)
                    .putString("ogT", title).putString("ogX", text);
        } else {
            e.putBoolean("og", false);
        }
        e.apply();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (!show) { nm.cancel(ONGOING_ID); return; }
        channels(c);
        boolean before = System.currentTimeMillis() < endMs;
        Notification.Builder b = builder(c, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFFF8A5B)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(before ? endMs : startMs)
                .setContentIntent(openApp(c, null, 1));
        if (before) b.setChronometerCountDown(true);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        nm.notify(ONGOING_ID, b.build());
    }

    /** After reboot / update / goal reached: rebuild what the page last asked for. */
    static void restore(Context c) {
        SharedPreferences p = prefs(c);
        scheduleAll(c, p.getString("alarms", "[]"));
        if (p.getBoolean("og", false)) {
            ongoing(c, true, p.getLong("ogS", 0), p.getLong("ogE", 0), p.getString("ogT", "Post trwa"), p.getString("ogX", ""));
        }
    }
}
