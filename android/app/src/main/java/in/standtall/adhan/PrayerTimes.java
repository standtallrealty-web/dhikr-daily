package in.standtall.adhan;

import java.util.Calendar;
import java.util.TimeZone;

/** Same calculation as the Dhikr Daily web app (Karachi 18/18, with the same minute adjustments). */
public final class PrayerTimes {
    public static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final double FAJR_ANGLE = 18, ISHA_ANGLE = 18;
    private static final int[] ADJ_MIN = {0, 1, 0, 1, 0}; // fajr, dhuhr, asr, maghrib, isha

    private static double rad(double d) { return d * Math.PI / 180; }
    private static double deg(double r) { return r * 180 / Math.PI; }
    private static double fixA(double a) { a = a - 360 * Math.floor(a / 360); return a < 0 ? a + 360 : a; }
    private static double fixH(double h) { h = h - 24 * Math.floor(h / 24); return h < 0 ? h + 24 : h; }
    private static double or(double v, double def) { return (Double.isNaN(v) || v == 0) ? def : v; }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0), B = 2 - A + Math.floor(A / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    /** returns {decl, eqt} */
    private static double[] sun(double jd) {
        double D = jd - 2451545.0, g = fixA(357.529 + 0.98560028 * D), q = fixA(280.459 + 0.98564736 * D);
        double L = fixA(q + 1.915 * Math.sin(rad(g)) + 0.020 * Math.sin(rad(2 * g))), e = 23.439 - 0.00000036 * D;
        double RA = deg(Math.atan2(Math.cos(rad(e)) * Math.sin(rad(L)), Math.cos(rad(L)))) / 15;
        double decl = deg(Math.asin(Math.sin(rad(e)) * Math.sin(rad(L))));
        return new double[]{decl, q / 15 - fixH(RA)};
    }

    private static final class Ctx {
        double jd, lat;
        double mid(double t) { return fixH(12 - sun(jd + t)[1]); }
        double ang(double a, double t, boolean ccw) {
            double dc = sun(jd + t)[0], noon = mid(t);
            double x = (-Math.sin(rad(a)) - Math.sin(rad(dc)) * Math.sin(rad(lat))) / (Math.cos(rad(dc)) * Math.cos(rad(lat)));
            if (x > 1 || x < -1) return Double.NaN;
            double v = deg(Math.acos(x)) / 15;
            return noon + (ccw ? -v : v);
        }
        double asr(double f, double t) {
            double dc = sun(jd + t)[0], a = -deg(Math.atan(1 / (f + Math.tan(rad(Math.abs(lat - dc))))));
            return ang(a, t, false);
        }
    }

    /** hours (local clock) for fajr, dhuhr, asr, sunset, isha */
    private static double[] solar(int y, int m, int d, double lat, double lon, double tz, double asrF) {
        Ctx c = new Ctx(); c.lat = lat; c.jd = julian(y, m, d) - lon / (15 * 24);
        double fajr = 5, sunrise = 6, dhuhr = 12, asr = 13, sunset = 18, isha = 18;
        for (int k = 0; k < 2; k++) {
            double nf = or(c.ang(FAJR_ANGLE, fajr / 24, true), 5);
            double ns = or(c.ang(0.833, sunrise / 24, true), 6);
            double nd = c.mid(dhuhr / 24);
            double na = or(c.asr(asrF, asr / 24), 13);
            double nss = or(c.ang(0.833, sunset / 24, false), 18);
            double ni = or(c.ang(ISHA_ANGLE, isha / 24, false), 18);
            fajr = nf; sunrise = ns; dhuhr = nd; asr = na; sunset = nss; isha = ni;
        }
        double adj = tz - lon / 15;
        return new double[]{fajr + adj, dhuhr + adj, asr + adj, sunset + adj, isha + adj};
    }

    /** Epoch millis for {fajr, dhuhr, asr, maghrib, isha} on the given local calendar date. */
    public static long[] forDate(int y, int month1, int d, double lat, double lon, boolean hanafi) {
        TimeZone tzone = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tzone);
        cal.clear(); cal.set(y, month1 - 1, d, 0, 0, 0);
        long mid = cal.getTimeInMillis();
        double tz = tzone.getOffset(mid) / 3600000.0;
        double[] s = solar(y, month1, d, lat, lon, tz, hanafi ? 2 : 1);
        long[] r = new long[5];
        for (int i = 0; i < 5; i++) r[i] = mid + Math.round(s[i] * 3600000.0 - 0.0) + ADJ_MIN[i] * 60000L;
        return r;
    }
}
