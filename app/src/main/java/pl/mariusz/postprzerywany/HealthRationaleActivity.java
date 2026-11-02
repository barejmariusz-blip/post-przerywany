package pl.mariusz.postprzerywany;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Shown by Health Connect when the user asks why the app wants step data. */
public class HealthRationaleActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView t = new TextView(this);
        t.setPadding(60, 120, 60, 60);
        t.setGravity(Gravity.START);
        t.setTextSize(17);
        t.setTextColor(0xFFEEF0F6);
        t.setBackgroundColor(0xFF0B0D12);
        t.setText("Post Przerywany czyta tylko liczbę kroków z Health Connect.\n\n"
                + "Używa jej, żeby pokazać kroki, przybliżone kalorie i wyzwania. "
                + "Dane zostają na tym telefonie — nic nie jest wysyłane do internetu.\n\n"
                + "Dostęp możesz cofnąć w każdej chwili w ustawieniach Health Connect.");
        setContentView(t);
    }
}
