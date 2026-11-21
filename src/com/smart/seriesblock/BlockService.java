package com.smart.seriesblock;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Non-UI InCallService. Telecom binds it once the MANAGE_ONGOING_CALLS app-op is allowed.
 * 1. An incoming call whose number is in a saved series is declined at once, the same as a tap
 *    on Decline. Why: some spam dialers move to the next number in their block every day, so
 *    blocking one number never stops them.
 * 2. Each incoming call gets one log line with its ring time, because Android's call log keeps
 *    only the talk time.
 */
public class BlockService extends InCallService {
    private final Map<Call, Series> declined = new WeakHashMap<>();

    private final Call.Callback callback = new Call.Callback() {
        @Override public void onStateChanged(Call call, int state) {
            if (state == Call.STATE_RINGING) check(call);
        }
    };

    @Override public void onCallAdded(Call call) {
        Store.prefs(this).edit().putString("seen", Store.now()).apply();
        call.registerCallback(callback);
        check(call);
    }

    @Override public void onCallRemoved(Call call) {
        call.unregisterCallback(callback);
        logRing(call);
        declined.remove(call);
    }

    private void check(Call call) {
        Call.Details d = call.getDetails();
        if (d.getState() != Call.STATE_RINGING) return;
        if (d.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;
        if (d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)) return;
        if (!Store.enabled(this) || declined.containsKey(call)) return;
        Series r = Series.match(number(d), Store.rules(this));
        if (r == null) return;
        declined.put(call, r);
        call.reject(false, null); // same as tapping Decline
        Store.prefs(this).edit().putInt("blocked", Store.prefs(this).getInt("blocked", 0) + 1).apply();
    }

    /** One line for each incoming call: ring time (from when the call arrived) and how it ended. */
    private void logRing(Call call) {
        Call.Details d = call.getDetails();
        if (d.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;
        if (d.getState() != Call.STATE_DISCONNECTED) return;
        long created = d.getCreationTimeMillis(), connected = d.getConnectTimeMillis();
        long now = System.currentTimeMillis();
        if (created <= 0) return;
        String how;
        Series r = declined.get(call);
        if (connected > 0) {
            long talk = Math.max(0, now - connected) / 1000;
            how = String.format(Locale.US, "answered, talk %d:%02d", talk / 60, talk % 60);
        } else if (r != null) {
            how = "declined by Series Block (" + r.text() + ")";
        } else {
            DisconnectCause c = d.getDisconnectCause();
            int code = c == null ? DisconnectCause.UNKNOWN : c.getCode();
            how = code == DisconnectCause.MISSED ? "missed"
                    : code == DisconnectCause.REJECTED ? "declined"
                    : code == DisconnectCause.ANSWERED_ELSEWHERE ? "answered elsewhere"
                    : "ended (cause " + code + ")";
        }
        double rang = Math.max(0, (connected > 0 ? connected : now) - created) / 1000.0;
        String num = number(d);
        Store.log(this, String.format(Locale.US, "rang %.1f s, %s  %s",
                rang, how, num == null ? "hidden number" : num));
    }

    private static String number(Call.Details d) {
        Uri h = d.getHandle();
        String n = h == null ? null : h.getSchemeSpecificPart();
        return n == null || n.isEmpty() ? null : n;
    }
}
