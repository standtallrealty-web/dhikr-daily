package in.standtall.adhan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

final class Scheduler {
    static final String EXTRA_IDX = "idx";
    static final String EXTRA_AT = "at";
    static final String EXTRA_TEST = "test";

    /** Times for one calendar day: {fajr, dhuhr, asr, maghrib, isha} in epoch ms. */
    static long[] day(Context c, int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offsetDays);
        return PrayerTimes.forDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                Prefs.lat(c), Prefs.lon(c), Prefs.hanafi(c));
    }

    private static PendingIntent pi(Context c, int code, int idx, long at, boolean test) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra(EXTRA_IDX, idx);
        i.putExtra(EXTRA_AT, at);
        i.putExtra(EXTRA_TEST, test);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showPi(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Next enabled prayer after now, or null. Returns {idx, atMillis}. */
    static long[] next(Context c) {
        long now = System.currentTimeMillis() + 1000;
        for (int d = 0; d <= 2; d++) {
            long[] t = day(c, d);
            for (int i = 0; i < 5; i++) {
                if (Prefs.prayerOn(c, i) && t[i] > now) return new long[]{i, t[i]};
            }
        }
        return null;
    }

    /** Sets one exact alarm for the next enabled prayer. The alarm re-arms the one after it. */
    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, 1, 0, 0, false));
        if (!Prefs.master(c)) return;
        long[] n = next(c);
        if (n == null) return;
        int idx = (int) n[0];
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(n[1], showPi(c)), pi(c, 1, idx, n[1], false));
    }

    /** One-off test alarm in the given number of milliseconds. */
    static void scheduleTest(Context c, int idx, long delayMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long at = System.currentTimeMillis() + delayMs;
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi(c)), pi(c, 2, idx, at, true));
    }
}
