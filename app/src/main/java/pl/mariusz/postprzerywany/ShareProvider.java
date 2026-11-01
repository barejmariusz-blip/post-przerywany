package pl.mariusz.postprzerywany;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Read-only snapshot for Dziennik FBW (same signing key required — see manifest permission).
 * content://pl.mariusz.postprzerywany.share/summary → one row, column "json".
 */
public class ShareProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{"json"});
        c.addRow(new Object[]{Alarms.prefs(getContext()).getString("share", "")});
        return c;
    }

    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
