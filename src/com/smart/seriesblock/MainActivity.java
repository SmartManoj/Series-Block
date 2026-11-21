package com.smart.seriesblock;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.InputType;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView matches, checks, log;
    private EditText box;
    private Switch on;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** adb: am start -n com.smart.seriesblock/.MainActivity --es rules "+91140;+911401234500 100" [--ez on true] */
    private void applyExtras(Intent i) {
        SharedPreferences.Editor e = Store.prefs(this).edit();
        if (i.hasExtra("rules")) e.putString("rules", Store.join(Series.parse(i.getStringExtra("rules"))));
        if (i.hasExtra("on")) e.putBoolean("on", i.getBooleanExtra("on", true));
        e.commit();
        load();
        Store.log(this, "settings: " + (Store.enabled(this) ? "on; " : "off; ")
                + Store.prefs(this).getString("rules", "").replace("\n", "; "));
    }

    private static boolean hasExtras(Intent i) {
        return i != null && (i.hasExtra("rules") || i.hasExtra("on"));
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (hasExtras(i)) applyExtras(i);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView about = new TextView(this);
        about.setTextSize(15);
        about.setText("Declines an incoming call at once when its number is in a series below. "
                + "Use it for a spam caller that moves to the next number every day.\n\n"
                + "A declined call counts as busy, so \"forward when busy\" still sends it on.\n");
        col.addView(about);

        on = new Switch(this);
        on.setText("Block these number series");
        on.setPadding(0, pad / 2, 0, pad / 2);
        on.setOnCheckedChangeListener((v, c) -> {
            if (c == Store.enabled(this)) return;
            Store.prefs(this).edit().putBoolean("on", c).apply();
            Store.log(this, c ? "turned on" : "turned off");
        });
        col.addView(on);

        TextView label = new TextView(this);
        label.setTextSize(15);
        label.setText("Rules, one on each line. Start numbers with +91 or 0.\n"
                + "• A prefix, for example +91140, blocks every number that starts with it.\n"
                + "• A first number and a length, for example +911401234500 100, blocks 100 numbers from that number.");
        col.addView(label);

        box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.setMinLines(3);
        box.setTypeface(Typeface.MONOSPACE);
        box.setHint("+91140");
        col.addView(box);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            List<Series> list = Series.parse(box.getText().toString());
            Store.prefs(this).edit().putString("rules", Store.join(list)).apply();
            Store.log(this, "saved " + Store.join(list).replace("\n", "; "));
            load();
            Toast.makeText(this, "Saved " + list.size() + (list.size() == 1 ? " rule" : " rules"),
                    Toast.LENGTH_SHORT).show();
        });
        col.addView(save);

        matches = new TextView(this);
        matches.setTextSize(15);
        matches.setPadding(0, pad, 0, 0);
        col.addView(matches);

        checks = new TextView(this);
        checks.setTextSize(15);
        checks.setPadding(0, pad, 0, pad);
        col.addView(checks);

        TextView head = new TextView(this);
        head.setTextSize(15);
        head.setText("Recent calls and changes. The ring time counts from when the call arrived:");
        col.addView(head);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        col.addView(log);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        // Edge-to-edge (target 36): keep clear of the status bar, the navigation bar and the keyboard.
        sv.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(sv);

        load();
        if (hasExtras(getIntent())) applyExtras(getIntent());
    }

    private void load() {
        List<Series> list = Store.rules(this);
        box.setText(Store.join(list));
        on.setChecked(Store.enabled(this));
        List<String> d = new ArrayList<>();
        for (Series r : list) d.add(r.describe());
        matches.setText(list.isEmpty() ? "No rules yet." : "Declines:\n  " + String.join("\n  ", d));
    }

    private boolean callAccess() {
        String op = AppOpsManager.permissionToOp("android.permission.MANAGE_ONGOING_CALLS");
        if (op == null) return true;
        AppOpsManager ao = getSystemService(AppOpsManager.class);
        return ao.unsafeCheckOpNoThrow(op, Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            SharedPreferences p = Store.prefs(MainActivity.this);
            String seen = p.getString("seen", null);
            checks.setText((callAccess() ? "✅ Call access allowed\n"
                    : "❌ Call access not allowed. Run once:\nadb shell appops set " + getPackageName()
                      + " MANAGE_ONGOING_CALLS allow\n")
                    + (seen != null ? "✅ Last call seen: " + seen + "\n"
                      : "⏳ No call seen yet (normal until your next call)\n")
                    + "Declined so far: " + p.getInt("blocked", 0));
            log.setText(p.getString("log", ""));
            ui.postDelayed(this, 1000);
        }
    };
}
