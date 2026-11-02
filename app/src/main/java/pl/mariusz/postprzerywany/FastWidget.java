package pl.mariusz.postprzerywany;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: live fasting clock, gradient ring, current stage, start / end button. */
public class FastWidget extends AppWidgetProvider {

    private static final long H = 3600_000L;
    private static final int[] SH = {0, 4, 8, 12, 16, 18, 21, 24, 36, 48, 72};
    private static final String[] SN = {"Trawienie", "Spadek cukru", "Zapasy glikogenu", "Spalanie tłuszczu", "Tłuszcz głównym paliwem",
            "Wczesna ketoza", "Ketoza", "Autofagia", "Hormon wzrostu rośnie", "Wysoki hormon wzrostu", "Głęboka odnowa"};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    /** Called whenever the app saves its state. */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, FastWidget.class));
        if (ids.length > 0) m.updateAppWidget(ids, build(c));
    }

    private static String hm(long t) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t)); }

    private static String dur(long ms) {
        ms = Math.max(0, ms);
        long h = ms / H, m = (ms % H) / 60000;
        return h > 0 ? h + " h " + m + " min" : m + " min";
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_fast);
        long now = System.currentTimeMillis();
        JSONObject s;
        try { s = new JSONObject(Alarms.prefs(c).getString("share", "{}")); } catch (Exception e) { s = new JSONObject(); }
        JSONObject cur = s.optJSONObject("cur"), win = s.optJSONObject("window"), plan = s.optJSONObject("plan");
        String label = plan != null ? plan.optString("label", "16:8") : "16:8";
        int fastH = plan != null ? plan.optInt("fastH", 16) : 16;
        int streak = s.optInt("streak", 0);
        float p;
        boolean eating = false;

        v.setViewVisibility(R.id.chrono, View.VISIBLE);
        v.setViewVisibility(R.id.big, View.GONE);
        if (cur != null) {
            long start = cur.optLong("start"), goal = (long) (cur.optDouble("goalH", fastH) * H), el = now - start;
            p = Math.min(1f, el / (float) goal);
            int st = 0;
            for (int i = 0; i < SH.length; i++) if (el >= SH[i] * H) st = i;
            v.setTextViewText(R.id.title, ("POST " + cur.optInt("goalH", fastH) + " H" + (streak > 1 ? "  ·  🔥 " + streak : "")));
            v.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - el, null, true);
            v.setChronometerCountDown(R.id.chrono, false);
            v.setTextViewText(R.id.sub, (el >= goal ? "Cel osiągnięty ✓" : "Cel o " + hm(start + goal) + " · zostało " + dur(goal - el)) + "\n" + SN[st]);
            v.setTextViewText(R.id.pct, Math.round(el * 100f / goal) + "%");
            v.setTextViewText(R.id.btn, "Zakończ post");
            v.setInt(R.id.btn, "setBackgroundResource", el >= goal ? R.drawable.widget_btn : R.drawable.widget_btn_end);
            v.setOnClickPendingIntent(R.id.btn, Alarms.openApp(c, "end", 301));
        } else if (win != null && now < win.optLong("end")) {
            eating = true;
            long ws = win.optLong("start"), we = win.optLong("end");
            p = Math.min(1f, (now - ws) / (float) Math.max(1, we - ws));
            v.setTextViewText(R.id.title, "OKNO JEDZENIA · " + label);
            v.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + (we - now), null, true);
            v.setChronometerCountDown(R.id.chrono, true);
            v.setTextViewText(R.id.sub, "Post od " + hm(we));
            v.setTextViewText(R.id.pct, "🍽️");
            v.setTextViewText(R.id.btn, "Zacznij post teraz");
            v.setInt(R.id.btn, "setBackgroundResource", R.drawable.widget_btn);
            v.setOnClickPendingIntent(R.id.btn, Alarms.openApp(c, "start", 302));
        } else {
            p = 0;
            v.setViewVisibility(R.id.chrono, View.GONE);
            v.setViewVisibility(R.id.big, View.VISIBLE);
            v.setTextViewText(R.id.big, label);
            v.setTextViewText(R.id.title, "GOTOWY NA POST" + (streak > 1 ? "  ·  🔥 " + streak : ""));
            v.setTextViewText(R.id.sub, fastH + " h postu");
            v.setTextViewText(R.id.pct, "");
            v.setTextViewText(R.id.btn, "Zacznij post " + fastH + " h");
            v.setInt(R.id.btn, "setBackgroundResource", R.drawable.widget_btn);
            v.setOnClickPendingIntent(R.id.btn, Alarms.openApp(c, "start", 302));
        }
        v.setImageViewBitmap(R.id.ring, ring(p, eating));
        v.setOnClickPendingIntent(R.id.root, Alarms.openApp(c, null, 300));
        return v;
    }

    private static Bitmap ring(float p, boolean eating) {
        int n = 240;
        float w = 22f, r = n / 2f - w / 2f - 6;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        RectF o = new RectF(n / 2f - r, n / 2f - r, n / 2f + r, n / 2f + r);
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0xFF262B38);
        cv.drawArc(o, 0, 360, false, track);
        if (p > 0.003f) {
            int[] cols = eating ? new int[]{0xFF34D399, 0xFF22D3EE, 0xFF22D3EE} : new int[]{0xFFFFB05C, 0xFFFF5E7A, 0xFFB26BFF};
            SweepGradient g = new SweepGradient(n / 2f, n / 2f, cols, new float[]{0f, Math.max(0.01f, p * 0.55f), Math.max(0.02f, p)});
            Matrix m = new Matrix();
            m.setRotate(-90, n / 2f, n / 2f);
            g.setLocalMatrix(m);
            Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(w);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setShader(g);
            cv.drawArc(o, -90, Math.max(1f, 360 * Math.min(p, 0.999f)), false, arc);
            double a = Math.toRadians(-90 + 360 * Math.min(p, 0.999f));
            Paint k = new Paint(Paint.ANTI_ALIAS_FLAG);
            k.setColor(Color.WHITE);
            cv.drawCircle(n / 2f + (float) (r * Math.cos(a)), n / 2f + (float) (r * Math.sin(a)), w * 0.42f, k);
        }
        return b;
    }
}
