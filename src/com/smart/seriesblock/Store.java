package com.smart.seriesblock;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings and a short log in SharedPreferences, so they survive when Android stops the process. */
final class Store {
    private Store() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("p", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static List<Series> rules(Context c) {
        return Series.parse(prefs(c).getString("rules", ""));
    }

    /** The saved form of the rules, one on each line. */
    static String join(List<Series> rules) {
        List<String> t = new ArrayList<>();
        for (Series r : rules) t.add(r.text());
        return String.join("\n", t);
    }

    static String now() {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    static synchronized void log(Context c, String s) {
        SharedPreferences p = prefs(c);
        String[] lines = (now() + "  " + s + "\n" + p.getString("log", "")).split("\n");
        p.edit().putString("log", String.join("\n", Arrays.copyOf(lines, Math.min(lines.length, 100)))).apply();
        Log.i("SeriesBlock", s);
    }
}
