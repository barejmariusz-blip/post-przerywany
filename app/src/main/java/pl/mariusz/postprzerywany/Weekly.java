package pl.mariusz.postprzerywany;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;

/** Sunday 19:00 summary of the week from both apps: fasts, workouts, steps, weight. */
public class Weekly extends BroadcastReceiver {

    private static final long H = 3600_000L, DAY = 24 * H;

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);
        if (!Alarms.prefs(c).getBoolean("weekly", true)) return;
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, 19);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        while (k.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || k.getTimeInMillis() <= System.currentTimeMillis()) k.add(Calendar.DAY_OF_MONTH, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, k.getTimeInMillis(), pi);
    }

    static void enable(Context c, boolean on) {
        Alarms.prefs(c).edit().putBoolean("weekly", on).apply();
        schedule(c);
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, Weekly.class).setAction("pp.weekly");
        return PendingIntent.getBroadcast(c, 900, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String n1(double v) { return String.format(Locale.forLanguageTag("pl-PL"), "%.1f", v); }
    private static String n0(long v) { return String.format(Locale.forLanguageTag("pl-PL"), "%,d", v).replace(' ', ' '); }

    static String text(Context c) {
        long now = System.currentTimeMillis(), from = now - 7 * DAY;
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject s = new JSONObject(Alarms.prefs(c).getString("share", "{}"));
            JSONArray fs = s.optJSONArray("fasts");
            int n = 0, hit = 0;
            double hours = 0;
            if (fs != null) for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.getJSONObject(i);
                long st = f.optLong("start"), en = f.optLong("end");
                if (en < from) continue;
                n++;
                hours += (en - st) / (double) H;
                if (en - st >= f.optDouble("goalH", 16) * H - 30000) hit++;
            }
            sb.append(n > 0 ? "⏱️ Posty: " + n + " (" + hit + " z celem) · średnio " + n1(hours / n) + " h" : "⏱️ W tym tygodniu bez zapisanych postów");

            JSONObject steps = s.optJSONObject("steps");
            if (steps != null) {
                long tot = 0; int days = 0;
                Calendar k = Calendar.getInstance();
                for (int i = 0; i < 7; i++) {
                    String key = String.format(Locale.US, "%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH));
                    long v = steps.optLong(key, -1);
                    if (v >= 0) { tot += v; days++; }
                    k.add(Calendar.DAY_OF_MONTH, -1);
                }
                if (days > 0 && tot > 0) sb.append("\n👟 Kroki: ").append(n0(tot)).append(" (śr. ").append(n0(tot / 7)).append("/dzień)");
            }

            JSONArray w = s.optJSONArray("wts");
            if (w != null && w.length() > 0) {
                JSONObject last = w.getJSONObject(w.length() - 1);
                double prev = Double.NaN;
                for (int i = w.length() - 1; i >= 0; i--) {
                    JSONObject x = w.getJSONObject(i);
                    if (x.optLong("d") <= now - 6 * DAY) { prev = x.optDouble("kg"); break; }
                }
                sb.append("\n⚖️ Waga: ").append(n1(last.optDouble("kg"))).append(" kg");
                if (!Double.isNaN(prev)) {
                    double d = last.optDouble("kg") - prev;
                    sb.append(" (").append(d > 0 ? "+" : d < 0 ? "−" : "±").append(n1(Math.abs(d))).append(" kg)");
                }
            }
        } catch (Exception ignored) { }

        try (Cursor cu = c.getContentResolver().query(Uri.parse("content://pl.mariusz.dziennikfbw.share/summary"), null, null, null, null)) {
            if (cu != null && cu.moveToFirst()) {
                JSONObject g = new JSONObject(cu.getString(0));
                JSONArray ss = g.optJSONArray("sessions");
                int n = 0; long kcal = 0;
                if (ss != null) for (int i = 0; i < ss.length(); i++) {
                    JSONObject x = ss.getJSONObject(i);
                    if (x.optLong("date") >= from) { n++; kcal += x.optLong("kcal"); }
                }
                sb.append("\n🏋️ Treningi: ").append(n).append(kcal > 0 ? " · ~" + n0(kcal) + " kcal" : "");
                JSONArray ch = g.optJSONArray("ch");
                if (ch != null && ch.length() > 0) sb.append("\n🎯 Aktywne wyzwania: ").append(ch.length());
            }
        } catch (Exception ignored) { }
        return sb.toString();
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        Alarms.channels(c);
        String body = text(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && !body.isEmpty()) {
            Notification.Builder b = Alarms.builder(c, Alarms.CH_REMIND)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(0xFFFF8A5B)
                    .setContentTitle("Twój tydzień 🗓️")
                    .setContentText(body.split("\n")[0])
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(Alarms.openApp(c, null, 950));
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);
            nm.notify(950, b.build());
        }
        schedule(c);
    }
}
