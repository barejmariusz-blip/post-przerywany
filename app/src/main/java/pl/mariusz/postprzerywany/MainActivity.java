package pl.mariusz.postprzerywany;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Hosts the fasting tracker (assets/index.html) in a WebView; data lives in the WebView's localStorage. */
public class MainActivity extends Activity {

    private static final int FILE_REQ = 42;
    private static final int NOTIF_REQ = 43;
    private static final String GYM_URI = "content://pl.mariusz.dziennikfbw.share/summary";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingAct;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Alarms.channels(this);
        pendingAct = getIntent() != null ? getIntent().getStringExtra("act") : null;

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0D12);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Wybierz plik kopii"), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String act = intent.getStringExtra("act");
        if (act != null && web != null) {
            web.evaluateJavascript("window.onNativeAction&&window.onNativeAction('" + act.replaceAll("[^a-z]", "") + "')", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIF_REQ && web != null) {
            web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack?window.onBack():false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    /** Called from the page as window.Android.* */
    class Bridge {
        @JavascriptInterface
        public String saveFile(String name, String content, String mime) {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                    return "ok";
                } else {
                    File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                    return "ok:" + f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "fail";
            }
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Post Przerywany", text));
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(ms);
            } catch (Exception ignored) { }
        }

        /** [{id, at, title, body, act}] — replaces all reminders. */
        @JavascriptInterface
        public void schedule(String json) { Alarms.scheduleAll(MainActivity.this, json); }

        @JavascriptInterface
        public void ongoing(boolean show, double startMs, double endMs, String title, String text) {
            Alarms.ongoing(MainActivity.this, show, (long) startMs, (long) endMs, title, text);
        }

        /** "granted" | "denied" | "ask" */
        @JavascriptInterface
        public String notifyState() {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return "ask";
                return "denied";
            }
            return "granted";
        }

        @JavascriptInterface
        public void askNotify() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIF_REQ);
                } else {
                    openNotifySettings();
                }
            });
        }

        @JavascriptInterface
        public void openNotifySettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(i);
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public boolean exactOk() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            return am == null || am.canScheduleExactAlarms();
        }

        @JavascriptInterface
        public void openExactSettings() {
            if (Build.VERSION.SDK_INT < 31) return;
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            });
        }

        /** Action from a tapped notification on cold start ("start" / "end"), consumed once. */
        @JavascriptInterface
        public String takeAction() {
            String a = pendingAct;
            pendingAct = null;
            return a == null ? "" : a;
        }

        /** Snapshot that Dziennik FBW can read through ShareProvider. */
        @JavascriptInterface
        public void publish(String json) {
            Alarms.prefs(MainActivity.this).edit().putString("share", json).apply();
        }

        /** Dziennik FBW snapshot, "" when the app is missing or too old. */
        @JavascriptInterface
        public String peer() {
            try (Cursor c = getContentResolver().query(Uri.parse(GYM_URI), null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String v = c.getString(0);
                    return v == null ? "" : v;
                }
            } catch (Exception ignored) { }
            return "";
        }

        @JavascriptInterface
        public boolean peerInstalled() {
            try {
                getPackageManager().getPackageInfo("pl.mariusz.dziennikfbw", 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void openPeer() {
            runOnUiThread(() -> {
                Intent i = getPackageManager().getLaunchIntentForPackage("pl.mariusz.dziennikfbw");
                if (i != null) startActivity(i);
            });
        }
    }
}
